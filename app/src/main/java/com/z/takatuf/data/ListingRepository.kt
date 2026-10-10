package com.z.takatuf.data

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import com.z.takatuf.data.firebase.toEditableFields
import com.z.takatuf.data.firebase.toListing
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.Instant

class ListingRepository(private val db: FirebaseFirestore) {

    private val listings = db.collection(LISTINGS)

    /**
     * The newest listings of every kind. Search and filters run on this set on the device,
     * which keeps results instant while typing; at university scale a few hundred recent
     * listings cover everything that is still open.
     */
    fun observeFeed(limit: Long = FEED_LIMIT): Flow<List<Listing>> =
        listings.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toListing() } }

    fun observe(id: String): Flow<Listing?> = listings.document(id).observe { it.toListing() }

    suspend fun get(id: String): Listing? = listings.document(id).get().await().toListing()

    /** Fetches specific listings (for example saved ones that have left the feed window). */
    suspend fun getMany(ids: Collection<String>): List<Listing> =
        ids.distinct().chunked(30).flatMap { chunk ->
            listings.whereIn(FieldPath.documentId(), chunk).get().await()
                .documents.mapNotNull { it.toListing() }
        }

    fun observeOwnedBy(uid: String): Flow<List<Listing>> =
        listings.whereEqualTo("ownerId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toListing() } }

    fun observeTeamsFor(eventId: String): Flow<List<Listing>> =
        listings.whereEqualTo("eventId", eventId)
            .observe { snapshot ->
                snapshot.documents.mapNotNull { it.toListing() }
                    .sortedByDescending { it.createdAt ?: Instant.MAX }
            }

    /** Publishes a new listing and returns its id. */
    suspend fun create(listing: Listing): String {
        val ref = listings.document()
        val fields = listing.toEditableFields() + mapOf(
            "ownerId" to listing.ownerId,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        ref.set(fields).awaitWrite()
        return ref.id
    }

    suspend fun update(listing: Listing) {
        listings.document(listing.id).update(listing.toEditableFields()).awaitWrite()
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
