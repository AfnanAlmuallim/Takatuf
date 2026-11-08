package com.z.takatuf.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.TeamRepository
import com.z.takatuf.data.WorkspaceRepository
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Deliverable
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Milestone
import com.z.takatuf.data.model.MilestoneStatus
import com.z.takatuf.data.model.TeamUpdate
import com.z.takatuf.data.toAppError
import com.z.takatuf.domain.Dates
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** Someone on the team, lead included, as the workspace shows them. */
data class TeamPerson(
    val uid: String,
    val name: String,
    val major: String,
    val roleTitle: String,
    val isLead: Boolean,
    val joinedAt: Instant?,
)

data class WorkspaceUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val listing: Listing? = null,
    val myUid: String = "",
    val milestones: List<Milestone> = emptyList(),
    val updates: List<TeamUpdate> = emptyList(),
    val deliverables: List<Deliverable> = emptyList(),
    val responsibilities: Map<String, String> = emptyMap(),
    /** Earlier applicants the lead can ask to reconfirm when reopening a place (US15). */
    val pastApplicants: List<Application> = emptyList(),
    val activeApplicants: Int = 0,
    val busy: Boolean = false,
    /** Changes after each successful write, so sheets can close themselves. */
    val done: Long = 0L,
    /** Set once the student has left; the screen goes back. */
    val left: Boolean = false,
    val message: UserMessage? = null,
) {
    val isLead: Boolean get() = listing?.isLead(myUid) == true
    val onTeam: Boolean get() = listing?.isOnTeam(myUid) == true

    val people: List<TeamPerson>
        get() {
            val l = listing ?: return emptyList()
            val lead = TeamPerson(l.ownerId, l.ownerName, l.ownerMajor, "", isLead = true, joinedAt = l.createdAt)
            return listOf(lead) + l.members.sortedBy { it.joinedAt ?: Instant.MAX }.map {
                TeamPerson(it.uid, it.name, it.major, it.roleTitle, isLead = false, joinedAt = it.joinedAt)
            }
        }

    /** Outstanding first by due date (undated last), then finished, most recent first (US14). */
    val outstanding: List<Milestone>
        get() = milestones.filterNot { it.isDone }.sortedWith(compareBy<Milestone> { it.dueDate ?: Instant.MAX }.thenBy { it.title })

    val completed: List<Milestone>
        get() = milestones.filter { it.isDone }.sortedByDescending { it.completedAt ?: it.updatedAt ?: Instant.MIN }

    fun canEdit(milestone: Milestone): Boolean =
        isLead || milestone.createdBy == myUid || milestone.assigneeId == myUid

    fun canDelete(milestone: Milestone): Boolean = isLead || milestone.createdBy == myUid
}

private data class Space(
    val milestones: List<Milestone> = emptyList(),
    val updates: List<TeamUpdate> = emptyList(),
    val deliverables: List<Deliverable> = emptyList(),
    val responsibilities: Map<String, String> = emptyMap(),
)

private data class WorkspaceExtras(
    val busy: Boolean = false,
    val done: Long = 0L,
    val left: Boolean = false,
    val message: UserMessage? = null,
)

/**
 * The team's private workspace. Every read here is refused by the security rules for anyone
 * who isn't the lead or a confirmed member, so leaving the team ends access at once (US13).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModel(
    private val listingId: String,
    private val session: Session,
    listings: ListingRepository,
    private val applications: ApplicationRepository,
    private val teams: TeamRepository,
    private val workspace: WorkspaceRepository,
) : ViewModel() {

    private val uid = session.uid.orEmpty()
    private val retry = MutableStateFlow(0)
    private val extras = MutableStateFlow(WorkspaceExtras())

    private sealed interface Head {
        data object Loading : Head
        data class Loaded(val listing: Listing?) : Head
        data class Failed(val error: AppError) : Head
    }

    private val head: Flow<Head> = retry.flatMapLatest {
        listings.observe(listingId)
            .map { Head.Loaded(it) as Head }
            .onStart { emit(Head.Loading) }
            .catch { emit(Head.Failed(it.toAppError())) }
    }

    /** Workspace content, read only while the student is on the team. */
    private val space: Flow<Space> = head
        .map { (it as? Head.Loaded)?.listing?.isOnTeam(uid) == true }
        .distinctUntilChanged()
        .flatMapLatest { onTeam ->
            if (!onTeam) {
                flowOf(Space())
            } else {
                combine(
                    workspace.observeMilestones(listingId).catch { emit(emptyList()) },
                    workspace.observeUpdates(listingId).catch { emit(emptyList()) },
                    workspace.observeDeliverables(listingId).catch { emit(emptyList()) },
                    workspace.observeResponsibilities(listingId).catch { emit(emptyMap()) },
                ) { m, u, d, r -> Space(m, u, d, r) }
            }
        }
        .onStart { emit(Space()) }

    /** Applications are only readable by the lead. */
    private val apps: Flow<List<Application>> = head
        .map { (it as? Head.Loaded)?.listing?.isLead(uid) == true }
        .distinctUntilChanged()
        .flatMapLatest { lead ->
            if (lead) applications.observeForListing(listingId).catch { emit(emptyList()) } else flowOf(emptyList())
        }
        .onStart { emit(emptyList()) }

    val state: StateFlow<WorkspaceUiState> = combine(head, space, apps, extras) { h, s, a, e ->
        val now = Instant.now()
        WorkspaceUiState(
            loading = h is Head.Loading,
            error = (h as? Head.Failed)?.error ?: if (h is Head.Loaded && h.listing == null) AppError.NotFound else null,
            listing = (h as? Head.Loaded)?.listing,
            myUid = uid,
            milestones = s.milestones,
            updates = s.updates,
            deliverables = s.deliverables,
            responsibilities = s.responsibilities,
            pastApplicants = a.filter {
                it.status in reconsiderable || (it.status == ApplicationStatus.Offered && it.offerExpired(now))
            },
            activeApplicants = a.count { it.status.isActive },
            busy = e.busy,
            done = e.done,
            left = e.left,
            message = e.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkspaceUiState())

    fun retry() = retry.update { it + 1 }

    fun messageShown() = extras.update { it.copy(message = null) }

    // Plan (US14)

    fun saveMilestone(existing: Milestone?, title: String, assignee: TeamPerson, due: LocalDate?, status: MilestoneStatus) =
        run(if (existing == null) R.string.msg_milestone_added else R.string.msg_milestone_saved) {
            val dueAt = due?.let(Dates::endOf)
            if (existing == null) {
                workspace.addMilestone(listingId, uid, title, assignee.uid, assignee.name, dueAt)
            } else {
                workspace.editMilestone(listingId, existing.id, title, assignee.uid, assignee.name, dueAt)
                if (existing.status != status) workspace.setMilestoneStatus(listingId, existing.id, status)
            }
        }

    fun setStatus(milestone: Milestone, status: MilestoneStatus) =
        run(if (status == MilestoneStatus.Done) R.string.msg_milestone_done else null) {
            workspace.setMilestoneStatus(listingId, milestone.id, status)
        }

    fun deleteMilestone(milestone: Milestone) = run(R.string.msg_milestone_deleted) {
        workspace.deleteMilestone(listingId, milestone.id)
    }

    // Updates and links (US13)

    fun postUpdate(text: String, link: String) = run(R.string.msg_update_posted) {
        workspace.postUpdate(listingId, uid, myName(), text, normaliseLink(link))
    }

    fun deleteUpdate(update: TeamUpdate) = run(R.string.msg_update_deleted) { workspace.deleteUpdate(listingId, update.id) }

    fun addLink(label: String, url: String) = run(R.string.msg_link_added) {
        workspace.addDeliverable(listingId, uid, myName(), label, normaliseLink(url))
    }

    fun deleteLink(deliverable: Deliverable) = run(R.string.msg_link_removed) {
        workspace.deleteDeliverable(listingId, deliverable.id)
    }

    fun setResponsibility(person: TeamPerson, text: String) = run(R.string.msg_responsibility_saved) {
        workspace.setResponsibility(listingId, person.uid, text, uid)
    }

    // People (US15, US16)

    fun reopen(roleId: String, places: Int, reconfirm: Collection<String>) {
        val listing = state.value.listing ?: return
        run(R.string.msg_place_reopened) { teams.reopenPlaces(listing, roleId, places, reconfirm) }
    }

    fun nominateLead(person: TeamPerson) {
        val member = state.value.listing?.members?.firstOrNull { it.uid == person.uid } ?: return
        run(R.string.msg_lead_nominated, listOf(person.name)) { teams.nominateLead(listingId, member) }
    }

    fun cancelNomination() = run(R.string.msg_nomination_cancelled) { teams.clearNomination(listingId) }

    fun acceptLead() {
        val me = session.profile ?: return
        run(R.string.msg_lead_accepted, listOf(state.value.listing?.title.orEmpty())) { teams.acceptLead(listingId, me) }
    }

    fun declineLead() = run(R.string.msg_lead_declined, listOf(state.value.listing?.title.orEmpty())) {
        teams.clearNomination(listingId)
    }

    fun removeMember(person: TeamPerson) = run(R.string.msg_member_removed, listOf(person.name)) {
        teams.removeMember(listingId, person.uid)
    }

    fun leave() {
        if (extras.value.busy) return
        extras.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = attempt { teams.leave(listingId, uid) }
            extras.update {
                it.copy(
                    busy = false,
                    left = result.isSuccess,
                    message = result.exceptionOrNull()?.let { e -> UserMessage(e.toAppError().message) },
                )
            }
        }
    }

    private fun myName(): String = session.profile?.displayName?.takeIf { it.isNotBlank() }
        ?: state.value.people.firstOrNull { it.uid == uid }?.name.orEmpty()

    private fun run(success: Int?, args: List<Any> = emptyList(), block: suspend () -> Unit) {
        if (extras.value.busy) return
        extras.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = attempt { block() }
            extras.update {
                it.copy(
                    busy = false,
                    done = if (result.isSuccess) System.nanoTime() else it.done,
                    message = result.fold(
                        onSuccess = { success?.let { s -> UserMessage(s, args) } },
                        onFailure = { e -> UserMessage(e.toAppError().message) },
                    ),
                )
            }
        }
    }

    private companion object {
        /** Who can be asked to reconfirm: anyone who applied but wasn't taken on. */
        val reconsiderable = setOf(ApplicationStatus.Submitted, ApplicationStatus.Shortlisted, ApplicationStatus.Declined)

        fun normaliseLink(link: String): String {
            val trimmed = link.trim()
            return when {
                trimmed.isEmpty() -> ""
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                else -> "https://$trimmed"
            }
        }
    }
}
