package com.z.takatuf.domain

/**
 * Sprint 1's early matching experiment: plain, explainable overlap between the skills a
 * student lists in their passport and the skills a listing's roles ask for. It decides
 * nothing on its own; it only highlights what already lines up, and the counts it produces
 * are used to order "Best match" results in Discover.
 */
object SkillMatcher {

    // Common spellings students use for the same skill.
    private val aliases = mapOf(
        "js" to "javascript",
        "ts" to "typescript",
        "py" to "python",
        "ml" to "machine learning",
        "ai" to "artificial intelligence",
        "ux" to "ux design",
        "ui" to "ui design",
        "ui/ux" to "ui/ux design",
        "ux/ui" to "ui/ux design",
        "reactjs" to "react",
        "react.js" to "react",
        "node" to "node.js",
        "nodejs" to "node.js",
        "golang" to "go",
        "postgres" to "postgresql",
        "k8s" to "kubernetes",
        "c sharp" to "c#",
        "cpp" to "c++",
        "jetpack compose" to "compose",
        "ms excel" to "excel",
        "microsoft excel" to "excel",
    )

    fun normalize(skill: String): String {
        val base = skill.trim().lowercase().replace(Regex("\\s+"), " ")
        return aliases[base] ?: base
    }

    /** The skills in [wanted] that [have] covers, in [wanted]'s order and spelling. */
    fun covered(have: Collection<String>, wanted: Collection<String>): List<String> {
        if (have.isEmpty() || wanted.isEmpty()) return emptyList()
        val haveSet = have.mapTo(HashSet(), ::normalize)
        return wanted.filter { normalize(it) in haveSet }.distinctBy(::normalize)
    }

    fun isCovered(have: Collection<String>, skill: String): Boolean =
        have.any { normalize(it) == normalize(skill) }
}
