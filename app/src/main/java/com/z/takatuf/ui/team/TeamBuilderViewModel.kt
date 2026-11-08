package com.z.takatuf.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.ProfileRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.toAppError
import com.z.takatuf.domain.ApplicantFit
import com.z.takatuf.domain.Recommendation
import com.z.takatuf.domain.TeamContext
import com.z.takatuf.domain.TeamMatcher
import com.z.takatuf.domain.TeamOption
import com.z.takatuf.ui.common.UserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class TeamBuilderUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val listing: Listing? = null,
    val recommendation: Recommendation? = null,
    val selected: Int = 0,
    /** People the owner pinned; they stay in every suggestion (US11). */
    val kept: Set<String> = emptySet(),
    /** Whether "Show other options" has been used, so the owner can go back. */
    val exploring: Boolean = false,
    /** Live offers to people outside the chosen option, which compete for the same places. */
    val otherOffers: Int = 0,
    val sending: Boolean = false,
    val sentAt: Long = 0L,
    val message: UserMessage? = null,
    val round: Int = 0,
) {
    val option: TeamOption? get() = recommendation?.options?.getOrNull(selected)

    /** Picks still waiting for an offer: anyone not holding a live one. */
    val toOffer: List<ApplicantFit>
        get() {
            val now = Instant.now()
            return option?.picks.orEmpty().filter { !it.application.hasLiveOffer(now) }
        }
}

private data class Choices(
    val selected: Int = 0,
    val kept: List<String> = emptyList(),
    val avoid: Set<String> = emptySet(),
    /** Bumped on each "Show other options", so the result can be compared with what came before. */
    val round: Int = 0,
)

private data class BuilderExtras(val sending: Boolean = false, val sentAt: Long = 0L, val message: UserMessage? = null)

/**
 * Builds team suggestions from the owner's current applicants. Nothing here changes who is on
 * the team: the only write is sending offers, which applicants still have to accept (US10).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamBuilderViewModel(
    private val listingId: String,
    private val session: Session,
    listings: ListingRepository,
    private val applications: ApplicationRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    private val retry = MutableStateFlow(0)
    private val choices = MutableStateFlow(Choices())
    private val extras = MutableStateFlow(BuilderExtras())
    private val people = PeopleCache(viewModelScope, profiles)
    private val uid = session.uid.orEmpty()

    private sealed interface Data {
        data object Loading : Data
        data class Loaded(val listing: Listing?, val applications: List<Application>) : Data
        data class Failed(val error: AppError) : Data
    }

    private val data = retry.flatMapLatest {
        combine(listings.observe(listingId), applications.observeForListing(listingId)) { listing, apps ->
            Data.Loaded(listing, apps) as Data
        }
            .onEach {
                if (it is Data.Loaded) {
                    people.request(it.applications.filter { app -> app.status.isActive }.map { app -> app.applicantId })
                    it.listing?.let { listing -> people.request(listing.memberIds + listing.ownerId) }
                }
            }
            .onStart { emit(Data.Loading) }
            .catch { emit(Data.Failed(it.toAppError())) }
    }

    val state: StateFlow<TeamBuilderUiState> =
        combine(data, people.people, session.passport, choices, extras) { d, p, myPassport, c, e ->
            val loaded = d as? Data.Loaded
            val listing = loaded?.listing
            val recommendation = listing?.let {
                val apps = loaded.applications
                val fits = fitsFor(it, apps, p, uid, myPassport).values.toList()
                TeamMatcher.recommend(TeamContext(it, teamSkills(it, p, uid, myPassport)), fits, c.kept.toCollection(LinkedHashSet()), c.avoid)
            }
            val selected = c.selected.coerceIn(0, ((recommendation?.options?.size ?: 1) - 1).coerceAtLeast(0))
            val chosen = recommendation?.options?.getOrNull(selected)?.pickIds.orEmpty()
            val now = Instant.now()
            TeamBuilderUiState(
                loading = d is Data.Loading,
                error = (d as? Data.Failed)?.error,
                listing = listing,
                recommendation = recommendation,
                selected = selected,
                kept = c.kept.toSet(),
                exploring = c.avoid.isNotEmpty(),
                otherOffers = loaded?.applications.orEmpty().count { it.hasLiveOffer(now) && it.applicantId !in chosen },
                sending = e.sending,
                sentAt = e.sentAt,
                message = e.message,
                round = c.round,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamBuilderUiState())

    fun select(index: Int) = choices.update { it.copy(selected = index) }

    /** Pins or unpins someone. Pinned people stay in every regenerated option. */
    fun toggleKeep(applicantId: String) = choices.update {
        val kept = if (applicantId in it.kept) it.kept - applicantId else it.kept + applicantId
        it.copy(kept = kept, avoid = it.avoid - applicantId)
    }

    /**
     * Looks for different combinations: everyone in the current option who isn't pinned is
     * passed over where someone comparable exists.
     */
    fun showOtherOptions() {
        val current = state.value
        val option = current.option ?: return
        val before = current.recommendation?.options?.map { it.pickIds }.orEmpty().toSet()
        val round = choices.value.round + 1
        choices.update { it.copy(avoid = it.avoid + (option.pickIds - current.kept), selected = 0, round = round) }
        // Say so when there is nothing new to see, rather than silently showing the same teams.
        viewModelScope.launch {
            val next = state.first { it.round == round }
            val after = next.recommendation?.options?.map { it.pickIds }.orEmpty().toSet()
            if (after == before) extras.update { it.copy(message = UserMessage(R.string.msg_no_other_options)) }
        }
    }

    fun resetOptions() = choices.update { it.copy(avoid = emptySet(), selected = 0) }

    fun sendOffers(days: Int) {
        val ids = state.value.toOffer.map { it.applicantId }
        if (ids.isEmpty() || extras.value.sending) return
        extras.update { it.copy(sending = true) }
        viewModelScope.launch {
            val result = attempt { applications.sendOffers(listingId, ids, days) }
            extras.update {
                it.copy(
                    sending = false,
                    sentAt = if (result.isSuccess) System.nanoTime() else it.sentAt,
                    message = result.fold(
                        onSuccess = { UserMessage(R.string.msg_offers_sent) },
                        onFailure = { e -> UserMessage(e.toAppError().message) },
                    ),
                )
            }
        }
    }

    fun retry() = retry.update { it + 1 }

    fun messageShown() = extras.update { it.copy(message = null) }
}
