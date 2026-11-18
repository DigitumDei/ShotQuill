package com.digitumdei.shotquill.media

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Copies a picked logo into the app's own storage so it stays available after the picker closes. */
class LogoImporter(
    private val contentResolver: ContentResolver,
    private val filesDir: File,
) {
    suspend fun import(contentUri: Uri): String = withContext(Dispatchers.IO) {
        val mimeType = contentResolver.getType(contentUri) ?: "image/png"
        val directory = File(filesDir, "brand")
        if (!directory.mkdirs() && !directory.exists()) {
            error("Failed to create directory: $directory")
        }
        val file = File(directory, "logo_${System.currentTimeMillis()}${MediaFileManager.mimeTypeToExtension(mimeType)}")
        contentResolver.openInputStream(contentUri)?.use { input ->
            try {
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        } ?: error("Unable to open the chosen image")
        "file://${file.absolutePath}"
    }
}
