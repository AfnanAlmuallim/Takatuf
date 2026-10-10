package com.z.takatuf.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth) {

    /** The signed-in user's uid, or null when signed out. */
    val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    val currentUid: String? get() = auth.currentUser?.uid
    val currentEmail: String? get() = auth.currentUser?.email

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    /** Creates the account and signs in; returns the new uid. */
    suspend fun signUp(email: String, password: String): String {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        return result.user?.uid ?: throw AppException(AppError.Unknown)
    }

    /**
     * With email-enumeration protection (Firebase's default) this succeeds whether or not an
     * account exists, so the interface must not claim that one does.
     */
    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()
}
