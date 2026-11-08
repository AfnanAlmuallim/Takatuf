package com.z.takatuf.domain

import com.z.takatuf.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Additional independent Sprint 02 boundaries for the implementation report. */
class Sprint02BoundaryTest {
    private val now = Instant.parse("2026-10-19T13:00:00Z")
    private fun listing(openings: Int = 2) = Listing(
        id = "s02", kind = ListingKind.Project, status = ListingStatus.Open,
        title = "Study collaboration", description = "Education", ownerId = "owner",
        ownerName = "Owner", ownerMajor = "SE", createdAt = now, updatedAt = now,
        recruitment = Recruitment("Prototype", listOf(Role("dev", "Developer", skills = listOf("Kotlin"), openings = openings)), 6, "4 weeks", 1, null),
    )
    private fun application(id: String, status: ApplicationStatus = ApplicationStatus.Submitted, hours: Int = 6) =
        Application("s02", "owner", id, id, "SE", "Study collaboration", ListingKind.Project,
            "dev", "Developer", "Contribute to prototype", hours, "", status, false, now, now)
    private fun fit(app: Application, context: TeamContext) = TeamMatcher.fit(
        app, Passport(app.applicantId, skills = listOf(Skill("Kotlin", SkillLevel.Advanced))),
        Profile(app.applicantId, app.applicantName, year = 4), context,
    )

    @Test fun onlyRecruitingApplicantsEnterSuggestions() {
        val context = TeamContext(listing(9), emptyList())
        val fits = ApplicationStatus.entries.map { fit(application(it.key, it), context) }
        val result = TeamMatcher.recommend(context, fits)
        assertEquals(setOf("submitted", "shortlisted", "offered"), result.options.first().pickIds)
        assertTrue(result.options.all { option -> option.pickIds.none { it in setOf("reconfirm", "accepted", "declined", "withdrawn", "left", "removed") } })
    }

    @Test fun equalScoresAreRepeatableForTheSameInputOrder() {
        val context = TeamContext(listing(), emptyList())
        val fits = listOf("a", "b", "c").map { fit(application(it), context) }
        assertEquals(1, fits.map { it.score }.distinct().size)
        val first = TeamMatcher.recommend(context, fits).options.map { it.pickIds }
        repeat(20) { assertEquals(first, TeamMatcher.recommend(context, fits).options.map { it.pickIds }) }
    }

    @Test fun filledRolesProduceNoAdditionalPicks() {
        val context = TeamContext(listing(0), listOf("Kotlin"))
        val result = TeamMatcher.recommend(context, listOf(fit(application("a"), context)), kept = setOf("a"))
        assertTrue(result.options.isEmpty())
        assertEquals(KeptReason.NotAvailable, result.keptIssues.single().reason)
    }

    @Test fun availabilityIsCappedAndZeroCommitmentIsHandled() {
        val context = TeamContext(listing(), emptyList())
        val full = fit(application("a", hours = 6), context)
        val excess = fit(application("a", hours = 60), context)
        val none = fit(application("a", hours = 0), context)
        assertEquals(full.score, excess.score, 1e-9)
        assertEquals(0.25, full.score - none.score, 1e-9)
        val noHours = TeamContext(listing().copy(recruitment = listing().recruitment!!.copy(hoursPerWeek = 0)), emptyList())
        assertTrue(fit(application("a", hours = 0), noHours).score.isFinite())
    }

    @Test fun offersExpireAtTheExactBoundaryAndMissingExpiryIsNotLive() {
        val offered = application("a", ApplicationStatus.Offered).copy(offerExpiresAt = now)
        assertTrue(offered.hasLiveOffer(now.minusNanos(1)))
        assertFalse(offered.hasLiveOffer(now))
        assertTrue(offered.offerExpired(now.plusNanos(1)))
        assertFalse(offered.copy(offerExpiresAt = null).hasLiveOffer(now))
        assertFalse(offered.copy(status = ApplicationStatus.Declined).hasLiveOffer(now.minusSeconds(1)))
    }

    @Test fun completedMilestonesStayDistinctAndAreNeverOverdue() {
        val pending = Milestone("m", "Prototype", "a", "Applicant", now, MilestoneStatus.Todo, "owner", null, now)
        assertFalse(pending.isDone)
        assertFalse(pending.isOverdue(now.minusNanos(1)))
        assertTrue(pending.isOverdue(now.plusNanos(1)))
        val done = pending.copy(status = MilestoneStatus.Done, completedAt = now)
        assertTrue(done.isDone)
        assertFalse(done.isOverdue(now.plusSeconds(86400)))
    }

    @Test fun workspaceLinksAcceptWebUrlsAndRejectInvalidSchemes() {
        assertEquals("https://example.org/evidence", Validators.normalizeUrl(" example.org/evidence "))
        assertEquals("https://example.org/evidence", Validators.normalizeUrl("https://example.org/evidence"))
        for (invalid in listOf("", "not-a-url", "https://example .org", "javascript:alert(1)", "ftp://example.org/file")) {
            assertNull(invalid, Validators.normalizeUrl(invalid))
        }
    }
}
