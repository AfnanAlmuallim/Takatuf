package com.z.takatuf.domain

import com.z.takatuf.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

/** Controlled correctness comparisons, not evidence of real-student usefulness. */
class Sprint02MatchingEvaluationTest {
    private val now = Instant.parse("2026-10-19T13:00:00Z")
    private val skills = listOf("Compose", "Authentication", "PostgreSQL", "UX research")
    data class Candidate(val id: String, val skills: List<String>, val hours: Int = 6, val year: Int = 4, val level: SkillLevel = SkillLevel.Advanced)
    data class Scenario(val id: String, val team: List<String>, val places: Int, val candidates: List<Candidate>, val undergraduate: Boolean = false)
    private fun combinations(xs: List<ApplicantFit>, n: Int): List<List<ApplicantFit>> = when {
        n == 0 -> listOf(emptyList())
        xs.size < n -> emptyList()
        else -> xs.indices.flatMap { i -> combinations(xs.drop(i + 1), n - 1).map { listOf(xs[i]) + it } }
    }

    @Test fun compareKnownCoverageAgainstIndependentSelectionBaselines() {
        val scenarios = listOf(
            Scenario("E1 overlapping high fits", listOf("Compose"), 2, listOf(
                Candidate("alpha", listOf("Compose", "Authentication")),
                Candidate("beta", listOf("Compose", "Authentication")),
                Candidate("gamma", listOf("PostgreSQL", "UX research"), level = SkillLevel.Intermediate))),
            Scenario("E2 existing members", listOf("Authentication", "PostgreSQL"), 1, listOf(
                Candidate("alpha", listOf("Authentication", "PostgreSQL")),
                Candidate("beta", listOf("Compose", "UX research"), level = SkillLevel.Intermediate),
                Candidate("gamma", listOf("Authentication"), hours = 3))),
            Scenario("E3 insufficient applicants", listOf("Compose"), 3, listOf(
                Candidate("alpha", listOf("Authentication")),
                Candidate("beta", listOf("PostgreSQL"), hours = 3))),
            Scenario("E4 eligibility and availability", emptyList(), 1, listOf(
                Candidate("alpha", skills, year = 6),
                Candidate("beta", skills, hours = 3, level = SkillLevel.Intermediate),
                Candidate("gamma", listOf("Compose", "Authentication"))), undergraduate = true),
        )
        val rows = scenarios.map { s ->
            val linked = if (s.undergraduate) LinkedEvent("event", "Student challenge", "Synthetic organizer", EventType.Hackathon, now, now.plusSeconds(86400), 2, 5, EligibleLevel.Undergraduate) else null
            val listing = Listing("eval", if (linked == null) ListingKind.Project else ListingKind.Team, ListingStatus.Open,
                "Education collaboration", "Controlled scenario", "owner", "Owner", "SE", now, now,
                Recruitment("Prototype", listOf(Role("dev", "Developer", skills = skills, openings = s.places)), 6, "4 weeks", 1, null), linkedEvent = linked)
            val context = TeamContext(listing, s.team)
            val fits = s.candidates.map { c ->
                val application = Application("eval", "owner", c.id, c.id, "SE", listing.title, listing.kind, "dev", "Developer",
                    "Controlled contribution", c.hours, "", ApplicationStatus.Submitted, false, now, now)
                TeamMatcher.fit(application, Passport(c.id, skills = c.skills.map { Skill(it, c.level) }), Profile(c.id, c.id, year = c.year), context)
            }
            val eligible = fits.filter { it.eligibility != Eligibility.Ineligible }
            fun coverage(picks: List<ApplicantFit>): Double {
                val covered = s.team.map { it.lowercase() }.toSet() + picks.flatMap { it.neededHave }
                return skills.count { it.lowercase() in covered }.toDouble() / skills.size
            }
            val selected = TeamMatcher.recommend(context, fits).options.first()
            val individual = eligible.sortedByDescending { it.score }.take(s.places)
            val randomExpected = combinations(eligible, minOf(s.places, eligible.size)).map(::coverage).average()
            val gapPicks = mutableListOf<ApplicantFit>()
            repeat(minOf(s.places, eligible.size)) {
                val covered = s.team.map { it.lowercase() }.toSet() + gapPicks.flatMap { it.neededHave }
                val next = eligible.filter { it !in gapPicks }.maxBy { c -> c.neededHave.count { it !in covered } }
                gapPicks += next
            }
            assertTrue(selected.picks.size <= s.places)
            assertTrue(selected.picks.all { it.eligibility != Eligibility.Ineligible })
            assertEquals(coverage(selected.picks), selected.coverage, 1e-9)
            if (s.id.startsWith("E1") || s.id.startsWith("E2") || s.id.startsWith("E4")) assertEquals(1.0, selected.coverage, 1e-9)
            if (s.id.startsWith("E3")) { assertFalse(selected.isComplete); assertEquals(0.75, selected.coverage, 1e-9) }
            """{"scenario":"${s.id}","places":${s.places},"eligibleApplicants":${eligible.size},"selected":["${selected.picks.joinToString("\",\"") { it.applicantId }}"],"matchingCoverage":${selected.coverage},"individualFitCoverage":${coverage(individual)},"randomExpectedCoverage":$randomExpected,"simpleGapCoverage":${coverage(gapPicks)},"unfilled":${selected.emptyPlaces}}"""
        }
        val output = """{"evaluationType":"Controlled synthetic algorithm correctness","studentParticipants":0,"scenarios":[${rows.joinToString(",")}]}"""
        File("build/evaluation").apply { mkdirs() }.resolve("sprint02-matching-evaluation.json").writeText(output)
    }
}
