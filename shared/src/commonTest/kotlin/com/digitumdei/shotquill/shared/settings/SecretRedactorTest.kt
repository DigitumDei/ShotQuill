package com.digitumdei.shotquill.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SecretRedactorTest {
    private val apiKey = "sk-test_1234567890"

    @Test
    fun redactsKnownSecretsFromLogOrErrorMessages() {
        val redacted = SecretRedactor.redactKnownSecrets(
            message = "OpenAI request failed with key $apiKey",
            secrets = listOf(apiKey),
        )

        assertFalse(redacted.contains(apiKey))
        assertEquals("OpenAI request failed with key [REDACTED]", redacted)
    }

    @Test
    fun redactsOpenAiApiKeyPatternsWithoutExplicitSecretList() {
        val redacted = SecretRedactor.redactOpenAiApiKeys("Header used sk-live_abcdefghi123456")

        assertEquals("Header used [REDACTED]", redacted)
    }

    @Test
    fun masksAnApiKeyWithoutReturningFullSecret() {
        val masked = SecretRedactor.maskApiKey(apiKey)

        assertFalse(masked.contains(apiKey))
        assertEquals("sk-...7890", masked)
    }

    @Test
    fun leavesShortKnownSecretsReadable() {
        val redacted = SecretRedactor.redactKnownSecrets(
            message = "Save failed because value x is invalid",
            secrets = listOf("x"),
        )

        assertEquals("Save failed because value x is invalid", redacted)
    }
}
