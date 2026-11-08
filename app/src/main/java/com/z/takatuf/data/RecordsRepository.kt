package com.z.takatuf.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import com.z.takatuf.data.firebase.awaitTransaction
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import com.z.takatuf.data.firebase.toContribution
import com.z.takatuf.data.firebase.toFeedback
import com.z.takatuf.data.firebase.toListing
import com.z.takatuf.data.firebase.toParticipation
import com.z.takatuf.data.firebase.toTimestamp
import com.z.takatuf.data.model.Contribution
import com.z.takatuf.data.model.Feedback
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Participation
import com.z.takatuf.data.model.ParticipationStatus
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.ReportReason
import com.z.takatuf.data.model.ReportTarget
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * Participation records, contributions and feedback (US17, US18). They live under the
 * student's passport, so they follow the passport's visibility and stay with the student after
 * a team ends. The security rules check every transition against the team itself.
 */
class RecordsRepository(private val db: FirebaseFirestore) {

    private fun passport(uid: String) = db.collection(ProfileRepository.PASSPORTS).document(uid)
    private fun participation(uid: String, listingId: String) = passport(uid).collection(PARTICIPATION).document(listingId)
    private fun contributions(uid: String) = passport(uid).collection(CONTRIBUTIONS)
    private fun feedback(uid: String) = passport(uid).collection(FEEDBACK)
    private fun listing(id: String) = db.collection(ListingRepository.LISTINGS).document(id)

    // ---------- participation ----------

    fun observeParticipation(uid: String): Flow<List<Participation>> =
        passport(uid).collection(PARTICIPATION)
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toParticipation() } }

    /**
     * Makes sure the signed-in student has an active record for a team they're on. Teams formed
     * before records existed get one the first time a member opens the workspace.
     */
    suspend fun ensureActive(uid: String, listing: Listing) {
        val ref = participation(uid, listing.id)
        val existing = attempt { ref.get().await().toParticipation() }.getOrNull()
        val isLead = listing.ownerId == uid
        val role = if (isLead) LEAD_ROLE else listing.members.firstOrNull { it.uid == uid }?.roleTitle?.ifBlank { MEMBER_ROLE } ?: MEMBER_ROLE
        if (existing != null && existing.status == ParticipationStatus.Active && existing.role == role) return
        if (existing != null && existing.status != ParticipationStatus.Active) return
        val joinedAt = if (isLead) listing.createdAt else listing.members.firstOrNull { it.uid == uid }?.joinedAt
        ref.set(
            record(listing, role, wasLead = isLead || existing?.wasLead == true, ParticipationStatus.Active, joinedAt ?: Instant.now(), endedAt = null),
        ).awaitWrite()
    }

    // ---------- contributions ----------

    fun observeContributions(uid: String): Flow<List<Contribution>> = callbackFlow {
        val registration = contributions(uid).orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                when {
                    error != null -> close(error)
                    snapshot != null -> trySend(snapshot.documents.mapNotNull { it.toContribution() })
                }
            }
        awaitClose { registration.remove() }
    }

    /** Everyone's contributions to one project, as the team (and its lead) may see them. */
    fun observeTeamContributions(listingId: String, uids: Collection<String>): Flow<List<Contribution>> {
        if (uids.isEmpty()) return flowOf(emptyList())
        val perPerson = uids.distinct().map { uid ->
            callbackFlow {
                val registration = contributions(uid).whereEqualTo("listingId", listingId)
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        when {
                            error != null -> close(error)
                            snapshot != null -> trySend(snapshot.documents.mapNotNull { it.toContribution() })
                        }
                    }
                awaitClose { registration.remove() }
            }.catch { emit(emptyList()) }
        }
        return combine(perPerson) { lists -> lists.flatMap { it }.sortedByDescending { it.createdAt ?: Instant.MAX } }
    }

    /**
     * Records a contribution. The id is chosen up front, so retrying after a lost connection
     * writes the same document instead of a second copy (US21). A lead recording one for a
     * member confirms it in the same write. It carries the project's current title, which the
     * security rules check; a passport record can hold an older one.
     */
    suspend fun addContribution(
        id: String,
        ownerUid: String,
        listingId: String,
        listingTitle: String,
        title: String,
        detail: String,
        links: List<String>,
        recorder: Profile,
    ) {
        val ref = contributions(ownerUid).document(id)
        if (attempt { ref.get().await().exists() }.getOrDefault(false)) return
        val byLead = recorder.uid != ownerUid
        val currentTitle = attempt { listing(listingId).get().await().getString("title") }.getOrNull() ?: listingTitle
        ref.set(
            mapOf(
                "listingId" to listingId,
                "listingTitle" to currentTitle.take(90),
                "title" to title.trim(),
                "detail" to detail.trim(),
                "links" to links,
                "recordedBy" to recorder.uid,
                "recordedByName" to recorder.displayName,
                "confirmedBy" to if (byLead) recorder.uid else null,
                "confirmedByName" to if (byLead) recorder.displayName else null,
                "confirmedAt" to if (byLead) FieldValue.serverTimestamp() else null,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitWrite()
    }

    /** The student edits their own claim. Any edit clears a lead's confirmation. */
    suspend fun editContribution(contribution: Contribution, title: String, detail: String, links: List<String>) {
        contributions(contribution.ownerUid).document(contribution.id).update(
            mapOf(
                "title" to title.trim(),
                "detail" to detail.trim(),
                "links" to links,
                "confirmedBy" to null,
                "confirmedByName" to null,
                "confirmedAt" to null,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitWrite()
    }

    /** The lead confirms a member's contribution, or withdraws the confirmation. */
    suspend fun setConfirmed(contribution: Contribution, lead: Profile, confirmed: Boolean) {
        val batch = db.batch()
        batch.update(
            contributions(contribution.ownerUid).document(contribution.id),
            mapOf(
                "confirmedBy" to if (confirmed) lead.uid else null,
                "confirmedByName" to if (confirmed) lead.displayName else null,
                "confirmedAt" to if (confirmed) FieldValue.serverTimestamp() else null,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        if (confirmed) {
            batch.set(
                AuditLog.ref(db),
                AuditLog.entry(AuditLog.Action.ContributionConfirmed, lead.uid, contribution.listingId, contribution.ownerUid, contribution.title),
            )
        }
        batch.commit().awaitWrite()
    }

    suspend fun deleteContribution(contribution: Contribution) {
        contributions(contribution.ownerUid).document(contribution.id).delete().awaitWrite()
    }

    // ---------- project completion ----------

    /**
     * The lead marks the project complete with a summary and an optional link to the result.
     * Everyone on the team gets a completed participation record in the same transaction, and
     * feedback opens (US17, US18).
     */
    suspend fun completeProject(listingId: String, lead: Profile, summary: String, link: String) {
        val listingRef = listing(listingId)
        db.runTransaction { tx ->
            val listing = tx.get(listingRef).toListing() ?: throw AppException(AppError.NotFound)
            if (listing.ownerId != lead.uid) throw AppException(AppError.PermissionDenied)
            if (listing.isCompleted) return@runTransaction
            val people = listOf(listing.ownerId) + listing.memberIds
            val existing = people.associateWith { uid -> tx.get(participation(uid, listing.id)).toParticipation() }
            tx.update(
                listingRef,
                mapOf(
                    "completedAt" to FieldValue.serverTimestamp(),
                    "completion" to mapOf("summary" to summary.trim(), "link" to link),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            people.forEach { uid ->
                val previous = existing[uid]
                val member = listing.members.firstOrNull { it.uid == uid }
                val role = previous?.role ?: if (uid == listing.ownerId) LEAD_ROLE else member?.roleTitle?.ifBlank { MEMBER_ROLE } ?: MEMBER_ROLE
                tx.set(
                    participation(uid, listing.id),
                    record(
                        listing,
                        role,
                        wasLead = uid == listing.ownerId || previous?.wasLead == true,
                        ParticipationStatus.Completed,
                        previous?.startedAt ?: member?.joinedAt ?: listing.createdAt ?: Instant.now(),
                        endedAt = FieldValue.serverTimestamp(),
                    ),
                )
            }
            tx.set(AuditLog.ref(db), AuditLog.entry(AuditLog.Action.ProjectCompleted, lead.uid, listing.id, detail = summary))
        }.awaitTransaction()
    }

    // ---------- feedback ----------

    fun observeFeedback(uid: String): Flow<List<Feedback>> =
        feedback(uid).orderBy("createdAt", Query.Direction.DESCENDING)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toFeedback() } }

    /** One review by its path; administrators use it to see reported feedback. */
    /** Someone else's reviews: the rules only return those moderators haven't hidden (US20). */
    fun observeVisibleFeedback(uid: String): Flow<List<Feedback>> =
        feedback(uid).whereEqualTo("hidden", false).orderBy("createdAt", Query.Direction.DESCENDING)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toFeedback() } }

    fun observeFeedbackAt(path: String): Flow<Feedback?> = db.document(path).observe { it.toFeedback() }

    /** The review [reviewerUid] already wrote about [subjectUid] for this project, if any. */
    suspend fun reviewBy(reviewerUid: String, subjectUid: String, listingId: String): Feedback? =
        attempt { feedback(subjectUid).document("${listingId}_$reviewerUid").get().await().toFeedback() }.getOrNull()

    /** Writes or edits a review. One per reviewer and project, so retries never duplicate it. */
    suspend fun giveFeedback(
        subjectUid: String,
        listingId: String,
        listingTitle: String,
        reviewer: Profile,
        contribution: Int,
        leadership: Int?,
        comment: String,
        existing: Feedback?,
    ) {
        val ref = feedback(subjectUid).document("${listingId}_${reviewer.uid}")
        if (existing != null) {
            ref.update(
                mapOf(
                    "contribution" to contribution,
                    "leadership" to leadership,
                    "comment" to comment.trim(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).awaitWrite()
            return
        }
        // A retry after a lost confirmation finds the review already saved (US21).
        if (attempt { ref.get().await().exists() }.getOrDefault(false)) return
        ref.set(
            mapOf(
                "listingId" to listingId,
                "listingTitle" to listingTitle.take(90),
                "reviewerUid" to reviewer.uid,
                "reviewerName" to reviewer.displayName,
                "contribution" to contribution,
                "leadership" to leadership,
                "comment" to comment.trim(),
                "disputed" to false,
                "disputeReason" to "",
                "disputedAt" to null,
                "hidden" to false,
                "moderationNote" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitWrite()
    }

    /**
     * The person reviewed disputes a review. The review is marked disputed (and kept), and a
     * report goes to the moderators in the same write (US18, US20).
     */
    suspend fun disputeFeedback(feedback: Feedback, me: Profile, reason: String) {
        val path = "${ProfileRepository.PASSPORTS}/${feedback.subjectUid}/$FEEDBACK/${feedback.id}"
        val targetKey = "${feedback.subjectUid}_${feedback.id}"
        val batch = db.batch()
        batch.update(
            feedback(feedback.subjectUid).document(feedback.id),
            mapOf("disputed" to true, "disputeReason" to reason.trim(), "disputedAt" to FieldValue.serverTimestamp()),
        )
        batch.set(
            db.collection(ModerationRepository.REPORTS).document("${ReportTarget.Feedback.key}_${targetKey}_${me.uid}"),
            ModerationRepository.reportFields(
                reporter = me,
                target = ReportTarget.Feedback,
                targetKey = targetKey,
                targetPath = path,
                targetTitle = "Feedback from ${feedback.reviewerName} on ${feedback.listingTitle}".take(120),
                targetOwnerUid = feedback.reviewerUid,
                listingId = feedback.listingId,
                reason = ReportReason.UnfairFeedback,
                details = reason,
            ),
        )
        batch.set(AuditLog.ref(db), AuditLog.entry(AuditLog.Action.FeedbackDisputed, me.uid, feedback.listingId, feedback.reviewerUid, reason))
        batch.commit().awaitWrite()
    }

    companion object {
        const val PARTICIPATION = "participation"
        const val CONTRIBUTIONS = "contributions"
        const val FEEDBACK = "feedback"
        const val LEAD_ROLE = "Lead"
        const val MEMBER_ROLE = "Member"

        /** A full participation record, as the rules expect it. */
        internal fun record(
            listing: Listing,
            role: String,
            wasLead: Boolean,
            status: ParticipationStatus,
            startedAt: Any,
            endedAt: Any?,
        ) = mapOf(
            "listingId" to listing.id,
            "listingTitle" to listing.title.take(90),
            "kind" to listing.kind.key,
            "role" to role.take(60).ifBlank { MEMBER_ROLE },
            "wasLead" to wasLead,
            "status" to status.key,
            "startedAt" to when (startedAt) {
                is Instant -> startedAt.toTimestamp()
                else -> startedAt
            },
            "endedAt" to endedAt,
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        /**
         * Ends a participation record inside a membership transaction. [previous] is the record
         * read earlier in the same transaction (Firestore needs every read before any write).
         */
        internal fun endIn(
            tx: Transaction,
            db: FirebaseFirestore,
            uid: String,
            listing: Listing,
            status: ParticipationStatus,
            previous: Participation?,
        ) {
            val ref = participationRef(db, uid, listing.id)
            val member = listing.members.firstOrNull { it.uid == uid }
            tx.set(
                ref,
                record(
                    listing,
                    previous?.role ?: member?.roleTitle?.ifBlank { MEMBER_ROLE } ?: MEMBER_ROLE,
                    wasLead = previous?.wasLead == true,
                    status,
                    previous?.startedAt ?: member?.joinedAt ?: listing.createdAt ?: Instant.now(),
                    endedAt = FieldValue.serverTimestamp(),
                ),
            )
        }

        internal fun participationRef(db: FirebaseFirestore, uid: String, listingId: String): DocumentReference =
            db.collection(ProfileRepository.PASSPORTS).document(uid).collection(PARTICIPATION).document(listingId)
    }
}
