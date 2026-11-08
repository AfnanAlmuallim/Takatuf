package com.z.takatuf.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.DraftStore
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.NotificationRepository
import com.z.takatuf.data.RecordsRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.TeamRepository
import com.z.takatuf.data.WorkspaceRepository
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Contribution
import com.z.takatuf.data.model.Deliverable
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Milestone
import com.z.takatuf.data.model.MilestoneStatus
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
import com.z.takatuf.data.model.TeamUpdate
import com.z.takatuf.data.toAppError
import com.z.takatuf.domain.Dates
import com.z.takatuf.domain.Validators
import com.z.takatuf.ui.common.UserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

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
    /** What each person contributed to this project (US17). */
    val contributions: List<Contribution> = emptyList(),
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
    val isCompleted: Boolean get() = listing?.isCompleted == true

    val people: List<TeamPerson>
        get() {
            val l = listing ?: return emptyList()
            val lead = TeamPerson(l.ownerId, l.ownerName, l.ownerMajor, "", isLead = true, joinedAt = l.createdAt)
            return listOf(lead) + l.members.sortedBy { it.joinedAt ?: Instant.MAX }.map {
                TeamPerson(it.uid, it.name, it.major, it.roleTitle, isLead = false, joinedAt = it.joinedAt)
            }
        }

    /** People who left; the Record tab keeps their work, but they take no new tasks (US17). */
    val formerPeople: List<TeamPerson>
        get() = listing?.departed.orEmpty().map {
            TeamPerson(it.uid, it.name, it.major, it.roleTitle, isLead = false, joinedAt = it.joinedAt)
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
    private val records: RecordsRepository,
    private val notifications: NotificationRepository,
    private val drafts: DraftStore,
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

    private val contributions: Flow<List<Contribution>> = head
        .map { h -> (h as? Head.Loaded)?.listing?.takeIf { it.isOnTeam(uid) }?.let { l -> listOf(l.ownerId) + l.memberIds + l.departed.map { it.uid } }.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { people -> records.observeTeamContributions(listingId, people) }
        .onStart { emit(emptyList()) }

    /** Applications are only readable by the lead. */
    private val apps: Flow<List<Application>> = head
        .map { (it as? Head.Loaded)?.listing?.isLead(uid) == true }
        .distinctUntilChanged()
        .flatMapLatest { lead ->
            if (lead) applications.observeForListing(listingId).catch { emit(emptyList()) } else flowOf(emptyList())
        }
        .onStart { emit(emptyList()) }

    val state: StateFlow<WorkspaceUiState> = combine(head, space, contributions, apps, extras) { h, s, c, a, e ->
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
            contributions = c,
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

    init {
        // Teams formed before participation records existed get one when a member opens them.
        viewModelScope.launch {
            val listing = head.filter { it is Head.Loaded }.first().let { (it as Head.Loaded).listing }
            if (listing != null && listing.isOnTeam(uid) && !listing.isCompleted) attempt { records.ensureActive(uid, listing) }
        }
    }

    fun retry() = retry.update { it + 1 }

    fun messageShown() = extras.update { it.copy(message = null) }

    private fun listing(): Listing? = state.value.listing
    private fun me() = session.profile

    /** Tells [recipients] about something just done here; never holds up the action itself. */
    private fun notify(actionId: String, recipients: Collection<String>, type: NotificationType, target: NotificationTarget = NotificationTarget.Workspace, note: String = "") {
        val listing = listing() ?: return
        val me = me() ?: return
        viewModelScope.launch {
            notifications.send(actionId, recipients, type, listing.id, listing.title, me.uid, me.displayName, target, listing.id, note)
        }
    }

    // Plan (US14)

    fun saveMilestone(existing: Milestone?, title: String, assignee: TeamPerson, due: LocalDate, status: MilestoneStatus) {
        val actionId = UUID.randomUUID().toString()
        val id = existing?.id ?: actionId
        run(if (existing == null) R.string.msg_milestone_added else R.string.msg_milestone_saved, after = {
            // The person it's assigned to hears about it, unless they assigned it themselves.
            if (assignee.uid != uid && (existing == null || existing.assigneeId != assignee.uid)) {
                notify(actionId, listOf(assignee.uid), NotificationType.MilestoneAssigned, note = title)
            }
        }) {
            val dueAt = Dates.endOf(due)
            if (existing == null) {
                workspace.addMilestone(id, listingId, uid, title, assignee.uid, assignee.name, dueAt)
            } else {
                workspace.editMilestone(listingId, existing.id, title, assignee.uid, assignee.name, dueAt)
                if (existing.status != status) workspace.setMilestoneStatus(listingId, existing.id, status)
            }
        }
    }

    fun setStatus(milestone: Milestone, status: MilestoneStatus) {
        val lead = listing()?.ownerId.orEmpty()
        val actionId = UUID.randomUUID().toString()
        run(if (status == MilestoneStatus.Done) R.string.msg_milestone_done else null, after = {
            if (status == MilestoneStatus.Done && lead != uid) {
                notify(actionId, listOf(lead), NotificationType.MilestoneCompleted, note = milestone.title)
            }
        }) {
            workspace.setMilestoneStatus(listingId, milestone.id, status)
        }
    }

    fun deleteMilestone(milestone: Milestone) = run(R.string.msg_milestone_deleted) {
        workspace.deleteMilestone(listingId, milestone.id)
    }

    // Updates and links (US13)

    fun postUpdate(text: String, link: String) {
        val id = UUID.randomUUID().toString()
        run(R.string.msg_update_posted, after = { drafts.clear(DraftStore.update(listingId)) }) {
            workspace.postUpdate(id, listingId, uid, myName(), text, normaliseLink(link))
        }
    }

    fun deleteUpdate(update: TeamUpdate) = run(R.string.msg_update_deleted) { workspace.deleteUpdate(listingId, update.id) }

    fun addLink(label: String, url: String) {
        val id = UUID.randomUUID().toString()
        run(R.string.msg_link_added) { workspace.addDeliverable(id, listingId, uid, myName(), label, normaliseLink(url)) }
    }

    fun deleteLink(deliverable: Deliverable) = run(R.string.msg_link_removed) {
        workspace.deleteDeliverable(listingId, deliverable.id)
    }

    fun setResponsibility(person: TeamPerson, text: String) = run(R.string.msg_responsibility_saved) {
        workspace.setResponsibility(listingId, person.uid, text, uid)
    }

    // Records (US17)

    /** Records a contribution for [person]: the student's own claim, or the lead's confirmed record. */
    fun addContribution(person: TeamPerson, title: String, detail: String, links: List<String>) {
        val listing = listing() ?: return
        val me = me() ?: return
        val id = UUID.randomUUID().toString()
        run(R.string.msg_contribution_saved, after = { drafts.clear(DraftStore.contribution(listing.id, person.uid)) }) {
            records.addContribution(id, person.uid, listing.id, listing.title, title, detail, links, me)
        }
    }

    fun editContribution(contribution: Contribution, title: String, detail: String, links: List<String>) =
        run(R.string.msg_contribution_saved) { records.editContribution(contribution, title, detail, links) }

    fun deleteContribution(contribution: Contribution) =
        run(R.string.msg_contribution_deleted) { records.deleteContribution(contribution) }

    fun setConfirmed(contribution: Contribution, confirmed: Boolean) {
        val me = me() ?: return
        run(if (confirmed) R.string.msg_contribution_confirmed else R.string.msg_contribution_unconfirmed) {
            records.setConfirmed(contribution, me, confirmed)
        }
    }

    /** The lead completes the project; everyone's participation is recorded and feedback opens. */
    fun completeProject(summary: String, link: String) {
        val listing = listing() ?: return
        val me = me() ?: return
        val actionId = UUID.randomUUID().toString()
        var closed = emptyList<String>()
        run(R.string.msg_project_completed, after = {
            notify(actionId, listing.memberIds, NotificationType.ProjectCompleted, note = summary)
            notify("${actionId}_closed", closed, NotificationType.RecruitmentClosed, NotificationTarget.Listing)
        }) {
            records.completeProject(listing.id, me, summary, link)
            // Best effort: completion stands even if closing the leftover applications fails.
            closed = attempt { applications.closeOpen(listing.id) }.getOrDefault(emptyList())
        }
    }

    // People (US15, US16)

    fun reopen(roleId: String, places: Int, reconfirm: Collection<String>) {
        val listing = listing() ?: return
        val actionId = UUID.randomUUID().toString()
        run(R.string.msg_place_reopened, after = {
            notify(actionId, reconfirm, NotificationType.ReconfirmRequested, NotificationTarget.Listing)
        }) { teams.reopenPlaces(listing, roleId, places, reconfirm, uid) }
    }

    fun nominateLead(person: TeamPerson) {
        val member = listing()?.members?.firstOrNull { it.uid == person.uid } ?: return
        val actionId = UUID.randomUUID().toString()
        run(R.string.msg_lead_nominated, listOf(person.name), after = {
            notify(actionId, listOf(person.uid), NotificationType.LeadRequested, NotificationTarget.Workspace)
        }) { teams.nominateLead(listingId, member, uid) }
    }

    fun cancelNomination() = run(R.string.msg_nomination_cancelled) { teams.clearNomination(listingId, uid, declinedByNominee = false) }

    fun acceptLead() {
        val me = me() ?: return
        val previous = listing()?.ownerId.orEmpty()
        val actionId = UUID.randomUUID().toString()
        run(R.string.msg_lead_accepted, listOf(listing()?.title.orEmpty()), after = {
            notify(actionId, listOf(previous), NotificationType.LeadAccepted)
        }) { teams.acceptLead(listingId, me) }
    }

    fun declineLead() {
        val owner = listing()?.ownerId.orEmpty()
        val actionId = UUID.randomUUID().toString()
        run(R.string.msg_lead_declined, listOf(listing()?.title.orEmpty()), after = {
            notify(actionId, listOf(owner), NotificationType.LeadDeclined)
        }) { teams.clearNomination(listingId, uid, declinedByNominee = true) }
    }

    fun removeMember(person: TeamPerson) {
        val actionId = UUID.randomUUID().toString()
        run(R.string.msg_member_removed, listOf(person.name), after = {
            notify(actionId, listOf(person.uid), NotificationType.MemberRemoved, NotificationTarget.Passport)
        }) { teams.removeMember(listingId, person.uid, uid) }
    }

    fun leave() {
        if (extras.value.busy) return
        val team = listing()?.let { listOf(it.ownerId) + it.memberIds }.orEmpty() - uid
        val actionId = UUID.randomUUID().toString()
        extras.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = attempt { teams.leave(listingId, uid) }
            if (result.isSuccess) notify(actionId, team, NotificationType.MemberLeft)
            extras.update {
                it.copy(
                    busy = false,
                    left = result.isSuccess,
                    message = result.exceptionOrNull()?.let { e -> UserMessage(e.toAppError().message, retry = ::leave) },
                )
            }
        }
    }

    private fun myName(): String = session.profile?.displayName?.takeIf { it.isNotBlank() }
        ?: state.value.people.firstOrNull { it.uid == uid }?.name.orEmpty()

    /**
     * Runs one action. On failure the message offers a retry that repeats exactly the same
     * action (same ids, same notifications), so it can't create a duplicate (US21).
     */
    private fun run(success: Int?, args: List<Any> = emptyList(), after: () -> Unit = {}, block: suspend () -> Unit) {
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
                        onSuccess = { success?.let { s -> UserMessage(s, args) } },
                        onFailure = { e -> UserMessage(e.toAppError().message, retry = { run(success, args, after, block) }) },
                    ),
                )
            }
        }
    }

    private companion object {
        /** Who can be asked to reconfirm: anyone who applied but wasn't taken on. */
        val reconsiderable = setOf(ApplicationStatus.Submitted, ApplicationStatus.Shortlisted, ApplicationStatus.Declined)

        /** Empty stays empty; anything else goes out in the form the backend checks for. */
        fun normaliseLink(link: String): String =
            if (link.isBlank()) "" else Validators.webLink(link) ?: link.trim()
    }
}
