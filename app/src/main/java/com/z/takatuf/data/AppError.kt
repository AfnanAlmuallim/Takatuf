package com.z.takatuf.data

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.z.takatuf.R

/** Failures the interface can explain. Each message says what happened and what to do next. */
enum class AppError(@param:StringRes val message: Int) {
    Offline(R.string.error_offline),
    PermissionDenied(R.string.error_permission_denied),
    NotFound(R.string.error_not_found),
    AlreadyApplied(R.string.error_already_applied),
    NotAccepting(R.string.error_not_accepting),
    EmailInUse(R.string.error_email_in_use),
    WrongCredentials(R.string.error_wrong_credentials),
    WeakPassword(R.string.error_weak_password),
    InvalidEmail(R.string.error_invalid_email),
    TooManyRequests(R.string.error_too_many_requests),
    UserDisabled(R.string.error_user_disabled),
    Unknown(R.string.error_unknown),
}

class AppException(val error: AppError, cause: Throwable? = null) : Exception(error.name, cause)

/** Like [runCatching], but lets coroutine cancellation propagate. */
inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun Throwable.toAppError(): AppError = when (this) {
    is AppException -> error
    is FirebaseAuthUserCollisionException -> AppError.EmailInUse
    is FirebaseAuthWeakPasswordException -> AppError.WeakPassword
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == "ERROR_INVALID_EMAIL") AppError.InvalidEmail else AppError.WrongCredentials
    is FirebaseAuthInvalidUserException ->
        if (errorCode == "ERROR_USER_DISABLED") AppError.UserDisabled else AppError.WrongCredentials
    is FirebaseTooManyRequestsException -> AppError.TooManyRequests
    is FirebaseNetworkException -> AppError.Offline
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.Offline
        else -> AppError.Unknown
    }
    else -> AppError.Unknown
}
