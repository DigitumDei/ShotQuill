package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.SecretRedactor
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class OpenAiProvider(
    private val settingsRepository: LocalSettingsRepository,
    private val transport: OpenAiHttpTransport,
    private val config: OpenAiProviderConfig = OpenAiProviderConfig(),
    private val logger: AiRequestLogger = NoopAiRequestLogger,
    private val imagePreprocessor: AiImageUploadPreprocessor = PlatformImageUploadPreprocessor,
) : AiProvider {
    override val name: String get() = "openai"
    override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> =
        withApiKey { apiKey ->
            val image = imagePreprocessor.preprocess(request.image, ImageUploadPreprocessingConfig())
            val httpRequest = OpenAiHttpRequest(
                method = "POST",
                url = "${config.baseUrl}/chat/completions",
                headers = authorizedHeaders(apiKey, "application/json"),
                bodyBytes = buildChatBody(
                    model = config.visionModel,
                    prompt = request.prompt,
                    image = image,
                    responseInstruction = "Return one concise image description as plain text.",
                ).encodeToByteArray(),
                redactedBody = "chat vision request with redacted image payload",
            )
            executeTextRequest(httpRequest, apiKey) { content, model ->
                VisionDescriptionOutput(description = content.trim(), modelName = model ?: config.visionModel)
            }
        }

    override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> =
        withApiKey { apiKey ->
            val httpRequest = OpenAiHttpRequest(
                method = "POST",
                url = "${config.baseUrl}/chat/completions",
                headers = authorizedHeaders(apiKey, "application/json"),
                bodyBytes = buildTextChatBody(
                    model = config.textModel,
                    prompt = request.prompt,
                    responseInstruction = "Return JSON with caption, shortCaption, and hashtags string array.",
                ).encodeToByteArray(),
                redactedBody = request.prompt,
            )
            executeTextRequest(httpRequest, apiKey) { content, model ->
                val parsed = OpenAiJson.parseCaptionOutput(content)
                parsed.copy(modelName = model ?: config.textModel)
            }
        }

    override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> =
        withApiKey { apiKey ->
            val httpRequest = OpenAiHttpRequest(
                method = "POST",
                url = "${config.baseUrl}/chat/completions",
                headers = authorizedHeaders(apiKey, "application/json"),
                bodyBytes = buildTextChatBody(
                    model = config.textModel,
                    prompt = request.prompt,
                    responseInstruction = "Return only accessible plain-language alt text.",
                ).encodeToByteArray(),
                redactedBody = request.prompt,
            )
            executeTextRequest(httpRequest, apiKey) { content, model ->
                AltTextGenerationOutput(altText = content.trim(), modelName = model ?: config.textModel)
            }
        }

    override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> =
        withApiKey { apiKey ->
            val multipart = buildImageEditBody(
                request.copy(
                    sourceImage = imagePreprocessor.preprocess(request.sourceImage, ImageUploadPreprocessingConfig(providerRequiresPng = true)),
                    maskImage = request.maskImage?.let {
                        imagePreprocessor.preprocess(it, ImageUploadPreprocessingConfig(providerRequiresPng = true))
                    },
                ),
            )
            val httpRequest = OpenAiHttpRequest(
                method = "POST",
                url = "${config.baseUrl}/images/edits",
                headers = authorizedHeaders(apiKey, "multipart/form-data; boundary=${multipart.boundary}"),
                bodyBytes = multipart.bodyBytes,
                redactedBody = multipart.redactedBody,
            )
            executeImageRequest(httpRequest, apiKey)
        }

    private fun <T> withApiKey(block: (String) -> AiProviderResult<T>): AiProviderResult<T> {
        val apiKey = settingsRepository.getOpenAiApiKey()
        return if (apiKey.isNullOrBlank()) {
            AiProviderResult.Failure(AiError.MissingApiKey)
        } else {
            block(apiKey)
        }
    }

    private fun <T> executeTextRequest(
        request: OpenAiHttpRequest,
        apiKey: String,
        mapper: (content: String, model: String?) -> T,
    ): AiProviderResult<T> {
        logger.log(request.toRedactedLogEvent(listOf(apiKey)))
        return when (val response = transport.execute(request)) {
            is OpenAiHttpResult.NetworkFailure -> AiProviderResult.Failure(
                AiErrorMapper.fromNetworkFailure(response.message, listOf(apiKey)),
            )
            is OpenAiHttpResult.Success -> {
                if (response.statusCode in 200..299) {
                    val content = OpenAiJson.extractChatContent(response.body)
                    if (content == null) {
                        AiProviderResult.Failure(AiError.ProviderFailure(statusCode = response.statusCode))
                    } else {
                        AiProviderResult.Success(mapper(content, OpenAiJson.extractModel(response.body)))
                    }
                } else {
                    AiProviderResult.Failure(AiErrorMapper.fromHttpStatus(response.statusCode, response.body, listOf(apiKey)))
                }
            }
        }
    }

    private fun executeImageRequest(request: OpenAiHttpRequest, apiKey: String): AiProviderResult<PhotoEditOutput> {
        logger.log(request.toRedactedLogEvent(listOf(apiKey)))
        return when (val response = transport.execute(request)) {
            is OpenAiHttpResult.NetworkFailure -> AiProviderResult.Failure(
                AiErrorMapper.fromNetworkFailure(response.message, listOf(apiKey)),
            )
            is OpenAiHttpResult.Success -> {
                if (response.statusCode !in 200..299) {
                    return AiProviderResult.Failure(
                        AiErrorMapper.fromHttpStatus(response.statusCode, response.body, listOf(apiKey)),
                    )
                }
                val imageBytes = OpenAiJson.extractFirstImageBase64(response.body)
                    ?.let { encodedImage -> runCatching { encodedImage.decodeBase64Bytes() }.getOrNull() }
                    ?: return AiProviderResult.Failure(AiError.ProviderFailure(statusCode = response.statusCode))
                AiProviderResult.Success(
                    PhotoEditOutput(
                        imageBytes = imageBytes,
                        mimeType = "image/png",
                        summary = OpenAiJson.extractFirstImageRevisedPrompt(response.body),
                        modelName = OpenAiJson.extractModel(response.body) ?: config.imageEditModel,
                    ),
                )
            }
        }
    }

    private fun authorizedHeaders(apiKey: String, contentType: String): Map<String, String> =
        mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to contentType,
        )

    private fun buildTextChatBody(model: String, prompt: String, responseInstruction: String): String =
        buildString {
            append("{")
            appendJsonField("model", model)
            append(",\"messages\":[")
            append("{")
            appendJsonField("role", "system")
            append(",")
            appendJsonField("content", responseInstruction)
            append("},{")
            appendJsonField("role", "user")
            append(",")
            appendJsonField("content", prompt)
            append("}]")
            append("}")
        }

    private fun buildChatBody(model: String, prompt: String, image: AiImageInput, responseInstruction: String): String =
        buildString {
            append("{")
            appendJsonField("model", model)
            append(",\"messages\":[")
            append("{")
            appendJsonField("role", "system")
            append(",")
            appendJsonField("content", responseInstruction)
            append("},{")
            appendJsonField("role", "user")
            append(",\"content\":[")
            append("{\"type\":\"text\",")
            appendJsonField("text", prompt)
            append("},{\"type\":\"image_url\",\"image_url\":{")
            appendJsonField("url", "data:${image.mimeType};base64,${image.bytes.encodeBase64()}")
            append("}}]}]}")
        }

    /**
     * Builds a multipart body for the OpenAI images/edits endpoint.
     *
     * The optional [PhotoEditGenerationRequest.maskImage] multipart field is deferred
     * scaffolding - no production caller populates it in the current issue scope.
     * The mask upload path is unreachable until mask-region support is implemented
     * end-to-end from [com.digitumdei.shotquill.shared.workflow.PhotoEditExecutionPipeline].
     */
    private fun buildImageEditBody(request: PhotoEditGenerationRequest): MultipartBody {
        val boundary = "shotquill-openai-boundary"
        val prompt = request.editRequest.prompt
        val bytes = buildList {
            addMultipartText(boundary, "model", config.imageEditModel)
            addMultipartText(boundary, "prompt", prompt)
            addMultipartFile(boundary, "image", request.sourceImage)
            request.maskImage?.let { addMultipartFile(boundary, "mask", it) }
            add("--$boundary--\r\n".encodeToByteArray())
        }.concat()
        return MultipartBody(
            boundary = boundary,
            bodyBytes = bytes,
            redactedBody = "multipart image edit request: model=${config.imageEditModel}, prompt=${prompt.take(120)}, image=[REDACTED_IMAGE_PAYLOAD]",
        )
    }
}

data class OpenAiProviderConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val visionModel: String = "gpt-4o-mini",
    val textModel: String = "gpt-4o-mini",
    val imageEditModel: String = "gpt-image-1",
)

interface OpenAiHttpTransport {
    fun execute(request: OpenAiHttpRequest): OpenAiHttpResult
}

data class OpenAiHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val bodyBytes: ByteArray,
    val redactedBody: String,
) {
    val bodyText: String get() = bodyBytes.decodeToString()
}

sealed class OpenAiHttpResult {
    data class Success(val statusCode: Int, val body: String) : OpenAiHttpResult()
    data class NetworkFailure(val message: String?) : OpenAiHttpResult()
}

interface AiRequestLogger {
    fun log(event: RedactedAiRequestLogEvent)
}

object NoopAiRequestLogger : AiRequestLogger {
    override fun log(event: RedactedAiRequestLogEvent) = Unit
}

data class RedactedAiRequestLogEvent(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

private fun OpenAiHttpRequest.toRedactedLogEvent(secrets: Iterable<String?>): RedactedAiRequestLogEvent =
    RedactedAiRequestLogEvent(
        method = method,
        url = url,
        headers = headers.mapValues { (name, value) ->
            if (name.equals("authorization", ignoreCase = true)) SecretRedactor.Redacted else value
        },
        body = SecretRedactor.redactKnownSecrets(redactedBody, secrets).redactDataUrls(),
    )

private fun String.redactDataUrls(): String =
    replace(Regex("data:[^\"]+"), "data:[REDACTED_IMAGE_PAYLOAD]")

private fun StringBuilder.appendJsonField(name: String, value: String) {
    append("\"")
    append(name)
    append("\":\"")
    append(value.jsonEscaped())
    append("\"")
}

private fun String.jsonEscaped(): String =
    buildString {
        this@jsonEscaped.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
}

private data class MultipartBody(
    val boundary: String,
    val bodyBytes: ByteArray,
    val redactedBody: String,
)

private fun MutableList<ByteArray>.addMultipartText(boundary: String, name: String, value: String) {
    add("--$boundary\r\n".encodeToByteArray())
    add("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".encodeToByteArray())
    add(value.encodeToByteArray())
    add("\r\n".encodeToByteArray())
}

private fun MutableList<ByteArray>.addMultipartFile(boundary: String, name: String, image: AiImageInput) {
    add("--$boundary\r\n".encodeToByteArray())
    add(
        "Content-Disposition: form-data; name=\"$name\"; filename=\"${image.fileName.jsonEscaped()}\"\r\n"
            .encodeToByteArray(),
    )
    add("Content-Type: ${image.mimeType}\r\n\r\n".encodeToByteArray())
    add(image.bytes)
    add("\r\n".encodeToByteArray())
}

private fun List<ByteArray>.concat(): ByteArray {
    val output = ByteArray(sumOf { it.size })
    var offset = 0
    forEach { bytes ->
        bytes.copyInto(output, destinationOffset = offset)
        offset += bytes.size
    }
    return output
}

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeBase64(): String = Base64.encode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun String.decodeBase64Bytes(): ByteArray = Base64.decode(this)
