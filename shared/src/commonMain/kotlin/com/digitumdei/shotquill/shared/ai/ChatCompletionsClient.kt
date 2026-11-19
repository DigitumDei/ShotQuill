package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.settings.SecretRedactor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class AiHttpResponse(
    val status: Int,
    val body: String,
)

/** Sends one JSON request and returns whatever came back. Throws when the service cannot be reached at all. */
fun interface AiHttpTransport {
    fun postJson(url: String, headers: Map<String, String>, body: String): AiHttpResponse
}

sealed interface AiResult {
    data class Success(val text: String, val model: String) : AiResult

    /** [message] is written for the person using the app and never contains the API key. */
    data class Failure(val message: String) : AiResult
}

/**
 * A minimal client for the OpenAI chat-completions protocol, which OpenRouter
 * and most other providers also speak. It does one thing: send some
 * instructions with one image and return the text that comes back.
 */
class ChatCompletionsClient(
    private val transport: AiHttpTransport,
) {
    @OptIn(ExperimentalEncodingApi::class)
    fun describeImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        instructions: String,
        jpeg: ByteArray,
    ): AiResult {
        val url = chatCompletionsUrl(baseUrl)
            ?: return AiResult.Failure("The AI service address in settings is not a web address.")
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", instructions)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,${Base64.encode(jpeg)}")
                            }
                        }
                    }
                }
            }
        }
        val response = try {
            transport.postJson(
                url = url,
                headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    // Shown in OpenRouter's usage list; other services ignore it.
                    "X-Title" to "ShotQuill",
                ),
                body = body.toString(),
            )
        } catch (e: Exception) {
            return AiResult.Failure(
                redact("The AI service could not be reached: ${e.message ?: "no connection"}", apiKey),
            )
        }
        return when {
            response.status in 200..299 -> readText(response.body, model)
            else -> AiResult.Failure(redact(describeError(response), apiKey))
        }
    }

    private fun readText(body: String, requestedModel: String): AiResult {
        val root = parse(body) ?: return AiResult.Failure("The AI service sent a reply that could not be read.")
        val message = (root["choices"] as? JsonArray)
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.get("message") as? JsonObject
        val text = cleanReply(message?.get("content").asText())
        if (text.isEmpty()) return AiResult.Failure("The model returned no text. Try again or pick another model.")
        return AiResult.Success(text = text, model = root["model"].asText().ifEmpty { requestedModel })
    }

    private fun describeError(response: AiHttpResponse): String {
        val detail = parse(response.body)
            ?.get("error")
            ?.let { error -> (error as? JsonObject)?.get("message").asText().ifEmpty { error.asText() } }
            .orEmpty()
        val suffix = if (detail.isEmpty()) "" else " ($detail)"
        return when (response.status) {
            401, 403 -> "The AI service did not accept the API key.$suffix"
            402 -> "The AI account has run out of credit.$suffix"
            404 -> "The AI service did not recognise the model or the address.$suffix"
            408, 429 -> "The AI service is busy or rate-limiting requests. Try again shortly.$suffix"
            else -> "The AI service returned an error (${response.status}).$suffix"
        }
    }

    private fun parse(body: String): JsonObject? =
        try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: Exception) {
            null
        }

    /** Content is usually a plain string, but some services return a list of text parts. */
    private fun JsonElement?.asText(): String = when (this) {
        is JsonPrimitive -> if (isString) content else ""
        is JsonArray -> mapNotNull { part -> (part as? JsonObject)?.get("text").asText().takeIf { it.isNotEmpty() } }
            .joinToString(" ")
        else -> ""
    }

    private fun redact(message: String, apiKey: String): String =
        SecretRedactor.redactKnownSecrets(message, listOf(apiKey))

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The address requests are posted to, or null when [baseUrl] is not an http(s)
 * address. A base such as `https://openrouter.ai/api/v1` gets the standard
 * path added; a full chat-completions address is used as it is.
 */
fun chatCompletionsUrl(baseUrl: String): String? {
    val trimmed = baseUrl.trim().trimEnd('/')
    val hasScheme = trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)
    if (!hasScheme || trimmed.substringAfter("://").isBlank() || trimmed.any { it.isWhitespace() }) return null
    return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
}

/** Tidies what a model sends back into one line of plain text. */
internal fun cleanReply(reply: String): String =
    reply.trim()
        .removeSurrounding("\"")
        .removeSurrounding("“", "”")
        .replace(Regex("\\s+"), " ")
        .trim()
