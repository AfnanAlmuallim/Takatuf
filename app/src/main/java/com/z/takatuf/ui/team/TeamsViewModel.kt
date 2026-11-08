package com.z.takatuf.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.NotificationRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.TeamRepository
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
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

/** One team the student is on, and what they do there. */
data class TeamSummary(
    val listing: Listing,
    val leads: Boolean,
    /** The role they joined for; empty for the lead or a former lead. */
    val roleTitle: String,
)

data class TeamsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val teams: List<TeamSummary> = emptyList(),
    /** Teams whose lead asked this student to take over (US16). */
    val leadRequests: List<Listing> = emptyList(),
    val busyListingId: String? = null,
    val message: UserMessage? = null,
)

private data class TeamsTransient(val busyListingId: String? = null, val message: UserMessage? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsViewModel(
    private val session: Session,
    listings: ListingRepository,
    private val teams: TeamRepository,
    private val notifications: NotificationRepository,
) : ViewModel() {

    private val uid = session.uid.orEmpty()
    private val retry = MutableStateFlow(0)
    private val transient = MutableStateFlow(TeamsTransient())

    private val data = retry.flatMapLatest {
        combine(
            listings.observeOwnedBy(uid).map { owned -> owned.filter { it.kind.recruits } },
            teams.observeMemberOf(uid),
        ) { owned, joined ->
            val all = (owned + joined).distinctBy { it.id }
            TeamsUiState(
                loading = false,
                teams = all
                    .map { listing ->
                        TeamSummary(
                            listing = listing,
                            leads = listing.isLead(uid),
                            roleTitle = listing.members.firstOrNull { it.uid == uid }?.roleTitle.orEmpty(),
                        )
                    }
                    // Teams with people come first; then the newest activity.
                    .sortedWith(
                        compareByDescending<TeamSummary> { it.listing.members.isNotEmpty() }
                            .thenByDescending { it.listing.updatedAt ?: Instant.MIN },
                    ),
                leadRequests = joined.filter { it.pendingLeader?.uid == uid },
            )
        }
            .onStart { emit(TeamsUiState()) }
            .catch { emit(TeamsUiState(loading = false, error = it.toAppError())) }
    }

    val state: StateFlow<TeamsUiState> = combine(data, transient) { d, t ->
        d.copy(busyListingId = t.busyListingId, message = t.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamsUiState())

    fun retry() = retry.update { it + 1 }

    fun messageShown() = transient.update { it.copy(message = null) }

    /** Takes over as lead. Until now the previous lead stayed in charge (US16). */
    fun acceptLead(listing: Listing) {
        val me = session.profile ?: return
        val actionId = java.util.UUID.randomUUID().toString()
        run(listing, R.string.msg_lead_accepted, after = { tell(listing, actionId, NotificationType.LeadAccepted) }) {
            teams.acceptLead(listing.id, me)
        }
    }

    fun declineLead(listing: Listing) {
        val actionId = java.util.UUID.randomUUID().toString()
        run(listing, R.string.msg_lead_declined, after = { tell(listing, actionId, NotificationType.LeadDeclined) }) {
            teams.clearNomination(listing.id, uid, declinedByNominee = true)
        }
    }

    /** Tells the lead who asked how the nominee answered (US19). */
    private fun tell(listing: Listing, actionId: String, type: NotificationType) {
        val me = session.profile ?: return
        viewModelScope.launch {
            notifications.send(actionId, listOf(listing.ownerId), type, listing.id, listing.title, me.uid, me.displayName, NotificationTarget.Workspace)
        }
    }

    private fun run(listing: Listing, success: Int, after: () -> Unit = {}, block: suspend () -> Unit) {
        if (transient.value.busyListingId != null) return
        transient.update { it.copy(busyListingId = listing.id) }
        viewModelScope.launch {
            val result = attempt { block() }
            if (result.isSuccess) after()
            transient.update {
                it.copy(
                    busyListingId = null,
                    message = result.fold(
                        onSuccess = { UserMessage(success, listOf(listing.title)) },
                        onFailure = { e -> UserMessage(e.toAppError().message, retry = { run(listing, success, after, block) }) },
                    ),
                )
            }
        }
    }
}

/** Badge for the Teams tab: leadership handovers waiting for this student's answer. */
fun TeamRepository.leadRequestCount(uid: String): Flow<Int> =
    observeMemberOf(uid)
        .map { teams -> teams.count { it.pendingLeader?.uid == uid } }
        .catch { emit(0) }
        .onStart { emit(0) }
