package com.z.takatuf.ui.applicants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.NotificationRepository
import com.z.takatuf.data.ProfileRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
import com.z.takatuf.data.model.PassportAccess
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.toAppError
import com.z.takatuf.domain.ApplicantFit
import com.z.takatuf.ui.common.UserMessage
import com.z.takatuf.ui.team.PeopleCache
import com.z.takatuf.ui.team.fitsFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ApplicantDetailUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val application: Application? = null,
    val listing: Listing? = null,
    val profile: Profile? = null,
    val passport: PassportAccess? = null,
    val fit: ApplicantFit? = null,
    val busy: Boolean = false,
    /** Changes each time an action succeeds, so a sheet can close itself. */
    val done: Long = 0L,
    val message: UserMessage? = null,
)

private data class Extras(val busy: Boolean = false, val done: Long = 0L, val message: UserMessage? = null)

class ApplicantDetailViewModel(
    private val listingId: String,
    private val applicantId: String,
    private val session: Session,
    listings: ListingRepository,
    private val applications: ApplicationRepository,
    profiles: ProfileRepository,
    private val notifications: NotificationRepository,
) : ViewModel() {

    private val extras = MutableStateFlow(Extras())
    private val people = PeopleCache(viewModelScope, profiles)
    private val uid = session.uid.orEmpty()
    private var markedReviewed = false

    private val listing = listings.observeWithLiveEvent(listingId)
        .onEach { it?.let { l -> people.request(l.memberIds + l.ownerId) } }
        .catch { emit(null) }
        .onStart { emit(null) }

    private val application = applications.observe(listingId, applicantId)
        .onEach { app -> if (app != null && !app.reviewed) markReviewed() }
        .map { Result.success(it) }
        .catch { emit(Result.failure(it)) }

    init {
        people.request(listOf(applicantId))
    }

    val state: StateFlow<ApplicantDetailUiState> =
        combine(application, listing, people.people, session.passport, extras) { result, l, p, myPassport, e ->
            val app = result.getOrNull()
            ApplicantDetailUiState(
                loading = false,
                error = result.exceptionOrNull()?.toAppError() ?: if (app == null) AppError.NotFound else null,
                application = app,
                listing = l,
                profile = p.profiles[applicantId],
                passport = p.passports[applicantId],
                fit = if (app != null && l != null) fitsFor(l, listOf(app), p, uid, myPassport)[applicantId] else null,
                busy = e.busy,
                done = e.done,
                message = e.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplicantDetailUiState())

    /** Opening an application clears its "new" mark for the owner. */
    private fun markReviewed() {
        if (markedReviewed) return
        markedReviewed = true
        viewModelScope.launch { attempt { applications.markReviewed(listingId, applicantId) } }
    }

    fun setShortlisted(shortlisted: Boolean) = run(if (shortlisted) R.string.msg_shortlisted else R.string.msg_unshortlisted) {
        applications.setShortlisted(listingId, applicantId, shortlisted)
    }

    /** Offers a place; it stays open for [days] days unless the applicant answers first. */
    fun sendOffer(days: Int) {
        val actionId = UUID.randomUUID().toString()
        run(R.string.msg_offer_sent, after = { tell(actionId, NotificationType.OfferReceived) }) {
            applications.sendOffers(listingId, listOf(applicantId), days, uid)
        }
    }

    /** Not going ahead with this application; the applicant is told (FR14, FR19). */
    fun notSelected() {
        val actionId = UUID.randomUUID().toString()
        run(R.string.msg_not_selected, after = { tell(actionId, NotificationType.ApplicationDeclined) }) {
            applications.markNotSelected(listingId, applicantId)
        }
    }

    fun cancelOffer() {
        val actionId = UUID.randomUUID().toString()
        run(R.string.msg_offer_cancelled, after = { tell(actionId, NotificationType.OfferCancelled) }) {
            applications.cancelOffer(listingId, applicantId, uid)
        }
    }

    /** Lets the applicant know; never holds up the action itself (US19). */
    private fun tell(actionId: String, type: NotificationType) {
        val me = session.profile ?: return
        val title = state.value.listing?.title ?: state.value.application?.listingTitle.orEmpty()
        viewModelScope.launch {
            notifications.send(actionId, listOf(applicantId), type, listingId, title, me.uid, me.displayName, NotificationTarget.Listing)
        }
    }

    private fun run(success: Int, after: () -> Unit = {}, block: suspend () -> Unit) {
        if (extras.value.busy) return
        extras.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = attempt { block() }
            if (result.isSuccess) after()
            extras.update {
                it.copy(
                    busy = false,
                    done = if (result.isSuccess) System.nanoTime() else it.done,
                    message = result.fold(
                        onSuccess = { UserMessage(success) },
                        onFailure = { e -> UserMessage(e.toAppError().message, retry = { run(success, after, block) }) },
                    ),
                )
            }
        }
    }

    fun messageShown() = extras.update { it.copy(message = null) }
}
