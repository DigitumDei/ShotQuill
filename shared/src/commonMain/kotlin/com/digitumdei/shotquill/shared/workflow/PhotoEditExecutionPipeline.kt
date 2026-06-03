package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.ai.AiError
import com.digitumdei.shotquill.shared.ai.AiImageInput
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.PhotoEditRequest
import com.digitumdei.shotquill.shared.domain.PhotoEditResult
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntry

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
    data object MissingApiKey : PhotoEditExecutionError()
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