package com.z.takatuf.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.data.AppError
import com.z.takatuf.data.AuthRepository
import com.z.takatuf.data.ProfileRepository
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Availability
import com.z.takatuf.data.model.Passport
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.Skill
import com.z.takatuf.data.model.Visibility
import com.z.takatuf.data.model.Weekday
import com.z.takatuf.data.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: Int = 0,
    val name: String = "",
    val major: String = "",
    val year: Int? = null,
    val headline: String = "",
    val skills: List<Skill> = emptyList(),
    val interests: List<String> = emptyList(),
    val availability: Availability = Availability(days = Weekday.entries.filter { it.ordinal <= Weekday.Thu.ordinal }.toSet()),
    val visibility: Visibility = Visibility.Everyone,
    val nameError: Boolean = false,
    val saving: Boolean = false,
    val error: AppError? = null,
) {
    val isLastStep: Boolean get() = step == LAST_STEP

    companion object {
        const val LAST_STEP = 2
    }
}

class OnboardingViewModel(
    private val uid: String,
    initial: Profile?,
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OnboardingUiState(
            name = initial?.displayName.orEmpty(),
            major = initial?.major.orEmpty(),
            year = initial?.year,
            headline = initial?.headline.orEmpty(),
            visibility = initial?.visibility ?: Visibility.Everyone,
        ),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Fills fields the student hasn't touched yet, e.g. when the profile arrives after sign-up. */
    fun prefill(profile: Profile) = _state.update {
        it.copy(
            name = it.name.ifBlank { profile.displayName },
            major = it.major.ifBlank { profile.major },
            year = it.year ?: profile.year,
            headline = it.headline.ifBlank { profile.headline },
        )
    }

    fun onName(value: String) = _state.update { it.copy(name = value, nameError = false) }
    fun onMajor(value: String) = _state.update { it.copy(major = value) }
    fun onYear(value: Int?) = _state.update { it.copy(year = value) }
    fun onHeadline(value: String) = _state.update { it.copy(headline = value) }
    fun onSkills(value: List<Skill>) = _state.update { it.copy(skills = value) }
    fun onInterests(value: List<String>) = _state.update { it.copy(interests = value) }
    fun onAvailability(value: Availability) = _state.update { it.copy(availability = value) }
    fun onVisibility(value: Visibility) = _state.update { it.copy(visibility = value) }

    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), error = null) }

    fun next() {
        val s = _state.value
        if (s.step == 0 && s.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        if (s.isLastStep) finish() else _state.update { it.copy(step = it.step + 1) }
    }

    /** Saves what has been entered so far; the rest can be added from the passport later. */
    fun finish() {
        val s = _state.value
        if (s.saving) return
        if (s.name.isBlank()) {
            _state.update { it.copy(step = 0, nameError = true) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            attempt {
                profiles.completeOnboarding(
                    profile = Profile(
                        uid = uid,
                        displayName = s.name.trim(),
                        major = s.major.trim(),
                        year = s.year,
                        headline = s.headline.trim(),
                        visibility = s.visibility,
                    ),
                    passport = Passport(
                        uid = uid,
                        skills = s.skills,
                        interests = s.interests,
                        availability = s.availability,
                    ),
                )
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = e.toAppError()) }
            }
            // On success the session moves on to the main app.
        }
    }

    fun signOut() = auth.signOut()
}
