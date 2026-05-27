package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.SecretRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenAiProviderTest {
    private val apiKey = "sk-test_openai_1234567890"

    @Test
    fun returnsMissingApiKeyBeforeMakingProviderCall() {
        val transport = SuccessfulOpenAiTransport()
        val provider = OpenAiProvider(InMemoryLocalSettingsRepository(), transport)

        val result = provider.generateCaption(sampleCaptionRequest())

        val failure = assertIs<AiProviderResult.Failure>(result)
        assertIs<AiError.MissingApiKey>(failure.error)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun buildsVisionChatRequestWithImageAndNoApiKeyInBody() {
        val transport = SuccessfulOpenAiTransport()
        val preprocessor = RecordingImageUploadPreprocessor()
        val provider = configuredProvider(transport, imagePreprocessor = preprocessor)

        val result = provider.describeVision(sampleVisionRequest())

        assertIs<AiProviderResult.Success<VisionDescriptionOutput>>(result)
        assertEquals(1, preprocessor.requests.size)
        assertEquals(1568, preprocessor.requests.single().maxLongEdgePx)
        assertEquals(85, preprocessor.requests.single().jpegQuality)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertTrue(request.url.endsWith("/chat/completions"))
        assertEquals("Bearer $apiKey", request.headers["Authorization"])
        assertTrue(request.bodyText.contains("image_url"))
        assertTrue(request.bodyText.contains("Describe the photo"))
        assertFalse(request.bodyText.contains(apiKey))
    }

    @Test
    fun buildsCaptionAndAltTextChatRequests() {
        val transport = SuccessfulOpenAiTransport()
        val provider = configuredProvider(transport)

        val caption = provider.generateCaption(sampleCaptionRequest())
        val altText = provider.generateAltText(sampleAltTextRequest())

        val captionSuccess = assertIs<AiProviderResult.Success<CaptionGenerationOutput>>(caption)
        assertEquals("A bright launch moment.", captionSuccess.value.caption)
        assertEquals(listOf("#shotquill"), captionSuccess.value.hashtags)
        val altTextSuccess = assertIs<AiProviderResult.Success<AltTextGenerationOutput>>(altText)
        assertEquals("A product photo on a bright table.", altTextSuccess.value.altText)
        assertTrue(transport.requests.all { it.url.endsWith("/chat/completions") })
    }

    @Test
    fun parsesOpenAiJsonWithoutMatchingNestedContentText() {
        val body = """
            {
              "model": "gpt-4o-mini",
              "choices": [
                {
                  "message": {
                    "content": "{\"caption\":\"caf\\u00e9 [launch]\",\"shortCaption\":\"caf\\u00e9\",\"hashtags\":[\"#launch]day\",\"#caf\\u00e9\"]}"
                  }
                }
              ]
            }
        """.trimIndent()

        val content = OpenAiJson.extractChatContent(body)
        val output = OpenAiJson.parseCaptionOutput(content ?: "")

        assertEquals("gpt-4o-mini", OpenAiJson.extractModel(body))
        assertEquals("caf\u00e9 [launch]", output.caption)
        assertEquals("caf\u00e9", output.shortCaption)
        assertEquals(listOf("#launch]day", "#caf\u00e9"), output.hashtags)
    }

    @Test
    fun buildsMultipartImageEditRequestAndReadsReturnedImageBytes() {
        val transport = SuccessfulOpenAiTransport()
        val provider = configuredProvider(transport)

        val result = provider.editPhoto(sampleEditGenerationRequest())

        val success = assertIs<AiProviderResult.Success<PhotoEditOutput>>(result)
        assertEquals("edited-image", success.value.imageBytes.decodeToString())
        val request = transport.requests.single()
        assertTrue(request.url.endsWith("/images/edits"))
        assertTrue(request.headers.getValue("Content-Type").startsWith("multipart/form-data"))
        assertTrue(request.bodyText.contains("name=\"prompt\""))
        assertTrue(request.bodyText.contains("Improve lighting while preserving the subject."))
        assertTrue(request.bodyText.contains("name=\"image\"; filename=\"photo.png\""))
        assertFalse(request.bodyText.contains(apiKey))
    }

    @Test
    fun mapsMalformedImageBase64ToProviderFailure() {
        val provider = configuredProvider(
            FailingOpenAiTransport(
                OpenAiHttpResult.Success(
                    statusCode = 200,
                    body = """{"model":"gpt-image-1","data":[{"b64_json":"not valid base64!"}]}""",
                ),
            ),
        )

        val result = provider.editPhoto(sampleEditGenerationRequest())

        val failure = assertIs<AiProviderResult.Failure>(result)
        assertIs<AiError.ProviderFailure>(failure.error)
    }

    @Test
    fun logsRequestsWithAuthorizationAndImagePayloadRedacted() {
        val transport = SuccessfulOpenAiTransport()
        val logger = RecordingAiRequestLogger()
        val provider = configuredProvider(transport, logger)

        provider.describeVision(sampleVisionRequest())
        provider.editPhoto(sampleEditGenerationRequest())

        assertEquals(2, logger.events.size)
        assertTrue(logger.events.all { it.headers["Authorization"] == SecretRedactor.Redacted })
        assertTrue(logger.events.all { !it.body.contains(apiKey) })
        assertTrue(logger.events.any { it.body.contains("[REDACTED_IMAGE_PAYLOAD]") })
    }

    @Test
    fun mapsHttpAndNetworkFailuresFromTransport() {
        val invalidKeyProvider = configuredProvider(FailingOpenAiTransport(OpenAiHttpResult.Success(401, "{}")))
        val quotaProvider = configuredProvider(
            FailingOpenAiTransport(OpenAiHttpResult.Success(429, """{"error":{"type":"insufficient_quota"}}""")),
        )
        val networkProvider = configuredProvider(FailingOpenAiTransport(OpenAiHttpResult.NetworkFailure("offline $apiKey")))

        val invalidKey = invalidKeyProvider.generateCaption(sampleCaptionRequest())
        val quota = quotaProvider.generateCaption(sampleCaptionRequest())
        val network = networkProvider.generateCaption(sampleCaptionRequest())

        val invalidKeyFailure = assertIs<AiProviderResult.Failure>(invalidKey)
        assertIs<AiError.InvalidApiKey>(invalidKeyFailure.error)
        val quotaFailure = assertIs<AiProviderResult.Failure>(quota)
        assertIs<AiError.QuotaExceeded>(quotaFailure.error)
        val networkFailure = assertIs<AiProviderResult.Failure>(network)
        assertIs<AiError.NetworkFailure>(networkFailure.error)
        assertFalse(networkFailure.error.userMessage.contains(apiKey))
    }

    private fun configuredProvider(
        transport: OpenAiHttpTransport,
        logger: AiRequestLogger = NoopAiRequestLogger,
        imagePreprocessor: AiImageUploadPreprocessor = PassthroughImageUploadPreprocessor,
    ): OpenAiProvider {
        val settingsRepository = InMemoryLocalSettingsRepository()
        settingsRepository.saveOpenAiApiKey(apiKey)
        return OpenAiProvider(
            settingsRepository = settingsRepository,
            transport = transport,
            logger = logger,
            imagePreprocessor = imagePreprocessor,
        )
    }
}

private object PassthroughImageUploadPreprocessor : AiImageUploadPreprocessor {
    override fun preprocess(image: AiImageInput, config: ImageUploadPreprocessingConfig): AiImageInput = image
}

private class RecordingImageUploadPreprocessor : AiImageUploadPreprocessor {
    val requests = mutableListOf<ImageUploadPreprocessingConfig>()

    override fun preprocess(image: AiImageInput, config: ImageUploadPreprocessingConfig): AiImageInput {
        requests += config
        return image.copy(mimeType = "image/jpeg", fileName = "photo.jpg")
    }
}

private class FailingOpenAiTransport(
    private val result: OpenAiHttpResult,
) : OpenAiHttpTransport {
    override fun execute(request: OpenAiHttpRequest): OpenAiHttpResult = result
}

private class RecordingAiRequestLogger : AiRequestLogger {
    val events = mutableListOf<RedactedAiRequestLogEvent>()

    override fun log(event: RedactedAiRequestLogEvent) {
        events += event
    }
}
