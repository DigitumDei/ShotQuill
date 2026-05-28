package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.PhotoEditRequest
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.TargetPlatform

interface AiProvider {
    fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput>
    fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput>
    fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput>
    fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput>
}

data class AiImageInput(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
) {
    init {
        require(bytes.isNotEmpty()) { "Image bytes cannot be empty" }
        require(mimeType.isNotBlank()) { "Image MIME type cannot be blank" }
        require(fileName.isNotBlank()) { "Image file name cannot be blank" }
    }

    override fun equals(other: Any?): Boolean =
        other is AiImageInput &&
            bytes.contentEquals(other.bytes) &&
            mimeType == other.mimeType &&
            fileName == other.fileName

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }
}

data class VisionDescriptionRequest(
    val draftId: PostDraftId,
    val mediaAssetId: MediaAssetId,
    val image: AiImageInput,
    val prompt: String,
) {
    init {
        require(prompt.isNotBlank()) { "Vision prompt cannot be blank" }
    }
}

data class CaptionGenerationRequest(
    val draftId: PostDraftId,
    val targetPlatform: TargetPlatform,
    val prompt: String,
) {
    init {
        require(prompt.isNotBlank()) { "Caption prompt cannot be blank" }
    }
}

data class AltTextGenerationRequest(
    val draftId: PostDraftId,
    val mediaAssetId: MediaAssetId,
    val prompt: String,
) {
    init {
        require(prompt.isNotBlank()) { "Alt text prompt cannot be blank" }
    }
}

data class PhotoEditGenerationRequest(
    val editRequest: PhotoEditRequest,
    val sourceImage: AiImageInput,
    val maskImage: AiImageInput? = null,
)

data class VisionDescriptionOutput(
    val description: String,
    val modelName: String?,
)

data class CaptionGenerationOutput(
    val caption: String,
    val shortCaption: String?,
    val hashtags: List<String>,
    val modelName: String?,
)

data class AltTextGenerationOutput(
    val altText: String,
    val modelName: String?,
)

data class PhotoEditOutput(
    val imageBytes: ByteArray,
    val mimeType: String,
    val summary: String?,
    val modelName: String?,
) {
    init {
        require(imageBytes.isNotEmpty()) { "Edited image bytes cannot be empty" }
        require(mimeType.isNotBlank()) { "Edited image MIME type cannot be blank" }
    }

    override fun equals(other: Any?): Boolean =
        other is PhotoEditOutput &&
            imageBytes.contentEquals(other.imageBytes) &&
            mimeType == other.mimeType &&
            summary == other.summary &&
            modelName == other.modelName

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (summary?.hashCode() ?: 0)
        result = 31 * result + (modelName?.hashCode() ?: 0)
        return result
    }
}
