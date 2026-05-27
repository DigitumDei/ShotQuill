package com.digitumdei.shotquill.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.digitumdei.shotquill.model.MediaCaptureResult
import com.digitumdei.shotquill.shared.domain.EpochClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import java.io.File
import java.io.FileOutputStream

class ContentResolverMediaImporter(
    private val contentResolver: ContentResolver,
    private val filesDir: File,
    private val clock: EpochClock = EpochClock.Default,
) {
    suspend fun importFromContentUri(contentUri: Uri): MediaCaptureResult = withContext(Dispatchers.IO) {
        val now = clock.nowMillis()
        val displayName = queryDisplayName(contentUri) ?: "img_${now}.jpg"
        val mimeType = contentResolver.getType(contentUri) ?: guessMimeType(displayName)
        val extension = MediaFileManager.mimeTypeToExtension(mimeType)
        val suffix = Random.nextInt(100000, 999999)
        val fileName = "img_${now}_gallery_${suffix}$extension"
        val destDir = File(filesDir, "media/originals/gallery")
        if (!destDir.mkdirs() && !destDir.exists()) {
            error("Failed to create directory: $destDir")
        }
        val destFile = File(destDir, fileName)

        contentResolver.openInputStream(contentUri)?.use { input ->
            try {
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } catch (e: Exception) {
                destFile.delete()
                throw e
            }
        } ?: error("Unable to open input stream for $contentUri")

        val (width, height) = MediaFileManager.readImageDimensions(destFile.absolutePath)
        MediaCaptureResult(
            uri = "file://${destFile.absolutePath}",
            mimeType = mimeType,
            widthPx = width,
            heightPx = height,
            createdAtEpochMillis = now,
        )
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

    private fun guessMimeType(fileName: String): String = MediaFileManager.guessMimeType(fileName)
}
