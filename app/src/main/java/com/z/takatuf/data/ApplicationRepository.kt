package com.z.takatuf.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import com.z.takatuf.data.firebase.toApplication
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

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
        if (existing != null && existing.status.isActive) throw AppException(AppError.AlreadyApplied)

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
                    "ownerId" to listing.ownerId,
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

    companion object {
        const val APPLICATIONS = "applications"
    }
}
