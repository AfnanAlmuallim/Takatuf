package com.z.takatuf.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import kotlinx.coroutines.flow.Flow

/** Saved listings live in `users/{uid}/saved/{listingId}`, readable only by their owner. */
class SavedRepository(private val db: FirebaseFirestore) {

    private fun saved(uid: String) = db.collection(USERS).document(uid).collection(SAVED)

    fun observeIds(uid: String): Flow<Set<String>> =
        saved(uid).observe { snapshot -> snapshot.documents.mapTo(LinkedHashSet()) { it.id } }

    suspend fun setSaved(uid: String, listingId: String, saved: Boolean) {
        val ref = saved(uid).document(listingId)
        if (saved) {
            ref.set(mapOf("savedAt" to FieldValue.serverTimestamp())).awaitWrite()
        } else {
            ref.delete().awaitWrite()
        }
    }

    companion object {
        const val USERS = "users"
        const val SAVED = "saved"
    }
}
