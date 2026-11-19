package com.astratouch.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveContentFilterTest {

    @Test
    fun `ordinary text is accepted`() {
        assertFalse(SensitiveContentFilter.isSensitive("Meeting notes for Tuesday at 10am"))
        assertFalse(SensitiveContentFilter.isSensitive("https://example.com/docs/getting-started"))
        assertFalse(SensitiveContentFilter.isSensitive("Order 88213 shipped"))
    }

    @Test
    fun `blank content is rejected`() {
        assertTrue(SensitiveContentFilter.isSensitive(""))
        assertTrue(SensitiveContentFilter.isSensitive("   \n  "))
    }

    @Test
    fun `one time codes are rejected`() {
        assertTrue(SensitiveContentFilter.isSensitive("123456"))
        assertTrue(SensitiveContentFilter.isSensitive("Your OTP is 448192"))
        assertTrue(SensitiveContentFilter.isSensitive("verification code: 5521"))
    }

    @Test
    fun `password shaped strings are rejected`() {
        assertTrue(SensitiveContentFilter.isSensitive("password: hunter2"))
        assertTrue(SensitiveContentFilter.isSensitive("api_key = AKIAIOSFODNN7EXAMPLE"))
    }

    @Test
    fun `private keys are rejected`() {
        assertTrue(
            SensitiveContentFilter.isSensitive(
                "-----BEGIN RSA PRIVATE KEY-----\nMIIEow==\n-----END RSA PRIVATE KEY-----",
            ),
        )
    }

    @Test
    fun `card numbers and cvv labels are rejected`() {
        assertTrue(SensitiveContentFilter.isSensitive("4111111111111111"))
        assertTrue(SensitiveContentFilter.isSensitive("CVV 123"))
    }

    @Test
    fun `over long content is rejected`() {
        assertTrue(SensitiveContentFilter.isSensitive("a".repeat(SensitiveContentFilter.MAX_STORE_CHARS + 1)))
    }

    @Test
    fun `token blob heuristic catches random secrets`() {
        assertTrue(SensitiveContentFilter.isSensitive("aB3!xY9#kL2@mN5\$pQ8"))
    }

    @Test
    fun `rejection reasons are user facing`() {
        assertTrue(SensitiveContentFilter.describeRejection("").isNotBlank())
        assertTrue(SensitiveContentFilter.describeRejection("password: x").contains("password"))
    }
}
