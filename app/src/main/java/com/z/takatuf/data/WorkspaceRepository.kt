package com.z.takatuf.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import com.z.takatuf.data.firebase.toDeliverable
import com.z.takatuf.data.firebase.toMilestone
import com.z.takatuf.data.firebase.toTeamUpdate
import com.z.takatuf.data.firebase.toTimestamp
import com.z.takatuf.data.model.Deliverable
import com.z.takatuf.data.model.Milestone
import com.z.takatuf.data.model.MilestoneStatus
import com.z.takatuf.data.model.TeamUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * The team's private workspace under `listings/{id}`: milestones, progress updates,
 * deliverable links and each member's responsibility. Only the lead and members can read or
 * write it; the security rules check membership on every request (US13, US14).
 */
class WorkspaceRepository(private val db: FirebaseFirestore) {

    /** True when an earlier attempt already reached the server. */
    private suspend fun alreadySaved(ref: DocumentReference): Boolean =
        attempt { ref.get().await().exists() }.getOrDefault(false)

    private fun listing(listingId: String) = db.collection(ListingRepository.LISTINGS).document(listingId)
    private fun milestones(listingId: String) = listing(listingId).collection(MILESTONES)
    private fun updates(listingId: String) = listing(listingId).collection(UPDATES)
    private fun deliverables(listingId: String) = listing(listingId).collection(DELIVERABLES)
    private fun responsibilities(listingId: String) = listing(listingId).collection(RESPONSIBILITIES)

    fun observeMilestones(listingId: String): Flow<List<Milestone>> =
        milestones(listingId).observe(includeMetadata = true) { snapshot -> snapshot.documents.mapNotNull { it.toMilestone() } }

    fun observeUpdates(listingId: String): Flow<List<TeamUpdate>> =
        updates(listingId).orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .observe(includeMetadata = true) { snapshot -> snapshot.documents.mapNotNull { it.toTeamUpdate() } }

    fun observeDeliverables(listingId: String): Flow<List<Deliverable>> =
        deliverables(listingId).orderBy("createdAt", Query.Direction.DESCENDING)
            .observe(includeMetadata = true) { snapshot -> snapshot.documents.mapNotNull { it.toDeliverable() } }

    /** Each member's responsibility, keyed by uid. */
    fun observeResponsibilities(listingId: String): Flow<Map<String, String>> =
        responsibilities(listingId).observe { snapshot ->
            snapshot.documents.associate { it.id to (it.getString("text") ?: "") }
        }

    /**
     * Adds a milestone under an id the caller chose, so retrying after a lost connection writes
     * the same milestone rather than a second one (US21). The same goes for updates and links.
     */
    suspend fun addMilestone(
        id: String,
        listingId: String,
        createdBy: String,
        title: String,
        assigneeId: String,
        assigneeName: String,
        dueDate: Instant,
    ) {
        val ref = milestones(listingId).document(id)
        if (alreadySaved(ref)) return
        ref.set(
            mapOf(
                "title" to title.trim(),
                "assigneeId" to assigneeId,
                "assigneeName" to assigneeName,
                "dueDate" to dueDate.toTimestamp(),
                "status" to MilestoneStatus.Todo.key,
                "createdBy" to createdBy,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "completedAt" to null,
            ),
        ).awaitWrite()
    }

    suspend fun editMilestone(
        listingId: String,
        milestoneId: String,
        title: String,
        assigneeId: String,
        assigneeName: String,
        dueDate: Instant,
    ) {
        milestones(listingId).document(milestoneId).update(
            mapOf(
                "title" to title.trim(),
                "assigneeId" to assigneeId,
                "assigneeName" to assigneeName,
                "dueDate" to dueDate.toTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitWrite()
    }

    suspend fun setMilestoneStatus(listingId: String, milestoneId: String, status: MilestoneStatus) {
        milestones(listingId).document(milestoneId).update(
            mapOf(
                "status" to status.key,
                "completedAt" to if (status == MilestoneStatus.Done) FieldValue.serverTimestamp() else null,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitWrite()
    }

    suspend fun deleteMilestone(listingId: String, milestoneId: String) {
        milestones(listingId).document(milestoneId).delete().awaitWrite()
    }

    suspend fun postUpdate(id: String, listingId: String, authorId: String, authorName: String, text: String, link: String) {
        val ref = updates(listingId).document(id)
        if (alreadySaved(ref)) return
        ref.set(
            mapOf(
                "authorId" to authorId,
                "authorName" to authorName,
                "text" to text.trim(),
                "link" to link.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitWrite()
    }

    suspend fun deleteUpdate(listingId: String, updateId: String) {
        updates(listingId).document(updateId).delete().awaitWrite()
    }

    suspend fun addDeliverable(id: String, listingId: String, addedBy: String, addedByName: String, label: String, url: String) {
        val ref = deliverables(listingId).document(id)
        if (alreadySaved(ref)) return
        ref.set(
            mapOf(
                "label" to label.trim(),
                "url" to url.trim(),
                "addedBy" to addedBy,
                "addedByName" to addedByName,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitWrite()
    }

    suspend fun deleteDeliverable(listingId: String, deliverableId: String) {
        deliverables(listingId).document(deliverableId).delete().awaitWrite()
    }

    suspend fun setResponsibility(listingId: String, memberId: String, text: String, updatedBy: String) {
        responsibilities(listingId).document(memberId).set(
            mapOf(
                "text" to text.trim(),
                "updatedBy" to updatedBy,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitWrite()
    }

    companion object {
        const val MILESTONES = "milestones"
        const val UPDATES = "updates"
        const val DELIVERABLES = "deliverables"
        const val RESPONSIBILITIES = "responsibilities"
    }
}
