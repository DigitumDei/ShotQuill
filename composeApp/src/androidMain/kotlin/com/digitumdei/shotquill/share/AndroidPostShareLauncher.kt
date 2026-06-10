package com.digitumdei.shotquill.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.digitumdei.shotquill.BuildConfig
import java.io.File

class AndroidPostShareLauncher(
    private val context: Context,
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
                val imageFile = File(imageUri.removePrefix("file://"))
                if (!imageFile.exists() || !imageFile.isFile) {
                    return null
                }
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    imageFile,
                )
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
}
