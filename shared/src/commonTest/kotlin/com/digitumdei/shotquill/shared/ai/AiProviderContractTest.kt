package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.PhotoEditRequest
import com.digitumdei.shotquill.shared.domain.PhotoEditRequestId
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiProviderContractTest {
    @Test
    fun fakeProviderSatisfiesProviderContractWithDeterministicOutputs() {
        val provider = FakeAiProvider()

        assertProviderContract(provider)

        val first = provider.generateCaption(sampleCaptionRequest())
        val second = provider.generateCaption(sampleCaptionRequest())
        assertEquals(first, second)
    }

    @Test
    fun fakeProviderEditPhotoSummaryDerivedFromRequestPrompt() {
        val provider = FakeAiProvider()
        val request = sampleEditGenerationRequest()

        val assembledPrompt = request.editRequest.prompt

        val result = provider.editPhoto(request)
        val success = assertIs<AiProviderResult.Success<PhotoEditOutput>>(result)
        val summary = success.value.summary
        assertNotNull(summary)

        assertTrue(
            summary.contains(assembledPrompt.take(50)),
            "FakeAiProvider summary should be derived from editRequest.prompt (the assembled prompt). " +
                "Expected prompt text in summary but got: $summary",
        )
        assertTrue(
            summary.startsWith("Fake improve_lighting edit for original:"),
            "Expected summary to start with the fake prefix but got: $summary",
        )
    }

    @Test
    fun openAiProviderSatisfiesProviderContractThroughHttpTransport() {
        val settingsRepository = InMemoryLocalSettingsRepository()
        settingsRepository.saveOpenAiApiKey("sk-test_contract_1234567890")
        val transport = SuccessfulOpenAiTransport()
        val provider = OpenAiProvider(
            settingsRepository = settingsRepository,
            transport = transport,
            imagePreprocessor = TestImageUploadPreprocessor,
        )

        assertProviderContract(provider)

        assertEquals(4, transport.requests.size)
        assertTrue(transport.requests.any { it.url.endsWith("/chat/completions") })
        assertTrue(transport.requests.any { it.url.endsWith("/images/edits") })
    }

    private fun assertProviderContract(provider: AiProvider) {
        val vision = provider.describeVision(sampleVisionRequest())
        val caption = provider.generateCaption(sampleCaptionRequest())
        val altText = provider.generateAltText(sampleAltTextRequest())
        val edit = provider.editPhoto(sampleEditGenerationRequest())

        val visionSuccess = assertIs<AiProviderResult.Success<VisionDescriptionOutput>>(vision)
        assertTrue(visionSuccess.value.description.isNotBlank())
        assertTrue(visionSuccess.value.modelName?.isNotBlank() == true)

        val captionSuccess = assertIs<AiProviderResult.Success<CaptionGenerationOutput>>(caption)
        assertTrue(captionSuccess.value.caption.isNotBlank())
        assertTrue(captionSuccess.value.shortCaption?.isNotBlank() == true)
        assertFalse(captionSuccess.value.hashtags.any { !it.startsWith("#") })

        val altTextSuccess = assertIs<AiProviderResult.Success<AltTextGenerationOutput>>(altText)
        assertTrue(altTextSuccess.value.altText.isNotBlank())

        val editSuccess = assertIs<AiProviderResult.Success<PhotoEditOutput>>(edit)
        assertTrue(editSuccess.value.imageBytes.isNotEmpty())
        assertTrue(editSuccess.value.mimeType.startsWith("image/"))
        assertNotNull(editSuccess.value.summary)
        assertTrue(editSuccess.value.summary.isNotBlank())
    }
}

internal fun sampleImageInput(): AiImageInput =
    AiImageInput(
        bytes = "image-bytes".encodeToByteArray(),
        mimeType = "image/png",
        fileName = "photo.png",
    )

internal fun sampleVisionRequest(): VisionDescriptionRequest =
    VisionDescriptionRequest(
        draftId = PostDraftId("draft-1"),
        mediaAssetId = MediaAssetId("media-1"),
        image = sampleImageInput(),
        prompt = "Describe the photo without guessing.",
    )

internal fun sampleCaptionRequest(): CaptionGenerationRequest =
    CaptionGenerationRequest(
        draftId = PostDraftId("draft-1"),
        targetPlatform = TargetPlatform.InstagramFeedSquare,
        prompt = "Write a caption for a bright product photo.",
    )

internal fun sampleAltTextRequest(): AltTextGenerationRequest =
    AltTextGenerationRequest(
        draftId = PostDraftId("draft-1"),
        mediaAssetId = MediaAssetId("media-1"),
        prompt = "Write plain alt text.",
    )

internal fun sampleEditGenerationRequest(): PhotoEditGenerationRequest =
    PhotoEditGenerationRequest(
        editRequest = PhotoEditRequest(
            id = PhotoEditRequestId("edit-request-1"),
            draftId = PostDraftId("draft-1"),
            sourceMediaAssetId = MediaAssetId("media-1"),
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.Standard,
            prompt = "Edit this image: Improve the lighting and exposure of the image. Improve lighting while preserving the subject. Apply a photorealistic edit. Preserve natural camera realism and avoid visibly generated or illustrated details. Use standard quality tier. Frame the result for Original (no resize) using preserve the original dimensions without resizing.",
            userRefinement = null,
            subjectDescription = null,
            targetPlatform = TargetPlatform.Original,
            maskRegion = null,
            createdAtEpochMillis = 1_700_000_000_000L,
        ),
        sourceImage = sampleImageInput(),
    )

internal class SuccessfulOpenAiTransport : OpenAiHttpTransport {
    val requests = mutableListOf<OpenAiHttpRequest>()

    override fun execute(request: OpenAiHttpRequest): OpenAiHttpResult {
        requests += request
        return if (request.url.endsWith("/images/edits")) {
            OpenAiHttpResult.Success(
                statusCode = 200,
                body = """{"model":"gpt-image-1","data":[{"b64_json":"ZWRpdGVkLWltYWdl","revised_prompt":"Improved lighting."}]}""",
            )
        } else {
            val content = when {
                request.bodyText.contains("caption, shortCaption") ->
                    """{\"caption\":\"A bright launch moment.\",\"shortCaption\":\"Bright launch.\",\"hashtags\":[\"#shotquill\"]}"""
                request.bodyText.contains("alt text") -> "A product photo on a bright table."
                else -> "A concise product photo description."
            }
            OpenAiHttpResult.Success(
                statusCode = 200,
                body = """{"model":"gpt-4o-mini","choices":[{"message":{"content":"$content"}}]}""",
            )
        }
    }
}

private object TestImageUploadPreprocessor : AiImageUploadPreprocessor {
    override fun preprocess(image: AiImageInput, config: ImageUploadPreprocessingConfig): AiImageInput = image
}
