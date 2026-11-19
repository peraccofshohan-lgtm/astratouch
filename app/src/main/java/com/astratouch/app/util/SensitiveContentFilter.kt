package com.astratouch.app.util

/**
 * Heuristic guard that keeps passwords, one-time codes and payment data out of
 * the AstraTouch clipboard history (Part 13 / Part 19 privacy audit).
 *
 * This is intentionally conservative: when in doubt, the content is rejected.
 * It is defence-in-depth on top of Android 13's `ClipDescription.EXTRA_IS_SENSITIVE`
 * flag, which is checked by the caller before this function runs.
 */
object SensitiveContentFilter {

    private val otpPatterns = listOf(
        Regex("""\b(otp|o\.?t\.?p|one[-\s]?time\s*(code|password|pin)|verification\s*code|2fa|two[-\s]?factor)\b""", RegexOption.IGNORE_CASE),
        Regex("""^\s*\d{4,8}\s*$"""),
    )

    private val passwordPatterns = listOf(
        Regex("""\b(password|passwd|passphrase|pwd|pin|secret|api[_\s-]?key|token|access[_\s-]?key)\b\s*[:=]""", RegexOption.IGNORE_CASE),
        Regex("""\bBEGIN\s+(RSA|OPENSSH|PGP|EC|DSA)\s+PRIVATE\s+KEY\b"""),
    )

    private val paymentPatterns = listOf(
        Regex("""\b\d{13,19}\b"""),
        Regex("""\b(cvv|cvc|card\s*number|iban|swift)\b""", RegexOption.IGNORE_CASE),
    )

    private val emailLike = Regex("""^[\w.+-]+@[\w-]+\.[\w.-]+$""")

    /**
     * @return true when the content must not be persisted.
     */
    fun isSensitive(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.length > MAX_STORE_CHARS) return true

        otpPatterns.forEach { if (it.containsMatchIn(trimmed)) return true }
        passwordPatterns.forEach { if (it.containsMatchIn(trimmed)) return true }
        paymentPatterns.forEach { if (it.containsMatchIn(trimmed)) return true }

        // Credential-shaped URL (user:pass@host) or a bare token-like blob.
        if (Regex("""\w+://[^/\s]+:[^/\s]+@""").containsMatchIn(trimmed)) return true
        if (trimmed.length in 24..512 && !trimmed.contains(' ') && emailLike.matches(trimmed).not()) {
            val hasUpper = trimmed.any { it.isUpperCase() }
            val hasDigit = trimmed.any { it.isDigit() }
            val hasSymbol = trimmed.any { !it.isLetterOrDigit() }
            if (hasUpper && hasDigit && hasSymbol) return true
        }
        return false
    }

    /** Human-readable reason used in the UI when a capture is skipped. */
    fun describeRejection(text: String): String = when {
        text.isBlank() -> "Nothing to capture."
        text.length > MAX_STORE_CHARS -> "This text is too long for clipboard history."
        else -> "Looks like a password, one-time code or payment detail, so AstraTouch did not save it."
    }

    const val MAX_STORE_CHARS = 20_000
}
