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
}
