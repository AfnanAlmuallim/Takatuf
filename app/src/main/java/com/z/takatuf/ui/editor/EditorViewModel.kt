package com.z.takatuf.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.RoleTemplate
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.EventDetails
import com.z.takatuf.data.model.EventFormat
import com.z.takatuf.data.model.EventType
import com.z.takatuf.data.model.LinkedEvent
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.ListingStatus
import com.z.takatuf.data.model.Recruitment
import com.z.takatuf.data.model.Role
import com.z.takatuf.data.toAppError
import com.z.takatuf.domain.Dates
import com.z.takatuf.domain.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class Field {
    Title, Outcome, Roles, Event, TeamSize, Organizer, SourceUrl, StartDate, EndDate,
    Eligibility, TeamSizeRange, Deadline, RegistrationDeadline,
}

data class EditorUiState(
    val kind: ListingKind,
    val editingId: String? = null,
    val loading: Boolean = false,
    val title: String = "",
    val description: String = "",
    val outcome: String = "",
    val roles: List<Role> = emptyList(),
    val hoursPerWeek: Int = 6,
    val duration: String = "",
    val teamSize: Int = 1,
    val deadline: LocalDate? = null,
    val status: ListingStatus = ListingStatus.Open,
    /** The event a team request is for. */
    val event: Listing? = null,
    val eventType: EventType = EventType.Competition,
    val organizer: String = "",
    val sourceUrl: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val registrationDeadline: LocalDate? = null,
    val eligibility: String = "",
    val teamSizeMin: Int = 2,
    val teamSizeMax: Int = 5,
    val format: EventFormat = EventFormat.InPerson,
    val location: String = "",
    /** Events a team request can be linked to. */
    val events: List<Listing> = emptyList(),
    val errors: Set<Field> = emptySet(),
    val saving: Boolean = false,
    val error: AppError? = null,
    /** Set once saved, so the screen can move on. */
    val savedId: String? = null,
) {
    val isEditing: Boolean get() = editingId != null
    val openPlaces: Int get() = roles.sumOf { it.openings }
}

class EditorViewModel(
    kind: ListingKind,
    private val listingId: String?,
    eventId: String?,
    private val session: Session,
    private val listings: ListingRepository,
    /** Suggested title for a team request, e.g. "Team for <event>", from string resources. */
    private val teamTitleFor: (eventTitle: String) -> String,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState(kind = kind, editingId = listingId, loading = listingId != null))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        if (listingId != null) load(listingId)
        if (kind == ListingKind.Team) {
            viewModelScope.launch {
                listings.observeFeed()
                    .catch { emit(emptyList()) }
                    .collect { feed ->
                        val now = Instant.now()
                        val events = feed.filter { it.kind == ListingKind.Event && it.event?.endDate?.isAfter(now) == true }
                            .sortedBy { it.event?.startDate }
                        _state.update { it.copy(events = events) }
                    }
            }
            if (eventId != null && listingId == null) {
                viewModelScope.launch { attempt { listings.get(eventId) }.getOrNull()?.let(::chooseEvent) }
            }
        }
    }

    private fun load(id: String) {
        viewModelScope.launch {
            val listing = attempt { listings.get(id) }.getOrNull()
            if (listing == null) {
                _state.update { it.copy(loading = false, error = AppError.NotFound) }
                return@launch
            }
            val linkedEvent = listing.linkedEvent?.let { attempt { listings.get(it.id) }.getOrNull() }
            _state.update { s ->
                val r = listing.recruitment
                val e = listing.event
                s.copy(
                    loading = false,
                    title = listing.title,
                    description = listing.description,
                    status = listing.status,
                    outcome = r?.outcome.orEmpty(),
                    roles = r?.roles.orEmpty(),
                    hoursPerWeek = r?.hoursPerWeek ?: s.hoursPerWeek,
                    duration = r?.duration.orEmpty(),
                    teamSize = r?.teamSize ?: s.teamSize,
                    deadline = r?.deadline?.let(Dates::lastDay),
                    event = linkedEvent,
                    eventType = e?.type ?: s.eventType,
                    organizer = e?.organizer.orEmpty(),
                    sourceUrl = e?.sourceUrl.orEmpty(),
                    startDate = e?.startDate?.let(Dates::startDate),
                    endDate = e?.endDate?.let(Dates::lastDay),
                    registrationDeadline = e?.registrationDeadline?.let(Dates::lastDay),
                    eligibility = e?.eligibility.orEmpty(),
                    teamSizeMin = e?.teamSizeMin ?: s.teamSizeMin,
                    teamSizeMax = e?.teamSizeMax ?: s.teamSizeMax,
                    format = e?.format ?: s.format,
                    location = e?.location.orEmpty(),
                )
            }
        }
    }

    fun update(transform: (EditorUiState) -> EditorUiState) = _state.update { transform(it).copy(error = null) }

    fun clearError(field: Field) = _state.update { it.copy(errors = it.errors - field) }

    fun chooseEvent(event: Listing) = _state.update { s ->
        val details = event.event
        s.copy(
            event = event,
            title = s.title.ifBlank { teamTitleFor(event.title) },
            // Default the application deadline to the event's registration deadline.
            deadline = s.deadline ?: details?.registrationDeadline?.let(Dates::lastDay),
            errors = s.errors - Field.Event - Field.TeamSize,
        )
    }

    fun addRoleFromTemplate(template: RoleTemplate) = _state.update { s ->
        s.copy(
            roles = s.roles + Role(
                id = newId(),
                title = template.title,
                description = template.description,
                skills = template.skills,
                openings = 1,
            ),
            errors = s.errors - Field.Roles - Field.TeamSize,
        )
    }

    fun saveRole(role: Role) = _state.update { s ->
        val exists = s.roles.any { it.id == role.id }
        s.copy(
            roles = if (exists) s.roles.map { if (it.id == role.id) role else it } else s.roles + role,
            errors = s.errors - Field.Roles - Field.TeamSize,
        )
    }

    fun removeRole(id: String) = _state.update { s -> s.copy(roles = s.roles.filterNot { it.id == id }) }

    fun newRoleId(): String = newId()

    fun publish() {
        val s = _state.value
        if (s.saving) return
        val errors = validate(s)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val profile = session.profile ?: return
        val listing = build(s, profile.displayName, profile.major, session.uid.orEmpty())
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            attempt {
                if (s.editingId != null) {
                    listings.update(listing)
                    s.editingId
                } else {
                    listings.create(listing)
                }
            }.onSuccess { id ->
                _state.update { it.copy(saving = false, savedId = id) }
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = e.toAppError()) }
            }
        }
    }

    private fun validate(s: EditorUiState): Set<Field> = buildSet {
        val today = Dates.today()
        if (s.title.trim().length !in 4..90) add(Field.Title)
        when (s.kind) {
            ListingKind.Project, ListingKind.Team -> {
                if (s.kind == ListingKind.Project && s.outcome.trim().length < 10) add(Field.Outcome)
                if (s.roles.isEmpty() || s.openPlaces == 0) add(Field.Roles)
                if (s.kind == ListingKind.Team) {
                    val event = s.event?.event
                    if (event == null) add(Field.Event)
                    else if (s.teamSize + s.openPlaces > event.teamSizeMax) add(Field.TeamSize)
                }
                if (s.deadline != null && s.deadline.isBefore(today) && !s.isEditing) add(Field.Deadline)
            }
            ListingKind.Event -> {
                if (s.organizer.isBlank()) add(Field.Organizer)
                if (Validators.normalizeUrl(s.sourceUrl) == null) add(Field.SourceUrl)
                if (s.startDate == null) add(Field.StartDate)
                if (s.endDate == null || (s.startDate != null && s.endDate.isBefore(s.startDate))) add(Field.EndDate)
                if (s.eligibility.isBlank()) add(Field.Eligibility)
                if (s.teamSizeMin < 1 || s.teamSizeMax < s.teamSizeMin) add(Field.TeamSizeRange)
                if (s.registrationDeadline != null && s.endDate != null && s.registrationDeadline.isAfter(s.endDate)) {
                    add(Field.RegistrationDeadline)
                }
            }
        }
    }

    private fun build(s: EditorUiState, ownerName: String, ownerMajor: String, ownerId: String): Listing {
        val recruitment = if (s.kind.recruits) {
            Recruitment(
                outcome = s.outcome.trim(),
                roles = s.roles,
                hoursPerWeek = s.hoursPerWeek,
                duration = s.duration.trim(),
                teamSize = s.teamSize,
                deadline = s.deadline?.let(Dates::endOf),
            )
        } else {
            null
        }
        val linkedEvent = if (s.kind == ListingKind.Team) {
            s.event?.let { e ->
                LinkedEvent(
                    id = e.id,
                    title = e.title,
                    organizer = e.event?.organizer.orEmpty(),
                    type = e.event?.type ?: EventType.Competition,
                    startDate = e.event?.startDate,
                    endDate = e.event?.endDate,
                    teamSizeMin = e.event?.teamSizeMin ?: 1,
                    teamSizeMax = e.event?.teamSizeMax ?: 1,
                )
            }
        } else {
            null
        }
        val event = if (s.kind == ListingKind.Event) {
            EventDetails(
                type = s.eventType,
                organizer = s.organizer.trim(),
                sourceUrl = Validators.normalizeUrl(s.sourceUrl).orEmpty(),
                startDate = Dates.startOf(s.startDate!!),
                endDate = Dates.endOf(s.endDate!!),
                registrationDeadline = s.registrationDeadline?.let(Dates::endOf),
                eligibility = s.eligibility.trim(),
                teamSizeMin = s.teamSizeMin,
                teamSizeMax = s.teamSizeMax,
                format = s.format,
                location = s.location.trim(),
            )
        } else {
            null
        }
        return Listing(
            id = s.editingId.orEmpty(),
            kind = s.kind,
            status = s.status,
            title = s.title.trim(),
            description = s.description.trim(),
            ownerId = ownerId,
            ownerName = ownerName,
            ownerMajor = ownerMajor,
            createdAt = null,
            updatedAt = null,
            recruitment = recruitment,
            linkedEvent = linkedEvent,
            event = event,
        )
    }

    private fun newId() = UUID.randomUUID().toString().take(8)
}
