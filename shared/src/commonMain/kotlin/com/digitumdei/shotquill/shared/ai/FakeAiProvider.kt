package com.digitumdei.shotquill.shared.ai

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

    override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> =
        AiProviderResult.Success(
            PhotoEditOutput(
                imageBytes = ("fake-edit:${request.editRequest.id.value}:${request.editRequest.intent.wireValue}")
                    .encodeToByteArray(),
                mimeType = request.sourceImage.mimeType,
                summary = "Fake ${request.editRequest.intent.wireValue} edit: ${stablePromptSummary(request.editRequest.prompt)}",
                modelName = modelName,
            ),
        )

    private fun stablePromptSummary(prompt: String): String =
        prompt
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(72)
}
