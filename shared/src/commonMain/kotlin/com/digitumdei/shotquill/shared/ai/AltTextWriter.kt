package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository

/**
 * Asks the configured AI service to describe a photo for people who cannot
 * see it. This is the only thing the app uses AI for: it writes neither
 * captions nor pixels.
 */
class AltTextWriter(
    private val settings: LocalSettingsRepository,
    private val client: ChatCompletionsClient,
) {
    /** True once an API key has been saved; without one nothing is ever sent. */
    val isConfigured: Boolean
        get() = settings.hasOpenAiApiKey()

    /** The models offered for alt text, most preferred first. */
    fun models(): List<String> = settings.readSettings().ai.modelsFor(AiOperation.AltText)

    /**
     * Blocking: call off the main thread. [jpeg] is the photo as it will be
     * shared; [caption] is passed only as context for naming what is shown.
     */
    fun write(jpeg: ByteArray, caption: String, model: String? = null): AiResult {
        val apiKey = settings.getOpenAiApiKey()
            ?: return AiResult.Failure("Add an API key in settings to have alt text written for you.")
        val ai = settings.readSettings().ai
        return client.describeImage(
            baseUrl = ai.baseUrl,
            apiKey = apiKey,
            model = model?.takeIf { it.isNotBlank() } ?: ai.modelsFor(AiOperation.AltText).first(),
            instructions = altTextInstructions(caption),
            jpeg = jpeg,
        )
    }
}

fun altTextInstructions(caption: String): String = buildString {
    appendLine("Write alt text for this photo, which is about to be posted on Instagram.")
    appendLine("Alt text is read aloud by screen readers to people who cannot see the image.")
    appendLine()
    appendLine("Rules:")
    appendLine("- Describe only what is visible. Do not guess at names, places, brands or feelings.")
    appendLine("- One or two plain sentences, about 125 characters if that is enough.")
    appendLine("- Do not begin with \"Image of\" or \"Photo of\".")
    appendLine("- No hashtags, emoji, quotation marks or line breaks.")
    append("- Reply with the alt text and nothing else.")
    val context = caption.trim()
    if (context.isNotEmpty()) {
        appendLine()
        appendLine()
        appendLine(
            "The caption for the post follows. Use it only to name things that are clearly shown in the " +
                "photo. Do not repeat it or describe anything that is not visible.",
        )
        append("Caption: ")
        append(context)
    }
}
