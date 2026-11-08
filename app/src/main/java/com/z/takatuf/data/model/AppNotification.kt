package com.z.takatuf.data.model

import java.time.Instant

/** Where tapping a notification takes the student. */
enum class NotificationTarget(val key: String) {
    Listing("listing"),
    Workspace("workspace"),
    Applicants("applicants"),
    Applicant("applicant"),
    Activity("activity"),
    Teams("teams"),
    Passport("passport"),
    Reports("reports");

    /** Opening this target needs a listing id. */
    val needsListing: Boolean get() = this == Listing || this == Workspace || this == Applicants || this == Applicant

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Activity
    }
}

/** Everything a student can be notified about (US19, FR19). The key is stored. */
enum class NotificationType(val key: String) {
    ApplicationReceived("application_received"),
    OfferReceived("offer_received"),
    OfferCancelled("offer_cancelled"),
    OfferAccepted("offer_accepted"),
    OfferDeclined("offer_declined"),
    ReconfirmRequested("reconfirm_requested"),
    InterestConfirmed("interest_confirmed"),
    MemberLeft("member_left"),
    MemberRemoved("member_removed"),
    LeadRequested("lead_requested"),
    LeadAccepted("lead_accepted"),
    LeadDeclined("lead_declined"),
    MilestoneAssigned("milestone_assigned"),
    ListingChanged("listing_changed"),
    EventChanged("event_changed"),
    ProjectCompleted("project_completed"),
    FeedbackReceived("feedback_received"),
    ReportResolved("report_resolved"),
    ModerationNotice("moderation_notice"),
    RecruitmentClosed("recruitment_closed"),
    ApplicationDeclined("application_declined"),
    MilestoneCompleted("milestone_completed");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** One item in a student's inbox, at `users/{uid}/notifications/{id}`. */
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val listingId: String,
    val listingTitle: String,
    val fromUid: String,
    val fromName: String,
    val target: NotificationTarget,
    val targetId: String,
    val note: String,
    val read: Boolean,
    val createdAt: Instant?,
)
