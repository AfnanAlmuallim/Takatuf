package com.z.takatuf.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.z.takatuf.data.firebase.awaitTransaction
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import com.z.takatuf.data.firebase.toApplication
import com.z.takatuf.data.firebase.toTimestamp
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.firebase.toListing
import com.z.takatuf.data.model.ParticipationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Profile
import com.z.takatuf.domain.Dates
import com.z.takatuf.data.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.Instant

class ApplicationRepository(private val db: FirebaseFirestore) {

    private fun applications(listingId: String) =
        db.collection(ListingRepository.LISTINGS).document(listingId).collection(APPLICATIONS)

    private fun ref(listingId: String, applicantId: String) = applications(listingId).document(applicantId)

    /** Everything the student has applied to, most recent activity first. */
    fun observeMine(uid: String): Flow<List<Application>> =
        db.collectionGroup(APPLICATIONS)
            .whereEqualTo("applicantId", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toApplication() } }

    /** Applications to any listing the student owns. */
    fun observeIncoming(uid: String): Flow<List<Application>> =
        db.collectionGroup(APPLICATIONS)
            .whereEqualTo("ownerId", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toApplication() } }

    fun observeForListing(listingId: String): Flow<List<Application>> =
        applications(listingId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toApplication() } }

    fun observe(listingId: String, applicantId: String): Flow<Application?> =
        ref(listingId, applicantId).observe { it.toApplication() }

    /**
     * Sends an application, or re-sends a withdrawn one. In the same batch the applicant lets
     * the listing's owner open their passport, which the "Teams I apply to" visibility needs.
     */
    suspend fun apply(
        listing: Listing,
        applicant: Profile,
        role: Role,
        contribution: String,
        hoursPerWeek: Int,
        availabilityNote: String,
    ) {
        val ref = ref(listing.id, applicant.uid)
        val existing = ref.get().await().toApplication()
        if (existing != null && !existing.status.canReapply) throw AppException(AppError.AlreadyApplied)

        val answer = mapOf(
            "roleId" to role.id,
            "roleTitle" to role.title,
            "contribution" to contribution.trim(),
            "hoursPerWeek" to hoursPerWeek,
            "availabilityNote" to availabilityNote.trim(),
            "applicantName" to applicant.displayName,
            "applicantMajor" to applicant.major,
            "listingTitle" to listing.title,
            "status" to ApplicationStatus.Submitted.key,
            "reviewed" to false,
            // The lead may have changed since an earlier application.
            "ownerId" to listing.ownerId,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val grant = db.collection(ProfileRepository.PASSPORTS).document(applicant.uid)
            .collection(ProfileRepository.VIEWERS).document(listing.ownerId)

        val batch = db.batch()
        if (existing == null) {
            batch.set(
                ref,
                answer + mapOf(
                    "listingId" to listing.id,
                    "applicantId" to applicant.uid,
                    "listingKind" to listing.kind.key,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
        } else {
            batch.update(ref, answer)
        }
        batch.set(grant, mapOf("listingId" to listing.id, "grantedAt" to FieldValue.serverTimestamp()))
        batch.commit().awaitWrite()
    }

    /**
     * After the team completes the project, closes every application still waiting so nobody is
     * left "under review" (US17). Returns who was affected, so they can be told.
     */
    suspend fun closeOpen(listingId: String): List<String> {
        val open = applications(listingId)
            .whereIn("status", ApplicationStatus.entries.filter { it.isActive }.map { it.key })
            .get().await().documents
        if (open.isEmpty()) return emptyList()
        val batch = db.batch()
        open.forEach { batch.update(it.reference, mapOf("status" to ApplicationStatus.Closed.key, "updatedAt" to FieldValue.serverTimestamp())) }
        batch.commit().awaitWrite()
        return open.map { it.id }
    }

    /** The owner decides not to go ahead with an application (FR14). */
    suspend fun markNotSelected(listingId: String, applicantId: String) {
        ref(listingId, applicantId).update(
            mapOf("status" to ApplicationStatus.NotSelected.key, "reviewed" to true, "updatedAt" to FieldValue.serverTimestamp()),
        ).awaitWrite()
    }

    suspend fun withdraw(listingId: String, applicantId: String) {
        ref(listingId, applicantId)
            .update(mapOf("status" to ApplicationStatus.Withdrawn.key, "updatedAt" to FieldValue.serverTimestamp()))
            .awaitWrite()
    }

    /** Shortlisting only organises the owner's decisions; it never changes membership. */
    suspend fun setShortlisted(listingId: String, applicantId: String, shortlisted: Boolean) {
        val status = if (shortlisted) ApplicationStatus.Shortlisted else ApplicationStatus.Submitted
        ref(listingId, applicantId)
            .update(mapOf("status" to status.key, "reviewed" to true))
            .awaitWrite()
    }

    suspend fun markReviewed(listingId: String, applicantId: String) {
        ref(listingId, applicantId).update("reviewed", true).awaitWrite()
    }

    /**
     * Offers a place to each applicant. An offer only reserves nothing: the first people to
     * accept fill the places, and the rules refuse acceptances once the team is full.
     */
    suspend fun sendOffers(listingId: String, applicantIds: Collection<String>, days: Int, actorUid: String, detail: String = "") {
        if (applicantIds.isEmpty()) return
        val expires = Dates.endOf(Dates.today().plusDays(days.toLong())).toTimestamp()
        applicantIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            // Logged with how the people were chosen, e.g. from a suggested team (NFR14).
            chunk.forEach { id ->
                batch.set(AuditLog.ref(db), AuditLog.entry(AuditLog.Action.OfferSent, actorUid, listingId, id, detail))
            }
            chunk.forEach { id ->
                batch.update(
                    ref(listingId, id),
                    mapOf(
                        "status" to ApplicationStatus.Offered.key,
                        "reviewed" to true,
                        "offeredAt" to FieldValue.serverTimestamp(),
                        "offerExpiresAt" to expires,
                    ),
                )
            }
            batch.commit().awaitWrite()
        }
    }

    suspend fun cancelOffer(listingId: String, applicantId: String, actorUid: String) {
        db.batch()
            .update(ref(listingId, applicantId), "status", ApplicationStatus.Shortlisted.key)
            .set(AuditLog.ref(db), AuditLog.entry(AuditLog.Action.OfferCancelled, actorUid, listingId, applicantId))
            .commit()
            .awaitWrite()
    }

    /**
     * Accepts an offer and joins the team in one transaction: the application, the role's open
     * places and the member list change together or not at all. Two people accepting the last
     * place at once can't both succeed; the second retries, sees no place left and stops.
     */
    suspend fun acceptOffer(listingId: String, me: Profile) {
        val listingRef = db.collection(ListingRepository.LISTINGS).document(listingId)
        val appRef = ref(listingId, me.uid)
        db.runTransaction { tx ->
            val listingSnap = tx.get(listingRef)
            val application = tx.get(appRef).toApplication() ?: throw AppException(AppError.OfferGone)
            // A retry after a lost confirmation finds the student already on the team: that's success.
            val memberIds = (listingSnap.get("memberIds") as? List<*>).orEmpty()
            if (application.status == ApplicationStatus.Accepted && me.uid in memberIds) return@runTransaction
            if (application.status != ApplicationStatus.Offered) throw AppException(AppError.OfferGone)
            if (listingSnap.get("completedAt") != null || listingSnap.getBoolean("hidden") == true) throw AppException(AppError.NotAccepting)
            if (application.offerExpired(Instant.now())) throw AppException(AppError.OfferExpired)

            val roles = (listingSnap.get("roles") as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
            val index = roles.indexOfFirst { it["id"] == application.roleId }
            val roleOpenings = (roles.getOrNull(index)?.get("openings") as? Number)?.toLong() ?: 0L
            val openPlaces = listingSnap.getLong("openPlaces") ?: 0L
            if (index < 0 || roleOpenings <= 0L || openPlaces <= 0L) throw AppException(AppError.TeamFull)

            val updatedRoles = roles.mapIndexed { i, role ->
                if (i == index) role.toMutableMap().apply { put("openings", roleOpenings - 1) } else role
            }
            tx.update(
                listingRef,
                mapOf(
                    "roles" to updatedRoles,
                    "openPlaces" to openPlaces - 1,
                    "teamSize" to (listingSnap.getLong("teamSize") ?: 1L) + 1,
                    "members.${me.uid}" to mapOf(
                        "name" to me.displayName,
                        "major" to me.major,
                        "roleId" to application.roleId,
                        "roleTitle" to application.roleTitle,
                        "joinedAt" to FieldValue.serverTimestamp(),
                    ),
                    "memberIds" to FieldValue.arrayUnion(me.uid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            tx.update(
                appRef,
                mapOf(
                    "status" to ApplicationStatus.Accepted.key,
                    "respondedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            // Joining starts a participation record for the student's passport (US17).
            listingSnap.toListing()?.let { listing ->
                tx.set(
                    RecordsRepository.participationRef(db, me.uid, listingId),
                    RecordsRepository.record(
                        listing,
                        application.roleTitle,
                        wasLead = false,
                        ParticipationStatus.Active,
                        startedAt = FieldValue.serverTimestamp(),
                        endedAt = null,
                    ),
                )
            }
            tx.set(AuditLog.ref(db), AuditLog.entry(AuditLog.Action.OfferAccepted, me.uid, listingId, me.uid, application.roleTitle))
        }.awaitTransaction()
    }

    suspend fun declineOffer(listingId: String, applicantId: String) {
        db.batch()
            .update(
                ref(listingId, applicantId),
                mapOf(
                    "status" to ApplicationStatus.Declined.key,
                    "respondedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .set(AuditLog.ref(db), AuditLog.entry(AuditLog.Action.OfferDeclined, applicantId, listingId, applicantId))
            .commit()
            .awaitWrite()
    }

    /** After a place reopens, earlier applicants confirm they're still interested (US15). */
    suspend fun requestReconfirmation(listingId: String, applicantIds: Collection<String>) {
        applicantIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id ->
                batch.update(
                    ref(listingId, id),
                    mapOf(
                        "status" to ApplicationStatus.Reconfirm.key,
                        "reviewed" to false,
                        "reconfirmRequestedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            batch.commit().awaitWrite()
        }
    }

    suspend fun confirmInterest(listingId: String, applicantId: String) {
        ref(listingId, applicantId)
            .update(
                mapOf(
                    "status" to ApplicationStatus.Submitted.key,
                    "reviewed" to false,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .awaitWrite()
    }

    companion object {
        const val APPLICATIONS = "applications"
    }
}
