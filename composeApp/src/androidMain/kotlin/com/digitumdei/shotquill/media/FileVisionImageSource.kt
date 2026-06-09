package com.digitumdei.shotquill.media

import com.digitumdei.shotquill.shared.ai.AiImageInput
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.workflow.SourceImageResult
import com.digitumdei.shotquill.shared.workflow.VisionImageSource
import java.io.File

class FileVisionImageSource : VisionImageSource {
    override fun load(mediaAsset: MediaAsset): SourceImageResult {
        if (!mediaAsset.uri.startsWith("file://")) {
            return SourceImageResult.Failure(
                "Unsupported URI scheme for vision image source: expected file:// but got " +
                    mediaAsset.uri.substringBefore("://", "").ifEmpty { mediaAsset.uri },
            )
        }
        if (mediaAsset.uri == "file://") {
            return SourceImageResult.Failure(
                "Vision image source URI is missing a file path: ${mediaAsset.uri}",
            )
        }
        return try {
            val bytes = MediaFileManager.readMediaAssetBytes(mediaAsset)
            val file = File(mediaAsset.uri.removePrefix("file://"))
            SourceImageResult.Success(
                AiImageInput(
                    bytes = bytes,
                    mimeType = mediaAsset.mimeType ?: MediaFileManager.guessMimeType(file.name),
                    fileName = file.name,
                ),
            )
        } catch (e: Exception) {
            SourceImageResult.Failure(e.message ?: "Unknown error loading vision image")
        }
    }
}
