package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform

object RequestSettingsFormatter {

    fun visionDescription(fileName: String, mimeType: String): String =
        "fileName=$fileName, mimeType=$mimeType"

    fun captionGeneration(targetPlatform: TargetPlatform, tone: String?): String {
        val base = "targetPlatform=${targetPlatform.wireValue}"
        return "$base, tone=${tone ?: "default"}"
    }

    fun altTextGeneration(targetPlatform: TargetPlatform): String =
        "targetPlatform=${targetPlatform.wireValue}"

    fun photoEdit(
        intent: EditIntent,
        realismLevel: RealismLevel,
        qualityTier: QualityTier,
        targetPlatform: TargetPlatform,
        hasRefinement: Boolean,
    ): String = buildString {
        append("intent=${intent.wireValue}")
        append(", realismLevel=${realismLevel.wireValue}")
        append(", qualityTier=${qualityTier.wireValue}")
        append(", targetPlatform=${targetPlatform.wireValue}")
        append(", hasRefinement=$hasRefinement")
    }
}
