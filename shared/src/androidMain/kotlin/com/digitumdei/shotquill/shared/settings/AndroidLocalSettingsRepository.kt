package com.digitumdei.shotquill.shared.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform

class AndroidLocalSettingsRepository(
    context: Context,
) : LocalSettingsRepository {
    private val applicationContext = context.applicationContext
    private val regularPreferences: SharedPreferences =
        applicationContext.getSharedPreferences(SettingsPreferencesName, Context.MODE_PRIVATE)
    private val secretPreferences: SharedPreferences by lazy { createSecretPreferences(applicationContext) }

    override fun readSettings(): LocalAppSettings =
        LocalAppSettings(
            defaultTargetPlatform = regularPreferences.enumSetting(
                KeyDefaultTargetPlatform,
                TargetPlatform.InstagramFeedSquare,
                TargetPlatform::fromWireValue,
            ),
            defaultQualityTier = regularPreferences.enumSetting(
                KeyDefaultQualityTier,
                QualityTier.Standard,
                QualityTier::fromWireValue,
            ),
            defaultRealismLevel = regularPreferences.enumSetting(
                KeyDefaultRealismLevel,
                RealismLevel.Photoreal,
                RealismLevel::fromWireValue,
            ),
            activeBrandProfileId = regularPreferences.getString(KeyActiveBrandProfileId, null)
                ?.takeIf { it.isNotBlank() }
                ?.let(::BrandProfileId),
            promptHistoryEnabled = regularPreferences.getBoolean(KeyPromptHistoryEnabled, true),
            defaultCropShape = regularPreferences.enumSetting(
                KeyDefaultCropShape,
                CropShape.Portrait,
                CropShape::fromWireValue,
            ),
            look = readLook(),
            logo = readLogo(),
            snippets = regularPreferences.getString(KeySnippets, null)
                ?.let(SnippetCodec::decode)
                ?: CaptionSnippet.Defaults,
        )

    private fun readLook(): LookSettings {
        val defaults = LookSettings()
        return LookSettings(
            warmth = regularPreferences.getFloat(KeyLookWarmth, defaults.warmth),
            contrast = regularPreferences.getFloat(KeyLookContrast, defaults.contrast),
            saturation = regularPreferences.getFloat(KeyLookSaturation, defaults.saturation),
            fade = regularPreferences.getFloat(KeyLookFade, defaults.fade),
        )
    }

    private fun readLogo(): LogoSettings {
        val defaults = LogoSettings()
        return LogoSettings(
            uri = regularPreferences.getString(KeyLogoUri, null)?.takeIf { it.isNotBlank() },
            widthFraction = regularPreferences.getFloat(KeyLogoWidth, defaults.widthFraction),
            opacity = regularPreferences.getFloat(KeyLogoOpacity, defaults.opacity),
            marginFraction = regularPreferences.getFloat(KeyLogoMargin, defaults.marginFraction),
            corner = regularPreferences.enumSetting(KeyLogoCorner, defaults.corner, LogoCorner::fromWireValue),
        )
    }

    override fun saveSettings(settings: LocalAppSettings) {
        regularPreferences.edit()
            .putString(KeyDefaultTargetPlatform, settings.defaultTargetPlatform.wireValue)
            .putString(KeyDefaultQualityTier, settings.defaultQualityTier.wireValue)
            .putString(KeyDefaultRealismLevel, settings.defaultRealismLevel.wireValue)
            .putString(KeyActiveBrandProfileId, settings.activeBrandProfileId?.value)
            .putBoolean(KeyPromptHistoryEnabled, settings.promptHistoryEnabled)
            .putString(KeyDefaultCropShape, settings.defaultCropShape.wireValue)
            .putFloat(KeyLookWarmth, settings.look.warmth)
            .putFloat(KeyLookContrast, settings.look.contrast)
            .putFloat(KeyLookSaturation, settings.look.saturation)
            .putFloat(KeyLookFade, settings.look.fade)
            .putString(KeyLogoUri, settings.logo.uri)
            .putFloat(KeyLogoWidth, settings.logo.widthFraction)
            .putFloat(KeyLogoOpacity, settings.logo.opacity)
            .putFloat(KeyLogoMargin, settings.logo.marginFraction)
            .putString(KeyLogoCorner, settings.logo.corner.wireValue)
            .putString(KeySnippets, SnippetCodec.encode(settings.snippets))
            .apply()
    }

    override fun getOpenAiApiKey(): String? =
        secretPreferences.getString(KeyOpenAiApiKey, null)?.takeIf { it.isNotBlank() }

    override fun saveOpenAiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "OpenAI API key cannot be blank" }
        secretPreferences.edit().putString(KeyOpenAiApiKey, trimmed).apply()
    }

    override fun hasOpenAiApiKey(): Boolean = !getOpenAiApiKey().isNullOrBlank()

    override fun clearOpenAiApiKey() {
        secretPreferences.edit().remove(KeyOpenAiApiKey).apply()
    }

    private fun <T> SharedPreferences.enumSetting(
        key: String,
        defaultValue: T,
        fromWireValue: (String) -> T?,
    ): T = getString(key, null)?.let(fromWireValue) ?: defaultValue

    private fun createSecretPreferences(context: Context): SharedPreferences =
        try {
            createEncryptedSecretPreferences(context)
        } catch (firstFailure: Exception) {
            context.deleteSharedPreferences(SecretPreferencesName)
            try {
                createEncryptedSecretPreferences(context)
            } catch (fallbackFailure: Exception) {
                context.getSharedPreferences(SecretPreferencesName, Context.MODE_PRIVATE)
            }
        }

    private fun createEncryptedSecretPreferences(context: Context): SharedPreferences {
        // BYOK secrets are encrypted through AndroidX Security Crypto, backed by
        // a MasterKey stored in the Android Keystore.
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SecretPreferencesName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val SettingsPreferencesName = "shotquill_local_settings"
        const val SecretPreferencesName = "shotquill_secret_settings"
        const val KeyDefaultTargetPlatform = "default_target_platform"
        const val KeyDefaultQualityTier = "default_quality_tier"
        const val KeyDefaultRealismLevel = "default_realism_level"
        const val KeyActiveBrandProfileId = "active_brand_profile_id"
        const val KeyPromptHistoryEnabled = "prompt_history_enabled"
        const val KeyOpenAiApiKey = "openai_api_key"
        const val KeyDefaultCropShape = "default_crop_shape"
        const val KeyLookWarmth = "look_warmth"
        const val KeyLookContrast = "look_contrast"
        const val KeyLookSaturation = "look_saturation"
        const val KeyLookFade = "look_fade"
        const val KeyLogoUri = "logo_uri"
        const val KeyLogoWidth = "logo_width_fraction"
        const val KeyLogoOpacity = "logo_opacity"
        const val KeyLogoMargin = "logo_margin_fraction"
        const val KeyLogoCorner = "logo_corner"
        const val KeySnippets = "caption_snippets"
    }
}
