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
import com.digitumdei.shotquill.shared.domain.MediaAssetId
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
        val sourceMediaAsset = currentDraft.mediaItems.firstOrNull { it.mediaAsset.id == visionDescription.mediaAssetId }?.mediaAsset
            ?: currentDraft.photoEditResults.firstOrNull { it.editedMediaAsset.id == visionDescription.mediaAssetId }?.editedMediaAsset
            ?: return PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.DraftNotFound,
            )

        val now = clock.nowMillis()
        val idSuffix = nextIdSuffix(now)
        val cleanedUserRefinement = userRefinement?.trim()?.takeIf { it.isNotEmpty() }
        val assembledPrompt = PhotoEditPromptAssembler.buildPrompt(
            intent = intent,
            userPrompt = prompt,
            realismLevel = realismLevel,
            qualityTier = qualityTier,
            targetPlatform = targetPlatform,
            maskRegion = maskRegion,
            subjectDescription = visionDescription.description,
            userRefinement = cleanedUserRefinement,
        )
        val editRequest = PhotoEditRequest(
            id = PhotoEditRequestId("photo-edit-request-$idSuffix"),
            draftId = draftId,
            sourceMediaAssetId = sourceMediaAsset.id,
            intent = intent,
            realismLevel = realismLevel,
            qualityTier = qualityTier,
            prompt = assembledPrompt,
            userRefinement = cleanedUserRefinement,
            subjectDescription = visionDescription.description,
            targetPlatform = targetPlatform,
            maskRegion = maskRegion,
            createdAtEpochMillis = now,
        )

        fun failureSummary(cause: PhotoEditExecutionError): String = when (cause) {
            is PhotoEditExecutionError.Provider -> cause.error.userMessage
            is PhotoEditExecutionError.FailedToLoadSourceImage -> cause.message
            is PhotoEditExecutionError.FailedToSaveEditedImage -> cause.message
            is PhotoEditExecutionError.FailurePersisted -> "Previous attempt failed"
            is PhotoEditExecutionError.DraftNotFound -> "Draft not found"
            is PhotoEditExecutionError.InvalidDraftStatus -> "Invalid draft status: ${cause.status.wireValue}"
        }

        fun persistFailure(cause: PhotoEditExecutionError): PhotoEditExecutionResult.Failure {
            val failureEntry = PromptHistoryEntry(
                id = PromptHistoryEntryId("prompt-photo-edit-failure-$idSuffix"),
                draftId = draftId,
                operationType = AiOperationType.PhotoEdit,
                prompt = assembledPrompt,
                responseSummary = failureSummary(cause),
                modelName = null,
                createdAtEpochMillis = now,
            )
            val updatedAt = operationUpdatedAt(currentDraft, now)
            val persistedDraft = repository.savePhotoEditFailure(
                draftId = draftId,
                editRequest = editRequest,
                promptHistoryEntry = failureEntry,
                updatedAt = updatedAt,
            ) ?: return PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.DraftNotFound,
            )
            return PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.FailurePersisted(
                    photoEditRequest = editRequest,
                    assembledPrompt = assembledPrompt,
                    promptHistoryEntry = failureEntry,
                    updatedDraft = persistedDraft,
                    cause = cause,
                ),
            )
        }

        val sourceImageResult = imageSource.load(sourceMediaAsset)
        if (sourceImageResult is SourceImageResult.Failure) {
            return persistFailure(
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
            is AiProviderResult.Failure -> return persistFailure(
                PhotoEditExecutionError.Provider(result.error),
            )
            is AiProviderResult.Success -> result.value
        }

        val editedMediaAssetId = MediaAssetId("photo-edited-$idSuffix")
        val saveResult = mediaSaver.save(
            bytes = editOutput.imageBytes,
            mimeType = editOutput.mimeType,
            originalMediaAsset = sourceMediaAsset,
            mediaAssetId = editedMediaAssetId,
            createdAtEpochMillis = now,
        )
        if (saveResult is SaveEditedImageResult.Failure) {
            return persistFailure(
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
        val targetStatus = photoEditStatus(currentBeforeSave) ?: return PhotoEditExecutionResult.Failure(
            PhotoEditExecutionError.InvalidDraftStatus(currentBeforeSave.status),
        )

        val updatedAt = operationUpdatedAt(currentBeforeSave, now)
        val persistedDraft = repository.savePhotoEditSuccess(
            draftId = draftId,
            editedMediaAsset = editedMediaAsset,
            editRequest = editRequest,
            editResult = editResult,
            promptHistoryEntry = promptHistoryEntry,
            targetStatus = targetStatus,
            updatedAt = updatedAt,
        ) ?: return PhotoEditExecutionResult.Failure(
            PhotoEditExecutionError.DraftNotFound,
        )

        return PhotoEditExecutionResult.Success(
            photoEditRequest = editRequest,
            photoEditResult = editResult,
            assembledPrompt = assembledPrompt,
            promptHistoryEntry = promptHistoryEntry,
            updatedDraft = persistedDraft,
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
        mediaAssetId: MediaAssetId,
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