package com.z.takatuf.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import com.z.takatuf.data.firebase.toEditableFields
import com.z.takatuf.data.firebase.toListing
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ParticipationStatus
import com.z.takatuf.data.model.ListingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant

class ListingRepository(private val db: FirebaseFirestore) {

    private val listings = db.collection(LISTINGS)

    /**
     * The newest listings of every kind. Search and filters run on this set on the device,
     * which keeps results instant while typing; at university scale a few hundred recent
     * listings cover everything that is still open. Hidden listings stay out of it, and the
     * security rules refuse a feed query that doesn't ask for that (US20).
     */
    fun observeFeed(limit: Long = FEED_LIMIT): Flow<List<Listing>> =
        listings.whereEqualTo("hidden", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toListing() } }

    /** A hidden listing is refused to anyone without a part in it, and reads to them as gone (US20). */
    fun observe(id: String): Flow<Listing?> = listings.document(id).observe { it.toListing() }
        .catch { if (it.isRefusal) emit(null) else throw it }

    /**
     * A listing with its event's current rules: a team request copies the event's eligibility
     * and dates when it is linked, and the organizer may change them later (US09).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWithLiveEvent(id: String): Flow<Listing?> = observe(id).flatMapLatest { listing ->
        val linked = listing?.linkedEvent
        if (linked == null) {
            flowOf(listing)
        } else {
            observe(linked.id).catch { emit(null) }.map { event ->
                val details = event?.event ?: return@map listing
                listing.copy(
                    linkedEvent = linked.copy(
                        eligibleLevel = details.eligibleLevel, startDate = details.startDate, endDate = details.endDate,
                        teamSizeMin = details.teamSizeMin, teamSizeMax = details.teamSizeMax,
                    ),
                )
            }
        }
    }

    suspend fun get(id: String): Listing? = try {
        listings.document(id).get().await().toListing()
    } catch (e: FirebaseFirestoreException) {
        if (e.isRefusal) null else throw e
    }

    /**
     * Fetches specific listings (for example saved ones that have left the feed window) one by
     * one, because the security rules judge each read: a single hidden listing would refuse a
     * query for all of them.
     */
    suspend fun getMany(ids: Collection<String>): List<Listing> = coroutineScope {
        ids.distinct().map { id -> async { get(id) } }.awaitAll().filterNotNull()
    }

    fun observeOwnedBy(uid: String): Flow<List<Listing>> =
        listings.whereEqualTo("ownerId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toListing() } }

    fun observeTeamsFor(eventId: String): Flow<List<Listing>> =
        listings.whereEqualTo("eventId", eventId).whereEqualTo("hidden", false)
            .observe { snapshot ->
                snapshot.documents.mapNotNull { it.toListing() }
                    .sortedByDescending { it.createdAt ?: Instant.MAX }
            }

    /** Publishes a new listing and returns its id. */
    suspend fun create(listing: Listing): String {
        val ref = listings.document()
        val fields = listing.toEditableFields() + mapOf(
            "ownerId" to listing.ownerId,
            "hidden" to false,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        val batch = db.batch().set(ref, fields)
        if (listing.kind.recruits) {
            // The lead's participation record starts with the listing (US17).
            batch.set(
                RecordsRepository.participationRef(db, listing.ownerId, ref.id),
                RecordsRepository.record(
                    listing.copy(id = ref.id),
                    RecordsRepository.LEAD_ROLE,
                    wasLead = true,
                    ParticipationStatus.Active,
                    startedAt = FieldValue.serverTimestamp(),
                    endedAt = null,
                ),
            )
        }
        batch.set(AuditLog.ref(db), AuditLog.entry(AuditLog.Action.ListingCreated, listing.ownerId, ref.id, detail = listing.kind.key))
        batch.commit().awaitWrite()
        return ref.id
    }

    private val Throwable.isRefusal: Boolean
        get() = this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    /** Saves the owner's edits. Correcting a listing also clears a moderator's outdated flag (R6). */
    suspend fun update(listing: Listing) {
        listings.document(listing.id).update(listing.toEditableFields() + ("flag" to null)).awaitWrite()
    }

    suspend fun setStatus(id: String, status: ListingStatus) {
        listings.document(id)
            .update(mapOf("status" to status.key, "updatedAt" to FieldValue.serverTimestamp()))
            .awaitWrite()
    }

    companion object {
        const val LISTINGS = "listings"
        const val FEED_LIMIT = 250L
    }
}
