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

/** Another student's Skill Passport. */
@Serializable
data class PersonRoute(val uid: String)

const val DeepLinkScheme = "takatuf://"
