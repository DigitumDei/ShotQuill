package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.domain.PhotoEditPromptAssembler

class FakeAiProvider(
    private val modelName: String = "fake-ai-provider",
) : AiProvider {
    override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> =
        AiProviderResult.Success(
            VisionDescriptionOutput(
                description = "Fake vision for ${request.mediaAssetId.value}: ${stablePromptSummary(request.prompt)}",
                modelName = modelName,
            ),
        )

    override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> {
        val summary = stablePromptSummary(request.prompt)
        return AiProviderResult.Success(
            CaptionGenerationOutput(
                caption = "Fake ${request.targetPlatform.wireValue} caption: $summary",
                shortCaption = "Fake short caption: $summary",
                hashtags = listOf("#shotquill", "#fakeprovider"),
                modelName = modelName,
            ),
        )
    }

    override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> =
        AiProviderResult.Success(
            AltTextGenerationOutput(
                altText = "Fake alt text for ${request.mediaAssetId.value}: ${stablePromptSummary(request.prompt)}",
                modelName = modelName,
            ),
        )

    override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> {
        val assembled = PhotoEditPromptAssembler.assemble(request.editRequest)
        return AiProviderResult.Success(
            PhotoEditOutput(
                imageBytes = ("fake-edit:${request.editRequest.id.value}:${request.editRequest.intent.wireValue}:${request.editRequest.targetPlatform.wireValue}")
                    .encodeToByteArray(),
                mimeType = request.sourceImage.mimeType,
                summary = "Fake ${request.editRequest.intent.wireValue} edit for ${request.editRequest.targetPlatform.wireValue}: ${stablePromptSummary(assembled)}",
                modelName = modelName,
            ),
        )
    }

    private fun stablePromptSummary(prompt: String): String =
        prompt
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(72)
}
