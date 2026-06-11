package com.digitumdei.shotquill.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.digitumdei.shotquill.BuildConfig
import java.io.File

class AndroidPostShareLauncher(
    private val context: Context,
    private val contentUriForFile: (File) -> Uri = { imageFile ->
        FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            imageFile,
        )
    },
) : PostShareLauncher {
    override fun share(imageUri: String?, text: String): ShareResult {
        return try {
            val handoff = buildShareHandoff(imageUri, text) ?: return ShareResult(
                success = false,
                errorMessage = "Unable to build share intent",
            )
            context.startActivity(handoff.intent)
            ShareResult(success = true, destinationUri = handoff.destinationUri)
        } catch (e: Exception) {
            ShareResult(success = false, errorMessage = e.message ?: "Unable to open share sheet")
        }
    }

    private data class ShareHandoff(
        val intent: Intent,
        val destinationUri: String?,
    )

    private fun buildShareHandoff(imageUri: String?, text: String): ShareHandoff? {
        if (imageUri != null) {
            val imageFile = resolveShareImageFile(imageUri) ?: return null
            if (!imageFile.exists() || !imageFile.isFile) {
                return null
            }
            val contentUri = contentUriForFile(imageFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return ShareHandoff(
                intent = Intent.createChooser(shareIntent, null),
                destinationUri = contentUri.toString(),
            )
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return ShareHandoff(
            intent = Intent.createChooser(shareIntent, null),
            destinationUri = null,
        )
    }

    private fun resolveShareImageFile(imageUri: String): File? {
        if (!imageUri.startsWith("file://")) {
            return null
        }

        val parsedUri = Uri.parse(imageUri)

        val uriPath = try {
            parsedUri.path
        } catch (_: Exception) {
            null
        }
        if (!uriPath.isNullOrBlank()) {
            val file = File(uriPath)
            if (file.exists()) return file
        }

        val rawPath = imageUri.removePrefix("file://")
        if (rawPath.isNotBlank()) {
            val file = File(rawPath)
            if (file.exists()) return file
        }

        return null
    }
}
