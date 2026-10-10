package com.z.takatuf.ui.applicants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.ProfileRepository
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.PassportAccess
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.toAppError
import com.z.takatuf.ui.common.UserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApplicantDetailUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val application: Application? = null,
    val listing: Listing? = null,
    val profile: Profile? = null,
    val passport: PassportAccess? = null,
    val busy: Boolean = false,
    val message: UserMessage? = null,
)

private data class Extras(
    val listing: Listing? = null,
    val profile: Profile? = null,
    val passport: PassportAccess? = null,
    val busy: Boolean = false,
    val message: UserMessage? = null,
)

class ApplicantDetailViewModel(
    private val listingId: String,
    private val applicantId: String,
    listings: ListingRepository,
    private val applications: ApplicationRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    private val extras = MutableStateFlow(Extras())
    private var markedReviewed = false

    init {
        viewModelScope.launch {
            val listing = attempt { listings.get(listingId) }.getOrNull()
            val profile = attempt { profiles.getProfile(applicantId) }.getOrNull()
            val passport = attempt { profiles.passportFor(applicantId) }.getOrDefault(PassportAccess.Hidden)
            extras.update { it.copy(listing = listing, profile = profile, passport = passport) }
        }
    }

    val state: StateFlow<ApplicantDetailUiState> =
        combine(
            applications.observe(listingId, applicantId)
                .onEach { app -> if (app != null && !app.reviewed) markReviewed() }
                .map { Result.success(it) }
                .catch { emit(Result.failure(it)) },
            extras,
        ) { result, e ->
            ApplicantDetailUiState(
                loading = false,
                error = result.exceptionOrNull()?.toAppError() ?: if (result.getOrNull() == null) AppError.NotFound else null,
                application = result.getOrNull(),
                listing = e.listing,
                profile = e.profile,
                passport = e.passport,
                busy = e.busy,
                message = e.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplicantDetailUiState())

    /** Opening an application clears its "new" mark for the owner. */
    private fun markReviewed() {
        if (markedReviewed) return
        markedReviewed = true
        viewModelScope.launch { attempt { applications.markReviewed(listingId, applicantId) } }
    }

    fun setShortlisted(shortlisted: Boolean) {
        if (extras.value.busy) return
        extras.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = attempt { applications.setShortlisted(listingId, applicantId, shortlisted) }
            extras.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { UserMessage(if (shortlisted) com.z.takatuf.R.string.msg_shortlisted else com.z.takatuf.R.string.msg_unshortlisted) },
                        onFailure = { e -> UserMessage(e.toAppError().message) },
                    ),
                )
            }
        }
    }

    fun messageShown() = extras.update { it.copy(message = null) }

    val isShortlisted: Boolean get() = state.value.application?.status == ApplicationStatus.Shortlisted
}
