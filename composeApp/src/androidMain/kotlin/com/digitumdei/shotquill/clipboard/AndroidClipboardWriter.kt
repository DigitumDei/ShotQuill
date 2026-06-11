package com.digitumdei.shotquill.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class AndroidClipboardWriter(
    context: Context,
) : ClipboardWriter {
    private val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun copy(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)
    }
}
