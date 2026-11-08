package com.z.takatuf.data

import com.z.takatuf.data.model.AppNotification
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Contribution
import com.z.takatuf.data.model.Evidence
import com.z.takatuf.data.model.Feedback
import com.z.takatuf.data.model.FeedbackSummary
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.ListingStatus
import com.z.takatuf.data.model.Milestone
import com.z.takatuf.data.model.MilestoneStatus
import com.z.takatuf.data.model.ModerationAction
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
import com.z.takatuf.data.model.ParticipationStatus
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.ReportReason
import com.z.takatuf.data.model.ReportStatus
import com.z.takatuf.data.model.ReportTarget
import com.z.takatuf.data.model.TeamMember
import com.z.takatuf.domain.Validators
import com.z.takatuf.notify.SystemNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Sprint 03 boundaries and state rules: the limits and fallbacks behind US17 to US21. */
class Sprint03BoundaryTest {
    private val now = Instant.parse("2026-11-05T09:00:00Z")

    private fun milestone(due: Instant?, status: MilestoneStatus = MilestoneStatus.Todo) = Milestone(
        id = "m", title = "Prototype", assigneeId = "a", assigneeName = "A", dueDate = due, status = status,
        createdBy = "a", completedAt = null, updatedAt = now,
    )

    private fun review(contribution: Int, leadership: Int? = null, disputed: Boolean = false, hidden: Boolean = false) = Feedback(
        id = "p_$contribution", subjectUid = "s", listingId = "p", listingTitle = "P", reviewerUid = "r$contribution", reviewerName = "R",
        contribution = contribution, leadership = leadership, comment = "", disputed = disputed, disputeReason = "", hidden = hidden,
        moderationNote = "", createdAt = now,
    )

    private fun contribution(links: List<String>, confirmedBy: String?) = Contribution(
        id = "c", ownerUid = "s", listingId = "p", listingTitle = "P", title = "Built it", detail = "", links = links, recordedBy = "s",
        recordedByName = "S", confirmedBy = confirmedBy, confirmedByName = confirmedBy, confirmedAt = null, createdAt = now,
    )

    private fun offer(expires: Instant) = Application(
        listingId = "p", ownerId = "o", applicantId = "a", applicantName = "A", applicantMajor = "", listingTitle = "P",
        listingKind = ListingKind.Project, roleId = "r", roleTitle = "R", contribution = "", hoursPerWeek = 6, availabilityNote = "",
        status = ApplicationStatus.Offered, reviewed = true, createdAt = now, updatedAt = now, offerExpiresAt = expires,
    )

    @Test
    fun `a milestone is on time before its due instant and overdue from that instant`() {
        val due = now
        assertFalse(milestone(due).isOverdue(now.minusMillis(1)))
        assertTrue(milestone(due).isOverdue(now))
        assertTrue(milestone(due).isOverdue(now.plusMillis(1)))
    }

    @Test
    fun `a finished or undated milestone is never overdue`() {
        assertFalse(milestone(now.minusSeconds(60), MilestoneStatus.Done).isOverdue(now))
        assertFalse(milestone(null).isOverdue(now))
    }

    @Test
    fun `removing a confirmation drops a claim back to what backs it`() {
        assertEquals(Evidence.Confirmed, contribution(listOf("https://github.com/x"), "lead").evidence)
        assertEquals(Evidence.Linked, contribution(listOf("https://github.com/x"), null).evidence)
        assertEquals(Evidence.SelfReported, contribution(emptyList(), null).evidence)
    }

    @Test
    fun `reviews hidden by moderators leave no score behind`() {
        val summary = FeedbackSummary.of(listOf(review(1, hidden = true), review(2, hidden = true)))
        assertFalse(summary.hasReviews)
        assertNull(summary.contributionAverage)
    }

    @Test
    fun `no leadership reviews means no leadership score, not zero`() {
        val summary = FeedbackSummary.of(listOf(review(4), review(5)))
        assertEquals(4.5, summary.contributionAverage!!, 1e-9)
        assertNull(summary.leadershipAverage)
    }

    @Test
    fun `the lowest and highest ratings average correctly`() {
        val summary = FeedbackSummary.of(listOf(review(1, 1), review(5, 5)))
        assertEquals(3.0, summary.contributionAverage!!, 1e-9)
        assertEquals(3.0, summary.leadershipAverage!!, 1e-9)
    }

    @Test
    fun `participation keys round trip so stored states stay distinct`() {
        ParticipationStatus.entries.forEach { assertEquals(it, ParticipationStatus.from(it.key)) }
        assertEquals(4, ParticipationStatus.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `unknown stored values fall back to the safest state`() {
        assertEquals(ReportStatus.Open, ReportStatus.from("archived"))
        assertEquals(ReportTarget.Listing, ReportTarget.from(null))
        assertEquals(ReportReason.Other, ReportReason.from("new_reason"))
        assertEquals(NotificationTarget.Activity, NotificationTarget.from("somewhere"))
        assertNull(NotificationType.from("unknown_type"))
    }

    @Test
    fun `each report kind offers only the decisions that apply to it`() {
        assertEquals(
            setOf(ModerationAction.FlagListing, ModerationAction.HideListing, ModerationAction.UnhideListing, ModerationAction.Resolve, ModerationAction.Dismiss),
            ModerationAction.forTarget(ReportTarget.Listing).toSet(),
        )
        assertEquals(setOf(ModerationAction.WarnUser, ModerationAction.Resolve, ModerationAction.Dismiss), ModerationAction.forTarget(ReportTarget.User).toSet())
        assertEquals(setOf(ModerationAction.HideFeedback, ModerationAction.RestoreFeedback, ModerationAction.Dismiss), ModerationAction.forTarget(ReportTarget.Feedback).toSet())
    }

    @Test
    fun `dismissing closes a report as dismissed and every other decision as resolved`() {
        assertEquals(ReportStatus.Dismissed, ModerationAction.Dismiss.closes)
        ModerationAction.entries.filter { it != ModerationAction.Dismiss && it != ModerationAction.Reviewing }
            .forEach { assertEquals(ReportStatus.Resolved, it.closes) }
    }

    @Test
    fun `a supporting link of exactly 300 characters is accepted and 301 is refused`() {
        val base = "https://github.com/"
        assertNotNull(Validators.webLink(base + "a".repeat(300 - base.length)))
        assertNull(Validators.webLink(base + "a".repeat(301 - base.length)))
    }

    @Test
    fun `supporting links are normalised, and other schemes and bare hosts are refused`() {
        assertEquals("https://github.com/x", Validators.webLink("github.com/x"))
        assertEquals("https://github.com/x", Validators.webLink("HTTPS://github.com/x"))
        assertNull(Validators.webLink("ftp://example.org/x"))
        assertNull(Validators.webLink("https://localhost/x"))
        assertNull(Validators.webLink("not a url"))
        assertNull(Validators.webLink(""))
    }

    @Test
    fun `only applications still in recruitment count as active`() {
        val active = setOf(ApplicationStatus.Submitted, ApplicationStatus.Shortlisted, ApplicationStatus.Offered, ApplicationStatus.Reconfirm)
        ApplicationStatus.entries.forEach { assertEquals(it.name, it in active, it.isActive) }
    }

    @Test
    fun `an offer can be answered before its expiry instant and not from that instant`() {
        assertFalse(offer(now).offerExpired(now.minusMillis(1)))
        assertTrue(offer(now).offerExpired(now))
        assertTrue(offer(now).offerExpired(now.plusMillis(1)))
    }

    @Test
    fun `an action-log entry never exceeds what the rules accept`() {
        val entry = AuditLog.entry(AuditLog.Action.Moderation, "admin", "p", "s", "x".repeat(500))
        assertEquals(300, (entry["detail"] as String).length)
        assertEquals("moderation", entry["action"])
    }

    @Test
    fun `a report is trimmed to the rules' limits and starts open`() {
        val fields = ModerationRepository.reportFields(
            reporter = Profile("r", "R".repeat(80)), target = ReportTarget.Listing, targetKey = "k".repeat(250), targetPath = "p".repeat(350),
            targetTitle = "", targetOwnerUid = "o", listingId = "p", reason = ReportReason.Spam, details = " " + "d".repeat(600),
        )
        assertEquals(60, (fields["reporterName"] as String).length)
        assertEquals(200, (fields["targetKey"] as String).length)
        assertEquals(300, (fields["targetPath"] as String).length)
        assertEquals("Untitled", fields["targetTitle"])
        assertEquals(500, (fields["details"] as String).length)
        assertEquals("open", fields["status"])
        assertEquals("", fields["resolution"])
    }

    private fun notice(target: NotificationTarget, listingId: String) = AppNotification(
        id = "n", type = NotificationType.ModerationNotice, listingId = listingId, listingTitle = "Fajer", fromUid = "a", fromName = "Takatuf moderators",
        target = target, targetId = "", note = "", read = false, createdAt = now,
    )

    @Test
    fun `D04 a notice with no listing never links to an empty listing`() {
        listOf(NotificationTarget.Listing, NotificationTarget.Workspace, NotificationTarget.Applicants, NotificationTarget.Applicant).forEach {
            assertTrue(it.needsListing)
            assertEquals("takatuf://passport", SystemNotifications.linkFor(notice(it, "")))
        }
    }

    @Test
    fun `D04 a notice with a listing still links to that listing`() {
        assertEquals("takatuf://listing/p", SystemNotifications.linkFor(notice(NotificationTarget.Listing, "p")))
        assertEquals("takatuf://team/p", SystemNotifications.linkFor(notice(NotificationTarget.Workspace, "p")))
        assertEquals("takatuf://passport", SystemNotifications.linkFor(notice(NotificationTarget.Passport, "")))
        assertFalse(NotificationTarget.Passport.needsListing)
    }

    @Test
    fun `a participation record keeps within the rules' limits`() {
        val listing = Listing(
            id = "p", kind = ListingKind.Project, status = ListingStatus.Open, title = "T".repeat(120), description = "", ownerId = "o",
            ownerName = "O", ownerMajor = "", createdAt = now, updatedAt = now,
        )
        val record = RecordsRepository.record(listing, " ", wasLead = false, ParticipationStatus.Left, now, endedAt = null)
        assertEquals(90, (record["listingTitle"] as String).length)
        assertEquals(RecordsRepository.MEMBER_ROLE, record["role"])
        assertEquals("left", record["status"])
        assertEquals("project", record["kind"])
    }

    @Test
    fun `someone who left stays listed as former until they rejoin`() {
        val fajer = TeamMember("f", "Fajer", "SE", "r1", "Tester", now)
        val nora = TeamMember("n", "Nora", "SE", "r1", "Designer", now)
        val listing = Listing(
            id = "p", kind = ListingKind.Project, status = ListingStatus.Open, title = "Title", description = "", ownerId = "o",
            ownerName = "O", ownerMajor = "", createdAt = now, updatedAt = now, members = listOf(nora), formerMembers = listOf(fajer),
        )
        assertEquals(listOf("f"), listing.departed.map { it.uid })
        assertEquals(emptyList<TeamMember>(), listing.copy(members = listOf(nora, fajer)).departed)
    }
}
