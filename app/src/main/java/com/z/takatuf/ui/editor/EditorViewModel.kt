package com.z.takatuf.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.data.AiRepository
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.DraftStore
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.NotificationRepository
import com.z.takatuf.data.RoleTemplate
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.EligibleLevel
import com.z.takatuf.data.model.EventDetails
import com.z.takatuf.data.model.EventFormat
import com.z.takatuf.data.model.EventType
import com.z.takatuf.data.model.LinkedEvent
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.ListingStatus
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
import com.z.takatuf.data.model.Recruitment
import com.z.takatuf.data.model.Role
import com.z.takatuf.data.model.RoleSuggestion
import com.z.takatuf.data.toAppError
import com.z.takatuf.domain.Dates
import com.z.takatuf.domain.Validators
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
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
    /** Who may enter an event, checked against each student's year (US09). */
    val eligibleLevel: EligibleLevel = EligibleLevel.Any,
    /** Members who joined through offers; the team can't be set smaller than them plus the lead. */
    val memberCount: Int = 0,
    /** Roles the AI read from the description, waiting for the owner to add or ignore them. */
    val suggestions: List<RoleSuggestion> = emptyList(),
    val suggesting: Boolean = false,
    val suggestError: AppError? = null,
    /** Set after a suggestion round that found nothing to add. */
    val suggestedNothing: Boolean = false,
    /** False when the build has no OpenAI key; the editor then doesn't offer suggestions. */
    val aiAvailable: Boolean = false,
    /** Events a team request can be linked to. */
    val events: List<Listing> = emptyList(),
    val errors: Set<Field> = emptySet(),
    val saving: Boolean = false,
    val error: AppError? = null,
    /** Set once saved, so the screen can move on. */
    val savedId: String? = null,
    /** The form was filled from a draft kept on this device (US21). */
    val draftRestored: Boolean = false,
) {
    val isEditing: Boolean get() = editingId != null
    val openPlaces: Int get() = roles.sumOf { it.openings }

    /** The smallest team size allowed: the lead plus everyone who already joined. */
    val minTeamSize: Int get() = 1 + memberCount

    /** Enough written about the work for the AI to suggest roles from. */
    val canSuggest: Boolean get() = (outcome.trim().length + description.trim().length) >= SuggestMinChars
}

const val SuggestMinChars = 40

class EditorViewModel(
    kind: ListingKind,
    private val listingId: String?,
    eventId: String?,
    private val session: Session,
    private val listings: ListingRepository,
    private val ai: AiRepository,
    /** Suggested title for a team request, e.g. "Team for <event>", from string resources. */
    private val teamTitleFor: (eventTitle: String) -> String,
    private val applications: ApplicationRepository,
    private val notifications: NotificationRepository,
    private val drafts: DraftStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditorUiState(kind = kind, editingId = listingId, loading = listingId != null, aiAvailable = ai.isAvailable),
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /** The listing as it was before editing, to tell who needs to hear about a change. */
    private var original: Listing? = null

    /** New listings keep a draft on the device until they are published (US21). */
    private val draftKey = DraftStore.listing(kind.key, null) + eventId?.let { "_$it" }.orEmpty()

    init {
        if (listingId != null) load(listingId) else restoreDraft()
        if (listingId == null) {
            viewModelScope.launch {
                _state.map { draftValues(it) }.distinctUntilChanged().collectLatest { values ->
                    delay(400)
                    if (_state.value.savedId == null) drafts.save(draftKey, values)
                }
            }
        }
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
            original = listing
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
                    eligibleLevel = e?.eligibleLevel ?: s.eligibleLevel,
                    memberCount = listing.members.size,
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

    /**
     * Asks the AI to read the outcome and description and propose roles. Nothing is added until
     * the owner picks a suggestion, and the editor works the same when the service is down.
     */
    fun suggestRoles() {
        val s = _state.value
        if (s.suggesting || !s.canSuggest) return
        _state.update { it.copy(suggesting = true, suggestError = null, suggestedNothing = false) }
        viewModelScope.launch {
            attempt { ai.suggestRoles(s.kind, s.title, s.outcome, s.description, s.event?.title) }
                .onSuccess { found ->
                    _state.update { current ->
                        val used = current.roles.map { it.title.trim().lowercase() }.toSet()
                        val fresh = found.filter { it.title.trim().lowercase() !in used }
                        current.copy(suggesting = false, suggestions = fresh, suggestedNothing = fresh.isEmpty())
                    }
                }
                .onFailure { e -> _state.update { it.copy(suggesting = false, suggestError = e.toAppError()) } }
        }
    }

    fun acceptSuggestion(suggestion: RoleSuggestion) = _state.update { s ->
        s.copy(
            roles = s.roles + suggestion.toRole(),
            suggestions = s.suggestions - suggestion,
            errors = s.errors - Field.Roles - Field.TeamSize,
        )
    }

    fun acceptAllSuggestions() = _state.update { s ->
        s.copy(
            roles = s.roles + s.suggestions.map { it.toRole() },
            suggestions = emptyList(),
            errors = s.errors - Field.Roles - Field.TeamSize,
        )
    }

    fun dismissSuggestion(suggestion: RoleSuggestion) = _state.update { it.copy(suggestions = it.suggestions - suggestion) }

    fun dismissSuggestions() = _state.update { it.copy(suggestions = emptyList(), suggestError = null, suggestedNothing = false) }

    private fun RoleSuggestion.toRole() = Role(
        id = newId(),
        title = title.trim().take(60),
        description = description.trim().take(300),
        skills = skills.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8),
        openings = openings.coerceIn(1, 10),
    )

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
                if (s.editingId == null) drafts.clear(draftKey)
                original?.let { before -> tellAboutChange(before, listing.copy(id = id)) }
                _state.update { it.copy(saving = false, savedId = id) }
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = e.toAppError()) }
            }
        }
    }

    /** Starts the form again from empty, dropping the saved draft. */
    fun discardDraft() {
        drafts.clear(draftKey)
        _state.update { s ->
            EditorUiState(kind = s.kind, aiAvailable = s.aiAvailable, events = s.events, event = s.event)
                .let { fresh -> s.event?.let { fresh.copy(title = teamTitleFor(it.title)) } ?: fresh }
        }
    }

    /**
     * Lets the people affected know when an owner changes something that matters to them (US19):
     * applicants and members hear about a team's changed terms, and the teams entered for an
     * event hear about its changed dates or rules. Best effort; the edit is already saved.
     */
    private fun tellAboutChange(before: Listing, after: Listing) {
        val me = session.profile ?: return
        viewModelScope.launch {
            val actionId = UUID.randomUUID().toString()
            if (after.kind == ListingKind.Event && eventChanged(before, after)) {
                val teams = attempt { listings.observeTeamsFor(after.id).first() }.getOrDefault(emptyList())
                teams.filter { !it.isCompleted }.forEach { team ->
                    notifications.send(
                        "${actionId}_${team.id}", listOf(team.ownerId) + team.memberIds, NotificationType.EventChanged,
                        team.id, team.title, me.uid, me.displayName, NotificationTarget.Listing, targetId = after.id,
                    )
                }
            } else if (after.kind.recruits && termsChanged(before, after)) {
                val active = attempt { applications.observeForListing(after.id).first() }.getOrDefault(emptyList())
                    .filter { it.status.isActive }.map { it.applicantId }
                notifications.send(
                    actionId, before.memberIds + active, NotificationType.ListingChanged,
                    after.id, after.title, me.uid, me.displayName, NotificationTarget.Listing,
                )
            }
        }
    }

    private fun eventChanged(before: Listing, after: Listing): Boolean {
        val a = before.event ?: return false
        val b = after.event ?: return false
        return a.startDate != b.startDate || a.endDate != b.endDate || a.registrationDeadline != b.registrationDeadline ||
            a.teamSizeMin != b.teamSizeMin || a.teamSizeMax != b.teamSizeMax || a.eligibility != b.eligibility ||
            a.eligibleLevel != b.eligibleLevel || a.format != b.format || a.location != b.location
    }

    private fun termsChanged(before: Listing, after: Listing): Boolean {
        val a = before.recruitment ?: return false
        val b = after.recruitment ?: return false
        fun Role.terms() = Triple(title, skills, openings)
        return a.deadline != b.deadline || a.hoursPerWeek != b.hoursPerWeek || a.duration != b.duration ||
            a.outcome != b.outcome || a.roles.map { it.terms() } != b.roles.map { it.terms() } ||
            before.title != after.title
    }

    private fun draftValues(s: EditorUiState): Map<String, String> {
        val written = listOf(s.title, s.description, s.outcome, s.organizer, s.eligibility).any { it.isNotBlank() } || s.roles.isNotEmpty()
        // Only worth keeping once something has been written; the defaults alone aren't a draft.
        if (!written || s.savedId != null) return emptyMap()
        val roles = JSONArray(
            s.roles.map {
                JSONObject().put("id", it.id).put("title", it.title).put("description", it.description)
                    .put("skills", JSONArray(it.skills)).put("openings", it.openings)
            },
        )
        return mapOf(
            "title" to s.title, "description" to s.description, "outcome" to s.outcome, "duration" to s.duration,
            "hours" to s.hoursPerWeek.toString(), "teamSize" to s.teamSize.toString(), "roles" to roles.toString(),
            "deadline" to s.deadline?.toString().orEmpty(), "organizer" to s.organizer, "sourceUrl" to s.sourceUrl,
            "eligibility" to s.eligibility, "location" to s.location, "start" to s.startDate?.toString().orEmpty(),
            "end" to s.endDate?.toString().orEmpty(), "registration" to s.registrationDeadline?.toString().orEmpty(),
            "eventType" to s.eventType.name, "format" to s.format.name, "level" to s.eligibleLevel.name,
            "sizeMin" to s.teamSizeMin.toString(), "sizeMax" to s.teamSizeMax.toString(),
        )
    }

    private fun restoreDraft() {
        val d = drafts.load(draftKey) ?: return
        fun date(key: String) = d[key]?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val roles = runCatching {
            val array = JSONArray(d["roles"].orEmpty().ifBlank { "[]" })
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val skills = o.optJSONArray("skills") ?: JSONArray()
                Role(
                    id = o.optString("id").ifBlank { newId() },
                    title = o.optString("title"),
                    description = o.optString("description"),
                    skills = (0 until skills.length()).map { skills.getString(it) },
                    openings = o.optInt("openings", 1),
                )
            }
        }.getOrDefault(emptyList())
        _state.update { s ->
            s.copy(
                title = d["title"].orEmpty().ifBlank { s.title },
                description = d["description"].orEmpty(),
                outcome = d["outcome"].orEmpty(),
                duration = d["duration"].orEmpty(),
                hoursPerWeek = d["hours"]?.toIntOrNull() ?: s.hoursPerWeek,
                teamSize = d["teamSize"]?.toIntOrNull() ?: s.teamSize,
                roles = roles,
                deadline = date("deadline"),
                organizer = d["organizer"].orEmpty(),
                sourceUrl = d["sourceUrl"].orEmpty(),
                eligibility = d["eligibility"].orEmpty(),
                location = d["location"].orEmpty(),
                startDate = date("start"),
                endDate = date("end"),
                registrationDeadline = date("registration"),
                eventType = EventType.entries.firstOrNull { it.name == d["eventType"] } ?: s.eventType,
                format = EventFormat.entries.firstOrNull { it.name == d["format"] } ?: s.format,
                eligibleLevel = EligibleLevel.entries.firstOrNull { it.name == d["level"] } ?: s.eligibleLevel,
                teamSizeMin = d["sizeMin"]?.toIntOrNull() ?: s.teamSizeMin,
                teamSizeMax = d["sizeMax"]?.toIntOrNull() ?: s.teamSizeMax,
                draftRestored = true,
            )
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
                    else if (s.teamSize + s.openPlaces < event.teamSizeMin) add(Field.TeamSize)
                }
                if (s.teamSize < s.minTeamSize) add(Field.TeamSize)
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
                    eligibleLevel = e.event?.eligibleLevel ?: EligibleLevel.Any,
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
                eligibleLevel = s.eligibleLevel,
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
