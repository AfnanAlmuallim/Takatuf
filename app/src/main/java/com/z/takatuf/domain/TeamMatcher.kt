package com.z.takatuf.domain

import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Passport
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.SkillLevel

enum class FitBand { Strong, Good, Partial }

enum class Eligibility { Eligible, Ineligible, Unknown }

/**
 * How one applicant fits the role they applied for (US09). Every number here can be shown to
 * the owner in plain words, so the ranking is never a black box (NFR10).
 */
data class ApplicantFit(
    val application: Application,
    /** 0–1, from the weighted factors below. */
    val score: Double,
    val band: FitBand,
    /** Skills the role asks for that the applicant lists. */
    val skillsCovered: List<String>,
    val skillsMissing: List<String>,
    /** Normalised skills needed by any open role that this applicant has. */
    val neededHave: Set<String>,
    /** Needed skills the current team lacks and this applicant would bring. */
    val bringsToTeam: List<String>,
    val hoursOffered: Int,
    val hoursAsked: Int,
    val relevantExperience: Int,
    val sharedInterests: List<String>,
    val eligibility: Eligibility,
    /** The passport is hidden or empty, so skills and experience are unknown, not poor (NFR11). */
    val limitedInfo: Boolean,
) {
    val applicantId: String get() = application.applicantId
    val roleId: String get() = application.roleId
}

/** What the owner's team looks like now: the listing plus skills the lead and members bring. */
data class TeamContext(
    val listing: Listing,
    val teamSkills: Collection<String>,
)

enum class Strategy {
    /** Greedy: each pick adds the most skills the team still lacks. */
    Coverage,

    /** The highest individual fit for each role, ignoring overlap. */
    Strongest,

    /** A second coverage search that leaves out the first option's top pick. */
    Alternative,
}

data class TeamOption(
    val strategy: Strategy,
    val picks: List<ApplicantFit>,
    /** Needed skills (display names) the team would cover with these picks. */
    val covered: List<String>,
    val missing: List<String>,
    val needed: List<String>,
    /** Places per role left empty by this option. */
    val unfilled: Map<String, Int>,
) {
    val pickIds: Set<String> get() = picks.mapTo(LinkedHashSet()) { it.applicantId }
    val coverage: Double get() = if (needed.isEmpty()) 1.0 else covered.size.toDouble() / needed.size
    val averageFit: Double get() = if (picks.isEmpty()) 0.0 else picks.sumOf { it.score } / picks.size
    val emptyPlaces: Int get() = unfilled.values.sum()
    val isComplete: Boolean get() = emptyPlaces == 0
}

enum class KeptReason {
    /** Withdrew, declined, joined elsewhere, or isn't eligible for the event. */
    NotAvailable,

    /** More people kept for a role than it has places. */
    RoleOverbooked,

    /** The event has an eligibility rule and this applicant hasn't recorded their year. */
    EligibilityUnknown,
}

data class KeptIssue(val applicantId: String, val name: String, val roleTitle: String, val reason: KeptReason)

data class Recommendation(
    val options: List<TeamOption>,
    val keptIssues: List<KeptIssue>,
    /** Open roles with no eligible applicant at all. */
    val emptyRoles: List<String>,
    val ineligible: List<ApplicantFit>,
    /** Applicants whose eligibility can't be checked (no year recorded); never suggested. */
    val unverified: List<ApplicantFit> = emptyList(),
    val needed: List<String>,
    /** Needed skills the current team (lead and members) already covers. */
    val coveredByTeam: List<String>,
    /** Suitable applicants per role id, across every option. */
    val applicantsPerRole: Map<String, Int> = emptyMap(),
)

/**
 * Rule-based matching from the charter (Technical Environment): a weighted score for each applicant, then a greedy
 * search that keeps adding whoever best complements the team's remaining needs. It never
 * changes membership; it only proposes people to send offers to (US10).
 */
object TeamMatcher {

    private const val WEIGHT_SKILLS = 0.50
    private const val WEIGHT_AVAILABILITY = 0.25
    private const val WEIGHT_EXPERIENCE = 0.15
    private const val WEIGHT_INTERESTS = 0.10

    /** Unknown factors count as average rather than zero, so a private passport isn't punished. */
    private const val NEUTRAL = 0.5

    private val considered = setOf(ApplicationStatus.Submitted, ApplicationStatus.Shortlisted, ApplicationStatus.Offered)

    private fun levelWeight(level: SkillLevel?): Double = when (level) {
        SkillLevel.Advanced -> 1.0
        SkillLevel.Intermediate -> 0.8
        SkillLevel.Beginner -> 0.5
        null -> 0.0
    }

    /** Skills any open role asks for, de-duplicated, in the order roles list them. */
    fun neededSkills(listing: Listing): List<String> =
        listing.recruitment?.roles.orEmpty()
            .filter { it.openings > 0 }
            .flatMap { it.skills }
            .distinctBy(SkillMatcher::normalize)

    fun fit(application: Application, passport: Passport?, profile: Profile?, context: TeamContext): ApplicantFit {
        val listing = context.listing
        val role = listing.recruitment?.roles?.firstOrNull { it.id == application.roleId }
        val roleSkills = role?.skills.orEmpty()
        val limitedInfo = passport == null || passport.isEmpty
        val levels = passport?.skills.orEmpty().associate { SkillMatcher.normalize(it.name) to it.level }

        val covered = roleSkills.filter { SkillMatcher.normalize(it) in levels }
        val missing = roleSkills.filterNot { SkillMatcher.normalize(it) in levels }
        val skillScore = when {
            limitedInfo -> NEUTRAL
            roleSkills.isEmpty() -> 0.6
            else -> roleSkills.sumOf { levelWeight(levels[SkillMatcher.normalize(it)]) } / roleSkills.size
        }

        val asked = listing.recruitment?.hoursPerWeek ?: 0
        val offered = application.hoursPerWeek
        val availabilityScore = if (asked <= 0) 1.0 else (offered.toDouble() / asked).coerceIn(0.0, 1.0)

        val tokens = buildSet {
            role?.title.orEmpty().lowercase().split(Regex("[^\\p{L}\\p{Nd}+#]+")).filter { it.length >= 4 }.forEach(::add)
            roleSkills.map(SkillMatcher::normalize).forEach(::add)
        }
        val relevant = passport?.experience.orEmpty().count { entry ->
            val text = "${entry.title} ${entry.organization} ${entry.description}".lowercase()
            tokens.any { text.contains(it) }
        }
        val experienceScore = when {
            limitedInfo -> NEUTRAL
            else -> (relevant / 2.0 + if (passport!!.links.isNotEmpty()) 0.2 else 0.0).coerceAtMost(1.0)
        }

        val listingText = buildString {
            append(listing.title).append(' ')
            append(listing.description).append(' ')
            append(listing.recruitment?.outcome.orEmpty()).append(' ')
            append(listing.linkedEvent?.title.orEmpty())
        }.lowercase()
        val sharedInterests = passport?.interests.orEmpty().filter { interest ->
            interest.lowercase().split(Regex("\\s+")).filter { it.length >= 4 }.any { listingText.contains(it) }
        }
        val interestScore = when {
            limitedInfo -> NEUTRAL
            sharedInterests.isNotEmpty() -> 1.0
            else -> 0.4
        }

        val score = WEIGHT_SKILLS * skillScore +
            WEIGHT_AVAILABILITY * availabilityScore +
            WEIGHT_EXPERIENCE * experienceScore +
            WEIGHT_INTERESTS * interestScore

        val needed = neededSkills(listing)
        val neededKeys = needed.mapTo(HashSet(), SkillMatcher::normalize)
        val teamKeys = context.teamSkills.mapTo(HashSet(), SkillMatcher::normalize)
        val neededHave = levels.keys.filterTo(HashSet()) { it in neededKeys }
        val brings = needed.filter { SkillMatcher.normalize(it) in neededHave && SkillMatcher.normalize(it) !in teamKeys }

        val eligibility = when (listing.eligibleLevel.admits(profile?.year)) {
            true -> Eligibility.Eligible
            false -> Eligibility.Ineligible
            null -> Eligibility.Unknown
        }

        return ApplicantFit(
            application = application,
            score = score,
            band = when {
                score >= 0.75 -> FitBand.Strong
                score >= 0.55 -> FitBand.Good
                else -> FitBand.Partial
            },
            skillsCovered = covered,
            skillsMissing = missing,
            neededHave = neededHave,
            bringsToTeam = brings,
            hoursOffered = offered,
            hoursAsked = asked,
            relevantExperience = relevant,
            sharedInterests = sharedInterests,
            eligibility = eligibility,
            limitedInfo = limitedInfo,
        )
    }

    /**
     * Suggests up to three different teams. [kept] people stay in every option (US11); people in
     * [avoid] are passed over when someone comparable exists, so "Show other options" finds new
     * combinations.
     */
    fun recommend(
        context: TeamContext,
        fits: List<ApplicantFit>,
        kept: Set<String> = emptySet(),
        avoid: Set<String> = emptySet(),
    ): Recommendation {
        val listing = context.listing
        val roles = listing.recruitment?.roles.orEmpty()
        val places = roles.associate { it.id to it.openings }
        val needed = neededSkills(listing)
        val neededKeys = needed.map(SkillMatcher::normalize)
        val teamKeys = context.teamSkills.mapTo(HashSet(), SkillMatcher::normalize)
        val coveredByTeam = needed.filter { SkillMatcher.normalize(it) in teamKeys }

        val ineligible = fits.filter { it.eligibility == Eligibility.Ineligible && it.application.status in considered }
        // Suggestions use only applicants known to be eligible (charter KPI "recommendation
        // validity"); those who haven't recorded a year are listed so the owner can ask them.
        val unverified = fits.filter { it.eligibility == Eligibility.Unknown && it.application.status in considered }
        val pool = fits.filter {
            it.application.status in considered &&
                it.eligibility == Eligibility.Eligible &&
                (places[it.roleId] ?: 0) > 0 &&
                !listing.isOnTeam(it.applicantId)
        }
        val poolIds = pool.mapTo(HashSet()) { it.applicantId }

        // Keep the people the owner pinned, in pin order, as long as their role has room.
        val keptIssues = mutableListOf<KeptIssue>()
        val keptValid = mutableListOf<ApplicantFit>()
        val keptPlaces = places.toMutableMap()
        kept.forEach { id ->
            val fit = fits.firstOrNull { it.applicantId == id }
            when {
                fit != null && fit in unverified -> keptIssues += KeptIssue(
                    id, fit.application.applicantName, fit.application.roleTitle, KeptReason.EligibilityUnknown,
                )
                fit == null || id !in poolIds -> keptIssues += KeptIssue(
                    id,
                    fit?.application?.applicantName.orEmpty(),
                    fit?.application?.roleTitle.orEmpty(),
                    KeptReason.NotAvailable,
                )
                (keptPlaces[fit.roleId] ?: 0) <= 0 -> keptIssues += KeptIssue(
                    id, fit.application.applicantName, fit.application.roleTitle, KeptReason.RoleOverbooked,
                )
                else -> {
                    keptValid += fit
                    keptPlaces[fit.roleId] = keptPlaces.getValue(fit.roleId) - 1
                }
            }
        }

        fun build(strategy: Strategy, picks: List<ApplicantFit>): TeamOption {
            val coveredKeys = teamKeys + picks.flatMap { it.neededHave }
            val covered = needed.filter { SkillMatcher.normalize(it) in coveredKeys }
            val filled = picks.groupingBy { it.roleId }.eachCount()
            return TeamOption(
                strategy = strategy,
                picks = picks,
                covered = covered,
                missing = needed - covered.toSet(),
                needed = needed,
                unfilled = places.mapValues { (role, open) -> (open - (filled[role] ?: 0)).coerceAtLeast(0) }
                    .filterValues { it > 0 },
            )
        }

        fun greedy(exclude: Set<String>): List<ApplicantFit> {
            val left = keptPlaces.toMutableMap()
            val chosen = keptValid.toMutableList()
            val covered = HashSet(teamKeys).apply { keptValid.forEach { addAll(it.neededHave) } }
            while (true) {
                val remainingNeeded = neededKeys.count { it !in covered }.coerceAtLeast(1)
                val best = pool
                    .filter { it !in chosen && it.applicantId !in exclude && (left[it.roleId] ?: 0) > 0 }
                    .maxWithOrNull(
                        compareBy<ApplicantFit> { candidate ->
                            val adds = candidate.neededHave.count { it !in covered }
                            0.55 * adds / remainingNeeded + 0.45 * candidate.score -
                                if (candidate.applicantId in avoid) 0.3 else 0.0
                        }.thenBy { it.score },
                    ) ?: break
                chosen += best
                covered += best.neededHave
                left[best.roleId] = left.getValue(best.roleId) - 1
            }
            return chosen
        }

        fun strongest(): List<ApplicantFit> {
            val chosen = keptValid.toMutableList()
            keptPlaces.forEach { (roleId, open) ->
                chosen += pool
                    .filter { it.roleId == roleId && it !in keptValid }
                    .sortedWith(compareByDescending<ApplicantFit> { if (it.applicantId in avoid) it.score - 0.3 else it.score })
                    .take(open)
            }
            return chosen
        }

        val coverage = build(Strategy.Coverage, greedy(emptySet()))
        val strongest = build(Strategy.Strongest, strongest())
        // A different team: leave out one of the first option's picks, trying them in order, and
        // keep the first combination that is no less complete. Leaving out someone who is the
        // only suitable applicant for their role would just open an avoidable gap.
        val candidates = coverage.picks.filter { it !in keptValid }.asSequence()
            .map { build(Strategy.Alternative, greedy(setOf(it.applicantId))) }
        val alternative = candidates.firstOrNull { it.emptyPlaces <= coverage.emptyPlaces } ?: candidates.firstOrNull()

        val options = listOfNotNull(coverage, strongest, alternative)
            .filter { it.picks.isNotEmpty() }
            .distinctBy { it.pickIds }

        val emptyRoles = roles.filter { role ->
            role.openings > 0 && pool.none { it.roleId == role.id }
        }.map { it.id }

        return Recommendation(
            options = options,
            keptIssues = keptIssues,
            emptyRoles = emptyRoles,
            ineligible = ineligible,
            unverified = unverified,
            needed = needed,
            coveredByTeam = coveredByTeam,
            applicantsPerRole = pool.groupingBy { it.roleId }.eachCount(),
        )
    }
}
