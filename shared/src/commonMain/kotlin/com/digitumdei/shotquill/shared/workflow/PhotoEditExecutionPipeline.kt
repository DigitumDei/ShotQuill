package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.ai.AiError
import com.digitumdei.shotquill.shared.ai.AiImageInput
import com.digitumdei.shotquill.shared.ai.AiProvider
import com.digitumdei.shotquill.shared.ai.AiProviderResult
import com.digitumdei.shotquill.shared.ai.PhotoEditGenerationRequest
import com.digitumdei.shotquill.shared.domain.AiOperationType
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.MaskRegion
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.PhotoEditPromptAssembler
import com.digitumdei.shotquill.shared.domain.PhotoEditRequest
import com.digitumdei.shotquill.shared.domain.PhotoEditRequestId
import com.digitumdei.shotquill.shared.domain.PhotoEditResult
import com.digitumdei.shotquill.shared.domain.PhotoEditResultId
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntry
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.primaryMediaAsset
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import kotlinx.datetime.Instant

interface PhotoEditExecutor {
    fun execute(
        draftId: PostDraftId,
        intent: EditIntent,
        realismLevel: RealismLevel,
        qualityTier: QualityTier,
        targetPlatform: TargetPlatform,
        prompt: String,
        userRefinement: String? = null,
        maskRegion: MaskRegion? = null,
        reuseVisionDescription: Boolean = true,
    ): PhotoEditExecutionResult
}

class PhotoEditExecutionPipeline(
    private val repository: ManualWorkflowRepository,
    private val aiProvider: AiProvider,
    private val settingsRepository: LocalSettingsRepository,
    private val imageSource: PhotoEditImageSource,
    private val mediaSaver: PhotoEditMediaSaver,
    private val visionImageSource: VisionImageSource,
    private val clock: EpochClock = EpochClock.Default,
) : PhotoEditExecutor {
    private val operationSequence = AtomicCounter(0)

    override fun execute(
        draftId: PostDraftId,
        intent: EditIntent,
        realismLevel: RealismLevel,
        qualityTier: QualityTier,
        targetPlatform: TargetPlatform,
        prompt: String,
        userRefinement: String?,
        maskRegion: MaskRegion?,
        reuseVisionDescription: Boolean,
    ): PhotoEditExecutionResult {
        val draft = repository.get(draftId) ?: return PhotoEditExecutionResult.Failure(
            PhotoEditExecutionError.DraftNotFound,
        )
        if (!settingsRepository.hasOpenAiApiKey()) {
            return PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.Provider(AiError.MissingApiKey),
            )
        }
        if (photoEditStatus(draft) == null) {
            return PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.InvalidDraftStatus(draft.status),
            )
        }

        val visionDescription = when (
            val result = VisionDescriptionAnalyzer(
                repository = repository,
                aiProvider = aiProvider,
                imageSource = visionImageSource,
                clock = clock,
            ).analyzePrimaryPhoto(draftId, reuseCached = reuseVisionDescription)
        ) {
            is VisionDescriptionAnalysisResult.Failure -> return PhotoEditExecutionResult.Failure(
                when (val error = result.error) {
                    VisionDescriptionAnalysisError.DraftNotFound ->
                        PhotoEditExecutionError.DraftNotFound
                    is VisionDescriptionAnalysisError.Provider ->
                        PhotoEditExecutionError.Provider(error.error)
                },
            )
            is VisionDescriptionAnalysisResult.Success -> result.visionDescription
        }

        val currentDraft = repository.get(draftId) ?: return PhotoEditExecutionResult.Failure(
            PhotoEditExecutionError.DraftNotFound,
        )
        val sourceMediaAsset = currentDraft.primaryMediaAsset()

        val now = clock.nowMillis()
        val idSuffix = nextIdSuffix(now)
        val editRequest = PhotoEditRequest(
            id = PhotoEditRequestId("photo-edit-request-$idSuffix"),
            draftId = draftId,
            sourceMediaAssetId = sourceMediaAsset.id,
            intent = intent,
            realismLevel = realismLevel,
            qualityTier = qualityTier,
            prompt = prompt,
            userRefinement = userRefinement?.trim()?.takeIf { it.isNotEmpty() },
            subjectDescription = visionDescription.description,
            targetPlatform = targetPlatform,
            maskRegion = maskRegion,
            createdAtEpochMillis = now,
        )
        val assembledPrompt = PhotoEditPromptAssembler.assemble(editRequest)

        val sourceImageResult = imageSource.load(sourceMediaAsset)
        if (sourceImageResult is SourceImageResult.Failure) {
            return PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.FailedToLoadSourceImage(sourceImageResult.message),
            )
        }

        val editOutput = when (
            val result = aiProvider.editPhoto(
                PhotoEditGenerationRequest(
                    editRequest = editRequest,
                    sourceImage = (sourceImageResult as SourceImageResult.Success).image,
                ),
            )
        ) {
            is AiProviderResult.Failure -> return PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.Provider(result.error),
            )
            is AiProviderResult.Success -> result.value
        }

        val saveResult = mediaSaver.save(
            bytes = editOutput.imageBytes,
            mimeType = editOutput.mimeType,
            originalMediaAsset = sourceMediaAsset,
            createdAtEpochMillis = now,
        )
        if (saveResult is SaveEditedImageResult.Failure) {
            return PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.FailedToSaveEditedImage(saveResult.message),
            )
        }

        val editedMediaAsset = (saveResult as SaveEditedImageResult.Success).mediaAsset
        val editResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-result-$idSuffix"),
            requestId = editRequest.id,
            draftId = draftId,
            editedMediaAsset = editedMediaAsset,
            summary = editOutput.summary,
            modelName = editOutput.modelName,
            createdAtEpochMillis = now,
        )
        val promptHistoryEntry = PromptHistoryEntry(
            id = PromptHistoryEntryId("prompt-photo-edit-$idSuffix"),
            draftId = draftId,
            operationType = AiOperationType.PhotoEdit,
            prompt = assembledPrompt,
            responseSummary = editOutput.summary,
            modelName = editOutput.modelName,
            createdAtEpochMillis = now,
        )

        val currentBeforeSave = repository.get(draftId) ?: return PhotoEditExecutionResult.Failure(
            PhotoEditExecutionError.DraftNotFound,
        )
        val saveStatus = photoEditStatus(currentBeforeSave) ?: return PhotoEditExecutionResult.Failure(
            PhotoEditExecutionError.InvalidDraftStatus(currentBeforeSave.status),
        )

        repository.save(editedMediaAsset)
        repository.savePhotoEditRequest(editRequest)
        repository.savePhotoEditResult(editResult)
        repository.savePromptHistoryEntry(promptHistoryEntry)
        repository.updateStatus(draftId, saveStatus, operationUpdatedAt(currentBeforeSave, now))

        val baseDraft = currentBeforeSave.transitionTo(saveStatus, operationUpdatedAt(currentBeforeSave, now))
        val updatedDraft = baseDraft.copy(
            photoEditRequests = currentBeforeSave.photoEditRequests + editRequest,
            photoEditResults = currentBeforeSave.photoEditResults + editResult,
            promptHistory = currentBeforeSave.promptHistory + promptHistoryEntry,
        )

        return PhotoEditExecutionResult.Success(
            photoEditRequest = editRequest,
            photoEditResult = editResult,
            assembledPrompt = assembledPrompt,
            promptHistoryEntry = promptHistoryEntry,
            updatedDraft = updatedDraft,
        )
    }

    private fun photoEditStatus(draft: PostDraft): DraftStatus? =
        when {
            draft.status == DraftStatus.PhotoEdited -> DraftStatus.PhotoEdited
            draft.status == DraftStatus.ReadyToShare -> DraftStatus.ReadyToShare
            draft.status.canTransitionTo(DraftStatus.PhotoEdited) -> DraftStatus.PhotoEdited
            else -> null
        }

    private fun nextIdSuffix(nowEpochMillis: Long): String =
        "$nowEpochMillis-${operationSequence.getAndIncrement()}"

    private fun operationUpdatedAt(draft: PostDraft, nowEpochMillis: Long): Instant {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        return if (now >= draft.updatedAt) now else draft.updatedAt
    }
}

sealed class SourceImageResult {
    data class Success(val image: AiImageInput) : SourceImageResult()
    data class Failure(val message: String) : SourceImageResult()
}

fun interface PhotoEditImageSource {
    fun load(mediaAsset: MediaAsset): SourceImageResult
}

sealed class SaveEditedImageResult {
    data class Success(val mediaAsset: MediaAsset) : SaveEditedImageResult()
    data class Failure(val message: String) : SaveEditedImageResult()
}

fun interface PhotoEditMediaSaver {
    fun save(
        bytes: ByteArray,
        mimeType: String,
        originalMediaAsset: MediaAsset,
        createdAtEpochMillis: Long,
    ): SaveEditedImageResult
}

sealed class PhotoEditExecutionResult {
    data class Success(
        val photoEditRequest: PhotoEditRequest,
        val photoEditResult: PhotoEditResult,
        val assembledPrompt: String,
        val promptHistoryEntry: PromptHistoryEntry,
        val updatedDraft: PostDraft,
    ) : PhotoEditExecutionResult()

    data class Failure(
        val error: PhotoEditExecutionError,
    ) : PhotoEditExecutionResult()
}

sealed class PhotoEditExecutionError {
    data object DraftNotFound : PhotoEditExecutionError()
    data class InvalidDraftStatus(val status: DraftStatus) : PhotoEditExecutionError()
    data class Provider(val error: AiError) : PhotoEditExecutionError()
    data class FailedToLoadSourceImage(val message: String) : PhotoEditExecutionError()
    data class FailedToSaveEditedImage(val message: String) : PhotoEditExecutionError()

    data class FailurePersisted(
        val photoEditRequest: PhotoEditRequest,
        val assembledPrompt: String,
        val promptHistoryEntry: PromptHistoryEntry,
        val updatedDraft: PostDraft,
        val cause: PhotoEditExecutionError,
    ) : PhotoEditExecutionError()
}