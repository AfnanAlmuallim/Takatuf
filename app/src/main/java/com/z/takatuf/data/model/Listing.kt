package com.z.takatuf.data.model

import java.time.Instant

enum class ListingKind(val key: String) {
    /** A student-led project recruiting members. */
    Project("project"),

    /** A team forming for a specific competition or hackathon. */
    Team("team"),

    /** A competition or hackathon shared so others can discover it. */
    Event("event");

    val recruits: Boolean get() = this != Event

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Project
    }
}

enum class ListingStatus(val key: String) {
    Open("open"), Closed("closed");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Open
    }
}

enum class EventType(val key: String) {
    Competition("competition"), Hackathon("hackathon");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Competition
    }
}

enum class EventFormat(val key: String) {
    InPerson("in_person"), Online("online"), Hybrid("hybrid");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: InPerson
    }
}

data class Role(
    val id: String,
    val title: String,
    val description: String = "",
    val skills: List<String> = emptyList(),
    val openings: Int = 1,
)

/** Recruitment details shared by projects and event team requests. */
data class Recruitment(
    val outcome: String,
    val roles: List<Role>,
    val hoursPerWeek: Int,
    val duration: String,
    /** Members the team already has, including the owner. */
    val teamSize: Int,
    /** When applications close; null keeps them open until the owner closes the listing. */
    val deadline: Instant?,
) {
    val openPlaces: Int get() = roles.sumOf { it.openings }
}

/** Official details of a competition or hackathon. */
data class EventDetails(
    val type: EventType,
    val organizer: String,
    val sourceUrl: String,
    val startDate: Instant,
    val endDate: Instant,
    val registrationDeadline: Instant?,
    val eligibility: String,
    val teamSizeMin: Int,
    val teamSizeMax: Int,
    val format: EventFormat,
    val location: String,
)

/** A copy of the key event facts kept on a team request, so list rows need no extra reads. */
data class LinkedEvent(
    val id: String,
    val title: String,
    val organizer: String,
    val type: EventType,
    val startDate: Instant?,
    val endDate: Instant?,
    val teamSizeMin: Int,
    val teamSizeMax: Int,
)

enum class ClosedReason {
    /** The owner closed recruitment. */
    ClosedByOwner,

    /** The application deadline passed. */
    DeadlinePassed,

    /** The team request's event is over. */
    EventOver,

    /** The event itself has ended. */
    Ended,

    /** The event still runs, but its registration deadline passed. */
    RegistrationClosed,
}

data class Listing(
    val id: String,
    val kind: ListingKind,
    val status: ListingStatus,
    val title: String,
    val description: String,
    val ownerId: String,
    val ownerName: String,
    val ownerMajor: String,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    /** Set for projects and team requests. */
    val recruitment: Recruitment? = null,
    /** Set for team requests. */
    val linkedEvent: LinkedEvent? = null,
    /** Set for events. */
    val event: EventDetails? = null,
) {
    val skillsWanted: List<String>
        get() = recruitment?.roles.orEmpty().flatMap { it.skills }.distinctBy { it.lowercase() }

    /** Why the listing no longer takes applications (or registrations), or null while it does. */
    fun closedReason(now: Instant): ClosedReason? = when {
        status == ListingStatus.Closed -> ClosedReason.ClosedByOwner
        event != null && !now.isBefore(event.endDate) -> ClosedReason.Ended
        event?.registrationDeadline?.let { !now.isBefore(it) } == true -> ClosedReason.RegistrationClosed
        linkedEvent?.endDate?.let { !now.isBefore(it) } == true -> ClosedReason.EventOver
        recruitment?.deadline?.let { !now.isBefore(it) } == true -> ClosedReason.DeadlinePassed
        else -> null
    }

    /** The moment that matters most for "closing soon" ordering. */
    val closesAt: Instant?
        get() = recruitment?.deadline ?: event?.registrationDeadline ?: event?.startDate
}
