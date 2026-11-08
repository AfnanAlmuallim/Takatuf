package com.z.takatuf.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import com.z.takatuf.data.firebase.toListing
import com.z.takatuf.data.firebase.toTimestamp
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.TeamMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * Membership after an offer is accepted: leaving, removal by the lead, leadership handover and
 * reopening places. Each change touches the listing and the member's application together, and
 * the security rules check both halves (US15, US16).
 */
class TeamRepository(private val db: FirebaseFirestore) {

    private val listings = db.collection(ListingRepository.LISTINGS)

    private fun application(listingId: String, uid: String) =
        listings.document(listingId).collection(ApplicationRepository.APPLICATIONS).document(uid)

    /** Teams the student joined through an offer (teams they lead come from their listings). */
    fun observeMemberOf(uid: String): Flow<List<Listing>> =
        listings.whereArrayContains("memberIds", uid)
            .observe { snapshot ->
                snapshot.documents.mapNotNull { it.toListing() }.sortedByDescending { it.updatedAt ?: Instant.MIN }
            }

    /** A member leaves. Their place isn't reopened automatically; the lead decides (US15). */
    suspend fun leave(listingId: String, uid: String) {
        val listingRef = listings.document(listingId)
        val appRef = application(listingId, uid)
        db.runTransaction { tx ->
            val listing = tx.get(listingRef).toListing() ?: throw AppException(AppError.NotFound)
            val app = tx.get(appRef)
            if (listing.members.none { it.uid == uid }) throw AppException(AppError.PermissionDenied)
            val update = mutableMapOf<String, Any?>(
                "members.$uid" to FieldValue.delete(),
                "memberIds" to FieldValue.arrayRemove(uid),
                "teamSize" to (listing.recruitment?.teamSize ?: 2) - 1,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (listing.pendingLeader?.uid == uid) update["pendingLeader"] = null
            tx.update(listingRef, update)
            if (app.exists() && app.getString("status") == ApplicationStatus.Accepted.key) {
                tx.update(appRef, mapOf("status" to ApplicationStatus.Left.key, "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }

    /** The lead removes a member; access to the workspace ends with the same write. */
    suspend fun removeMember(listingId: String, memberId: String) {
        val listingRef = listings.document(listingId)
        val appRef = application(listingId, memberId)
        db.runTransaction { tx ->
            val listing = tx.get(listingRef).toListing() ?: throw AppException(AppError.NotFound)
            val app = tx.get(appRef)
            val update = mutableMapOf<String, Any?>(
                "members.$memberId" to FieldValue.delete(),
                "memberIds" to FieldValue.arrayRemove(memberId),
                "teamSize" to (listing.recruitment?.teamSize ?: 2) - 1,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (listing.pendingLeader?.uid == memberId) update["pendingLeader"] = null
            tx.update(listingRef, update)
            if (app.exists() && app.getString("status") == ApplicationStatus.Accepted.key) {
                tx.update(appRef, mapOf("status" to ApplicationStatus.Removed.key))
            }
        }.await()
    }

    /** The lead nominates a successor; nothing changes until they accept. */
    suspend fun nominateLead(listingId: String, member: TeamMember) {
        listings.document(listingId).update(
            mapOf(
                "pendingLeader" to mapOf(
                    "uid" to member.uid,
                    "name" to member.name,
                    "requestedAt" to FieldValue.serverTimestamp(),
                ),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitWrite()
    }

    /** The lead takes the nomination back, or the nominee declines it. */
    suspend fun clearNomination(listingId: String) {
        listings.document(listingId)
            .update(mapOf("pendingLeader" to null, "updatedAt" to FieldValue.serverTimestamp()))
            .awaitWrite()
    }

    /**
     * The nominee accepts: they become the lead and the previous lead stays on as a member.
     * Applications then move to the new lead so they can keep managing recruitment.
     */
    suspend fun acceptLead(listingId: String, me: Profile) {
        val listingRef = listings.document(listingId)
        db.runTransaction { tx ->
            val listing = tx.get(listingRef).toListing() ?: throw AppException(AppError.NotFound)
            if (listing.pendingLeader?.uid != me.uid) throw AppException(AppError.PermissionDenied)
            val previous = listing.ownerId
            val memberIds = listing.memberIds.filter { it != me.uid } + previous
            tx.update(
                listingRef,
                mapOf(
                    "ownerId" to me.uid,
                    "ownerName" to me.displayName,
                    "ownerMajor" to me.major,
                    "pendingLeader" to null,
                    "members.${me.uid}" to FieldValue.delete(),
                    "members.$previous" to mapOf(
                        "name" to listing.ownerName,
                        "major" to listing.ownerMajor,
                        "roleId" to "",
                        "roleTitle" to "",
                        "joinedAt" to (listing.createdAt?.toTimestamp() ?: FieldValue.serverTimestamp()),
                    ),
                    "memberIds" to memberIds.distinct(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
        adoptApplications(listingId, me.uid)
    }

    /**
     * Points every application at the new lead, so their Activity tab lists them. Applicants
     * shared their passport with this listing's lead, so the new lead takes over that access too.
     */
    private suspend fun adoptApplications(listingId: String, uid: String) {
        val apps = attempt {
            listings.document(listingId).collection(ApplicationRepository.APPLICATIONS).get().await().documents
        }.getOrNull() ?: return
        attempt {
            apps.filter { it.getString("ownerId") != uid }.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.update(it.reference, "ownerId", uid) }
                batch.commit().awaitWrite()
            }
        }
        apps.map { it.id }.filter { it != uid }.forEach { applicantId ->
            // A grant that already exists can't be rewritten by the lead; that's fine, access is there.
            attempt {
                db.collection(ProfileRepository.PASSPORTS).document(applicantId)
                    .collection(ProfileRepository.VIEWERS).document(uid)
                    .set(mapOf("listingId" to listingId, "grantedAt" to FieldValue.serverTimestamp()))
                    .awaitWrite()
            }
        }
    }

    /**
     * Reopens [places] places for a role without touching the workspace. Applicants who weren't
     * picked must confirm they're still interested before they're considered again.
     */
    suspend fun reopenPlaces(listing: Listing, roleId: String, places: Int, reconfirm: Collection<String>) {
        val recruitment = listing.recruitment ?: return
        val roles = recruitment.roles.map { role ->
            mapOf(
                "id" to role.id,
                "title" to role.title,
                "description" to role.description,
                "skills" to role.skills,
                "openings" to if (role.id == roleId) role.openings + places else role.openings,
            )
        }
        val update = mutableMapOf<String, Any?>(
            "roles" to roles,
            "openPlaces" to recruitment.openPlaces + places,
            "status" to "open",
            "reopenedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        // A passed deadline would keep the reopened place shut.
        if (recruitment.deadline?.isBefore(Instant.now()) == true) update["deadline"] = null
        val batch = db.batch()
        batch.update(listings.document(listing.id), update)
        reconfirm.forEach { id ->
            batch.update(
                application(listing.id, id),
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
