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
        require(updatedAt >= createdAt) {
            "Post draft updatedAt must be after or equal to createdAt"
        }
    }

    fun transitionTo(next: DraftStatus, updatedAt: Instant): PostDraft {
        require(status.canTransitionTo(next)) {
            "Cannot transition post draft from ${status.wireValue} to ${next.wireValue}"
        }
        require(updatedAt >= this.updatedAt) {
            "Transition updatedAt must be after or equal to current updatedAt"
        }
        return copy(status = next, updatedAt = updatedAt)
    }
}

fun PostDraft.primaryMediaAsset(): MediaAsset =
    mediaItems.minByOrNull { it.order }?.mediaAsset
        ?: error("Post draft must include at least one media item")

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
) {
    init {
        require(widthPx == null || widthPx > 0) { "widthPx must be greater than zero" }
        require(heightPx == null || heightPx > 0) { "heightPx must be greater than zero" }
        require(createdAtEpochMillis >= 0) { "createdAtEpochMillis must be non-negative" }
    }
}

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
) {
    init {
        require(completedAtEpochMillis == null || completedAtEpochMillis >= createdAtEpochMillis) {
            "completedAtEpochMillis must be after or equal to createdAtEpochMillis"
        }
    }
}

data class BrandProfile(
    val id: BrandProfileId,
    val displayName: String,
    val voice: String,
    val audience: String?,
    val defaultHashtags: List<String>,
    val imageAssets: List<BrandImageAsset>,
    val websiteOrSocialLinks: List<String>,
    val visualStyleNotes: String?,
    val productNamingNotes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
        require(voice.isNotBlank()) { "voice cannot be blank" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "updatedAtEpochMillis must be after or equal to createdAtEpochMillis"
        }
    }
}

data class BrandImageAsset(
    val mediaAsset: MediaAsset,
    val title: String,
    val description: String?,
) {
    init {
        require(title.isNotBlank()) { "Brand image asset title cannot be blank" }
    }
}
