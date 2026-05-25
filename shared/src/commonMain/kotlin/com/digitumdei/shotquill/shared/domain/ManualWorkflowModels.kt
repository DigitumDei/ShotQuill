package com.digitumdei.shotquill.shared.domain

import kotlinx.datetime.Instant

data class PostDraft(
    val id: PostDraftId,
    val format: PostFormat,
    val status: DraftStatus,
    val mediaItems: List<PostMediaItem>,
    val caption: CaptionDraft?,
    val targetPlatforms: Set<TargetPlatform>,
    val brandProfile: BrandProfile?,
    val visionDescription: VisionDescription?,
    val captionRequests: List<CaptionRequest>,
    val captionResults: List<CaptionResult>,
    val altTextResults: List<AltTextResult>,
    val photoEditRequests: List<PhotoEditRequest>,
    val photoEditResults: List<PhotoEditResult>,
    val promptHistory: List<PromptHistoryEntry>,
    val exportRecords: List<ExportRecord>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(mediaItems.isNotEmpty()) { "Post draft must include at least one media item" }
        require(mediaItems.map { it.order }.toSet().size == mediaItems.size) {
            "Post draft media item orders must be unique"
        }
        require(format != PostFormat.SingleImage || mediaItems.size == 1) {
            "Single image post drafts must include exactly one media item"
        }
    }

    fun transitionTo(next: DraftStatus, updatedAt: Instant): PostDraft {
        require(status.canTransitionTo(next)) {
            "Cannot transition post draft from ${status.wireValue} to ${next.wireValue}"
        }
        return copy(status = next, updatedAt = updatedAt)
    }
}

fun PostDraft.primaryMediaAsset(): MediaAsset =
    mediaItems.minBy { it.order }.mediaAsset

data class PostMediaItem(
    val mediaAsset: MediaAsset,
    val order: Int,
) {
    init {
        require(order >= 0) { "Post media item order must be zero or greater" }
    }
}

data class MediaAsset(
    val id: MediaAssetId,
    val type: MediaType,
    val uri: String,
    val mimeType: String?,
    val widthPx: Int?,
    val heightPx: Int?,
    val createdAtEpochMillis: Long,
)

data class CaptionDraft(
    val text: String,
    val hashtags: List<String>,
)

data class VisionDescription(
    val id: VisionDescriptionId,
    val draftId: PostDraftId,
    val mediaAssetId: MediaAssetId,
    val description: String,
    val modelName: String?,
    val createdAtEpochMillis: Long,
)

data class CaptionRequest(
    val id: CaptionRequestId,
    val draftId: PostDraftId,
    val targetPlatform: TargetPlatform,
    val prompt: String,
    val tone: String?,
    val brandProfileId: BrandProfileId?,
    val createdAtEpochMillis: Long,
)

data class CaptionResult(
    val id: CaptionResultId,
    val requestId: CaptionRequestId,
    val draftId: PostDraftId,
    val targetPlatform: TargetPlatform,
    val caption: String,
    val hashtags: List<String>,
    val modelName: String?,
    val createdAtEpochMillis: Long,
)

data class AltTextResult(
    val id: AltTextResultId,
    val draftId: PostDraftId,
    val mediaAssetId: MediaAssetId,
    val altText: String,
    val modelName: String?,
    val createdAtEpochMillis: Long,
)

data class PhotoEditRequest(
    val id: PhotoEditRequestId,
    val draftId: PostDraftId,
    val sourceMediaAssetId: MediaAssetId,
    val intent: EditIntent,
    val realismLevel: RealismLevel,
    val qualityTier: QualityTier,
    val prompt: String,
    val createdAtEpochMillis: Long,
)

data class PhotoEditResult(
    val id: PhotoEditResultId,
    val requestId: PhotoEditRequestId,
    val draftId: PostDraftId,
    val editedMediaAsset: MediaAsset,
    val summary: String?,
    val modelName: String?,
    val createdAtEpochMillis: Long,
)

data class PromptHistoryEntry(
    val id: PromptHistoryEntryId,
    val draftId: PostDraftId,
    val operationType: AiOperationType,
    val prompt: String,
    val responseSummary: String?,
    val modelName: String?,
    val createdAtEpochMillis: Long,
)

data class ExportRecord(
    val id: ExportRecordId,
    val draftId: PostDraftId,
    val targetPlatform: TargetPlatform,
    val status: ExportStatus,
    val destinationUri: String?,
    val errorMessage: String?,
    val createdAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
)

data class BrandProfile(
    val id: BrandProfileId,
    val displayName: String,
    val voice: String,
    val audience: String?,
    val defaultHashtags: List<String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
