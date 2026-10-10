package com.z.takatuf.data.model

import java.time.Instant

enum class ApplicationStatus(val key: String) {
    Submitted("submitted"),
    Shortlisted("shortlisted"),
    Withdrawn("withdrawn");

    val isActive: Boolean get() = this != Withdrawn

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
) {
    val isNew: Boolean get() = status == ApplicationStatus.Submitted && !reviewed
}
