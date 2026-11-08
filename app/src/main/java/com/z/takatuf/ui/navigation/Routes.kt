package com.z.takatuf.ui.navigation

import com.z.takatuf.data.model.ListingKind
import kotlinx.serialization.Serializable

@Serializable
data object DiscoverRoute

@Serializable
data object ActivityRoute

@Serializable
data object PassportRoute

@Serializable
data class ListingRoute(val id: String)

/** Creates a listing of [kind], or edits [listingId]. [eventId] pre-links a team request. */
@Serializable
data class EditorRoute(
    val kind: String,
    val listingId: String? = null,
    val eventId: String? = null,
) {
    val listingKind: ListingKind get() = ListingKind.from(kind)
}

@Serializable
data class ApplicantsRoute(val listingId: String)

@Serializable
data class ApplicantRoute(val listingId: String, val applicantId: String)

/** Suggested member combinations for a recruiting listing (US10, US11). */
@Serializable
data class TeamBuilderRoute(val listingId: String)

@Serializable
data object TeamsRoute

/** A team's private space: plan, updates and people (US13 to US16). */
@Serializable
data class WorkspaceRoute(val listingId: String)

/** Another student's Skill Passport. */
@Serializable
data class PersonRoute(val uid: String)

/** Reviewing teammates after a project ends or someone leaves (US18). */
@Serializable
data class FeedbackRoute(val listingId: String)

/** The student's notifications (US19). */
@Serializable
data object NotificationsRoute

/** The administrators' report queue (US20). */
@Serializable
data object ModerationRoute

/** One report, for an administrator to act on. */
@Serializable
data class ReportRoute(val id: String)

const val DeepLinkScheme = "takatuf://"
