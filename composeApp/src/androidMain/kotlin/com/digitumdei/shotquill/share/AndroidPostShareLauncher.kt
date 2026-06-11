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
    override fun share(imageUri: String?, text: String): Boolean {
        return try {
            val chooser = buildChooserIntent(imageUri, text) ?: return false
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildChooserIntent(imageUri: String?, text: String): Intent? {
        val shareIntent =
            if (imageUri != null) {
                val imageFile = resolveShareImageFile(imageUri) ?: return null
                if (!imageFile.exists() || !imageFile.isFile) {
                    return null
                }
                val contentUri = contentUriForFile(imageFile)
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }
        return Intent.createChooser(shareIntent, null)
    }

    private fun resolveShareImageFile(imageUri: String): File? {
        val parsedUri = Uri.parse(imageUri)
        if (parsedUri.scheme != "file") {
            return null
        }

        val decodedPath = try {
            parsedUri.path
        } catch (_: Exception) {
            parsedUri.encodedPath?.let { Uri.decode(it) }
        }

        if (decodedPath.isNullOrBlank()) {
            return null
        }

        return File(decodedPath)
    }
}
