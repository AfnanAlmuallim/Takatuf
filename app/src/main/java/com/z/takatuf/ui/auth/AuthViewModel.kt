package com.z.takatuf.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.AuthRepository
import com.z.takatuf.data.ProfileRepository
import com.z.takatuf.data.attempt
import com.z.takatuf.data.toAppError
import com.z.takatuf.domain.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SignIn, SignUp, Reset }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    val submitting: Boolean = false,
    val error: AppError? = null,
    /** Set after a reset link was requested for this address. */
    val resetSentTo: String? = null,
)

class AuthViewModel(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setMode(mode: AuthMode) = _state.update {
        it.copy(mode = mode, nameError = null, emailError = null, passwordError = null, error = null, resetSentTo = null)
    }

    /** Forgets everything typed, including the password, once the form is left behind. */
    fun clear() = _state.update { AuthUiState() }

    fun onName(value: String) = _state.update { it.copy(name = value, nameError = null, error = null) }
    fun onEmail(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        val nameError = if (s.mode == AuthMode.SignUp && s.name.isBlank()) R.string.auth_error_name else null
        // Only an empty field is caught here; Firebase rejects malformed addresses itself.
        val emailError = if (s.email.isBlank()) R.string.auth_error_email else null
        val passwordError = when {
            s.mode == AuthMode.Reset -> null
            s.mode == AuthMode.SignIn && s.password.isEmpty() -> R.string.auth_error_password_empty
            s.mode == AuthMode.SignUp && !Validators.isStrongPassword(s.password) -> R.string.auth_error_password_weak
            else -> null
        }
        if (nameError != null || emailError != null || passwordError != null) {
            _state.update { it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            attempt {
                when (s.mode) {
                    AuthMode.SignIn -> auth.signIn(s.email, s.password)
                    AuthMode.SignUp -> {
                        val uid = auth.signUp(s.email, s.password)
                        // If this write fails, onboarding creates the profile instead.
                        attempt { profiles.createProfile(uid, s.name.trim()) }
                    }
                    AuthMode.Reset -> {
                        auth.sendPasswordReset(s.email)
                        _state.update { it.copy(resetSentTo = s.email.trim()) }
                    }
                }
            }.onSuccess {
                // The session switches to the next screen by itself.
                _state.update { it.copy(submitting = false) }
            }.onFailure { e ->
                _state.update { it.copy(submitting = false, error = e.toAppError()) }
            }
        }
    }
}
