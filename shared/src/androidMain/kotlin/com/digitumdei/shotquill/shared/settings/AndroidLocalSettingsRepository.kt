package com.digitumdei.shotquill.shared.settings

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.digitumdei.shotquill.shared.ai.AiOperation
import com.digitumdei.shotquill.shared.ai.AiSettings
import com.digitumdei.shotquill.shared.ai.parseModelList
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.social.BlueskySettings
import com.digitumdei.shotquill.shared.social.FirebaseHostSettings
import com.digitumdei.shotquill.shared.social.InstagramSettings
import com.digitumdei.shotquill.shared.social.PublishTarget

class AndroidLocalSettingsRepository(
    context: Context,
    /**
     * Opens the encrypted store for keys, passwords and tokens. It is a
     * parameter so tests can stand in for the Android Keystore, which is not
     * there to use outside a real phone.
     */
    private val openSecretPreferences: (Context) -> SharedPreferences = ::encryptedSecretPreferences,
) : LocalSettingsRepository {
    private val applicationContext = context.applicationContext
    private val regularPreferences: SharedPreferences =
        applicationContext.getSharedPreferences(SettingsPreferencesName, Context.MODE_PRIVATE)
    private val secretStore: SecretStore by lazy { openSecretStore() }

    override val secretStorageNotice: String?
        get() = when (val store = secretStore) {
            is SecretStore.Unavailable -> SecretsUnavailableMessage
            is SecretStore.Available -> if (store.wasReset) SecretsResetMessage else null
        }

    override fun readSettings(): LocalAppSettings =
        LocalAppSettings(
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
            ai = readAi(),
            bluesky = BlueskySettings(
                handle = regularPreferences.getString(KeyBlueskyHandle, null).orEmpty(),
                server = regularPreferences.getString(KeyBlueskyServer, null)?.takeIf { it.isNotBlank() }
                    ?: BlueskySettings.DefaultServer,
            ),
            instagram = InstagramSettings(
                username = regularPreferences.getString(KeyInstagramUsername, null).orEmpty(),
                tokenSavedAtMillis = regularPreferences.getLong(KeyInstagramTokenSavedAt, 0L),
                tokenExpiresAtMillis = regularPreferences.getLong(KeyInstagramTokenExpiresAt, 0L),
            ),
            firebaseHost = FirebaseHostSettings(
                apiKey = regularPreferences.getString(KeyFirebaseApiKey, null).orEmpty(),
                appId = regularPreferences.getString(KeyFirebaseAppId, null).orEmpty(),
                projectId = regularPreferences.getString(KeyFirebaseProjectId, null).orEmpty(),
                storageBucket = regularPreferences.getString(KeyFirebaseBucket, null).orEmpty(),
                email = regularPreferences.getString(KeyFirebaseEmail, null).orEmpty(),
            ),
            usualTags = PublishTarget.entries.mapNotNull { target ->
                regularPreferences.getString(KeyUsualTagsPrefix + target.wireValue, null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { target to it }
            }.toMap(),
        )

    private fun readAi(): AiSettings =
        AiSettings(
            baseUrl = regularPreferences.getString(KeyAiBaseUrl, null)?.takeIf { it.isNotBlank() }
                ?: AiSettings.DefaultBaseUrl,
            models = AiOperation.entries.mapNotNull { operation ->
                regularPreferences.getString(KeyAiModelsPrefix + operation.wireValue, null)
                    ?.let(::parseModelList)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { operation to it }
            }.toMap(),
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
            // Left behind by the removed AI flow; nothing reads them any more.
            .remove("default_target_platform")
            .remove("default_quality_tier")
            .remove("default_realism_level")
            .remove("active_brand_profile_id")
            .remove("prompt_history_enabled")
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
            .putString(KeyAiBaseUrl, settings.ai.baseUrl)
            .putString(KeyBlueskyHandle, settings.bluesky.handle)
            .putString(KeyBlueskyServer, settings.bluesky.server)
            .putString(KeyInstagramUsername, settings.instagram.username)
            .putLong(KeyInstagramTokenSavedAt, settings.instagram.tokenSavedAtMillis)
            .putLong(KeyInstagramTokenExpiresAt, settings.instagram.tokenExpiresAtMillis)
            .putString(KeyFirebaseApiKey, settings.firebaseHost.apiKey)
            .putString(KeyFirebaseAppId, settings.firebaseHost.appId)
            .putString(KeyFirebaseProjectId, settings.firebaseHost.projectId)
            .putString(KeyFirebaseBucket, settings.firebaseHost.storageBucket)
            .putString(KeyFirebaseEmail, settings.firebaseHost.email)
            .also { editor ->
                PublishTarget.entries.forEach { target ->
                    val tags = settings.usualTags[target]
                    if (tags.isNullOrBlank()) {
                        editor.remove(KeyUsualTagsPrefix + target.wireValue)
                    } else {
                        editor.putString(KeyUsualTagsPrefix + target.wireValue, tags)
                    }
                }
            }
            .also { editor ->
                AiOperation.entries.forEach { operation ->
                    val models = settings.ai.models[operation]
                    if (models.isNullOrEmpty()) {
                        editor.remove(KeyAiModelsPrefix + operation.wireValue)
                    } else {
                        editor.putString(KeyAiModelsPrefix + operation.wireValue, models.joinToString("\n"))
                    }
                }
            }
            .apply()
    }

    override fun getOpenAiApiKey(): String? = readSecret(KeyOpenAiApiKey)

    override fun saveOpenAiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "OpenAI API key cannot be blank" }
        writableSecrets().edit().putString(KeyOpenAiApiKey, trimmed).apply()
    }

    override fun hasOpenAiApiKey(): Boolean = !getOpenAiApiKey().isNullOrBlank()

    override fun clearOpenAiApiKey() {
        removeSecret(KeyOpenAiApiKey)
    }

    override fun getSecret(name: String): String? = readSecret(SecretPrefix + name)

    override fun saveSecret(name: String, value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "A secret cannot be blank" }
        writableSecrets().edit().putString(SecretPrefix + name, trimmed).apply()
    }

    override fun clearSecret(name: String) {
        removeSecret(SecretPrefix + name)
    }

    private fun <T> SharedPreferences.enumSetting(
        key: String,
        defaultValue: T,
        fromWireValue: (String) -> T?,
    ): T = getString(key, null)?.let(fromWireValue) ?: defaultValue

    private sealed interface SecretStore {
        /** [wasReset] is true when what was saved before could not be read and had to be cleared. */
        class Available(val preferences: SharedPreferences, val wasReset: Boolean) : SecretStore
        data object Unavailable : SecretStore
    }

    /**
     * Opens the encrypted store. A store that cannot be read, for example
     * after its key was lost, is cleared and opened afresh, and that is
     * reported through [secretStorageNotice] rather than passed over. When
     * encryption is not available at all, secrets are not stored: they are
     * never written without it.
     */
    private fun openSecretStore(): SecretStore =
        try {
            SecretStore.Available(openSecretPreferences(applicationContext), wasReset = false)
        } catch (firstFailure: Exception) {
            val hadSavedSecrets = secretFile().let { it.isFile && it.length() > 0 }
            applicationContext.deleteSharedPreferences(SecretPreferencesName)
            try {
                SecretStore.Available(openSecretPreferences(applicationContext), wasReset = hadSavedSecrets)
            } catch (secondFailure: Exception) {
                SecretStore.Unavailable
            }
        }

    private fun secretFile(): File =
        File(File(applicationContext.applicationInfo.dataDir, "shared_prefs"), "$SecretPreferencesName.xml")

    private fun readSecret(key: String): String? =
        when (val store = secretStore) {
            is SecretStore.Available ->
                // A single value that will not decrypt is treated as not saved.
                runCatching { store.preferences.getString(key, null) }.getOrNull()?.takeIf { it.isNotBlank() }

            is SecretStore.Unavailable -> null
        }

    private fun writableSecrets(): SharedPreferences =
        when (val store = secretStore) {
            is SecretStore.Available -> store.preferences
            is SecretStore.Unavailable -> throw IllegalStateException(SecretsUnavailableMessage)
        }

    private fun removeSecret(key: String) {
        val store = secretStore
        if (store is SecretStore.Available) store.preferences.edit().remove(key).apply()
    }

    companion object {
        const val SecretsUnavailableMessage =
            "This phone's secure storage could not be opened, so keys, passwords and tokens cannot be saved here."
        const val SecretsResetMessage =
            "The saved keys, passwords and tokens could not be read and have been cleared. Enter them again."

        /**
         * Secrets are encrypted through AndroidX Security Crypto, with a key
         * held in the Android Keystore that never leaves the phone.
         */
        fun encryptedSecretPreferences(context: Context): SharedPreferences {
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

        private const val SettingsPreferencesName = "shotquill_local_settings"
        const val SecretPreferencesName = "shotquill_secret_settings"
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
        const val KeyAiBaseUrl = "ai_base_url"
        const val KeyAiModelsPrefix = "ai_models_"
        const val KeyBlueskyHandle = "bluesky_handle"
        const val KeyBlueskyServer = "bluesky_server"
        const val KeyInstagramUsername = "instagram_username"
        const val KeyInstagramTokenSavedAt = "instagram_token_saved_at"
        const val KeyInstagramTokenExpiresAt = "instagram_token_expires_at"
        const val KeyFirebaseApiKey = "firebase_api_key"
        const val KeyFirebaseAppId = "firebase_app_id"
        const val KeyFirebaseProjectId = "firebase_project_id"
        const val KeyFirebaseBucket = "firebase_storage_bucket"
        const val KeyFirebaseEmail = "firebase_upload_email"
        const val KeyUsualTagsPrefix = "usual_tags_"
        const val SecretPrefix = "secret_"
    }
}
