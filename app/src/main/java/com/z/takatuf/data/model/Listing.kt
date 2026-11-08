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

/** Who may enter an event, checked against the year on each student's profile. */
enum class EligibleLevel(val key: String) {
    Any("any"), Undergraduate("undergraduate"), Graduate("graduate");

    /** True or false when the year settles it; null when the student hasn't recorded a year. */
    fun admits(year: Int?): Boolean? = when {
        this == Any -> true
        year == null -> null
        this == Undergraduate -> year in 1..5
        else -> year >= 6
    }

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Any
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
    /** Members the team has now: the lead, anyone joined through Takatuf, and anyone added outside it. */
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
    val eligibleLevel: EligibleLevel = EligibleLevel.Any,
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
    val eligibleLevel: EligibleLevel = EligibleLevel.Any,
)

/** Someone who joined the team by accepting an offer (the lead is not listed here). */
data class TeamMember(
    val uid: String,
    val name: String,
    val major: String,
    val roleId: String,
    val roleTitle: String,
    val joinedAt: Instant?,
)

/** A leadership handover waiting for the nominated member to accept. */
data class PendingLeader(val uid: String, val name: String, val requestedAt: Instant?)

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

    /** Every place on the team is filled. */
    Full,

    /** The lead marked the project complete (US17). */
    Completed,

    /** Moderators hid the listing after a report (US20). */
    Hidden,
}

/** How a completed project ended: a summary and, ideally, a link to the result (US17). */
data class Completion(val summary: String, val link: String)

/** A moderator's "details may be outdated" flag, shown until the owner corrects them (R6). */
data class ListingFlag(val reason: String, val at: Instant?)

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
    val members: List<TeamMember> = emptyList(),
    /** People who left or were removed; rejoining puts them back in [members]. */
    val formerMembers: List<TeamMember> = emptyList(),
    val pendingLeader: PendingLeader? = null,
    val completedAt: Instant? = null,
    val completion: Completion? = null,
    val hidden: Boolean = false,
    val flag: ListingFlag? = null,
    val moderationNote: String = "",
) {
    val isCompleted: Boolean get() = completedAt != null

    val memberIds: List<String> get() = members.map { it.uid }

    /** Former members who aren't back on the team; their work can still be confirmed and reviewed. */
    val departed: List<TeamMember> get() = formerMembers.filter { !isOnTeam(it.uid) }

    /** Everyone on the team. The recorded size can include people who aren't on Takatuf. */
    val headcount: Int get() = maxOf(recruitment?.teamSize ?: 0, 1 + members.size)

    fun isLead(uid: String): Boolean = uid == ownerId

    /** The lead or anyone who joined; they can open the team workspace. */
    fun isOnTeam(uid: String): Boolean = uid == ownerId || members.any { it.uid == uid }

    /** Eligibility rule the team's members must meet, from the event a team request is for. */
    val eligibleLevel: EligibleLevel
        get() = linkedEvent?.eligibleLevel ?: event?.eligibleLevel ?: EligibleLevel.Any

    val skillsWanted: List<String>
        get() = recruitment?.roles.orEmpty().flatMap { it.skills }.distinctBy { it.lowercase() }

    /**
     * Why the listing no longer takes applications (or registrations), or null while it does.
     *
     * Every deadline and end date is exclusive: open strictly before it, closed from that
     * instant on. A team request copies its event's dates when it's linked, so pass the live
     * [parentEvent] when it's known; its current end date wins over that copy.
     */
    fun closedReason(now: Instant, parentEvent: Listing? = null): ClosedReason? {
        val linkedEnd = parentEvent?.takeIf { it.id == linkedEvent?.id }?.event?.endDate ?: linkedEvent?.endDate
        return when {
            hidden -> ClosedReason.Hidden
            completedAt != null -> ClosedReason.Completed
            status == ListingStatus.Closed -> ClosedReason.ClosedByOwner
            event != null && reached(event.endDate, now) -> ClosedReason.Ended
            reached(event?.registrationDeadline, now) -> ClosedReason.RegistrationClosed
            reached(linkedEnd, now) -> ClosedReason.EventOver
            recruitment != null && recruitment.openPlaces == 0 -> ClosedReason.Full
            reached(recruitment?.deadline, now) -> ClosedReason.DeadlinePassed
            else -> null
        }
    }

    /** True from the [limit] instant on; an unset limit is never reached. */
    private fun reached(limit: Instant?, now: Instant): Boolean = limit != null && !now.isBefore(limit)

    /** The moment that matters most for "closing soon" ordering. */
    val closesAt: Instant?
        get() = recruitment?.deadline ?: event?.registrationDeadline ?: event?.startDate
}
