package com.digitumdei.shotquill.shared.settings

import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform

data class LocalAppSettings(
    val defaultTargetPlatform: TargetPlatform = TargetPlatform.InstagramFeedSquare,
    val defaultQualityTier: QualityTier = QualityTier.Standard,
    val defaultRealismLevel: RealismLevel = RealismLevel.Photoreal,
    val activeBrandProfileId: BrandProfileId? = null,
    val promptHistoryEnabled: Boolean = true,
)

interface LocalSettingsRepository {
    fun readSettings(): LocalAppSettings
    fun saveSettings(settings: LocalAppSettings)
    fun getOpenAiApiKey(): String?
    fun saveOpenAiApiKey(apiKey: String)
    fun hasOpenAiApiKey(): Boolean
    fun clearOpenAiApiKey()
}
