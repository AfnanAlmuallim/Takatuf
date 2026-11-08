package com.z.takatuf.domain

import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.EligibleLevel
import com.z.takatuf.data.model.EventType
import com.z.takatuf.data.model.Experience
import com.z.takatuf.data.model.LinkedEvent
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.ListingStatus
import com.z.takatuf.data.model.Passport
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.Recruitment
import com.z.takatuf.data.model.Role
import com.z.takatuf.data.model.Skill
import com.z.takatuf.data.model.SkillLevel
import com.z.takatuf.data.model.TeamMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** US09 to US11: applicant fit and complementary team suggestions (charter, Technical Environment). */
class TeamMatcherTest {
    private val now = Instant.parse("2026-10-11T09:00:00Z")
    private val dev = Role("dev", "Mobile developer", skills = listOf("Kotlin", "Firebase", "SQL", "Figma"), openings = 2)
    private val designer = Role("des", "Designer", skills = listOf("Prototyping"), openings = 1)

    private fun listing(
        roles: List<Role> = listOf(dev),
        members: List<TeamMember> = emptyList(),
        linkedEvent: LinkedEvent? = null,
    ) = Listing(
        id = "p", kind = if (linkedEvent != null) ListingKind.Team else ListingKind.Project, status = ListingStatus.Open,
        title = "Campus parking finder", description = "A live map of free bays", ownerId = "lead", ownerName = "Sara",
        ownerMajor = "", createdAt = now, updatedAt = now,
        recruitment = Recruitment(
            outcome = "A tested prototype", roles = roles, hoursPerWeek = 6, duration = "",
            teamSize = 1 + members.size, deadline = null,
        ),
        linkedEvent = linkedEvent,
        members = members,
    )

    private fun app(uid: String, roleId: String = "dev", hours: Int = 6, status: ApplicationStatus = ApplicationStatus.Submitted) = Application(
        listingId = "p", ownerId = "lead", applicantId = uid, applicantName = uid.replaceFirstChar { it.uppercase() },
        applicantMajor = "", listingTitle = "Campus parking finder", listingKind = ListingKind.Project, roleId = roleId,
        roleTitle = if (roleId == "dev") "Mobile developer" else "Designer", contribution = "", hoursPerWeek = hours,
        availabilityNote = "", status = status, reviewed = false, createdAt = now, updatedAt = now,
    )

    private fun passport(uid: String, vararg skills: Pair<String, SkillLevel>) =
        Passport(uid, skills = skills.map { Skill(it.first, it.second) })

    private fun profile(uid: String, year: Int? = 3) = Profile(uid, uid.replaceFirstChar { it.uppercase() }, year = year)

    private val adv = SkillLevel.Advanced
    private val mid = SkillLevel.Intermediate

    /** Amal and Badr both bring Kotlin and Firebase; Chen brings the SQL and Figma they lack. */
    private fun fits(context: TeamContext, vararg extra: Application): List<ApplicantFit> {
        val passports = mapOf(
            "amal" to passport("amal", "Kotlin" to adv, "Firebase" to adv),
            "badr" to passport("badr", "Kotlin" to adv, "Firebase" to adv),
            "chen" to passport("chen", "SQL" to mid, "Figma" to mid),
            "dana" to passport("dana", "Kotlin" to mid),
        )
        val apps = listOf(app("amal"), app("badr"), app("chen")) + extra
        return apps.map { TeamMatcher.fit(it, passports[it.applicantId], profile(it.applicantId), context) }
    }

    @Test
    fun `fit shows covered skills, hours and the factors behind the score`() {
        val strong = Passport(
            "amal",
            skills = listOf("Kotlin", "Firebase", "SQL", "Figma").map { Skill(it, adv) },
            interests = listOf("Smart parking"),
            experience = listOf(Experience("x", "Android developer", "PSU Clubs app")),
        )
        val context = TeamContext(listing(), emptyList())
        val full = TeamMatcher.fit(app("amal"), strong, profile("amal"), context)
        assertEquals(listOf("Kotlin", "Firebase", "SQL", "Figma"), full.skillsCovered)
        assertTrue(full.skillsMissing.isEmpty())
        assertEquals(1, full.relevantExperience)
        assertEquals(listOf("Smart parking"), full.sharedInterests)
        assertEquals(0.925, full.score, 1e-9)
        assertEquals(FitBand.Strong, full.band)

        // Half the hours the team asks for costs a quarter of the availability weight.
        val half = TeamMatcher.fit(app("amal", hours = 3), strong, profile("amal"), context)
        assertEquals(3, half.hoursOffered)
        assertEquals(6, half.hoursAsked)
        assertEquals(full.score - 0.125, half.score, 1e-9)
    }

    @Test
    fun `a hidden passport counts as average, not poor (NFR11)`() {
        val context = TeamContext(listing(), emptyList())
        val hidden = TeamMatcher.fit(app("amal"), null, profile("amal"), context)
        val weak = TeamMatcher.fit(app("dana"), passport("dana", "Swift" to mid), profile("dana"), context)
        assertTrue(hidden.limitedInfo)
        assertEquals(0.625, hidden.score, 1e-9)
        assertEquals(FitBand.Good, hidden.band)
        assertTrue(hidden.score > weak.score)
        assertEquals(FitBand.Partial, weak.band)
    }

    @Test
    fun `eligibility follows the event level and the recorded year`() {
        val event = LinkedEvent("e", "Najd Smart City Hackathon", "Riyadh Tech Society", EventType.Hackathon, now, now, 2, 5, EligibleLevel.Undergraduate)
        val context = TeamContext(listing(linkedEvent = event), emptyList())
        val p = passport("amal", "Kotlin" to adv)
        assertEquals(Eligibility.Eligible, TeamMatcher.fit(app("amal"), p, profile("amal", year = 3), context).eligibility)
        assertEquals(Eligibility.Ineligible, TeamMatcher.fit(app("amal"), p, profile("amal", year = 7), context).eligibility)
        assertEquals(Eligibility.Unknown, TeamMatcher.fit(app("amal"), p, profile("amal", year = null), context).eligibility)
    }

    @Test
    fun `what someone adds leaves out skills the team already has`() {
        val context = TeamContext(listing(), teamSkills = listOf("kotlin"))
        val fit = TeamMatcher.fit(app("amal"), passport("amal", "Kotlin" to adv, "SQL" to mid), profile("amal"), context)
        assertEquals(listOf("SQL"), fit.bringsToTeam)
    }

    @Test
    fun `the coverage option prefers complementary people over duplicates (US10)`() {
        val context = TeamContext(listing(), emptyList())
        val result = TeamMatcher.recommend(context, fits(context))
        val coverage = result.options.first()
        assertEquals(Strategy.Coverage, coverage.strategy)
        assertEquals(setOf("amal", "chen"), coverage.pickIds)
        assertTrue(coverage.missing.isEmpty())
        assertTrue(coverage.isComplete)

        // US11: an alternative to compare, showing a different gap.
        val strongest = result.options.first { it.strategy == Strategy.Strongest }
        assertEquals(setOf("amal", "badr"), strongest.pickIds)
        assertEquals(listOf("SQL", "Figma"), strongest.missing)
        assertTrue(result.options.size >= 2)
        assertEquals(result.options.size, result.options.map { it.pickIds }.toSet().size)
    }

    @Test
    fun `kept people stay in every option, and impossible keeps are explained (US11)`() {
        val context = TeamContext(listing(), emptyList())
        val kept = TeamMatcher.recommend(context, fits(context), kept = setOf("badr"))
        assertTrue(kept.options.isNotEmpty())
        assertTrue(kept.options.all { "badr" in it.pickIds })
        assertTrue(kept.keptIssues.isEmpty())

        val overbooked = TeamMatcher.recommend(context, fits(context), kept = linkedSetOf("amal", "badr", "chen"))
        assertEquals(listOf(KeptIssue("chen", "Chen", "Mobile developer", KeptReason.RoleOverbooked)), overbooked.keptIssues)

        val withdrawn = fits(context, app("dana", status = ApplicationStatus.Withdrawn))
        val gone = TeamMatcher.recommend(context, withdrawn, kept = setOf("dana"))
        assertEquals(KeptReason.NotAvailable, gone.keptIssues.single().reason)
        assertTrue(gone.options.none { "dana" in it.pickIds })
    }

    @Test
    fun `show other options passes over earlier picks when someone comparable exists`() {
        val context = TeamContext(listing(), emptyList())
        val result = TeamMatcher.recommend(context, fits(context), avoid = setOf("amal"))
        assertEquals(setOf("badr", "chen"), result.options.first().pickIds)
    }

    @Test
    fun `members, ineligible applicants and filled roles shape the suggestion (US10, US15)`() {
        val members = listOf(TeamMember("badr", "Badr", "", "dev", "Mobile developer", now))
        val event = LinkedEvent("e", "Hackathon", "Org", EventType.Hackathon, now, now, 2, 5, EligibleLevel.Undergraduate)
        val team = listing(roles = listOf(dev, designer), members = members, linkedEvent = event)
        val context = TeamContext(team, teamSkills = listOf("Kotlin", "Firebase"))
        val all = fits(context).map {
            if (it.applicantId == "chen") TeamMatcher.fit(it.application, passport("chen", "SQL" to mid, "Figma" to mid), profile("chen", year = 7), context) else it
        }
        val result = TeamMatcher.recommend(context, all)

        // Badr is already on the team, Chen's year doesn't match the event, nobody applied as designer.
        assertTrue(result.options.none { "badr" in it.pickIds || "chen" in it.pickIds })
        assertEquals(listOf("chen"), result.ineligible.map { it.applicantId })
        assertEquals(listOf("des"), result.emptyRoles)
        assertEquals(listOf("Kotlin", "Firebase"), result.coveredByTeam)
        val option = result.options.first()
        assertFalse(option.isComplete)
        assertEquals(1, option.unfilled["des"])
        assertEquals(listOf("SQL", "Figma", "Prototyping"), option.missing)
    }

    @Test
    fun `with no applicants there is nothing to suggest, and the empty role is named`() {
        val context = TeamContext(listing(), emptyList())
        val result = TeamMatcher.recommend(context, emptyList())
        assertTrue(result.options.isEmpty())
        assertEquals(listOf("dev"), result.emptyRoles)
    }
}
