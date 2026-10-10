package com.z.takatuf.data.model

/** Who may open a student's Skill Passport. Their name and major are always visible. */
enum class Visibility(val key: String) {
    /** Any signed-in student. */
    Everyone("everyone"),

    /** Only owners of listings the student has applied to. */
    Applied("applied"),

    /** Nobody else; owners see only the application itself. */
    Private("private");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Everyone
    }
}

/** The public identity card stored at `profiles/{uid}`. Readable by any signed-in student. */
data class Profile(
    val uid: String,
    val displayName: String,
    val major: String = "",
    val year: Int? = null,
    val headline: String = "",
    val visibility: Visibility = Visibility.Everyone,
    val onboarded: Boolean = false,
) {
    val initials: String get() = displayName.initials()
}

fun String.initials(): String =
    trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

enum class SkillLevel(val value: Int) {
    Beginner(1), Intermediate(2), Advanced(3);

    companion object {
        fun from(value: Number?) = entries.firstOrNull { it.value == value?.toInt() } ?: Intermediate
    }
}

data class Skill(val name: String, val level: SkillLevel = SkillLevel.Intermediate)

data class Experience(
    val id: String,
    val title: String,
    val organization: String = "",
    val period: String = "",
    val description: String = "",
)

/** A link to work the student can point to: a repository, portfolio, demo or paper. */
data class EvidenceLink(val id: String, val label: String, val url: String)

/** Ordered as the Saudi week runs, Sunday first. */
enum class Weekday(val key: String) {
    Sun("sun"), Mon("mon"), Tue("tue"), Wed("wed"), Thu("thu"), Fri("fri"), Sat("sat");

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key }
    }
}

data class Availability(
    val hoursPerWeek: Int = 0,
    val days: Set<Weekday> = emptySet(),
    val note: String = "",
) {
    val isSet: Boolean get() = hoursPerWeek > 0
}

/** The gated half of a profile, stored at `passports/{uid}`; see [Visibility]. */
data class Passport(
    val uid: String,
    val skills: List<Skill> = emptyList(),
    val interests: List<String> = emptyList(),
    val experience: List<Experience> = emptyList(),
    val availability: Availability = Availability(),
    val links: List<EvidenceLink> = emptyList(),
) {
    val isEmpty: Boolean
        get() = skills.isEmpty() && interests.isEmpty() && experience.isEmpty() &&
            !availability.isSet && links.isEmpty()
}

/** What the current user is allowed to see of someone else's passport. */
sealed interface PassportAccess {
    data class Visible(val passport: Passport) : PassportAccess
    data object Hidden : PassportAccess
    data object Missing : PassportAccess
}
