package com.digitumdei.shotquill.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.digitumdei.shotquill.BuildConfig
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
        var resolvedDestinationUri: String? = null
        return try {
            if (imageUri != null) {
                val imageFile = resolveShareImageFile(imageUri)
                if (imageFile == null) {
                    return ShareResult(
                        success = false,
                        errorMessage = if (!imageUri.startsWith("file://")) {
                            "Image URI does not reference a local file: $imageUri"
                        } else {
                            "Unable to resolve image file: $imageUri"
                        },
                    )
                }
                val contentUri = contentUriForFile(imageFile)
                resolvedDestinationUri = contentUri.toString()
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, null))
                ShareResult(success = true, destinationUri = resolvedDestinationUri)
            } else {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(shareIntent, null))
                ShareResult(success = true, destinationUri = null)
            }
        } catch (e: Exception) {
            ShareResult(
                success = false,
                destinationUri = resolvedDestinationUri,
                errorMessage = e.message?.let { "Unable to open share sheet: $it" }
                    ?: "Unable to open share sheet",
            )
        }
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
            if (file.exists() && file.isFile) return file
        }

        val rawPath = imageUri.removePrefix("file://")
        if (rawPath.isNotBlank()) {
            val file = File(rawPath)
            if (file.exists() && file.isFile) return file

            val decodedPath = try {
                URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                null
            }
            if (decodedPath != null && decodedPath != rawPath) {
                val decodedFile = File(decodedPath)
                if (decodedFile.exists() && decodedFile.isFile) return decodedFile
            }
        }

        return null
    }
}
