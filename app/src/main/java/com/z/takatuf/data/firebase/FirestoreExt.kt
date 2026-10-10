package com.z.takatuf.data.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.Date

/** Emits the mapped result of every snapshot; closes with the listener's error. */
internal fun <T> Query.observe(map: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> close(error)
            snapshot != null -> trySend(map(snapshot))
        }
    }
    awaitClose { registration.remove() }
}

internal fun <T> DocumentReference.observe(map: (DocumentSnapshot) -> T): Flow<T> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> close(error)
            snapshot != null -> trySend(map(snapshot))
        }
    }
    awaitClose { registration.remove() }
}

/**
 * Firestore applies writes locally at once but only completes the task when the server
 * confirms. Offline, that can take indefinitely, so after a short wait the write is treated
 * as queued: the local cache already shows it and it syncs when the connection returns.
 */
internal suspend fun Task<Void>.awaitWrite(timeoutMillis: Long = 10_000) {
    withTimeoutOrNull(timeoutMillis) { await() }
}

/** Reads a document's fields, estimating server timestamps that are still pending locally. */
internal fun DocumentSnapshot.fields(): Map<String, Any?> =
    getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).orEmpty()

internal fun Map<*, *>.str(key: String): String = this[key] as? String ?: ""
internal fun Map<*, *>.strOrNull(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }
internal fun Map<*, *>.int(key: String, default: Int = 0): Int = (this[key] as? Number)?.toInt() ?: default
internal fun Map<*, *>.intOrNull(key: String): Int? = (this[key] as? Number)?.toInt()
internal fun Map<*, *>.bool(key: String): Boolean = this[key] as? Boolean ?: false
internal fun Map<*, *>.instant(key: String): Instant? = (this[key] as? Timestamp)?.toDate()?.toInstant()
internal fun Map<*, *>.strings(key: String): List<String> =
    (this[key] as? List<*>)?.filterIsInstance<String>().orEmpty()

internal fun Map<*, *>.maps(key: String): List<Map<*, *>> =
    (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

internal fun Map<*, *>.map(key: String): Map<*, *>? = this[key] as? Map<*, *>

internal fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))
