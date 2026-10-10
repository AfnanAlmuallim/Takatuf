package com.z.takatuf.domain

import java.net.URI

object Validators {

    const val MIN_PASSWORD_LENGTH = 8

    /** At least eight characters with at least one letter and one digit. */
    fun isStrongPassword(value: String): Boolean =
        value.length >= MIN_PASSWORD_LENGTH && value.any(Char::isLetter) && value.any(Char::isDigit)

    /**
     * Returns a canonical https/http URL, adding https:// when the scheme is missing, or null
     * when the text can't be a web address.
     */
    fun normalizeUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        return try {
            val uri = URI(withScheme)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            if ((scheme == "https" || scheme == "http") && host != null && host.contains('.') && !host.startsWith('.') && !host.endsWith('.')) {
                withScheme
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
