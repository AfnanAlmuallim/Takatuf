package com.z.takatuf.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import com.z.takatuf.data.firebase.toFields
import com.z.takatuf.data.firebase.toPassport
import com.z.takatuf.data.firebase.toProfile
import com.z.takatuf.data.model.Passport
import com.z.takatuf.data.model.PassportAccess
import com.z.takatuf.data.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ProfileRepository(private val db: FirebaseFirestore) {

    private fun profileRef(uid: String) = db.collection(PROFILES).document(uid)
    private fun passportRef(uid: String) = db.collection(PASSPORTS).document(uid)

    /**
     * Emits the profile, or null once the server confirms there is none. A cache-only "missing"
     * snapshot is skipped, so a returning student is never sent back to onboarding just
     * because they opened the app offline on a fresh install.
     */
    fun observeProfile(uid: String): Flow<Profile?> =
        profileRef(uid).observe { snapshot ->
            when {
                snapshot.exists() -> ProfileResult(snapshot.toProfile())
                snapshot.metadata.isFromCache -> null
                else -> ProfileResult(null)
            }
        }.filterNotNull().map { it.profile }

    private class ProfileResult(val profile: Profile?)

    suspend fun getProfile(uid: String): Profile? = profileRef(uid).get().await().toProfile()

    fun observePassport(uid: String): Flow<Passport?> = passportRef(uid).observe { it.toPassport() }

    /** Reads someone else's passport; the security rules decide whether it is visible. */
    suspend fun passportFor(uid: String): PassportAccess = try {
        passportRef(uid).get().await().toPassport()
            ?.let { PassportAccess.Visible(it) }
            ?: PassportAccess.Missing
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) PassportAccess.Hidden else throw e
    }

    suspend fun createProfile(uid: String, displayName: String) {
        val fields = Profile(uid = uid, displayName = displayName).toFields() +
            ("createdAt" to FieldValue.serverTimestamp())
        profileRef(uid).set(fields).awaitWrite()
    }

    suspend fun saveProfile(profile: Profile) {
        profileRef(profile.uid).set(profile.toFields(), SetOptions.merge()).awaitWrite()
    }

    suspend fun savePassport(passport: Passport) {
        passportRef(passport.uid).set(passport.toFields()).awaitWrite()
    }

    /** Writes both halves of the passport together and marks onboarding as done. */
    suspend fun completeOnboarding(profile: Profile, passport: Passport) {
        db.batch()
            .set(profileRef(profile.uid), profile.copy(onboarded = true).toFields(), SetOptions.merge())
            .set(passportRef(passport.uid), passport.toFields())
            .commit()
            .awaitWrite()
    }

    companion object {
        const val PROFILES = "profiles"
        const val PASSPORTS = "passports"
        const val VIEWERS = "viewers"
    }
}
