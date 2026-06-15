package com.digitumdei.shotquill.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiFailureRecordTest {
    @Test
    fun constructsValidAiFailureRecord() {
        val record = AiFailureRecord(
            id = AiFailureRecordId("failure-1"),
            draftId = PostDraftId("draft-1"),
            operationType = AiOperationType.CaptionGeneration,
            errorType = AiErrorType.RateLimited,
            userMessage = "Rate limited.",
            attempt = 1,
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals(AiFailureRecordId("failure-1"), record.id)
        assertEquals(PostDraftId("draft-1"), record.draftId)
        assertEquals(AiOperationType.CaptionGeneration, record.operationType)
        assertEquals(AiErrorType.RateLimited, record.errorType)
        assertEquals("Rate limited.", record.userMessage)
        assertEquals(1, record.attempt)
        assertEquals(1_700_000_000_000L, record.createdAtEpochMillis)
    }

    @Test
    fun attemptZeroThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            AiFailureRecord(
                id = AiFailureRecordId("failure-1"),
                draftId = PostDraftId("draft-1"),
                operationType = AiOperationType.VisionDescription,
                errorType = AiErrorType.NetworkFailure,
                userMessage = "Network failed.",
                attempt = 0,
                createdAtEpochMillis = 1_700_000_000_000L,
            )
        }
    }

    @Test
    fun negativeCreatedAtEpochMillisThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            AiFailureRecord(
                id = AiFailureRecordId("failure-1"),
                draftId = PostDraftId("draft-1"),
                operationType = AiOperationType.AltTextGeneration,
                errorType = AiErrorType.ProviderFailure,
                userMessage = "Provider failed.",
                attempt = 1,
                createdAtEpochMillis = -1L,
            )
        }
    }
}
