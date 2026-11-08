package com.z.takatuf.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.toAppError
import com.z.takatuf.ui.common.UserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class OwnedListing(
    val listing: Listing,
    val active: Int,
    val new: Int,
    val shortlisted: Int,
    val offered: Int,
)

data class ActivityUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val applications: List<Application> = emptyList(),
    val owned: List<OwnedListing> = emptyList(),
    /** The application an answer is being sent for. */
    val busyListingId: String? = null,
    /** Set after accepting an offer, so the screen can open the team. */
    val joinedListingId: String? = null,
    val message: UserMessage? = null,
) {
    val newApplicants: Int get() = owned.sumOf { it.new }
    val activeApplications: Int get() = applications.count { it.status.isActive }

    /** Offers and reconfirmation requests waiting for this student's answer. */
    val needsAnswer: Int get() = applications.count { it.needsAnswer(Instant.now()) }
}

/** The applicant has to respond: a live offer or a request to confirm interest. */
fun Application.needsAnswer(now: Instant): Boolean = hasLiveOffer(now) || status == ApplicationStatus.Reconfirm

private data class Transient(
    val busyListingId: String? = null,
    val joinedListingId: String? = null,
    val message: UserMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val session: Session,
    listings: ListingRepository,
    private val applications: ApplicationRepository,
) : ViewModel() {

    private val uid = session.uid.orEmpty()
    private val retry = MutableStateFlow(0)
    private val transient = MutableStateFlow(Transient())

    private val data = retry.flatMapLatest {
        combine(
            applications.observeMine(uid),
            listings.observeOwnedBy(uid),
            applications.observeIncoming(uid),
        ) { mine, owned, incoming ->
            val byListing = incoming.groupBy { it.listingId }
            ActivityUiState(
                loading = false,
                applications = mine.sortedByDescending { it.needsAnswer(Instant.now()) },
                owned = owned.map { listing ->
                    val apps = byListing[listing.id].orEmpty()
                    OwnedListing(
                        listing = listing,
                        active = apps.count { it.status.isActive },
                        new = apps.count { it.isNew },
                        shortlisted = apps.count { it.status == ApplicationStatus.Shortlisted },
                        offered = apps.count { it.status == ApplicationStatus.Offered },
                    )
                },
            )
        }.catch { emit(ActivityUiState(loading = false, error = it.toAppError())) }
    }

    val state: StateFlow<ActivityUiState> = combine(data, transient) { d, t ->
        d.copy(busyListingId = t.busyListingId, joinedListingId = t.joinedListingId, message = t.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    fun retry() = retry.update { it + 1 }

    fun messageShown() = transient.update { it.copy(message = null) }

    fun joinHandled() = transient.update { it.copy(joinedListingId = null) }

    fun acceptOffer(application: Application) {
        val me = session.profile ?: return
        run(application, R.string.msg_joined_team, joined = true) { applications.acceptOffer(application.listingId, me) }
    }

    fun declineOffer(application: Application) =
        run(application, R.string.msg_offer_declined) { applications.declineOffer(application.listingId, uid) }

    fun confirmInterest(application: Application) =
        run(application, R.string.msg_interest_confirmed) { applications.confirmInterest(application.listingId, uid) }

    fun withdraw(application: Application) =
        run(application, R.string.msg_application_withdrawn) { applications.withdraw(application.listingId, uid) }

    private fun run(application: Application, success: Int, joined: Boolean = false, block: suspend () -> Unit) {
        if (transient.value.busyListingId != null) return
        transient.update { it.copy(busyListingId = application.listingId) }
        viewModelScope.launch {
            val result = attempt { block() }
            transient.update {
                it.copy(
                    busyListingId = null,
                    joinedListingId = if (joined && result.isSuccess) application.listingId else it.joinedListingId,
                    message = result.fold(
                        onSuccess = { UserMessage(success, listOf(application.listingTitle)) },
                        onFailure = { e -> UserMessage(e.toAppError().message) },
                    ),
                )
            }
        }
    }
}

/**
 * Badge for the Activity tab: new applicants to review plus offers and reconfirmation requests
 * waiting for an answer.
 */
fun ApplicationRepository.attentionCount(uid: String): Flow<Int> =
    combine(
        observeIncoming(uid).map { apps -> apps.count { it.isNew } }.catch { emit(0) }.onStart { emit(0) },
        observeMine(uid).map { apps -> apps.count { it.needsAnswer(Instant.now()) } }.catch { emit(0) }.onStart { emit(0) },
    ) { incoming, mine -> incoming + mine }
