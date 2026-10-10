package com.z.takatuf.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillMatcherTest {

    @Test
    fun `matching ignores case, spacing and common aliases`() {
        assertEquals("javascript", SkillMatcher.normalize("  JS "))
        assertEquals("machine learning", SkillMatcher.normalize("ML"))
        assertEquals("compose", SkillMatcher.normalize("Jetpack   Compose"))
        assertTrue(SkillMatcher.isCovered(listOf("reactjs"), "React"))
    }

    @Test
    fun `covered keeps the wanted spelling and order and drops duplicates`() {
        val covered = SkillMatcher.covered(
            have = listOf("figma", "Kotlin", "UX"),
            wanted = listOf("Kotlin", "Figma", "UX design", "Python", "figma"),
        )
        assertEquals(listOf("Kotlin", "Figma", "UX design"), covered)
    }

    @Test
    fun `nothing is covered when either side is empty`() {
        assertTrue(SkillMatcher.covered(emptyList(), listOf("Kotlin")).isEmpty())
        assertTrue(SkillMatcher.covered(listOf("Kotlin"), emptyList()).isEmpty())
        assertFalse(SkillMatcher.isCovered(emptyList(), "Kotlin"))
    }
}
