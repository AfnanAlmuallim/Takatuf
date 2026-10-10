package com.z.takatuf.data

import com.z.takatuf.data.model.Passport
import com.z.takatuf.data.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState

    /** Signed in, but the Skill Passport hasn't been set up yet. */
    data class Onboarding(val uid: String, val profile: Profile?) : SessionState

    data class Ready(val profile: Profile) : SessionState
}

/** The signed-in student, shared by every screen. Listeners follow the auth state. */
@OptIn(ExperimentalCoroutinesApi::class)
class Session(
    private val auth: AuthRepository,
    profiles: ProfileRepository,
    scope: CoroutineScope,
) {
    val state: StateFlow<SessionState> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(SessionState.SignedOut)
            } else {
                profiles.observeProfile(uid)
                    .map { profile ->
                        if (profile?.onboarded == true) SessionState.Ready(profile)
                        else SessionState.Onboarding(uid, profile)
                    }
                    .catch { emit(SessionState.Onboarding(uid, null)) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    val passport: StateFlow<Passport?> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null)
            else profiles.observePassport(uid).catch { emit(null) }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val uid: String? get() = auth.currentUid
    val email: String? get() = auth.currentEmail

    val profile: Profile?
        get() = when (val s = state.value) {
            is SessionState.Ready -> s.profile
            is SessionState.Onboarding -> s.profile
            else -> null
        }
}
