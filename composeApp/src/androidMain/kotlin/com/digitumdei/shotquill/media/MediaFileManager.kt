package com.digitumdei.shotquill.media

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.digitumdei.shotquill.model.MediaCaptureResult
import java.io.File
import java.io.FileOutputStream

class MediaFileManager(
    private val contentResolver: ContentResolver,
    private val filesDir: File,
) {
    fun importFromContentUri(contentUri: Uri): MediaCaptureResult {
        val now = System.currentTimeMillis()
        val displayName = queryDisplayName(contentUri) ?: "img_${now}.jpg"
        val mimeType = contentResolver.getType(contentUri) ?: guessMimeType(displayName)
        val extension = mimeTypeToExtension(mimeType)
        val fileName = "img_${now}_gallery$extension"
        val destDir = File(filesDir, "gallery_imports").also { it.mkdirs() }
        val destFile = File(destDir, fileName)

        contentResolver.openInputStream(contentUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open input stream for $contentUri")

        val (width, height) = readImageDimensions(destFile.absolutePath)
        return MediaCaptureResult(
            uri = destFile.toURI().toString(),
            mimeType = mimeType,
            widthPx = width,
            heightPx = height,
            createdAtEpochMillis = now,
        )
    }

    fun handleCameraCapture(captureFile: File): MediaCaptureResult {
        val now = System.currentTimeMillis()
        val mimeType = "image/jpeg"
        val (width, height) = readImageDimensions(captureFile.absolutePath)
        return MediaCaptureResult(
            uri = captureFile.toURI().toString(),
            mimeType = mimeType,
            widthPx = width,
            heightPx = height,
            createdAtEpochMillis = now,
        )
    }

    fun createCameraCaptureFile(): File {
        val now = System.currentTimeMillis()
        val dir = File(filesDir, "camera_captures").also { it.mkdirs() }
        return File(dir, "img_${now}_camera.jpg")
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        return cursor.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) else null
            } else null
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

    private fun mimeTypeToExtension(mimeType: String): String = when {
        mimeType.contains("png") -> ".png"
        mimeType.contains("webp") -> ".webp"
        mimeType.contains("gif") -> ".gif"
        mimeType.contains("bmp") -> ".bmp"
        mimeType.contains("heic") -> ".heic"
        mimeType.contains("heif") -> ".heif"
        else -> ".jpg"
    }
}
