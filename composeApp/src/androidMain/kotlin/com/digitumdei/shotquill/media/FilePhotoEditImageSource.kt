package com.digitumdei.shotquill.media

import com.digitumdei.shotquill.shared.ai.AiImageInput
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.workflow.PhotoEditImageSource
import com.digitumdei.shotquill.shared.workflow.SourceImageResult
import java.io.File

class FilePhotoEditImageSource : PhotoEditImageSource {
    override fun load(mediaAsset: MediaAsset): SourceImageResult {
        return try {
            val file = File(mediaAsset.uri.removePrefix("file://"))
            if (!file.exists()) {
                return SourceImageResult.Failure("Source file not found: ${file.absolutePath}")
            }
            SourceImageResult.Success(
                AiImageInput(
                    bytes = file.readBytes(),
                    mimeType = mediaAsset.mimeType ?: MediaFileManager.guessMimeType(file.name),
                    fileName = file.name,
                ),
            )
        } catch (e: Exception) {
            SourceImageResult.Failure(e.message ?: "Unknown error loading source image")
        }
    }
}