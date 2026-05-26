package com.digitumdei.shotquill.shared.settings

import com.digitumdei.shotquill.shared.domain.AiOperationType
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.ExportRecordId
import com.digitumdei.shotquill.shared.domain.ExportStatus
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntry
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId
import com.digitumdei.shotquill.shared.domain.TargetPlatform
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
    fun masksOpenAiApiKeyWithoutReturningFullSecret() {
        val masked = SecretRedactor.maskOpenAiApiKey(apiKey)

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

    @Test
    fun allowsPromptHistoryValuesToBeMappedBeforeStorageRedaction() {
        val entry = PromptHistoryEntry(
            id = PromptHistoryEntryId("prompt-1"),
            draftId = PostDraftId("draft-1"),
            operationType = AiOperationType.CaptionGeneration,
            prompt = "Use $apiKey for the request",
            responseSummary = null,
            modelName = "caption-model",
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals("Use $apiKey for the request", entry.prompt)
    }

    @Test
    fun allowsExportErrorsToBeMappedBeforeStorageRedaction() {
        val record = ExportRecord(
            id = ExportRecordId("export-1"),
            draftId = PostDraftId("draft-1"),
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            status = ExportStatus.Failed,
            destinationUri = null,
            errorMessage = "OpenAI call failed for $apiKey",
            createdAtEpochMillis = 1_700_000_000_000L,
            completedAtEpochMillis = null,
        )

        assertEquals("OpenAI call failed for $apiKey", record.errorMessage)
    }
}
