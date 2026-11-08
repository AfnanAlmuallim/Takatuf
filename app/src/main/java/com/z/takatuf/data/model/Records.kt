package com.z.takatuf.data.model

import java.time.Instant

enum class ParticipationStatus(val key: String) {
    Active("active"), Completed("completed"), Left("left"), Removed("removed");

    /** Finished one way or another; feedback opens for this person (US18). */
    val ended: Boolean get() = this != Active

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Active
    }
}

/**
 * A student's part in one team, at `passports/{uid}/participation/{listingId}` (US17).
 * It follows the passport's visibility, so the record travels with the student.
 */
data class Participation(
    val listingId: String,
    val listingTitle: String,
    val kind: ListingKind,
    val role: String,
    val wasLead: Boolean,
    val status: ParticipationStatus,
    val startedAt: Instant?,
    val endedAt: Instant?,
)

/** How much a contribution claim is backed up, strongest first (US17). */
enum class Evidence {
    /** The team lead confirmed it. */
    Confirmed,

    /** It links to the work, but nobody has confirmed it. */
    Linked,

    /** The student's own claim, with nothing attached. */
    SelfReported,
}

/** Something a student did on a team, at `passports/{uid}/contributions/{id}`. */
data class Contribution(
    val id: String,
    val ownerUid: String,
    val listingId: String,
    val listingTitle: String,
    val title: String,
    val detail: String,
    val links: List<String>,
    val recordedBy: String,
    val recordedByName: String,
    val confirmedBy: String?,
    val confirmedByName: String?,
    val confirmedAt: Instant?,
    val createdAt: Instant?,
    /** Saved on this device but not yet confirmed by the server (US21). */
    val pending: Boolean = false,
) {
    val evidence: Evidence
        get() = when {
            confirmedBy != null -> Evidence.Confirmed
            links.isNotEmpty() -> Evidence.Linked
            else -> Evidence.SelfReported
        }
}

/**
 * A teammate's review, at `passports/{subject}/feedback/{listingId}_{reviewerUid}` (US18).
 * [leadership] is only given for someone who led the team.
 */
data class Feedback(
    val id: String,
    val subjectUid: String,
    val listingId: String,
    val listingTitle: String,
    val reviewerUid: String,
    val reviewerName: String,
    val contribution: Int,
    val leadership: Int?,
    val comment: String,
    val disputed: Boolean,
    val disputeReason: String,
    val hidden: Boolean,
    val moderationNote: String,
    val createdAt: Instant?,
)

/** A summary that never turns a lack of reviews into a low score (US18, NFR11). */
data class FeedbackSummary(
    val count: Int,
    val contributionAverage: Double?,
    val leadershipAverage: Double?,
    val disputed: Int,
) {
    val hasReviews: Boolean get() = count > 0

    companion object {
        fun of(feedback: List<Feedback>): FeedbackSummary {
            // Hidden reviews were removed by moderators; disputed ones still count but are flagged.
            val shown = feedback.filterNot { it.hidden }
            return FeedbackSummary(
                count = shown.size,
                contributionAverage = shown.map { it.contribution }.takeIf { it.isNotEmpty() }?.average(),
                leadershipAverage = shown.mapNotNull { it.leadership }.takeIf { it.isNotEmpty() }?.average(),
                disputed = shown.count { it.disputed },
            )
        }
    }
}
