package com.digitumdei.shotquill.shared.domain

object PhotoEditPromptAssembler {
    fun assemble(request: PhotoEditRequest): String = buildPrompt(
        intent = request.intent,
        userPrompt = request.prompt,
        realismLevel = request.realismLevel,
        qualityTier = request.qualityTier,
        targetPlatform = request.targetPlatform,
        maskRegion = request.maskRegion,
        subjectDescription = request.subjectDescription,
        userRefinement = request.userRefinement,
    )

    fun buildPrompt(
        intent: EditIntent,
        userPrompt: String,
        realismLevel: RealismLevel,
        qualityTier: QualityTier,
        targetPlatform: TargetPlatform,
        maskRegion: MaskRegion?,
        subjectDescription: String?,
        userRefinement: String?,
    ): String {
        val preset = targetPlatform.platformPreset
        val normalizedPrompt = normalize(userPrompt)
        val normalizedSubject = subjectDescription?.let(::normalize).orEmpty()
        val normalizedRefinement = userRefinement?.let(::normalize).orEmpty()
        return buildString {
            append("Edit this image: ${intent.promptIntent}")
            if (normalizedPrompt.isNotEmpty()) {
                append(" $normalizedPrompt.")
            }
            append(" Apply a ${realismLevel.adjective} edit. ${realismLevel.promptIntent}")
            append(" Use ${qualityTier.wireValue} quality tier.")
            append(" Frame the result for ${preset.displayName}")
            if (preset.aspectRatio != null) {
                append(" at ${preset.aspectRatio.width}:${preset.aspectRatio.height}")
                if (preset.recommendedWidthPx != null && preset.recommendedHeightPx != null) {
                    append(", ${preset.recommendedWidthPx}x${preset.recommendedHeightPx}px")
                }
                append(", and ${preset.defaultFramingBehavior.naturalDescription}")
            } else {
                append(" using ${preset.defaultFramingBehavior.naturalDescription}")
            }
            append(".")
            if (maskRegion != null) {
                val bounds = maskRegion.bounds
                when (bounds) {
                    is MaskBounds.Normalized -> {
                        val right = bounds.left + bounds.width
                        val bottom = bounds.top + bounds.height
                        append(" The edit is constrained to the region spanning ${
                            bounds.left
                        } to $right horizontally and ${
                            bounds.top
                        } to $bottom vertically in normalized coordinates.")
                    }
                    is MaskBounds.Pixel -> {
                        val right = bounds.left + bounds.width
                        val bottom = bounds.top + bounds.height
                        append(" The edit is constrained to the pixel region from (${bounds.left}, ${bounds.top}) to ($right, $bottom).")
                    }
                }
            }
            if (normalizedSubject.isNotEmpty()) {
                append(" The subject is $normalizedSubject.")
                append(" Preserve the subject's appearance.")
            }
            if (normalizedRefinement.isNotEmpty()) {
                append(" $normalizedRefinement.")
            }
        }
    }

    private fun normalize(text: String): String = text
        .trim()
        .replace(Regex("\\s+"), " ")
        .trimEnd('.', '!', '?')
        .trimEnd()
}
