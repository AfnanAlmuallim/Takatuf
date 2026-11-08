package com.z.takatuf.data.model

import java.time.Instant

enum class ApplicationStatus(val key: String) {
    Submitted("submitted"),
    Shortlisted("shortlisted"),

    /** The owner offered a place; the applicant has until `offerExpiresAt` to answer. */
    Offered("offered"),

    /** A place reopened; the applicant must confirm they're still interested. */
    Reconfirm("reconfirm"),

    /** The applicant accepted an offer and is a member of the team. */
    Accepted("accepted"),
    Declined("declined"),
    Withdrawn("withdrawn"),

    /** A member who later left the team. */
    Left("left"),

    /** A member the lead removed from the team. */
    Removed("removed");

    /** Still part of recruitment: the owner can act on it and the applicant can withdraw. */
    val isActive: Boolean get() = this == Submitted || this == Shortlisted || this == Offered || this == Reconfirm

    /** The student may send a fresh application to the same listing. */
    val canReapply: Boolean get() = this == Withdrawn || this == Declined || this == Left

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Submitted
    }
}

/**
 * Stored at `listings/{listingId}/applications/{applicantId}`. Using the applicant's uid as
 * the document id makes a second active application to the same listing impossible.
 */
data class Application(
    val listingId: String,
    val ownerId: String,
    val applicantId: String,
    val applicantName: String,
    val applicantMajor: String,
    val listingTitle: String,
    val listingKind: ListingKind,
    val roleId: String,
    val roleTitle: String,
    val contribution: String,
    val hoursPerWeek: Int,
    val availabilityNote: String,
    val status: ApplicationStatus,
    /** Set once the owner has opened the application. */
    val reviewed: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val offeredAt: Instant? = null,
    val offerExpiresAt: Instant? = null,
    val respondedAt: Instant? = null,
    val reconfirmRequestedAt: Instant? = null,
) {
    val isNew: Boolean get() = status == ApplicationStatus.Submitted && !reviewed

    /** When anything last happened to it, including offers and reconfirmation requests. */
    val lastActivityAt: Instant?
        get() = listOfNotNull(updatedAt, offeredAt, reconfirmRequestedAt, respondedAt).maxOrNull()

    /** An offer that has passed its deadline can no longer be accepted. */
    fun offerExpired(now: Instant): Boolean =
        status == ApplicationStatus.Offered && (offerExpiresAt == null || !offerExpiresAt.isAfter(now))

    /** An offer the applicant can still accept. */
    fun hasLiveOffer(now: Instant): Boolean = status == ApplicationStatus.Offered && !offerExpired(now)
}
