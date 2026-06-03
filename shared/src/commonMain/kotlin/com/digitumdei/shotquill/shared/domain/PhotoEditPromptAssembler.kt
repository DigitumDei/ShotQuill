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
            append(" ${request.intent.promptIntent}")
            append(" Apply a ${request.realismLevel.adjective} edit. ${request.realismLevel.promptIntent}")
            append(" Use ${request.qualityTier.wireValue} quality tier.")
            if (!request.subjectDescription.isNullOrBlank()) {
                append(" The subject is ${request.subjectDescription.trim()}.")
                append(" Preserve the subject's appearance.")
            }
            if (!request.userRefinement.isNullOrBlank()) {
                append(" ${request.userRefinement.trim()}.")
            }
        }
    }
}