package com.z.takatuf.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import com.z.takatuf.data.firebase.toNotification
import com.z.takatuf.data.model.AppNotification
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await

/**
 * Each student's inbox at `users/{uid}/notifications` (US19, FR19). The person acting leaves
 * the notification for everyone affected. Each action has its own id, which is also the
 * notification's document id, so retrying an action never sends the same notice twice.
 */
class NotificationRepository(private val db: FirebaseFirestore) {

    private fun inbox(uid: String) = db.collection(USERS).document(uid).collection(NOTIFICATIONS)

    fun observe(uid: String, limit: Long = 60): Flow<List<AppNotification>> =
        inbox(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toNotification() } }

    fun unreadCount(uid: String): Flow<Int> =
        observe(uid).map { list -> list.count { !it.read } }.catch { emit(0) }.onStart { emit(0) }

    /** Unread notifications, read once; used by the background check. */
    suspend fun unread(uid: String): List<AppNotification> =
        inbox(uid).whereEqualTo("read", false).limit(20).get().await().documents.mapNotNull { it.toNotification() }

    suspend fun markRead(uid: String, id: String) {
        inbox(uid).document(id).update("read", true).awaitWrite()
    }

    suspend fun markAllRead(uid: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        ids.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(inbox(uid).document(it), "read", true) }
            batch.commit().awaitWrite()
        }
    }

    suspend fun delete(uid: String, id: String) {
        inbox(uid).document(id).delete().awaitWrite()
    }

    /**
     * Tells [recipients] about something [fromUid] just did. Best effort: a notification that
     * can't be delivered never undoes the action. A notification that already exists (a retry
     * of the same action) is refused by the rules and simply skipped.
     */
    suspend fun send(
        actionId: String,
        recipients: Collection<String>,
        type: NotificationType,
        listingId: String,
        listingTitle: String,
        fromUid: String,
        fromName: String,
        target: NotificationTarget,
        targetId: String = listingId,
        note: String = "",
    ) {
        recipients.filter { it.isNotBlank() && it != fromUid }.distinct().forEach { uid ->
            attempt {
                inbox(uid).document(actionId).set(
                    mapOf(
                        "type" to type.key,
                        "listingId" to listingId,
                        "listingTitle" to listingTitle.take(120),
                        "fromUid" to fromUid,
                        "fromName" to fromName.take(60).ifBlank { "Takatuf" },
                        "target" to target.key,
                        "targetId" to targetId.take(256),
                        "note" to note.take(500),
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                ).awaitWrite()
            }
        }
    }

    companion object {
        const val USERS = "users"
        const val NOTIFICATIONS = "notifications"
    }
}
