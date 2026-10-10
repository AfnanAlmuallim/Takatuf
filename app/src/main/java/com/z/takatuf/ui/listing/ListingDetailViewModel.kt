package com.z.takatuf.ui.listing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.SavedRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.ListingStatus
import com.z.takatuf.data.model.Visibility
import com.z.takatuf.data.toAppError
import com.z.takatuf.ui.common.UserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApplicantCounts(val active: Int, val new: Int, val shortlisted: Int)

data class ListingDetailUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val listing: Listing? = null,
    /** The live event a team request is linked to. */
    val event: Listing? = null,
    /** Team requests linked to this event. */
    val teams: List<Listing> = emptyList(),
    val myApplication: Application? = null,
    val saved: Boolean = false,
    val myUid: String = "",
    val mySkills: List<String> = emptyList(),
    val myHours: Int = 0,
    val myVisibility: Visibility = Visibility.Everyone,
    val applicantCounts: ApplicantCounts? = null,
    val applying: Boolean = false,
    val applyError: AppError? = null,
    /** Bumped after a successful application so the sheet closes. */
    val appliedAt: Long = 0,
    val busy: Boolean = false,
    val message: UserMessage? = null,
) {
    val isOwner: Boolean get() = listing != null && listing.ownerId == myUid
    val notFound: Boolean get() = !loading && error == null && listing == null
}

private data class Transient(
    val applying: Boolean = false,
    val applyError: AppError? = null,
    val appliedAt: Long = 0,
    val busy: Boolean = false,
    val message: UserMessage? = null,
)

private sealed interface Loaded {
    data object Loading : Loaded
    data class Ok(val listing: Listing?) : Loaded
    data class Failed(val error: AppError) : Loaded
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListingDetailViewModel(
    private val listingId: String,
    private val session: Session,
    private val listings: ListingRepository,
    private val applications: ApplicationRepository,
    private val saved: SavedRepository,
) : ViewModel() {

    private val uid = session.uid.orEmpty()
    private val retry = MutableStateFlow(0)
    private val transient = MutableStateFlow(Transient())

    private val loaded: Flow<Loaded> = retry.flatMapLatest {
        listings.observe(listingId)
            .map<Listing?, Loaded> { Loaded.Ok(it) }
            .onStart { emit(Loaded.Loading) }
            .catch { emit(Loaded.Failed(it.toAppError())) }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val listing: Flow<Listing?> = loaded.map { (it as? Loaded.Ok)?.listing }

    private val event: Flow<Listing?> = listing.map { it?.linkedEvent?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id.isNullOrEmpty()) flowOf(null) else listings.observe(id).catch { emit(null) } }

    private val teams: Flow<List<Listing>> = listing.map { it?.kind == ListingKind.Event }
        .distinctUntilChanged()
        .flatMapLatest { isEvent ->
            if (isEvent) listings.observeTeamsFor(listingId).catch { emit(emptyList()) } else flowOf(emptyList())
        }

    private val myApplication: Flow<Application?> =
        applications.observe(listingId, uid).catch { emit(null) }.onStart { emit(null) }

    private val isSaved: Flow<Boolean> =
        saved.observeIds(uid).map { listingId in it }.catch { emit(false) }.onStart { emit(false) }

    private val counts: Flow<ApplicantCounts?> = listing
        .map { it != null && it.ownerId == uid && it.kind.recruits }
        .distinctUntilChanged()
        .flatMapLatest { owner ->
            if (!owner) {
                flowOf(null)
            } else {
                applications.observeForListing(listingId)
                    .map<List<Application>, ApplicantCounts?> { apps ->
                        ApplicantCounts(
                            active = apps.count { it.status.isActive },
                            new = apps.count { it.isNew },
                            shortlisted = apps.count { it.status == ApplicationStatus.Shortlisted },
                        )
                    }
                    .catch { emit(null) }
            }
        }

    private val core = combine(loaded, event, teams, myApplication, isSaved) { l, e, t, app, s ->
        Core(l, e, t, app, s)
    }

    private data class Core(
        val loaded: Loaded,
        val event: Listing?,
        val teams: List<Listing>,
        val myApplication: Application?,
        val saved: Boolean,
    )

    val state: StateFlow<ListingDetailUiState> =
        combine(core, counts, session.passport, session.state, transient) { c, counts, passport, _, t ->
            ListingDetailUiState(
                loading = c.loaded is Loaded.Loading,
                error = (c.loaded as? Loaded.Failed)?.error,
                listing = (c.loaded as? Loaded.Ok)?.listing,
                event = c.event,
                teams = c.teams,
                myApplication = c.myApplication,
                saved = c.saved,
                myUid = uid,
                mySkills = passport?.skills.orEmpty().map { it.name },
                myHours = passport?.availability?.hoursPerWeek ?: 0,
                myVisibility = session.profile?.visibility ?: Visibility.Everyone,
                applicantCounts = counts,
                applying = t.applying,
                applyError = t.applyError,
                appliedAt = t.appliedAt,
                busy = t.busy,
                message = t.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListingDetailUiState())

    fun retry() = retry.update { it + 1 }

    fun messageShown() = transient.update { it.copy(message = null) }

    fun clearApplyError() = transient.update { it.copy(applyError = null) }

    fun toggleSave() {
        val save = !state.value.saved
        viewModelScope.launch { attempt { saved.setSaved(uid, listingId, save) } }
    }

    fun apply(roleId: String, contribution: String, hoursPerWeek: Int, note: String) {
        val listing = state.value.listing ?: return
        val applicant = session.profile ?: return
        val role = listing.recruitment?.roles?.firstOrNull { it.id == roleId } ?: return
        if (transient.value.applying) return
        transient.update { it.copy(applying = true, applyError = null) }
        viewModelScope.launch {
            attempt { applications.apply(listing, applicant, role, contribution, hoursPerWeek, note) }
                .onSuccess {
                    transient.update {
                        it.copy(
                            applying = false,
                            appliedAt = System.nanoTime(),
                            message = UserMessage(R.string.msg_application_sent),
                        )
                    }
                }
                .onFailure { e ->
                    val error = e.toAppError().let {
                        // The rules refuse applications once a listing closes.
                        if (it == AppError.PermissionDenied) AppError.NotAccepting else it
                    }
                    transient.update { it.copy(applying = false, applyError = error) }
                }
        }
    }

    fun withdraw() = runBusy(R.string.msg_application_withdrawn) { applications.withdraw(listingId, uid) }

    fun setRecruitmentOpen(open: Boolean) = runBusy(
        if (open) R.string.msg_recruitment_reopened else R.string.msg_recruitment_closed,
    ) {
        listings.setStatus(listingId, if (open) ListingStatus.Open else ListingStatus.Closed)
    }

    private fun runBusy(successMessage: Int, block: suspend () -> Unit) {
        if (transient.value.busy) return
        transient.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = attempt { block() }
            transient.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { UserMessage(successMessage) },
                        onFailure = { e -> UserMessage(e.toAppError().message) },
                    ),
                )
            }
        }
    }

}
