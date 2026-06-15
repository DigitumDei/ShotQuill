package com.digitumdei.shotquill.shared.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse

class AiErrorMapperTest {
    private val apiKey = "sk-test_secret_1234567890"

    @Test
    fun mapsInvalidApiKey() {
        val error = AiErrorMapper.fromHttpStatus(401, """{"error":{"message":"bad $apiKey"}}""", listOf(apiKey))

        assertIs<AiError.InvalidApiKey>(error)
        assertFalse(error.userMessage.contains(apiKey))
    }

    @Test
    fun mapsRateAndQuotaFailuresSeparately() {
        val rate = AiErrorMapper.fromHttpStatus(429, """{"error":{"type":"rate_limit"}}""")
        val quota = AiErrorMapper.fromHttpStatus(429, """{"error":{"type":"insufficient_quota"}}""")

        assertIs<AiError.RateLimited>(rate)
        assertIs<AiError.QuotaExceeded>(quota)
    }

    @Test
    fun mapsBadImageAndContentPolicyFailures() {
        val badImage = AiErrorMapper.fromHttpStatus(400, """{"error":{"message":"image size is too large"}}""")
        val policy = AiErrorMapper.fromHttpStatus(400, """{"error":{"code":"content_policy_violation"}}""")

        assertIs<AiError.ImageRejected>(badImage)
        assertIs<AiError.ContentPolicyViolation>(policy)
    }

    @Test
    fun mapsNetworkFailuresWithSecretRedaction() {
        val error = AiErrorMapper.fromNetworkFailure("network failed for $apiKey", listOf(apiKey))

        assertEquals("network failed for [REDACTED]", error.userMessage)
    }

    @Test
    fun mapsContextLengthExceededFromStatus413() {
        val error = AiErrorMapper.fromHttpStatus(413, "{}")

        assertIs<AiError.ContextLengthExceeded>(error)
    }

    @Test
    fun mapsContextLengthExceededFromBodyKeywords() {
        val error = AiErrorMapper.fromHttpStatus(
            400,
            """{"error":{"code":"context_length_exceeded","message":"maximum context length"}}""",
        )

        assertIs<AiError.ContextLengthExceeded>(error)
    }
}
