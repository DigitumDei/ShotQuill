package com.digitumdei.shotquill.media

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import com.digitumdei.shotquill.image.SvgLogo
import com.digitumdei.shotquill.image.looksLikeSvg
import com.digitumdei.shotquill.image.svgCautions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class ImportedLogo(
    /** A file URI inside the app's own storage. */
    val uri: String,
    /** Something worth knowing about the file, such as live text in an SVG. Null when there is nothing to say. */
    val caution: String?,
)

class LogoImportException(message: String) : Exception(message)

/**
 * Copies a picked logo into the app's own storage so it stays available after
 * the picker closes, and checks that it can actually be drawn. SVG files are
 * kept as SVG; nothing is converted.
 */
class LogoImporter(
    private val contentResolver: ContentResolver,
    private val filesDir: File,
) {
    suspend fun import(contentUri: Uri): ImportedLogo = withContext(Dispatchers.IO) {
        val bytes = contentResolver.openInputStream(contentUri)?.use { input ->
            val collected = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (collected.size() <= MaxBytes) {
                val read = input.read(buffer)
                if (read < 0) break
                collected.write(buffer, 0, read)
            }
            collected.toByteArray()
        } ?: throw LogoImportException("The chosen file could not be opened.")
        if (bytes.size > MaxBytes) throw LogoImportException("That file is too large to use as a logo.")

        val isSvg = looksLikeSvg(bytes)
        val extension = if (isSvg) {
            ".svg"
        } else {
            MediaFileManager.mimeTypeToExtension(contentResolver.getType(contentUri) ?: "image/png")
        }
        val directory = File(filesDir, "brand")
        if (!directory.mkdirs() && !directory.exists()) {
            throw LogoImportException("There is nowhere to keep the logo.")
        }
        val file = File(directory, "logo_${System.currentTimeMillis()}$extension")
        file.writeBytes(bytes)

        val usable = if (isSvg) SvgLogo.load(file) != null else isReadableBitmap(file)
        if (!usable) {
            file.delete()
            throw LogoImportException(
                if (isSvg) {
                    "This SVG could not be read. In Inkscape, save it as Plain SVG with the page resized to the artwork."
                } else {
                    "That file is not an image the app can use. Choose an SVG or a PNG."
                },
            )
        }
        ImportedLogo(
            uri = "file://${file.absolutePath}",
            caution = if (isSvg) svgCautions(bytes.decodeToString()) else null,
        )
    }

    private fun isReadableBitmap(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private companion object {
        const val MaxBytes = 20 * 1024 * 1024
    }
}
