package com.z.takatuf.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.SavedRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.ClosedReason
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.toAppError
import com.z.takatuf.domain.SkillMatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

enum class KindFilter { All, Projects, Teams, Events }

enum class SortOrder { BestMatch, Newest, ClosingSoon }

data class DiscoverFilters(
    val skills: Set<String> = emptySet(),
    val maxHours: Int? = null,
    val includeClosed: Boolean = false,
) {
    val activeCount: Int get() = skills.size + (if (maxHours != null) 1 else 0) + (if (includeClosed) 1 else 0)
}

private data class Controls(
    val query: String = "",
    val kind: KindFilter = KindFilter.All,
    val savedOnly: Boolean = false,
    val filters: DiscoverFilters = DiscoverFilters(),
    val sort: SortOrder = SortOrder.BestMatch,
)

data class DiscoverUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val query: String = "",
    val kind: KindFilter = KindFilter.All,
    val savedOnly: Boolean = false,
    val filters: DiscoverFilters = DiscoverFilters(),
    val sort: SortOrder = SortOrder.BestMatch,
    val items: List<Listing> = emptyList(),
    val savedIds: Set<String> = emptySet(),
    val myApplications: Map<String, ApplicationStatus> = emptyMap(),
    val mySkills: List<String> = emptyList(),
    val myUid: String = "",
    /** Skills the open listings ask for, most requested first; offered as filters. */
    val wantedSkills: List<String> = emptyList(),
    /** True when nothing at all has been posted yet (as opposed to nothing matching). */
    val feedEmpty: Boolean = false,
)

private sealed interface Feed {
    data object Loading : Feed
    data class Loaded(val listings: List<Listing>) : Feed
    data class Failed(val error: AppError) : Feed
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModel(
    private val session: Session,
    private val listings: ListingRepository,
    private val saved: SavedRepository,
    applications: ApplicationRepository,
) : ViewModel() {

    private val uid = session.uid.orEmpty()
    private val controls = MutableStateFlow(Controls())
    private val retry = MutableStateFlow(0)

    // Shared so the feed and saved listeners are attached once, however many times they're combined.
    private val feed: Flow<Feed> = retry.flatMapLatest {
        listings.observeFeed()
            .map<List<Listing>, Feed> { Feed.Loaded(it) }
            .onStart { emit(Feed.Loading) }
            .catch { emit(Feed.Failed(it.toAppError())) }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val savedIds: Flow<Set<String>> = saved.observeIds(uid)
        .catch { emit(emptySet()) }
        .onStart { emit(emptySet()) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val myApplications: Flow<Map<String, ApplicationStatus>> =
        applications.observeMine(uid)
            .map { list -> list.associate { it.listingId to it.status } }
            .catch { emit(emptyMap()) }
            .onStart { emit(emptyMap()) }

    /** Saved listings that are older than the feed window are fetched on their own. */
    private val savedOutsideFeed: Flow<List<Listing>> =
        combine(savedIds, feed) { ids, f -> ids - (f as? Feed.Loaded)?.listings.orEmpty().map { it.id }.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { missing ->
                flow { emit(if (missing.isEmpty()) emptyList() else attempt { listings.getMany(missing) }.getOrDefault(emptyList())) }
            }
            .onStart { emit(emptyList()) }

    private val inputs = combine(feed, savedIds, myApplications, savedOutsideFeed) { f, ids, apps, extra ->
        Inputs(f, ids, apps, extra)
    }

    val state: StateFlow<DiscoverUiState> =
        combine(inputs, controls, session.passport) { input, c, passport ->
            val mySkills = passport?.skills.orEmpty().map { it.name }
            val all = (input.feed as? Feed.Loaded)?.listings.orEmpty()
            DiscoverUiState(
                loading = input.feed is Feed.Loading,
                error = (input.feed as? Feed.Failed)?.error,
                query = c.query,
                kind = c.kind,
                savedOnly = c.savedOnly,
                filters = c.filters,
                sort = c.sort,
                items = arrange(all, input, c, mySkills),
                savedIds = input.savedIds,
                myApplications = input.myApplications,
                mySkills = mySkills,
                myUid = uid,
                wantedSkills = wantedSkills(all),
                feedEmpty = input.feed is Feed.Loaded && all.isEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    private data class Inputs(
        val feed: Feed,
        val savedIds: Set<String>,
        val myApplications: Map<String, ApplicationStatus>,
        val savedOutsideFeed: List<Listing>,
    )

    fun onQuery(value: String) = controls.update { it.copy(query = value) }
    fun onKind(value: KindFilter) = controls.update { it.copy(kind = value) }
    fun onSavedOnly(value: Boolean) = controls.update { it.copy(savedOnly = value) }
    fun onFilters(value: DiscoverFilters) = controls.update { it.copy(filters = value) }
    fun onSort(value: SortOrder) = controls.update { it.copy(sort = value) }
    fun clearAll() = controls.update { Controls(sort = it.sort) }
    fun retry() = retry.update { it + 1 }

    fun toggleSave(listingId: String, save: Boolean) {
        viewModelScope.launch { attempt { saved.setSaved(uid, listingId, save) } }
    }

    private fun arrange(all: List<Listing>, input: Inputs, c: Controls, mySkills: List<String>): List<Listing> {
        val now = Instant.now()
        var list = if (c.savedOnly) {
            (all + input.savedOutsideFeed).distinctBy { it.id }.filter { it.id in input.savedIds }
        } else {
            all
        }
        list = list.filter {
            when (c.kind) {
                KindFilter.All -> true
                KindFilter.Projects -> it.kind == ListingKind.Project
                KindFilter.Teams -> it.kind == ListingKind.Team
                KindFilter.Events -> it.kind == ListingKind.Event
            }
        }
        // Saved items always show, closed or not, so they can be found and removed.
        if (!c.filters.includeClosed && !c.savedOnly) {
            list = list.filter { it.closedReason(now).let { r -> r == null || r == ClosedReason.RegistrationClosed } }
        }
        val terms = c.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isNotEmpty()) {
            list = list.filter { listing ->
                val haystack = searchText(listing)
                terms.all { haystack.contains(it) }
            }
        }
        if (c.filters.skills.isNotEmpty()) {
            list = list.filter { SkillMatcher.covered(c.filters.skills, it.skillsWanted).isNotEmpty() }
        }
        c.filters.maxHours?.let { max -> list = list.filter { l -> l.recruitment == null || l.recruitment.hoursPerWeek <= max } }

        return when (c.sort) {
            SortOrder.Newest -> list.sortedByDescending { it.createdAt ?: Instant.MAX }
            SortOrder.ClosingSoon -> list.sortedWith(
                compareBy<Listing> { it.closedReason(now) != null }
                    .thenBy { it.closesAt?.takeIf { d -> d.isAfter(now) } ?: Instant.MAX },
            )
            SortOrder.BestMatch -> list.sortedWith(
                compareBy<Listing> { it.closedReason(now) != null }
                    .thenByDescending { if (it.ownerId == uid) -1 else SkillMatcher.covered(mySkills, it.skillsWanted).size }
                    .thenByDescending { it.createdAt ?: Instant.MAX },
            )
        }
    }

    private fun searchText(listing: Listing): String = buildString {
        append(listing.title).append(' ')
        append(listing.description).append(' ')
        append(listing.ownerName).append(' ')
        listing.recruitment?.let { r ->
            append(r.outcome).append(' ')
            r.roles.forEach { role -> append(role.title).append(' ').append(role.skills.joinToString(" ")).append(' ') }
        }
        listing.linkedEvent?.let { append(it.title).append(' ').append(it.organizer).append(' ') }
        listing.event?.let { append(it.organizer).append(' ').append(it.location).append(' ').append(it.type.key).append(' ') }
    }.lowercase()

    private fun wantedSkills(all: List<Listing>): List<String> {
        val now = Instant.now()
        return all.asSequence()
            .filter { it.closedReason(now) == null }
            .flatMap { it.skillsWanted.asSequence() }
            .groupBy { SkillMatcher.normalize(it) }
            .entries
            .sortedByDescending { it.value.size }
            .take(24)
            .map { it.value.first() }
    }
}
