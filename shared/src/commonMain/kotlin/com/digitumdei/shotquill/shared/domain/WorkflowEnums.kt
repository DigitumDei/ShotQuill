package com.digitumdei.shotquill.shared.domain

enum class PostFormat {
    SingleImage,
    Carousel,
    Story,
}

enum class DraftStatus(val wireValue: String) {
    Draft("draft"),
    PhotoAdded("photo_added"),
    TextGenerated("text_generated"),
    PhotoEdited("photo_edited"),
    ReadyToShare("ready_to_share"),
    Shared("shared"),
    Archived("archived");

    fun canTransitionTo(next: DraftStatus): Boolean = next in (allowedTransitions[this] ?: emptySet())

    companion object {
        private val allowedTransitions = mapOf(
            Draft to setOf(PhotoAdded, Archived),
            PhotoAdded to setOf(TextGenerated, PhotoEdited, ReadyToShare, Archived),
            TextGenerated to setOf(PhotoEdited, ReadyToShare, Archived),
            PhotoEdited to setOf(TextGenerated, ReadyToShare, Archived),
            ReadyToShare to setOf(Shared, TextGenerated, PhotoEdited, Archived),
            Shared to setOf(Archived),
            Archived to emptySet(),
        )

        fun fromWireValue(value: String): DraftStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class TargetPlatform(val wireValue: String) {
    InstagramFeedSquare("instagram_feed_square"),
    InstagramPortrait("instagram_portrait"),
    InstagramStoryReel("instagram_story_reel"),
    FacebookPost("facebook_post"),
    BlueskyPost("bluesky_post"),
    Original("original");

    companion object {
        fun fromWireValue(value: String): TargetPlatform? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class MediaType(val wireValue: String) {
    Photo("photo"),
    EditedPhoto("edited_photo");

    companion object {
        fun fromWireValue(value: String): MediaType? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class EditIntent(val wireValue: String) {
    Enhance("enhance"),
    RemoveBackground("remove_background"),
    ReplaceBackground("replace_background"),
    Crop("crop"),
    ColorCorrect("color_correct"),
    RemoveObject("remove_object"),
    AddText("add_text");

    companion object {
        fun fromWireValue(value: String): EditIntent? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class RealismLevel(
    val wireValue: String,
    val promptIntent: String,
) {
    Photoreal(
        wireValue = "photoreal",
        promptIntent = "Preserve natural camera realism and avoid visibly generated or illustrated details.",
    ),
    Polished(
        wireValue = "polished",
        promptIntent = "Keep the image believable while improving composition, lighting, and presentation.",
    ),
    Stylized(
        wireValue = "stylized",
        promptIntent = "Allow a clearly art-directed look while retaining the user's subject and brand cues.",
    );

    companion object {
        fun fromWireValue(value: String): RealismLevel? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class QualityTier(
    val wireValue: String,
    val modelMappingNote: String,
    val costNote: String,
) {
    Draft(
        wireValue = "draft",
        modelMappingNote = "Mapped by the AI provider to the lowest-cost viable model and image size.",
        costNote = "Lowest per-call cost; intended for quick iteration.",
    ),
    Standard(
        wireValue = "standard",
        modelMappingNote = "Mapped by the AI provider to the default production model and image size.",
        costNote = "Balanced per-call cost and output quality.",
    ),
    High(
        wireValue = "high",
        modelMappingNote = "Mapped by the AI provider to the highest-quality configured model and image size.",
        costNote = "Highest per-call cost; intended for final assets.",
    );

    companion object {
        fun fromWireValue(value: String): QualityTier? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class AiOperationType(val wireValue: String) {
    VisionDescription("vision_description"),
    CaptionGeneration("caption_generation"),
    AltTextGeneration("alt_text_generation"),
    PhotoEdit("photo_edit");

    companion object {
        fun fromWireValue(value: String): AiOperationType? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class ExportStatus(val wireValue: String) {
    Pending("pending"),
    Exported("exported"),
    Failed("failed"),
    Cancelled("cancelled");

    companion object {
        fun fromWireValue(value: String): ExportStatus? = entries.firstOrNull { it.wireValue == value }
    }
}
