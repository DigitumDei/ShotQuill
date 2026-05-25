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

    fun canTransitionTo(next: DraftStatus): Boolean = next in allowedTransitions.getValue(this)

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
    Instagram("instagram"),
    Facebook("facebook"),
    LinkedIn("linkedin"),
    X("x"),
    TikTok("tiktok"),
    Threads("threads"),
    Pinterest("pinterest");

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

enum class RealismLevel(val wireValue: String) {
    Natural("natural"),
    Polished("polished"),
    Stylized("stylized");

    companion object {
        fun fromWireValue(value: String): RealismLevel? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class QualityTier(val wireValue: String) {
    Draft("draft"),
    Standard("standard"),
    High("high");

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
