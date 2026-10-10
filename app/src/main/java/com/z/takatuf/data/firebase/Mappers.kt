package com.z.takatuf.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Availability
import com.z.takatuf.data.model.EventDetails
import com.z.takatuf.data.model.EventFormat
import com.z.takatuf.data.model.EventType
import com.z.takatuf.data.model.EvidenceLink
import com.z.takatuf.data.model.Experience
import com.z.takatuf.data.model.LinkedEvent
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.ListingStatus
import com.z.takatuf.data.model.Passport
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.Recruitment
import com.z.takatuf.data.model.Role
import com.z.takatuf.data.model.Skill
import com.z.takatuf.data.model.SkillLevel
import com.z.takatuf.data.model.Visibility
import com.z.takatuf.data.model.Weekday
import com.z.takatuf.domain.SkillMatcher

// Documents are mapped by hand rather than with reflection, so field names live in one
// place, missing fields get safe defaults, and release builds need no keep rules.

internal fun DocumentSnapshot.toProfile(): Profile? {
    if (!exists()) return null
    val f = fields()
    return Profile(
        uid = id,
        displayName = f.str("displayName"),
        major = f.str("major"),
        year = f.intOrNull("year"),
        headline = f.str("headline"),
        visibility = Visibility.from(f["visibility"] as? String),
        onboarded = f.bool("onboarded"),
    )
}

internal fun Profile.toFields(): Map<String, Any?> = mapOf(
    "displayName" to displayName.trim(),
    "major" to major.trim(),
    "year" to year,
    "headline" to headline.trim(),
    "visibility" to visibility.key,
    "onboarded" to onboarded,
    "updatedAt" to FieldValue.serverTimestamp(),
)

internal fun DocumentSnapshot.toPassport(): Passport? {
    if (!exists()) return null
    val f = fields()
    val availability = f.map("availability")
    return Passport(
        uid = id,
        skills = f.maps("skills").map {
            Skill(name = it.str("name"), level = SkillLevel.from(it["level"] as? Number))
        }.filter { it.name.isNotBlank() },
        interests = f.strings("interests"),
        experience = f.maps("experience").map {
            Experience(
                id = it.str("id"),
                title = it.str("title"),
                organization = it.str("organization"),
                period = it.str("period"),
                description = it.str("description"),
            )
        },
        availability = Availability(
            hoursPerWeek = availability?.int("hoursPerWeek") ?: 0,
            days = availability?.strings("days").orEmpty().mapNotNull(Weekday::from).toSet(),
            note = availability?.str("note").orEmpty(),
        ),
        links = f.maps("links").map {
            EvidenceLink(id = it.str("id"), label = it.str("label"), url = it.str("url"))
        },
    )
}

internal fun Passport.toFields(): Map<String, Any?> = mapOf(
    "skills" to skills.map { mapOf("name" to it.name.trim(), "level" to it.level.value) },
    "interests" to interests,
    "experience" to experience.map {
        mapOf(
            "id" to it.id,
            "title" to it.title.trim(),
            "organization" to it.organization.trim(),
            "period" to it.period.trim(),
            "description" to it.description.trim(),
        )
    },
    "availability" to mapOf(
        "hoursPerWeek" to availability.hoursPerWeek,
        "days" to Weekday.entries.filter { it in availability.days }.map { it.key },
        "note" to availability.note.trim(),
    ),
    "links" to links.map { mapOf("id" to it.id, "label" to it.label.trim(), "url" to it.url.trim()) },
    "updatedAt" to FieldValue.serverTimestamp(),
)

internal fun DocumentSnapshot.toListing(): Listing? {
    if (!exists()) return null
    val f = fields()
    val kind = ListingKind.from(f["kind"] as? String)
    val recruitment = if (kind.recruits) {
        Recruitment(
            outcome = f.str("outcome"),
            roles = f.maps("roles").map {
                Role(
                    id = it.str("id"),
                    title = it.str("title"),
                    description = it.str("description"),
                    skills = it.strings("skills"),
                    openings = it.int("openings", 1),
                )
            },
            hoursPerWeek = f.int("hoursPerWeek"),
            duration = f.str("duration"),
            teamSize = f.int("teamSize", 1),
            deadline = f.instant("deadline"),
        )
    } else {
        null
    }
    val linkedEvent = f.map("event")?.takeIf { kind == ListingKind.Team }?.let {
        LinkedEvent(
            id = f.str("eventId"),
            title = it.str("title"),
            organizer = it.str("organizer"),
            type = EventType.from(it["type"] as? String),
            startDate = it.instant("startDate"),
            endDate = it.instant("endDate"),
            teamSizeMin = it.int("teamSizeMin", 1),
            teamSizeMax = it.int("teamSizeMax", 1),
        )
    }
    val event = if (kind == ListingKind.Event) {
        val start = f.instant("startDate")
        val end = f.instant("endDate")
        if (start != null && end != null) {
            EventDetails(
                type = EventType.from(f["eventType"] as? String),
                organizer = f.str("organizer"),
                sourceUrl = f.str("sourceUrl"),
                startDate = start,
                endDate = end,
                registrationDeadline = f.instant("registrationDeadline"),
                eligibility = f.str("eligibility"),
                teamSizeMin = f.int("teamSizeMin", 1),
                teamSizeMax = f.int("teamSizeMax", 1),
                format = EventFormat.from(f["format"] as? String),
                location = f.str("location"),
            )
        } else {
            null
        }
    } else {
        null
    }
    return Listing(
        id = id,
        kind = kind,
        status = ListingStatus.from(f["status"] as? String),
        title = f.str("title"),
        description = f.str("description"),
        ownerId = f.str("ownerId"),
        ownerName = f.str("ownerName"),
        ownerMajor = f.str("ownerMajor"),
        createdAt = f.instant("createdAt"),
        updatedAt = f.instant("updatedAt"),
        recruitment = recruitment,
        linkedEvent = linkedEvent,
        event = event,
    )
}

/**
 * Fields written when a listing is created or edited. Ownership and creation time are added
 * by the repository, because the security rules forbid changing them afterwards.
 */
internal fun Listing.toEditableFields(): Map<String, Any?> {
    val fields = mutableMapOf<String, Any?>(
        "kind" to kind.key,
        "status" to status.key,
        "title" to title.trim(),
        "description" to description.trim(),
        "ownerName" to ownerName,
        "ownerMajor" to ownerMajor,
        "updatedAt" to FieldValue.serverTimestamp(),
    )
    recruitment?.let { r ->
        fields += mapOf(
            "outcome" to r.outcome.trim(),
            "roles" to r.roles.map {
                mapOf(
                    "id" to it.id,
                    "title" to it.title.trim(),
                    "description" to it.description.trim(),
                    "skills" to it.skills,
                    "openings" to it.openings,
                )
            },
            "hoursPerWeek" to r.hoursPerWeek,
            "duration" to r.duration.trim(),
            "teamSize" to r.teamSize,
            "openPlaces" to r.openPlaces,
            "deadline" to r.deadline?.toTimestamp(),
            // Normalised copy used for matching and search.
            "skills" to skillsWanted.map(SkillMatcher::normalize).distinct(),
        )
    }
    linkedEvent?.let { e ->
        fields += mapOf(
            "eventId" to e.id,
            "event" to mapOf(
                "title" to e.title,
                "organizer" to e.organizer,
                "type" to e.type.key,
                "startDate" to e.startDate?.toTimestamp(),
                "endDate" to e.endDate?.toTimestamp(),
                "teamSizeMin" to e.teamSizeMin,
                "teamSizeMax" to e.teamSizeMax,
            ),
        )
    }
    event?.let { e ->
        fields += mapOf(
            "eventType" to e.type.key,
            "organizer" to e.organizer.trim(),
            "sourceUrl" to e.sourceUrl.trim(),
            "startDate" to e.startDate.toTimestamp(),
            "endDate" to e.endDate.toTimestamp(),
            "registrationDeadline" to e.registrationDeadline?.toTimestamp(),
            "eligibility" to e.eligibility.trim(),
            "teamSizeMin" to e.teamSizeMin,
            "teamSizeMax" to e.teamSizeMax,
            "format" to e.format.key,
            "location" to e.location.trim(),
        )
    }
    return fields
}

internal fun DocumentSnapshot.toApplication(): Application? {
    if (!exists()) return null
    val f = fields()
    return Application(
        listingId = f.str("listingId"),
        ownerId = f.str("ownerId"),
        applicantId = f.str("applicantId").ifEmpty { id },
        applicantName = f.str("applicantName"),
        applicantMajor = f.str("applicantMajor"),
        listingTitle = f.str("listingTitle"),
        listingKind = ListingKind.from(f["listingKind"] as? String),
        roleId = f.str("roleId"),
        roleTitle = f.str("roleTitle"),
        contribution = f.str("contribution"),
        hoursPerWeek = f.int("hoursPerWeek"),
        availabilityNote = f.str("availabilityNote"),
        status = ApplicationStatus.from(f["status"] as? String),
        reviewed = f.bool("reviewed"),
        createdAt = f.instant("createdAt"),
        updatedAt = f.instant("updatedAt"),
    )
}
