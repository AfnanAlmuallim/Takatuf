package com.z.takatuf.data

import com.z.takatuf.data.model.ClosedReason
import com.z.takatuf.data.model.Contribution
import com.z.takatuf.data.model.Evidence
import com.z.takatuf.data.model.Feedback
import com.z.takatuf.data.model.FeedbackSummary
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.ListingStatus
import com.z.takatuf.data.model.ModerationAction
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
import com.z.takatuf.data.model.ParticipationStatus
import com.z.takatuf.data.model.Recruitment
import com.z.takatuf.data.model.ReportReason
import com.z.takatuf.data.model.ReportStatus
import com.z.takatuf.data.model.ReportTarget
import com.z.takatuf.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** US17 to US20: records, feedback summaries, completion and moderation states. */
class RecordsTest {
    private val now = Instant.parse("2026-10-20T09:00:00Z")

    private fun contribution(links: List<String> = emptyList(), confirmedBy: String? = null) = Contribution(
        id = "c", ownerUid = "nora", listingId = "p", listingTitle = "Parking", title = "Built the map", detail = "",
        links = links, recordedBy = "nora", recordedByName = "Nora", confirmedBy = confirmedBy,
        confirmedByName = confirmedBy?.let { "Sara" }, confirmedAt = null, createdAt = now,
    )

    private fun review(contribution: Int, leadership: Int? = null, disputed: Boolean = false, hidden: Boolean = false) = Feedback(
        id = "p_$contribution", subjectUid = "nora", listingId = "p", listingTitle = "Parking", reviewerUid = "r$contribution",
        reviewerName = "R", contribution = contribution, leadership = leadership, comment = "", disputed = disputed,
        disputeReason = if (disputed) "Not fair" else "", hidden = hidden, moderationNote = "", createdAt = now,
    )

    private fun listing(completedAt: Instant? = null, hidden: Boolean = false, status: ListingStatus = ListingStatus.Open) = Listing(
        id = "p", kind = ListingKind.Project, status = status, title = "Parking", description = "", ownerId = "sara",
        ownerName = "Sara", ownerMajor = "", createdAt = now, updatedAt = now,
        recruitment = Recruitment("A prototype", listOf(Role("r", "Dev", skills = listOf("Kotlin"), openings = 1)), 6, "", 1, null),
        completedAt = completedAt, hidden = hidden,
    )

    @Test
    fun `evidence-backed and unsupported claims are told apart`() {
        assertEquals(Evidence.SelfReported, contribution().evidence)
        assertEquals(Evidence.Linked, contribution(links = listOf("https://github.com/x")).evidence)
        assertEquals(Evidence.Confirmed, contribution(links = listOf("https://github.com/x"), confirmedBy = "sara").evidence)
        assertEquals(Evidence.Confirmed, contribution(confirmedBy = "sara").evidence)
    }

    @Test
    fun `participation states are distinguishable and only active is ongoing`() {
        assertFalse(ParticipationStatus.Active.ended)
        assertTrue(ParticipationStatus.Completed.ended)
        assertTrue(ParticipationStatus.Left.ended)
        assertTrue(ParticipationStatus.Removed.ended)
        assertEquals(ParticipationStatus.Active, ParticipationStatus.from("something new"))
    }

    @Test
    fun `no reviews is not a low score`() {
        val none = FeedbackSummary.of(emptyList())
        assertFalse(none.hasReviews)
        assertNull(none.contributionAverage)
        assertNull(none.leadershipAverage)
    }

    @Test
    fun `summaries leave out hidden reviews but count disputed ones`() {
        val summary = FeedbackSummary.of(listOf(review(5, 4), review(3, disputed = true), review(1, hidden = true)))
        assertEquals(2, summary.count)
        assertEquals(4.0, summary.contributionAverage!!, 1e-9)
        assertEquals(4.0, summary.leadershipAverage!!, 1e-9)
        assertEquals(1, summary.disputed)
    }

    @Test
    fun `completed and hidden listings stop taking applications, hidden first`() {
        assertNull(listing().closedReason(now))
        assertEquals(ClosedReason.Completed, listing(completedAt = now).closedReason(now))
        assertEquals(ClosedReason.Hidden, listing(completedAt = now, hidden = true).closedReason(now))
        assertEquals(ClosedReason.Completed, listing(completedAt = now, status = ListingStatus.Closed).closedReason(now))
    }

    @Test
    fun `report reasons and moderation actions fit what was reported`() {
        assertTrue(ReportReason.UnfairFeedback in ReportReason.forTarget(ReportTarget.Feedback))
        assertFalse(ReportReason.UnfairFeedback in ReportReason.forTarget(ReportTarget.Listing))
        assertTrue(ModerationAction.HideListing in ModerationAction.forTarget(ReportTarget.Listing))
        assertFalse(ModerationAction.HideListing in ModerationAction.forTarget(ReportTarget.User))
        // Every decision except "reviewing" closes the report, so each one needs a recorded reason.
        ModerationAction.entries.filter { it != ModerationAction.Reviewing }.forEach { assertTrue(it.closes!!.closed) }
        assertNull(ModerationAction.Reviewing.closes)
        assertFalse(ReportStatus.Open.closed)
    }

    @Test
    fun `notification keys match the security rules`() {
        // The rules list these exact keys; a renamed enum would make every such notice fail.
        val types = setOf(
            "application_received", "offer_received", "offer_cancelled", "offer_accepted", "offer_declined",
            "reconfirm_requested", "interest_confirmed", "member_left", "member_removed", "lead_requested",
            "lead_accepted", "lead_declined", "milestone_assigned", "listing_changed", "event_changed",
            "project_completed", "feedback_received", "report_resolved", "moderation_notice", "recruitment_closed",
            "application_declined", "milestone_completed",
        )
        assertEquals(types, NotificationType.entries.map { it.key }.toSet())
        val targets = setOf("listing", "workspace", "applicants", "applicant", "activity", "teams", "passport", "reports")
        assertEquals(targets, NotificationTarget.entries.map { it.key }.toSet())
    }
}
