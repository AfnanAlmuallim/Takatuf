package com.z.takatuf.domain

import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.EligibleLevel
import com.z.takatuf.data.model.EventType
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.random.Random

/**
 * The recommendation evaluation from the charter's KPIs (Sprint 3):
 *
 * - Validity: every suggested team uses eligible, active applicants of that request, respects
 *   each role's places and keeps the people the owner pinned; teams that can't be completed
 *   say why.
 * - Usefulness: average requirement coverage and unmet roles compared with random eligible
 *   selection, individual-fit ranking and a simple gap-filling method. The target is to match
 *   or beat individual-fit ranking on average coverage.
 * - Performance: how long a recommendation takes (NFR09).
 *
 * Cases are synthetic and generated from a fixed seed, so the numbers are reproducible. The
 * report is written to app/build/evaluation/recommendation-evaluation.md.
 */
class RecommendationEvaluationTest {

    private val now = Instant.parse("2026-10-20T09:00:00Z")
    private val vocabulary = listOf(
        "Kotlin", "Jetpack Compose", "Firebase", "SQL", "Python", "FastAPI", "Figma", "Prototyping", "UX research",
        "Data analysis", "Data visualization", "NLP", "Arduino", "IoT", "Circuit design", "Threat modeling", "Linux",
        "Network security", "Pitching", "Market research", "Copywriting", "Project management", "Technical writing",
        "React", "TypeScript",
    )
    private val active = setOf(ApplicationStatus.Submitted, ApplicationStatus.Shortlisted, ApplicationStatus.Offered)

    enum class Family(val label: String) {
        Balanced("Balanced pool"),
        Overlapping("Overlapping strong applicants"),
        Sparse("Too few applicants"),
        Eligibility("Event with eligibility rules"),
        Pinned("Owner pinned someone"),
        Availability("Availability conflicts"),
    }

    private data class Case(
        val family: Family,
        val context: TeamContext,
        val fits: List<ApplicantFit>,
        val kept: Set<String>,
    )

    private enum class Method(val label: String) {
        Takatuf("Takatuf (first suggestion)"),
        Random("Random eligible selection"),
        IndividualFit("Individual-fit ranking"),
        GapFilling("Simple gap-filling"),
    }

    private data class Outcome(val coverage: Double, val emptyRoles: Int, val emptyPlaces: Int, val complete: Boolean, val averageFit: Double)

    // ---------- case generation ----------

    private fun roles(rnd: Random): List<Role> {
        val pool = vocabulary.shuffled(rnd).toMutableList()
        return (1..rnd.nextInt(1, 4)).map { i ->
            val skills = (1..rnd.nextInt(2, 5)).map { pool.removeAt(0) }
            Role("r$i", "Role $i", skills = skills, openings = rnd.nextInt(1, 3))
        }
    }

    private fun listing(roles: List<Role>, eligibility: EligibleLevel, index: Int) = Listing(
        id = "case$index", kind = if (eligibility == EligibleLevel.Any) ListingKind.Project else ListingKind.Team,
        status = ListingStatus.Open, title = "Evaluation case $index", description = "", ownerId = "lead", ownerName = "Lead",
        ownerMajor = "", createdAt = now, updatedAt = now,
        recruitment = Recruitment(outcome = "", roles = roles, hoursPerWeek = 6, duration = "", teamSize = 1, deadline = null),
        linkedEvent = if (eligibility == EligibleLevel.Any) null else LinkedEvent(
            "event", "Hackathon", "Organizer", EventType.Hackathon, now, now.plusSeconds(86_400), 2, 6, eligibility,
        ),
    )

    private fun application(listing: Listing, uid: String, roleId: String, hours: Int, status: ApplicationStatus) = Application(
        listingId = listing.id, ownerId = "lead", applicantId = uid, applicantName = uid, applicantMajor = "",
        listingTitle = listing.title, listingKind = listing.kind, roleId = roleId, roleTitle = roleId, contribution = "",
        hoursPerWeek = hours, availabilityNote = "", status = status, reviewed = false, createdAt = now, updatedAt = now,
    )

    private fun level(rnd: Random) = SkillLevel.entries[rnd.nextInt(SkillLevel.entries.size)]

    private fun status(rnd: Random): ApplicationStatus = when (rnd.nextInt(100)) {
        in 0..49 -> ApplicationStatus.Submitted
        in 50..74 -> ApplicationStatus.Shortlisted
        in 75..84 -> ApplicationStatus.Offered
        in 85..92 -> ApplicationStatus.Withdrawn
        else -> ApplicationStatus.Declined
    }

    private fun generate(family: Family, rnd: Random, index: Int): Case {
        val roles = roles(rnd)
        val eligibility = if (family == Family.Eligibility) EligibleLevel.Undergraduate else EligibleLevel.Any
        val listing = listing(roles, eligibility, index)
        val needed = roles.flatMap { it.skills }
        val teamSkills = needed.shuffled(rnd).take(rnd.nextInt(0, 3))
        val context = TeamContext(listing, teamSkills)
        val people = mutableListOf<Triple<Application, Passport, Profile>>()

        fun add(roleId: String, skills: List<Pair<String, SkillLevel>>, year: Int? = rnd.nextInt(1, 5), status: ApplicationStatus = status(rnd), hours: Int = rnd.nextInt(3, 11)) {
            val uid = "a${people.size}"
            people += Triple(
                application(listing, uid, roleId, hours, status),
                Passport(uid, skills = skills.distinctBy { it.first }.map { Skill(it.first, it.second) }),
                Profile(uid, uid, year = year),
            )
        }

        fun ordinary(role: Role) {
            val own = role.skills.filter { rnd.nextDouble() < 0.6 }
            val others = (needed - role.skills.toSet()).filter { rnd.nextDouble() < 0.2 }
            val random = vocabulary.shuffled(rnd).take(rnd.nextInt(0, 3))
            add(role.id, (own + others + random).map { it to level(rnd) })
        }

        when (family) {
            Family.Balanced, Family.Pinned -> repeat(rnd.nextInt(6, 11)) { ordinary(roles.random(rnd)) }
            Family.Sparse -> repeat(rnd.nextInt(0, 4)) { ordinary(roles.random(rnd)) }
            // The strongest applicants can only give a few hours a week, below what the listing asks.
            Family.Availability -> roles.forEach { role ->
                repeat(rnd.nextInt(1, 3)) { add(role.id, role.skills.map { it to SkillLevel.Advanced }, hours = rnd.nextInt(1, 3)) }
                repeat(rnd.nextInt(1, 3)) { add(role.id, role.skills.filter { rnd.nextBoolean() }.map { it to SkillLevel.Intermediate }, hours = rnd.nextInt(6, 11)) }
            }
            Family.Eligibility -> repeat(rnd.nextInt(6, 11)) {
                ordinary(roles.random(rnd))
                // Some applicants are graduates (not admitted) or haven't recorded a year (unknown).
                val (app, passport, profile) = people.removeAt(people.lastIndex)
                val year = when (rnd.nextInt(100)) {
                    in 0..29 -> rnd.nextInt(6, 8)
                    in 30..44 -> null
                    else -> profile.year
                }
                people += Triple(app, passport, profile.copy(year = year))
            }
            Family.Overlapping -> roles.forEach { role ->
                // Strong look-alikes cover the same half of the role at an advanced level; a few
                // specialists cover what they all lack, plus skills other roles need.
                val half = role.skills.take((role.skills.size + 1) / 2)
                val rest = role.skills - half.toSet()
                repeat(rnd.nextInt(2, 5)) { add(role.id, half.map { it to SkillLevel.Advanced }, status = ApplicationStatus.Submitted) }
                repeat(rnd.nextInt(1, 3)) {
                    val extra = (needed - role.skills.toSet()).shuffled(rnd).take(2)
                    add(role.id, (rest + extra).map { it to SkillLevel.Intermediate }, status = ApplicationStatus.Submitted)
                }
            }
        }

        val fits = people.map { (app, passport, profile) -> TeamMatcher.fit(app, passport, profile, context) }
        val kept = if (family == Family.Pinned) {
            listOfNotNull(
                fits.filter { it.application.status in active && it.eligibility == Eligibility.Eligible }.randomOrNull(rnd)?.applicantId,
                // Now and then the owner pins someone who has since withdrawn; that must be reported, not used.
                fits.firstOrNull { it.application.status == ApplicationStatus.Withdrawn }?.applicantId?.takeIf { rnd.nextBoolean() },
            ).toSet()
        } else {
            emptySet()
        }
        return Case(family, context, fits, kept)
    }

    /**
     * The charter keeps development scenarios apart from the final evaluation cases. Seed 2026
     * was used while building the matcher; seed 4711 is the held-out set the KPIs are judged on.
     */
    private fun cases(seed: Int): List<Case> {
        val rnd = Random(seed)
        return Family.entries.flatMap { family -> (0 until 60).map { i -> generate(family, rnd, family.ordinal * 100 + i) } }
    }

    @Test
    fun `development set - suggestions are valid`() = runEvaluation(cases(2026), "development set (seed 2026)", "recommendation-evaluation-development.md")

    @Test
    fun `held-out evaluation set - valid, at least as useful as individual-fit ranking, and fast`() =
        runEvaluation(cases(4711), "held-out evaluation set (seed 4711)", "recommendation-evaluation.md")

    // ---------- the four methods ----------

    private fun eligiblePool(case: Case): List<ApplicantFit> {
        val places = case.context.listing.recruitment!!.roles.associate { it.id to it.openings }
        return case.fits.filter {
            it.application.status in active && it.eligibility == Eligibility.Eligible && (places[it.roleId] ?: 0) > 0
        }
    }

    /** Pinned people first (where valid), then [pick] fills each role's remaining places. */
    private fun fillRoles(case: Case, pick: (role: Role, candidates: List<ApplicantFit>, chosen: List<ApplicantFit>, places: Int) -> List<ApplicantFit>): List<ApplicantFit> {
        val pool = eligiblePool(case)
        val chosen = pool.filter { it.applicantId in case.kept }.toMutableList()
        case.context.listing.recruitment!!.roles.forEach { role ->
            val left = role.openings - chosen.count { it.roleId == role.id }
            if (left > 0) chosen += pick(role, pool.filter { it.roleId == role.id && it !in chosen }, chosen.toList(), left)
        }
        return chosen
    }

    private fun randomTeam(case: Case, rnd: Random) = fillRoles(case) { _, candidates, _, places -> candidates.shuffled(rnd).take(places) }

    private fun individualFitTeam(case: Case) = fillRoles(case) { _, candidates, _, places -> candidates.sortedByDescending { it.score }.take(places) }

    /** Adds whoever brings the most still-missing skills, ignoring everything else. */
    private fun gapFillingTeam(case: Case): List<ApplicantFit> {
        val teamKeys = case.context.teamSkills.mapTo(HashSet(), SkillMatcher::normalize)
        return fillRoles(case) { _, candidates, chosen, places ->
            val covered = HashSet(teamKeys).apply { chosen.forEach { addAll(it.neededHave) } }
            val picks = mutableListOf<ApplicantFit>()
            repeat(places) {
                val best = candidates.filter { it !in picks }.maxByOrNull { c -> c.neededHave.count { it !in covered } } ?: return@repeat
                picks += best
                covered += best.neededHave
            }
            picks
        }
    }

    private fun outcome(case: Case, picks: List<ApplicantFit>): Outcome {
        val listing = case.context.listing
        val needed = TeamMatcher.neededSkills(listing).map(SkillMatcher::normalize)
        val covered = case.context.teamSkills.mapTo(HashSet(), SkillMatcher::normalize).apply { picks.forEach { addAll(it.neededHave) } }
        val roles = listing.recruitment!!.roles.filter { it.openings > 0 }
        val filled = picks.groupingBy { it.roleId }.eachCount()
        val emptyPlaces = roles.sumOf { (it.openings - (filled[it.id] ?: 0)).coerceAtLeast(0) }
        return Outcome(
            coverage = if (needed.isEmpty()) 1.0 else needed.count { it in covered }.toDouble() / needed.size,
            emptyRoles = roles.count { (filled[it.id] ?: 0) == 0 },
            emptyPlaces = emptyPlaces,
            complete = emptyPlaces == 0,
            averageFit = if (picks.isEmpty()) 0.0 else picks.sumOf { it.score } / picks.size,
        )
    }

    // ---------- validity ----------

    /** Every way a suggestion could break the charter's validity rules, as readable messages. */
    private fun violations(case: Case, recommendation: Recommendation): List<String> = buildList {
        val listing = case.context.listing
        val places = listing.recruitment!!.roles.associate { it.id to it.openings }
        val byId = case.fits.associateBy { it.applicantId }
        val validKept = case.kept.filter { id ->
            val fit = byId[id]
            fit != null && fit.application.status in active && fit.eligibility == Eligibility.Eligible
        }
        recommendation.options.forEach { option ->
            val tag = "${listing.id}/${option.strategy}"
            option.picks.forEach { pick ->
                if (pick.application.listingId != listing.id) add("$tag: ${pick.applicantId} applied elsewhere")
                if (pick.application.status !in active) add("$tag: ${pick.applicantId} is ${pick.application.status}")
                if (pick.eligibility != Eligibility.Eligible) add("$tag: ${pick.applicantId} is not confirmed eligible")
                if (byId[pick.applicantId] == null) add("$tag: ${pick.applicantId} never applied")
                if (listing.isOnTeam(pick.applicantId)) add("$tag: ${pick.applicantId} is already on the team")
            }
            if (option.pickIds.size != option.picks.size) add("$tag: someone is picked twice")
            option.picks.groupingBy { it.roleId }.eachCount().forEach { (role, n) ->
                if (n > (places[role] ?: 0)) add("$tag: $n people for $role, which has ${places[role]} places")
            }
            validKept.forEach { if (it !in option.pickIds) add("$tag: pinned $it was dropped") }
            // An incomplete team must be explained: either the role really had too few suitable
            // applicants, or (for a later option) the screen says another option fills it.
            option.unfilled.forEach { (role, empty) ->
                val suitable = recommendation.applicantsPerRole[role] ?: 0
                val filledElsewhere = recommendation.options.any { other -> other !== option && (other.unfilled[role] ?: 0) < empty }
                val first = option === recommendation.options.first()
                if (suitable >= (places[role] ?: 0) && (first || !filledElsewhere)) {
                    add("$tag: $role left $empty empty although $suitable applicants were suitable")
                }
                if (suitable == 0 && role !in recommendation.emptyRoles) add("$tag: $role has no applicants but is not reported")
            }
        }
        (case.kept - validKept.toSet()).forEach { id ->
            if (recommendation.keptIssues.none { it.applicantId == id }) add("${listing.id}: unusable pin $id is not reported")
        }
        if (recommendation.options.isEmpty() && eligiblePool(case).isNotEmpty()) add("${listing.id}: no suggestion although applicants exist")
    }

    // ---------- the evaluation ----------

    private fun runEvaluation(cases: List<Case>, setName: String, reportFile: String) {
        val rnd = Random(7)
        val results = mutableMapOf<Pair<Family, Method>, MutableList<Outcome>>()
        val problems = mutableListOf<String>()
        val timings = mutableListOf<Long>()
        var options = 0
        var infeasible = 0
        var infeasibleExplained = 0

        cases.forEach { case ->
            val started = System.nanoTime()
            val recommendation = TeamMatcher.recommend(case.context, case.fits, kept = case.kept)
            timings += System.nanoTime() - started
            options += recommendation.options.size
            problems += violations(case, recommendation)

            val first = recommendation.options.firstOrNull()?.picks.orEmpty()
            val takatuf = outcome(case, first)
            if (!takatuf.complete) {
                infeasible++
                if (recommendation.emptyRoles.isNotEmpty() || recommendation.options.firstOrNull()?.unfilled?.isNotEmpty() == true) infeasibleExplained++
            }
            fun record(method: Method, value: Outcome) = results.getOrPut(case.family to method) { mutableListOf() }.add(value)
            record(Method.Takatuf, takatuf)
            // Random selection is averaged over 20 draws so one lucky draw doesn't decide it.
            val draws = (1..20).map { outcome(case, randomTeam(case, rnd)) }
            record(Method.Random, Outcome(draws.map { it.coverage }.average(), draws.sumOf { it.emptyRoles } / 20, draws.sumOf { it.emptyPlaces } / 20, draws.all { it.complete }, draws.map { it.averageFit }.average()))
            record(Method.IndividualFit, outcome(case, individualFitTeam(case)))
            record(Method.GapFilling, outcome(case, gapFillingTeam(case)))
        }

        // A large request to bound the search time: five roles and 200 applicants.
        val stress = stressCase()
        val stressStarted = System.nanoTime()
        TeamMatcher.recommend(stress.context, stress.fits)
        val stressMs = (System.nanoTime() - stressStarted) / 1e6

        fun mean(family: Family?, method: Method, f: (Outcome) -> Double) =
            results.filterKeys { (fam, m) -> m == method && (family == null || fam == family) }.values.flatten().map(f).average()

        val report = buildString {
            appendLine("# Recommendation evaluation")
            appendLine()
            appendLine("Generated by `RecommendationEvaluationTest`, $setName: ${cases.size} synthetic cases in ${Family.entries.size} families, ${options} suggested teams checked.")
            appendLine()
            appendLine("## Validity")
            appendLine()
            appendLine("- Rule violations found: ${problems.size}")
            appendLine("- Cases where no complete team was possible: $infeasible, of which explained (empty roles or unfilled places shown): $infeasibleExplained")
            appendLine()
            appendLine("## Usefulness: average requirement coverage")
            appendLine()
            appendLine("| Case family | " + Method.entries.joinToString(" | ") { it.label } + " |")
            appendLine("|---|" + Method.entries.joinToString("") { "---|" })
            (Family.entries.map { it to it.label } + listOf(null to "All cases")).forEach { (family, label) ->
                appendLine("| $label | " + Method.entries.joinToString(" | ") { pct(mean(family, it) { o -> o.coverage }) } + " |")
            }
            appendLine()
            appendLine("## Unmet roles and team quality (all cases)")
            appendLine()
            appendLine("| Method | Open roles left with nobody, per case | Places left empty per case | Complete teams | Average individual fit |")
            appendLine("|---|---|---|---|---|")
            Method.entries.forEach { m ->
                val all = results.filterKeys { it.second == m }.values.flatten()
                appendLine(
                    "| ${m.label} | ${num(all.map { it.emptyRoles.toDouble() }.average())} | ${num(all.map { it.emptyPlaces.toDouble() }.average())} | " +
                        "${pct(all.count { it.complete }.toDouble() / all.size)} | ${num(all.map { it.averageFit }.average())} |",
                )
            }
            appendLine()
            appendLine("## Search time")
            appendLine()
            val sorted = timings.sorted()
            appendLine("- Per recommendation (all three options): median ${ms(sorted[sorted.size / 2])}, 95th percentile ${ms(sorted[(sorted.size * 95) / 100])}, slowest ${ms(sorted.last())}")
            appendLine("- Stress case (5 roles, 200 applicants): ${num(stressMs)} ms")
            if (problems.isNotEmpty()) {
                appendLine()
                appendLine("## Violations")
                problems.take(50).forEach { appendLine("- $it") }
            }
        }
        File("build/evaluation").apply { mkdirs() }.resolve(reportFile).writeText(report)
        println(report)

        assertEquals("validity violations: ${problems.take(10)}", 0, problems.size)
        assertEquals("every incomplete team is explained", infeasible, infeasibleExplained)
        val takatuf = mean(null, Method.Takatuf) { it.coverage }
        val individual = mean(null, Method.IndividualFit) { it.coverage }
        assertTrue("coverage ${pct(takatuf)} should match or beat individual-fit ${pct(individual)}", takatuf >= individual)
        assertTrue("slowest recommendation ${ms(timings.max())}", timings.max() < 250_000_000)
        assertTrue("stress case took $stressMs ms", stressMs < 2_000)
    }

    private fun stressCase(): Case {
        val rnd = Random(99)
        val roles = vocabulary.chunked(5).take(5).mapIndexed { i, skills -> Role("r$i", "Role $i", skills = skills, openings = 2) }
        val listing = listing(roles, EligibleLevel.Any, 999)
        val context = TeamContext(listing, emptyList())
        val fits = (0 until 200).map { i ->
            val role = roles[i % roles.size]
            val skills = (role.skills.filter { rnd.nextBoolean() } + vocabulary.shuffled(rnd).take(2)).distinct()
            TeamMatcher.fit(
                application(listing, "s$i", role.id, rnd.nextInt(3, 11), ApplicationStatus.Submitted),
                Passport("s$i", skills = skills.map { Skill(it, level(rnd)) }),
                Profile("s$i", "s$i", year = 3),
                context,
            )
        }
        return Case(Family.Balanced, context, fits, emptySet())
    }

    private fun pct(v: Double) = String.format(Locale.ROOT, "%.1f%%", v * 100)
    private fun num(v: Double) = String.format(Locale.ROOT, "%.2f", v)
    private fun ms(nanos: Long) = String.format(Locale.ROOT, "%.2f ms", nanos / 1e6)
}
