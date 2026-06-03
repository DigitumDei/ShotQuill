package com.digitumdei.shotquill.media

import android.graphics.BitmapFactory
import com.digitumdei.shotquill.model.MediaCaptureResult
import com.digitumdei.shotquill.shared.domain.MediaAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import java.io.File

class MediaFileManager(
    private val filesDir: File,
) {
    suspend fun handleCameraCapture(captureFile: File): MediaCaptureResult = withContext(Dispatchers.IO) {
        val fileTime = captureFile.lastModified()
        val createdAtEpochMillis = if (fileTime > 0L) fileTime else System.currentTimeMillis()
        val mimeType = "image/jpeg"
        val (width, height) = readImageDimensions(captureFile.absolutePath)
        MediaCaptureResult(
            uri = "file://${captureFile.absolutePath}",
            mimeType = mimeType,
            widthPx = width,
            heightPx = height,
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }

    fun createCameraCaptureFile(): File {
        val now = System.currentTimeMillis()
        val suffix = Random.nextInt(100000, 999999)
        val dir = File(filesDir, "media/originals/camera")
        if (!dir.mkdirs() && !dir.exists()) {
            error("Failed to create directory: $dir")
        }
        return File(dir, "img_${now}_camera_${suffix}.jpg")
    }

    companion object {
        fun readImageDimensions(filePath: String): Pair<Int?, Int?> {
            return try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(filePath, options)
                Pair(options.outWidth.takeIf { it > 0 }, options.outHeight.takeIf { it > 0 })
            } catch (_: Exception) {
                Pair(null, null)
            }
        }

        fun readMediaAssetBytes(mediaAsset: MediaAsset): ByteArray {
            val file = File(mediaAsset.uri.removePrefix("file://"))
            require(file.exists()) { "Source file not found: ${file.absolutePath}" }
            return file.readBytes()
        }

        fun guessMimeType(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                "heic", "heif" -> "image/heic"
                else -> "image/jpeg"
            }
        }

        fun mimeTypeToExtension(mimeType: String): String {
            val mt = mimeType.lowercase()
            return when {
                mt.contains("png") -> ".png"
                mt.contains("webp") -> ".webp"
                mt.contains("gif") -> ".gif"
                mt.contains("bmp") -> ".bmp"
                mt.contains("heic") -> ".heic"
                mt.contains("heif") -> ".heif"
                else -> ".jpg"
            }
        }
    }
}
