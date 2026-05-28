package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.ai.AiError
import com.digitumdei.shotquill.shared.ai.AiImageInput
import com.digitumdei.shotquill.shared.ai.AiProvider
import com.digitumdei.shotquill.shared.ai.AiProviderResult
import com.digitumdei.shotquill.shared.ai.VisionDescriptionRequest
import com.digitumdei.shotquill.shared.domain.AiOperationType
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntry
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId
import com.digitumdei.shotquill.shared.domain.VisionDescription
import com.digitumdei.shotquill.shared.domain.VisionDescriptionId
import com.digitumdei.shotquill.shared.domain.VisionDescriptionPromptFactory
import com.digitumdei.shotquill.shared.domain.primaryMediaAsset
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository

class VisionDescriptionAnalyzer(
    private val repository: ManualWorkflowRepository,
    private val aiProvider: AiProvider,
    private val imageSource: VisionImageSource,
    private val clock: EpochClock = EpochClock.Default,
) {
    private val operationSequence = AtomicCounter(0)

    fun analyzePrimaryPhoto(
        draftId: PostDraftId,
        reuseCached: Boolean = true,
    ): VisionDescriptionAnalysisResult {
        val draft = repository.get(draftId) ?: return VisionDescriptionAnalysisResult.Failure(
            VisionDescriptionAnalysisError.DraftNotFound,
        )
        val mediaAsset = draft.primaryMediaAsset()

        if (reuseCached) {
            cachedVisionDescription(draft, mediaAsset)?.let {
                return VisionDescriptionAnalysisResult.Success(it, cacheHit = true)
            }
        }

        val prompt = VisionDescriptionPromptFactory.buildPrompt(mediaAsset)
        val image = imageSource.load(mediaAsset)
        return when (val providerResult = aiProvider.describeVision(
            VisionDescriptionRequest(
                draftId = draft.id,
                mediaAssetId = mediaAsset.id,
                image = image,
                prompt = prompt,
            ),
        )) {
            is AiProviderResult.Failure -> VisionDescriptionAnalysisResult.Failure(
                VisionDescriptionAnalysisError.Provider(providerResult.error),
            )
            is AiProviderResult.Success -> {
                val now = clock.nowMillis()
                val idSuffix = nextIdSuffix(now)
                val description = VisionDescription(
                    id = VisionDescriptionId("vision-description-$idSuffix"),
                    draftId = draft.id,
                    mediaAssetId = mediaAsset.id,
                    description = providerResult.value.description.trim(),
                    modelName = providerResult.value.modelName,
                    createdAtEpochMillis = now,
                )
                val promptHistoryEntry = PromptHistoryEntry(
                    id = PromptHistoryEntryId("prompt-vision-description-$idSuffix"),
                    draftId = draft.id,
                    operationType = AiOperationType.VisionDescription,
                    prompt = prompt,
                    responseSummary = description.description,
                    modelName = description.modelName,
                    createdAtEpochMillis = now,
                )
                repository.saveVisionDescription(description)
                repository.savePromptHistoryEntry(promptHistoryEntry)
                VisionDescriptionAnalysisResult.Success(description, cacheHit = false)
            }
        }
    }

    private fun cachedVisionDescription(draft: PostDraft, mediaAsset: MediaAsset): VisionDescription? =
        repository.listVisionDescriptionsForDraft(draft.id)
            .filter { it.mediaAssetId == mediaAsset.id }
            .maxByOrNull { it.createdAtEpochMillis }
            ?: draft.visionDescription?.takeIf { it.mediaAssetId == mediaAsset.id }

    private fun nextIdSuffix(nowEpochMillis: Long): String =
        "$nowEpochMillis-${operationSequence.getAndIncrement()}"
}

fun interface VisionImageSource {
    fun load(mediaAsset: MediaAsset): AiImageInput
}

sealed class VisionDescriptionAnalysisResult {
    data class Success(
        val visionDescription: VisionDescription,
        val cacheHit: Boolean,
    ) : VisionDescriptionAnalysisResult()

    data class Failure(
        val error: VisionDescriptionAnalysisError,
    ) : VisionDescriptionAnalysisResult()
}

sealed class VisionDescriptionAnalysisError {
    data object DraftNotFound : VisionDescriptionAnalysisError()
    data class Provider(val error: AiError) : VisionDescriptionAnalysisError()
}
