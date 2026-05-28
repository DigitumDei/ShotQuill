package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.ai.AiError
import com.digitumdei.shotquill.shared.ai.AiProvider
import com.digitumdei.shotquill.shared.ai.AiProviderResult
import com.digitumdei.shotquill.shared.ai.AltTextGenerationRequest
import com.digitumdei.shotquill.shared.ai.CaptionGenerationRequest
import com.digitumdei.shotquill.shared.domain.AiOperationType
import com.digitumdei.shotquill.shared.domain.AltTextResult
import com.digitumdei.shotquill.shared.domain.AltTextResultId
import com.digitumdei.shotquill.shared.domain.CaptionDraft
import com.digitumdei.shotquill.shared.domain.CaptionPromptAssembler
import com.digitumdei.shotquill.shared.domain.CaptionRequest
import com.digitumdei.shotquill.shared.domain.CaptionRequestId
import com.digitumdei.shotquill.shared.domain.CaptionResult
import com.digitumdei.shotquill.shared.domain.CaptionResultId
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntry
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.VisionDescription
import com.digitumdei.shotquill.shared.domain.primaryMediaAsset
import com.digitumdei.shotquill.shared.settings.ActiveBrandProfileStore
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import kotlinx.datetime.Instant

class PostTextGenerationPipeline(
    private val repository: ManualWorkflowRepository,
    private val aiProvider: AiProvider,
    private val imageSource: VisionImageSource,
    private val activeBrandProfileStore: ActiveBrandProfileStore,
    private val settingsRepository: LocalSettingsRepository,
    private val clock: EpochClock = EpochClock.Default,
) {
    private val operationSequence = AtomicCounter(0)

    fun generateText(
        draftId: PostDraftId,
        targetPlatform: TargetPlatform = settingsRepository.readSettings().defaultTargetPlatform,
        reuseVisionDescription: Boolean = true,
    ): PostTextGenerationResult {
        val draft = repository.get(draftId) ?: return PostTextGenerationResult.Failure(
            PostTextGenerationError.DraftNotFound,
        )
        if (!settingsRepository.hasOpenAiApiKey()) {
            return PostTextGenerationResult.Failure(PostTextGenerationError.Provider(AiError.MissingApiKey))
        }
        val textStatus = textGeneratedStatus(draft) ?: return PostTextGenerationResult.Failure(
            PostTextGenerationError.InvalidDraftStatus(draft.status),
        )

        val visionDescription = when (
            val result = VisionDescriptionAnalyzer(
                repository = repository,
                aiProvider = aiProvider,
                imageSource = imageSource,
                clock = clock,
            ).analyzePrimaryPhoto(draftId, reuseCached = reuseVisionDescription)
        ) {
            is VisionDescriptionAnalysisResult.Failure -> return PostTextGenerationResult.Failure(
                when (val error = result.error) {
                    VisionDescriptionAnalysisError.DraftNotFound -> PostTextGenerationError.DraftNotFound
                    is VisionDescriptionAnalysisError.Provider -> PostTextGenerationError.Provider(error.error)
                },
            )
            is VisionDescriptionAnalysisResult.Success -> result.visionDescription
        }

        val currentDraft = repository.get(draftId) ?: return PostTextGenerationResult.Failure(
            PostTextGenerationError.DraftNotFound,
        )
        val activeBrandProfile = activeBrandProfileStore.readActiveBrandProfile()
        val promptAssembler = CaptionPromptAssembler(activeBrandProfileStore)
        val captionPrompt = promptAssembler.assembleCaptionPrompt(
            visionDescription = visionDescription.description,
            targetPlatform = targetPlatform,
        )
        val altTextPrompt = promptAssembler.assembleAltTextPrompt(visionDescription.description)

        val captionOutput = when (
            val result = aiProvider.generateCaption(
                CaptionGenerationRequest(
                    draftId = draftId,
                    targetPlatform = targetPlatform,
                    prompt = captionPrompt,
                ),
            )
        ) {
            is AiProviderResult.Failure -> return PostTextGenerationResult.Failure(
                PostTextGenerationError.Provider(result.error),
            )
            is AiProviderResult.Success -> result.value
        }
        val altTextOutput = when (
            val result = aiProvider.generateAltText(
                AltTextGenerationRequest(
                    draftId = draftId,
                    mediaAssetId = currentDraft.primaryMediaAsset().id,
                    prompt = altTextPrompt,
                ),
            )
        ) {
            is AiProviderResult.Failure -> return PostTextGenerationResult.Failure(
                PostTextGenerationError.Provider(result.error),
            )
            is AiProviderResult.Success -> result.value
        }

        val now = clock.nowMillis()
        val idSuffix = nextIdSuffix(now)
        val captionRequest = CaptionRequest(
            id = CaptionRequestId("caption-request-$idSuffix"),
            draftId = draftId,
            targetPlatform = targetPlatform,
            prompt = captionPrompt,
            tone = activeBrandProfile?.voice,
            brandProfileId = activeBrandProfile?.id,
            createdAtEpochMillis = now,
        )
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-$idSuffix"),
            requestId = captionRequest.id,
            draftId = draftId,
            targetPlatform = targetPlatform,
            caption = captionOutput.caption.trim(),
            shortCaption = captionOutput.shortCaption.trim().takeIf { it.isNotEmpty() },
            hashtags = captionOutput.hashtags.map { it.trim() }.filter { it.isNotEmpty() },
            modelName = captionOutput.modelName,
            createdAtEpochMillis = now,
        )
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-result-$idSuffix"),
            draftId = draftId,
            mediaAssetId = currentDraft.primaryMediaAsset().id,
            altText = altTextOutput.altText.trim(),
            modelName = altTextOutput.modelName,
            createdAtEpochMillis = now,
        )
        val captionHistoryEntry = PromptHistoryEntry(
            id = PromptHistoryEntryId("prompt-caption-generation-$idSuffix"),
            draftId = draftId,
            operationType = AiOperationType.CaptionGeneration,
            prompt = captionPrompt,
            responseSummary = captionResult.responseSummary(),
            modelName = captionResult.modelName,
            createdAtEpochMillis = now,
        )
        val altTextHistoryEntry = PromptHistoryEntry(
            id = PromptHistoryEntryId("prompt-alt-text-generation-$idSuffix"),
            draftId = draftId,
            operationType = AiOperationType.AltTextGeneration,
            prompt = altTextPrompt,
            responseSummary = altTextResult.altText,
            modelName = altTextResult.modelName,
            createdAtEpochMillis = now,
        )
        val updatedDraft = currentDraft.copy(
            status = textStatus,
            caption = CaptionDraft(
                text = captionResult.caption,
                hashtags = captionResult.hashtags,
            ),
            targetPlatforms = currentDraft.targetPlatforms + targetPlatform,
            brandProfile = activeBrandProfile ?: currentDraft.brandProfile,
            captionRequests = currentDraft.captionRequests + captionRequest,
            captionResults = currentDraft.captionResults + captionResult,
            altTextResults = currentDraft.altTextResults + altTextResult,
            promptHistory = currentDraft.promptHistory + captionHistoryEntry + altTextHistoryEntry,
            updatedAt = operationUpdatedAt(currentDraft, now),
        )
        repository.save(updatedDraft)

        return PostTextGenerationResult.Success(
            draft = updatedDraft,
            visionDescription = visionDescription,
            captionRequest = captionRequest,
            captionResult = captionResult,
            altTextResult = altTextResult,
            promptHistoryEntries = listOf(captionHistoryEntry, altTextHistoryEntry),
        )
    }

    private fun textGeneratedStatus(draft: PostDraft): DraftStatus? =
        when {
            draft.status == DraftStatus.TextGenerated -> DraftStatus.TextGenerated
            draft.status == DraftStatus.PhotoEdited -> DraftStatus.PhotoEdited
            draft.status == DraftStatus.ReadyToShare -> DraftStatus.ReadyToShare
            draft.status.canTransitionTo(DraftStatus.TextGenerated) -> DraftStatus.TextGenerated
            else -> null
        }

    private fun nextIdSuffix(nowEpochMillis: Long): String =
        "$nowEpochMillis-${operationSequence.getAndIncrement()}"

    private fun operationUpdatedAt(draft: PostDraft, nowEpochMillis: Long): Instant {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        return if (now >= draft.updatedAt) now else draft.updatedAt
    }

    private fun CaptionResult.responseSummary(): String =
        buildString {
            append(caption)
            shortCaption?.takeIf { it.isNotBlank() }?.let {
                append("\nShort: ")
                append(it)
            }
            if (hashtags.isNotEmpty()) {
                append("\nHashtags: ")
                append(hashtags.joinToString(" "))
            }
        }
}

sealed class PostTextGenerationResult {
    data class Success(
        val draft: PostDraft,
        val visionDescription: VisionDescription,
        val captionRequest: CaptionRequest,
        val captionResult: CaptionResult,
        val altTextResult: AltTextResult,
        val promptHistoryEntries: List<PromptHistoryEntry>,
    ) : PostTextGenerationResult()

    data class Failure(
        val error: PostTextGenerationError,
    ) : PostTextGenerationResult()
}

sealed class PostTextGenerationError {
    data object DraftNotFound : PostTextGenerationError()
    data class InvalidDraftStatus(val status: DraftStatus) : PostTextGenerationError()
    data class Provider(val error: AiError) : PostTextGenerationError()
}
