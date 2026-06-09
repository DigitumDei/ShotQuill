package com.digitumdei.shotquill.shared.domain

object PhotoEditPromptAssembler {
    fun buildPrompt(
        intent: EditIntent,
        realismLevel: RealismLevel,
        qualityTier: QualityTier,
        targetPlatform: TargetPlatform,
        subjectDescription: String?,
        userRefinement: String?,
    ): String {
        val preset = targetPlatform.platformPreset
        val normalizedSubject = subjectDescription?.let(::normalize).orEmpty()
        val normalizedRefinement = userRefinement?.let(::normalize).orEmpty()
        return buildString {
            append("Edit this image: ${intent.promptIntent}.")
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
