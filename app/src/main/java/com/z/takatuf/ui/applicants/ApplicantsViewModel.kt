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
import com.z.takatuf.data.toAppError
import com.z.takatuf.ui.common.UserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ApplicantFilter { All, New, Shortlisted, Withdrawn }

data class ApplicantsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val listing: Listing? = null,
    /** Everyone who applied, ordered for review: new first, then shortlisted, withdrawn last. */
    val applicants: List<Application> = emptyList(),
    val passports: Map<String, PassportAccess> = emptyMap(),
    val filter: ApplicantFilter = ApplicantFilter.All,
    val message: UserMessage? = null,
) {
    val shown: List<Application>
        get() = applicants.filter {
            when (filter) {
                ApplicantFilter.All -> it.status.isActive
                ApplicantFilter.New -> it.isNew
                ApplicantFilter.Shortlisted -> it.status == ApplicationStatus.Shortlisted
                ApplicantFilter.Withdrawn -> it.status == ApplicationStatus.Withdrawn
            }
        }

    fun count(filter: ApplicantFilter): Int = copy(filter = filter).shown.size
}

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicantsViewModel(
    private val listingId: String,
    listings: ListingRepository,
    private val applications: ApplicationRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val retry = MutableStateFlow(0)
    private val filter = MutableStateFlow(ApplicantFilter.All)
    private val passports = MutableStateFlow<Map<String, PassportAccess>>(emptyMap())
    private val message = MutableStateFlow<UserMessage?>(null)
    private val requested = mutableSetOf<String>()

    private sealed interface Data {
        data object Loading : Data
        data class Loaded(val listing: Listing?, val applications: List<Application>) : Data
        data class Failed(val error: AppError) : Data
    }

    private val data = retry.flatMapLatest {
        combine(listings.observe(listingId), applications.observeForListing(listingId)) { listing, apps ->
            Data.Loaded(listing, apps) as Data
        }
            .onEach { if (it is Data.Loaded) loadPassports(it.applications) }
            .onStart { emit(Data.Loading) }
            // Anyone but the owner is refused here by the security rules.
            .catch { emit(Data.Failed(it.toAppError())) }
    }

    val state: StateFlow<ApplicantsUiState> =
        combine(data, filter, passports, message) { d, f, p, m ->
            val loaded = d as? Data.Loaded
            val apps = loaded?.applications.orEmpty()
            ApplicantsUiState(
                loading = d is Data.Loading,
                error = (d as? Data.Failed)?.error,
                listing = loaded?.listing,
                applicants = apps.sortedWith(
                    compareBy<Application> { !it.status.isActive }
                        .thenByDescending { it.isNew }
                        .thenByDescending { it.status == ApplicationStatus.Shortlisted },
                ),
                passports = p,
                filter = f,
                message = m,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplicantsUiState())

    /** Passports are read one by one; the rules decide which ones this owner may open. */
    private fun loadPassports(apps: List<Application>) {
        apps.map { it.applicantId }.filter { requested.add(it) }.forEach { uid ->
            viewModelScope.launch {
                val access = attempt { profiles.passportFor(uid) }.getOrDefault(PassportAccess.Hidden)
                passports.update { it + (uid to access) }
            }
        }
    }

    fun onFilter(value: ApplicantFilter) = filter.update { value }

    fun retry() = retry.update { it + 1 }

    fun messageShown() = message.update { null }

    fun setShortlisted(applicantId: String, shortlisted: Boolean) {
        viewModelScope.launch {
            attempt { applications.setShortlisted(listingId, applicantId, shortlisted) }
                .onFailure { e -> message.update { UserMessage(e.toAppError().message) } }
        }
    }
}
