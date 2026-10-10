package com.z.takatuf.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.toAppError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class OwnedListing(
    val listing: Listing,
    val active: Int,
    val new: Int,
    val shortlisted: Int,
)

data class ActivityUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val applications: List<Application> = emptyList(),
    val owned: List<OwnedListing> = emptyList(),
) {
    val newApplicants: Int get() = owned.sumOf { it.new }
    val activeApplications: Int get() = applications.count { it.status.isActive }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    session: Session,
    listings: ListingRepository,
    applications: ApplicationRepository,
) : ViewModel() {

    private val uid = session.uid.orEmpty()
    private val retry = MutableStateFlow(0)

    val state: StateFlow<ActivityUiState> = retry.flatMapLatest {
        combine(
            applications.observeMine(uid),
            listings.observeOwnedBy(uid),
            applications.observeIncoming(uid),
        ) { mine, owned, incoming ->
            val byListing = incoming.groupBy { it.listingId }
            ActivityUiState(
                loading = false,
                applications = mine,
                owned = owned.map { listing ->
                    val apps = byListing[listing.id].orEmpty()
                    OwnedListing(
                        listing = listing,
                        active = apps.count { it.status.isActive },
                        new = apps.count { it.isNew },
                        shortlisted = apps.count { it.status == ApplicationStatus.Shortlisted },
                    )
                },
            )
        }.catch { emit(ActivityUiState(loading = false, error = it.toAppError())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    fun retry() = retry.update { it + 1 }
}

/** Badge count for the Activity tab: applicants the owner hasn't opened yet. */
@OptIn(ExperimentalCoroutinesApi::class)
fun ApplicationRepository.newApplicantCount(uid: String) =
    observeIncoming(uid).map { apps -> apps.count { it.isNew } }.catch { emit(0) }
