package com.digitumdei.shotquill.shared.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object OpenAiJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun extractChatContent(body: String): String? =
        body.asJsonObject()
            ?.getArray("choices")
            ?.firstObject()
            ?.getObject("message")
            ?.getString("content")

    fun extractModel(body: String): String? =
        body.asJsonObject()?.getString("model")

    fun extractFirstImageBase64(body: String): String? =
        body.asJsonObject()
            ?.getArray("data")
            ?.firstObject()
            ?.getString("b64_json")

    fun extractFirstImageRevisedPrompt(body: String): String? =
        body.asJsonObject()
            ?.getArray("data")
            ?.firstObject()
            ?.getString("revised_prompt")

    fun parseCaptionOutput(content: String): CaptionGenerationOutput {
        val stripped = content.stripMarkdownCodeFence()
        val contentObject = stripped.asJsonObject()
        val caption = contentObject?.getString("caption") ?: stripped.trim()
        val shortCaption = contentObject?.getString("shortCaption")
            ?: contentObject?.getString("short_caption")
            ?: caption.take(96)
        val hashtags = contentObject?.getStringArray("hashtags").orEmpty()
        return CaptionGenerationOutput(
            caption = caption,
            shortCaption = shortCaption,
            hashtags = hashtags,
            modelName = null,
        )
    }

    fun extractString(body: String, name: String): String? =
        body.asJsonObject()?.getString(name)

    private fun String.stripMarkdownCodeFence(): String {
        val trimmed = trim()
        if (!trimmed.startsWith("```")) return trimmed
        val withoutOpening = trimmed.removePrefix("```json").removePrefix("```").trimStart('\n', '\r')
        return withoutOpening.trimEnd().removeSuffix("```").trimEnd()
    }

    private fun String.asJsonObject(): JsonObject? =
        runCatching { json.parseToJsonElement(this) as? JsonObject }.getOrNull()

    private fun JsonObject.getString(name: String): String? =
        get(name).contentOrNull()

    private fun JsonObject.getObject(name: String): JsonObject? =
        get(name) as? JsonObject

    private fun JsonObject.getArray(name: String): JsonArray? =
        get(name) as? JsonArray

    private fun JsonObject.getStringArray(name: String): List<String> =
        getArray(name)
            ?.mapNotNull { it.contentOrNull() }
            .orEmpty()

    private fun JsonArray.firstObject(): JsonObject? =
        firstOrNull() as? JsonObject

    private fun JsonElement?.contentOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull
}
