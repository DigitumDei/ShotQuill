package com.digitumdei.shotquill.shared.domain

object PhotoEditPromptAssembler {
    fun assemble(request: PhotoEditRequest): String {
        val preset = request.targetPlatform.platformPreset
        return buildString {
            append("Edit this image: ${request.intent.promptIntent} ${normalize(request.prompt)}.")
            append(" Apply a ${request.realismLevel.adjective} edit. ${request.realismLevel.promptIntent}")
            append(" Use ${request.qualityTier.wireValue} quality tier.")
            append(" Frame the result for ${preset.displayName}")
            if (preset.aspectRatio != null) {
                append(" at ${preset.aspectRatio.width}:${preset.aspectRatio.height}, ${preset.recommendedWidthPx}x${preset.recommendedHeightPx}px, and ${preset.defaultFramingBehavior.naturalDescription}")
            } else {
                append(" using ${preset.defaultFramingBehavior.naturalDescription}")
            }
            append(".")
            if (request.maskRegion != null) {
                val bounds = request.maskRegion.bounds
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
            if (!request.subjectDescription.isNullOrBlank()) {
                append(" The subject is ${normalize(request.subjectDescription)}.")
                append(" Preserve the subject's appearance.")
            }
            if (!request.userRefinement.isNullOrBlank()) {
                append(" ${normalize(request.userRefinement)}.")
            }
        }
    }

    private fun normalize(text: String): String = text
        .trim()
        .replace(Regex("\\s+"), " ")
        .trimEnd('.')
}