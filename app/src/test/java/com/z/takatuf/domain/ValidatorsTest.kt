package com.z.takatuf.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `passwords need eight characters with a letter and a digit`() {
        assertTrue(Validators.isStrongPassword("takatuf26"))
        assertFalse(Validators.isStrongPassword("short1"))
        assertFalse(Validators.isStrongPassword("onlyletters"))
        assertFalse(Validators.isStrongPassword("12345678"))
    }

    @Test
    fun `urls get https added and must have a real host`() {
        assertEquals("https://github.com/you/project", Validators.normalizeUrl("github.com/you/project"))
        assertEquals("http://example.org/a", Validators.normalizeUrl("http://example.org/a"))
        assertNull(Validators.normalizeUrl("not a link"))
        assertNull(Validators.normalizeUrl("https://localhost"))
        assertNull(Validators.normalizeUrl("ftp://example.org"))
        assertNull(Validators.normalizeUrl(""))
    }

    /** D02: the app's link policy, matching the `webLink` security rule case for case. */
    @Test
    fun `progress and deliverable links follow the backend's web-link policy`() {
        assertEquals("https://github.com/example/parking", Validators.webLink("github.com/example/parking"))
        assertEquals("http://docs.example.org", Validators.webLink("http://docs.example.org"))
        assertEquals("https://example.org/x", Validators.webLink("HTTPS://example.org/x"))
        for (bad in listOf("not-a-url", "https://", "ftp://example.org/file", "https://localhost/x", "https://exa mple.org", "https://example.org/" + "a".repeat(300))) {
            assertNull(bad, Validators.webLink(bad))
        }
    }
}
