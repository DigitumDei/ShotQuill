package com.digitumdei.shotquill.shared.domain

import kotlinx.datetime.Instant

@JvmInline
value class PostDraftId(val value: String)

@JvmInline
value class MediaAssetId(val value: String)

enum class PostFormat {
    SingleImage,
    Carousel,
    Story,
}

/** Where a post has got to. A removed post is kept, hidden, until it is cleared from Settings. */
enum class DraftStatus(val wireValue: String) {
    PhotoAdded("photo_added"),
    Shared("shared"),
    Archived("archived");

    companion object {
        fun fromWireValue(value: String): DraftStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

/** A post as the list of posts needs it, without loading its photo's details. */
data class DraftSummary(
    val id: PostDraftId,
    val status: DraftStatus,
    val captionText: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val primaryMediaUri: String?,
)

/** A post as it is stored: its photo and its words. */
data class PostDraft(
    val id: PostDraftId,
    val format: PostFormat,
    val status: DraftStatus,
    val mediaItems: List<PostMediaItem>,
    val caption: String? = null,
    val altText: String? = null,
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
}

data class PostMediaItem(
    val mediaAsset: MediaAsset,
    val order: Int,
) {
    init {
        require(order >= 0) { "Post media item order must be zero or greater" }
    }
}

/** A photo on the phone. */
data class MediaAsset(
    val id: MediaAssetId,
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
