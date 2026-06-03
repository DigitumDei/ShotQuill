package com.digitumdei.shotquill.media

import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.workflow.PhotoEditMediaSaver
import com.digitumdei.shotquill.shared.workflow.SaveEditedImageResult
import java.io.File
import java.io.FileOutputStream

class AndroidPhotoEditMediaSaver(
    private val filesDir: File,
) : PhotoEditMediaSaver {
    override fun save(
        bytes: ByteArray,
        mimeType: String,
        originalMediaAsset: MediaAsset,
        mediaAssetId: MediaAssetId,
        createdAtEpochMillis: Long,
    ): SaveEditedImageResult {
        val extension = MediaFileManager.mimeTypeToExtension(mimeType)
        val destDir = File(filesDir, "media/edited")
        if (!destDir.mkdirs() && !destDir.exists()) {
            return SaveEditedImageResult.Failure("Failed to create directory: $destDir")
        }
        val fileName = "${mediaAssetId.value}$extension"
        val destFile = File(destDir, fileName)
        return try {
            FileOutputStream(destFile).use { output ->
                output.write(bytes)
            }
            val (width, height) = MediaFileManager.readImageDimensions(destFile.absolutePath)
            SaveEditedImageResult.Success(
                MediaAsset(
                    id = mediaAssetId,
                    type = MediaType.EditedPhoto,
                    uri = "file://${destFile.absolutePath}",
                    mimeType = mimeType,
                    widthPx = width,
                    heightPx = height,
                    createdAtEpochMillis = createdAtEpochMillis,
                ),
            )
        } catch (e: Exception) {
            destFile.delete()
            SaveEditedImageResult.Failure(e.message ?: "Unknown error saving edited image")
        }
    }
}