package com.z.takatuf.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Key membership, recommendation and moderation actions, written in the same batch or
 * transaction as the action itself (US22, NFR14). Entries are append-only and only
 * administrators can read them; the rules forbid writing one in someone else's name.
 */
object AuditLog {
    const val COLLECTION = "auditLog"

    enum class Action(val key: String) {
        ListingCreated("listing_created"),
        OfferSent("offer_sent"),
        OfferCancelled("offer_cancelled"),
        OfferAccepted("offer_accepted"),
        OfferDeclined("offer_declined"),
        MemberLeft("member_left"),
        MemberRemoved("member_removed"),
        LeadNominated("lead_nominated"),
        LeadAccepted("lead_accepted"),
        LeadDeclined("lead_declined"),
        PlaceReopened("place_reopened"),
        ProjectCompleted("project_completed"),
        ContributionConfirmed("contribution_confirmed"),
        FeedbackDisputed("feedback_disputed"),
        ReportCreated("report_created"),
        Moderation("moderation"),
    }

    fun ref(db: FirebaseFirestore): DocumentReference = db.collection(COLLECTION).document()

    fun entry(action: Action, actorUid: String, listingId: String = "", subjectUid: String = "", detail: String = "") = mapOf(
        "action" to action.key,
        "actorUid" to actorUid,
        "listingId" to listingId,
        "subjectUid" to subjectUid,
        "detail" to detail.take(300),
        "at" to FieldValue.serverTimestamp(),
    )
}
