package com.z.takatuf.ui.applicants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.ProfileRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.PassportAccess
import com.z.takatuf.data.toAppError
import com.z.takatuf.domain.ApplicantFit
import com.z.takatuf.ui.common.UserMessage
import com.z.takatuf.ui.team.PeopleCache
import com.z.takatuf.ui.team.fitsFor
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

enum class ApplicantFilter { All, New, Shortlisted, Offered, Joined, Past }

data class ApplicantsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val listing: Listing? = null,
    /** Everyone who applied, ordered for review: new first, then by fit. */
    val applicants: List<Application> = emptyList(),
    val passports: Map<String, PassportAccess> = emptyMap(),
    /** How each applicant fits their role, given the team as it is now (US09). */
    val fits: Map<String, ApplicantFit> = emptyMap(),
    val filter: ApplicantFilter = ApplicantFilter.All,
    val message: UserMessage? = null,
) {
    val shown: List<Application>
        get() = applicants.filter { matches(it, filter) }

    fun count(filter: ApplicantFilter): Int = applicants.count { matches(it, filter) }

    private fun matches(app: Application, filter: ApplicantFilter): Boolean = when (filter) {
        ApplicantFilter.All -> app.status.isActive
        ApplicantFilter.New -> app.isNew
        ApplicantFilter.Shortlisted -> app.status == ApplicationStatus.Shortlisted
        ApplicantFilter.Offered -> app.status == ApplicationStatus.Offered
        ApplicantFilter.Joined -> app.status == ApplicationStatus.Accepted
        ApplicantFilter.Past -> !app.status.isActive && app.status != ApplicationStatus.Accepted
    }

    /** Enough active applicants to suggest a team from. */
    val canSuggestTeam: Boolean
        get() = (listing?.recruitment?.openPlaces ?: 0) > 0 &&
            applicants.any { it.status in setOf(ApplicationStatus.Submitted, ApplicationStatus.Shortlisted, ApplicationStatus.Offered) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicantsViewModel(
    private val listingId: String,
    private val session: Session,
    listings: ListingRepository,
    private val applications: ApplicationRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    private val retry = MutableStateFlow(0)
    private val filter = MutableStateFlow(ApplicantFilter.All)
    private val message = MutableStateFlow<UserMessage?>(null)
    private val people = PeopleCache(viewModelScope, profiles)
    private val uid = session.uid.orEmpty()

    private sealed interface Data {
        data object Loading : Data
        data class Loaded(val listing: Listing?, val applications: List<Application>) : Data
        data class Failed(val error: AppError) : Data
    }

    private val data = retry.flatMapLatest {
        combine(listings.observe(listingId), applications.observeForListing(listingId)) { listing, apps ->
            Data.Loaded(listing, apps) as Data
        }
            .onEach {
                if (it is Data.Loaded) {
                    people.request(it.applications.map { app -> app.applicantId })
                    it.listing?.let { listing -> people.request(listing.memberIds + listing.ownerId) }
                }
            }
            .onStart { emit(Data.Loading) }
            // Anyone but the owner is refused here by the security rules.
            .catch { emit(Data.Failed(it.toAppError())) }
    }

    val state: StateFlow<ApplicantsUiState> =
        combine(data, filter, people.people, session.passport, message) { d, f, p, myPassport, m ->
            val loaded = d as? Data.Loaded
            val listing = loaded?.listing
            // After a handover the new lead's own application stays on record; it isn't an applicant.
            val apps = loaded?.applications.orEmpty().filter { it.applicantId != listing?.ownerId }
            val fits = if (listing != null) fitsFor(listing, apps, p, uid, myPassport) else emptyMap()
            ApplicantsUiState(
                loading = d is Data.Loading,
                error = (d as? Data.Failed)?.error,
                listing = listing,
                applicants = apps.sortedWith(
                    compareBy<Application> { !it.status.isActive }
                        .thenByDescending { it.isNew }
                        .thenByDescending { fits[it.applicantId]?.score ?: 0.0 },
                ),
                passports = p.passports,
                fits = fits,
                filter = f,
                message = m,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplicantsUiState())

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
