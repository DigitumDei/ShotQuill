package com.digitumdei.shotquill.shared.domain

object PhotoEditPromptAssembler {
    fun assemble(request: PhotoEditRequest): String {
        val preset = request.targetPlatform.platformPreset
        return buildString {
            append("Edit the photo as follows: \"")
            append(request.prompt.trim())
            append("\".")
            append(" The target platform is ${preset.displayName}")
            if (preset.aspectRatio != null) {
                append(" (${preset.aspectRatio.width}:${preset.aspectRatio.height}")
                if (preset.recommendedWidthPx != null && preset.recommendedHeightPx != null) {
                    append(", ${preset.recommendedWidthPx}x${preset.recommendedHeightPx}px")
                }
                append(", ${preset.defaultFramingBehavior.wireValue} framing")
                append(")")
            } else {
                append(" (${preset.defaultFramingBehavior.wireValue} framing)")
            }
            append(".")
            append(" Apply ${request.realismLevel.wireValue} realism: ${request.realismLevel.promptIntent.trimEnd('.')}.")
            append(" Use ${request.qualityTier.wireValue} quality tier.")
            if (!request.subjectDescription.isNullOrBlank()) {
                append(" The subject is ${request.subjectDescription.trim()}.")
            }
            if (!request.userRefinement.isNullOrBlank()) {
                append(" Additional user notes: ${request.userRefinement.trim()}.")
            }
        }
    }
}