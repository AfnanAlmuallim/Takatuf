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
    /** Longest link the backend stores. */
    const val MAX_LINK_LENGTH = 300

    private val webLinkPattern = Regex("https?://[^\\s/]+[.][^\\s]*")

    /**
     * A link as the app stores it, or null when [value] isn't a usable web link: http(s), a
     * host with a dot, no spaces, at most [MAX_LINK_LENGTH] characters once normalised. The
     * Firestore rules (`webLink`) apply the same test, so a link the app accepts is one the
     * backend accepts, and a malformed link is refused even when sent directly.
     */
    fun webLink(value: String): String? {
        val url = normalizeUrl(value) ?: return null
        val scheme = url.substringBefore("://")
        val canonical = scheme.lowercase() + url.substring(scheme.length)
        return canonical.takeIf { it.length <= MAX_LINK_LENGTH && webLinkPattern.matches(it) }
    }

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
