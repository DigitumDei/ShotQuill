package com.digitumdei.shotquill.media

import com.digitumdei.shotquill.shared.ai.AiImageInput
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.workflow.VisionImageSource
import java.io.File

class FileVisionImageSource : VisionImageSource {
    override fun load(mediaAsset: MediaAsset): AiImageInput {
        val bytes = MediaFileManager.readMediaAssetBytes(mediaAsset)
        val file = File(mediaAsset.uri.removePrefix("file://"))
        return AiImageInput(
            bytes = bytes,
            mimeType = mediaAsset.mimeType ?: MediaFileManager.guessMimeType(file.name),
            fileName = file.name,
        )
    }
}
