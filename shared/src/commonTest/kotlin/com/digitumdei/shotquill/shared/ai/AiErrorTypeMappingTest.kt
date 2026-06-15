package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.domain.AiErrorType
import kotlin.test.Test
import kotlin.test.assertEquals

class AiErrorTypeMappingTest {
    @Test
    fun missingApiKeyMapsToMissingApiKeyType() {
        assertEquals(AiErrorType.MissingApiKey, AiError.MissingApiKey.toFailureType())
    }

    @Test
    fun invalidApiKeyMapsToInvalidApiKeyType() {
        assertEquals(AiErrorType.InvalidApiKey, AiError.InvalidApiKey().toFailureType())
    }

    @Test
    fun rateLimitedMapsToRateLimitedType() {
        assertEquals(AiErrorType.RateLimited, AiError.RateLimited().toFailureType())
    }

    @Test
    fun quotaExceededMapsToQuotaExceededType() {
        assertEquals(AiErrorType.QuotaExceeded, AiError.QuotaExceeded().toFailureType())
    }

    @Test
    fun contextLengthExceededMapsToContextLengthExceededType() {
        assertEquals(AiErrorType.ContextLengthExceeded, AiError.ContextLengthExceeded().toFailureType())
    }

    @Test
    fun contentPolicyViolationMapsToContentPolicyViolationType() {
        assertEquals(AiErrorType.ContentPolicyViolation, AiError.ContentPolicyViolation().toFailureType())
    }

    @Test
    fun imageRejectedMapsToImageRejectedType() {
        assertEquals(AiErrorType.ImageRejected, AiError.ImageRejected().toFailureType())
    }

    @Test
    fun imageUnavailableMapsToImageUnavailableType() {
        assertEquals(AiErrorType.ImageUnavailable, AiError.ImageUnavailable().toFailureType())
    }

    @Test
    fun networkFailureMapsToNetworkFailureType() {
        assertEquals(AiErrorType.NetworkFailure, AiError.NetworkFailure().toFailureType())
    }

    @Test
    fun providerFailureMapsToProviderFailureType() {
        assertEquals(AiErrorType.ProviderFailure, AiError.ProviderFailure(statusCode = 500).toFailureType())
    }
}
