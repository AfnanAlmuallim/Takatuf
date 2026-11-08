package com.z.takatuf.ui.passport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.AuthRepository
import com.z.takatuf.data.ProfileRepository
import com.z.takatuf.data.RecordsRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Availability
import com.z.takatuf.data.model.Contribution
import com.z.takatuf.data.model.Feedback
import com.z.takatuf.data.model.Participation
import com.z.takatuf.data.model.EvidenceLink
import com.z.takatuf.data.model.Experience
import com.z.takatuf.data.model.Passport
import com.z.takatuf.data.model.PassportAccess
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.Skill
import com.z.takatuf.data.model.Visibility
import com.z.takatuf.data.toAppError
import com.z.takatuf.ui.common.UserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MyPassportUiState(
    val profile: Profile? = null,
    val passport: Passport? = null,
    val email: String? = null,
    /** Teams this student has been part of, what they did and what teammates said (US17, US18). */
    val records: List<Participation> = emptyList(),
    val contributions: List<Contribution> = emptyList(),
    val feedback: List<Feedback> = emptyList(),
    /** Administrators also see the moderation queue (US20). */
    val isAdmin: Boolean = false,
    val saving: Boolean = false,
    /** Bumped after each successful save so an open sheet can close. */
    val savedAt: Long = 0,
    val message: UserMessage? = null,
)

private data class Activity(val saving: Boolean = false, val savedAt: Long = 0, val message: UserMessage? = null)

private data class RecordData(
    val records: List<Participation> = emptyList(),
    val contributions: List<Contribution> = emptyList(),
    val feedback: List<Feedback> = emptyList(),
)

class MyPassportViewModel(
    private val session: Session,
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
    private val recordsRepo: RecordsRepository,
    isAdmin: Flow<Boolean>,
) : ViewModel() {

    private val activity = MutableStateFlow(Activity())
    private val uid = session.uid.orEmpty()

    private val recordData: Flow<RecordData> = combine(
        recordsRepo.observeParticipation(uid).catch { emit(emptyList()) },
        recordsRepo.observeContributions(uid).catch { emit(emptyList()) },
        recordsRepo.observeFeedback(uid).catch { emit(emptyList()) },
    ) { p, c, f -> RecordData(p, c, f) }.onStart { emit(RecordData()) }

    val state: StateFlow<MyPassportUiState> =
        combine(session.state, session.passport, recordData, isAdmin, activity) { _, passport, r, admin, a ->
            MyPassportUiState(
                profile = session.profile,
                passport = passport ?: session.uid?.let { Passport(uid = it) },
                email = session.email,
                records = r.records,
                contributions = r.contributions,
                feedback = r.feedback,
                isAdmin = admin,
                saving = a.saving,
                savedAt = a.savedAt,
                message = a.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyPassportUiState())

    private fun passport(): Passport? = state.value.passport

    fun saveBasics(name: String, major: String, year: Int?, headline: String) {
        val profile = state.value.profile ?: return
        if (name.isBlank()) return
        save { profiles.saveProfile(profile.copy(displayName = name.trim(), major = major.trim(), year = year, headline = headline.trim())) }
    }

    fun setVisibility(visibility: Visibility) {
        val profile = state.value.profile ?: return
        if (profile.visibility == visibility) return
        save(R.string.msg_visibility_saved) { profiles.saveProfile(profile.copy(visibility = visibility)) }
    }

    fun saveSkills(skills: List<Skill>) = updatePassport { it.copy(skills = skills) }
    fun saveInterests(interests: List<String>) = updatePassport { it.copy(interests = interests) }
    fun saveAvailability(availability: Availability) = updatePassport { it.copy(availability = availability) }
    fun saveExperience(experience: List<Experience>) = updatePassport { it.copy(experience = experience) }
    fun saveLinks(links: List<EvidenceLink>) = updatePassport { it.copy(links = links) }

    private fun updatePassport(transform: (Passport) -> Passport) {
        val current = passport() ?: return
        save { profiles.savePassport(transform(current)) }
    }

    fun sendPasswordReset() {
        val email = session.email ?: return
        viewModelScope.launch {
            val result = attempt { auth.sendPasswordReset(email) }
            activity.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { UserMessage(R.string.msg_reset_sent, listOf(email)) },
                        onFailure = { e -> UserMessage(e.toAppError().message) },
                    ),
                )
            }
        }
    }


    // Records (US17, US18)

    fun addContribution(record: Participation, title: String, detail: String, links: List<String>) {
        val me = session.profile ?: return
        val id = UUID.randomUUID().toString()
        save(R.string.msg_contribution_saved) {
            recordsRepo.addContribution(id, me.uid, record.listingId, record.listingTitle, title, detail, links, me)
        }
    }

    fun editContribution(contribution: Contribution, title: String, detail: String, links: List<String>) =
        save(R.string.msg_contribution_saved) { recordsRepo.editContribution(contribution, title, detail, links) }

    fun deleteContribution(contribution: Contribution) =
        save(R.string.msg_contribution_deleted) { recordsRepo.deleteContribution(contribution) }

    /** Marks a review as disputed and sends it to the moderators, with the student's reason. */
    fun dispute(feedback: Feedback, reason: String) {
        val me = session.profile ?: return
        save(R.string.msg_dispute_sent) { recordsRepo.disputeFeedback(feedback, me, reason) }
    }

    fun messageShown() = activity.update { it.copy(message = null) }

    private fun save(successMessage: Int = R.string.msg_passport_saved, block: suspend () -> Unit) {
        if (activity.value.saving) return
        activity.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = attempt { block() }
            activity.update {
                it.copy(
                    saving = false,
                    savedAt = if (result.isSuccess) System.nanoTime() else it.savedAt,
                    message = result.fold(
                        onSuccess = { UserMessage(successMessage) },
                        onFailure = { e -> UserMessage(e.toAppError().message, retry = { save(successMessage, block) }) },
                    ),
                )
            }
        }
    }
}

data class PersonUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val profile: Profile? = null,
    val passport: PassportAccess? = null,
    val records: List<Participation> = emptyList(),
    val contributions: List<Contribution> = emptyList(),
    val feedback: List<Feedback> = emptyList(),
)

/** Another student's passport, as the security rules allow the viewer to see it. */
class PersonViewModel(
    private val uid: String,
    private val profiles: ProfileRepository,
    private val records: RecordsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PersonUiState())
    val state: StateFlow<PersonUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val profile = profiles.getProfile(uid)
                val passport = if (profile != null) profiles.passportFor(uid) else null
                // The record follows the passport's visibility; a hidden passport shows none of it.
                val history = if (passport is PassportAccess.Visible) {
                    RecordData(
                        attempt { records.observeParticipation(uid).first() }.getOrDefault(emptyList()),
                        attempt { records.observeContributions(uid).first() }.getOrDefault(emptyList()),
                        attempt { records.observeVisibleFeedback(uid).first() }.getOrDefault(emptyList()),
                    )
                } else {
                    RecordData()
                }
                Triple(profile, passport, history)
            }.onSuccess { (profile, passport, history) ->
                _state.update {
                    PersonUiState(
                        loading = false,
                        error = if (profile == null) AppError.NotFound else null,
                        profile = profile,
                        passport = passport,
                        records = history.records,
                        contributions = history.contributions,
                        feedback = history.feedback,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.toAppError()) }
            }
        }
    }
}
