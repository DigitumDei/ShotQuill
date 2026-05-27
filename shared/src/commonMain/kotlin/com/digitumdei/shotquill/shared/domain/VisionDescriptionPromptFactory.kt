package com.digitumdei.shotquill.shared.domain

object VisionDescriptionPromptFactory {
    fun buildPrompt(mediaAsset: MediaAsset): String =
        buildString {
            appendLine("Describe this imported photo for later social caption and edit prompts.")
            appendLine("Keep the description concise, factual, and grounded only in visible evidence.")
            appendLine()
            appendLine("Capture these details:")
            appendLine("- Main subject")
            appendLine("- Setting and background")
            appendLine("- Visible text or logos")
            appendLine("- Important details to preserve")
            appendLine("- Mood and context")
            appendLine("- Crop or framing notes")
            appendLine()
            appendLine("Return 3 to 6 short sentences. Do not invent brand names, locations, or text.")
            appendLine()
            appendLine("Media context:")
            appendLine("- Asset id: ${mediaAsset.id.value}")
            mediaAsset.mimeType?.takeIf { it.isNotBlank() }?.let {
                appendLine("- MIME type: $it")
            }
            if (mediaAsset.widthPx != null && mediaAsset.heightPx != null) {
                appendLine("- Pixel size: ${mediaAsset.widthPx} x ${mediaAsset.heightPx}")
            }
            appendLine()
            appendLine("Image upload preprocessing:")
            appendLine("- Downscale before upload to a maximum 1568 px long edge.")
            appendLine("- Re-encode as JPEG quality 85 unless the provider requires PNG.")
            appendLine("- Strip EXIF metadata before upload.")
            appendLine("- Choose base64 inline versus hosted URL inside the provider module.")
        }.trim()
}
