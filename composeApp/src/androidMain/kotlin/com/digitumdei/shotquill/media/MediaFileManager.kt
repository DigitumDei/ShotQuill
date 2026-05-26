package com.digitumdei.shotquill.media

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.digitumdei.shotquill.model.MediaCaptureResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MediaFileManager(
    private val contentResolver: ContentResolver,
    private val filesDir: File,
) {
    suspend fun importFromContentUri(contentUri: Uri): MediaCaptureResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val displayName = queryDisplayName(contentUri) ?: "img_${now}.jpg"
        val mimeType = contentResolver.getType(contentUri) ?: guessMimeType(displayName)
        val extension = mimeTypeToExtension(mimeType)
        val fileName = "img_${now}_gallery$extension"
        val destDir = File(filesDir, "media/originals/gallery").also { it.mkdirs() }
        val destFile = File(destDir, fileName)

        contentResolver.openInputStream(contentUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open input stream for $contentUri")

        val (width, height) = readImageDimensions(destFile.absolutePath)
        MediaCaptureResult(
            uri = destFile.absolutePath,
            mimeType = mimeType,
            widthPx = width,
            heightPx = height,
            createdAtEpochMillis = now,
        )
    }

    suspend fun handleCameraCapture(captureFile: File): MediaCaptureResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val mimeType = "image/jpeg"
        val (width, height) = readImageDimensions(captureFile.absolutePath)
        MediaCaptureResult(
            uri = captureFile.absolutePath,
            mimeType = mimeType,
            widthPx = width,
            heightPx = height,
            createdAtEpochMillis = now,
        )
    }

    fun createCameraCaptureFile(): File {
        val now = System.currentTimeMillis()
        val dir = File(filesDir, "media/originals/camera").also { it.mkdirs() }
        return File(dir, "img_${now}_camera.jpg")
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
            cursor.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readImageDimensions(filePath: String): Pair<Int?, Int?> {
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

    private fun guessMimeType(fileName: String): String {
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

    private fun mimeTypeToExtension(mimeType: String): String {
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
