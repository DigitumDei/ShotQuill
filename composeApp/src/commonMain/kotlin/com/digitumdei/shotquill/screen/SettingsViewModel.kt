package com.digitumdei.shotquill.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.digitumdei.shotquill.image.PhotoRenderer
import com.digitumdei.shotquill.shared.ai.AiOperation
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.shared.settings.LocalAppSettings
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.SecretRedactor
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.social.BlueskySettings
import com.digitumdei.shotquill.shared.social.FirebaseHostSettings
import com.digitumdei.shotquill.shared.social.InstagramSettings
import com.digitumdei.shotquill.shared.social.PublishTarget
import com.digitumdei.shotquill.shared.social.parseFirebaseConfig

data class SettingsState(
    val settings: LocalAppSettings = LocalAppSettings(),
    val logoSize: PixelSize? = null,
    /** Something to tell the user about the logo they just chose, or why choosing it failed. */
    val logoNote: String? = null,
    val logoNoteIsError: Boolean = false,
    /** The saved AI API key with its middle hidden, or null when there is none. The key itself is never shown. */
    val apiKeyHint: String? = null,
    val apiKeyMessage: String? = null,
    /** Whether a Bluesky app password is stored. The password itself is never shown. */
    val blueskyPasswordSaved: Boolean = false,
    val blueskyMessage: String? = null,
    val instagramTokenSaved: Boolean = false,
    val instagramMessage: String? = null,
    val firebasePasswordSaved: Boolean = false,
    val firebaseMessage: String? = null,
) {
    val blueskyConfigured: Boolean
        get() = blueskyPasswordSaved && settings.bluesky.cleanHandle.isNotEmpty()

    /** True when everything Instagram posting needs has been entered: a token and working image hosting. */
    val instagramConfigured: Boolean
        get() = instagramTokenSaved && imageHostConfigured
    val imageHostConfigured: Boolean
        get() = settings.firebaseHost.hasProject && settings.firebaseHost.email.isNotBlank() && firebasePasswordSaved

    val hasApiKey: Boolean get() = apiKeyHint != null
}

/** Every change is written straight away: there is no save button to forget. */
class SettingsViewModel(
    private val repository: LocalSettingsRepository,
    private val renderer: PhotoRenderer?,
    private val clock: EpochClock = EpochClock.Default,
) {
    var state: SettingsState by mutableStateOf(readState())
        private set

    fun setLook(look: LookSettings) = update { it.copy(look = look) }

    fun resetLook() = update { it.copy(look = LookSettings()) }

    fun setDefaultShape(shape: CropShape) = update { it.copy(defaultCropShape = shape) }

    /** The hashtags a new post starts with on [target]. */
    fun setUsualTags(target: PublishTarget, tags: String) = update { it.withUsualTags(target, tags) }

    fun setLogoUri(uri: String?) {
        val previous = state.settings.logo.uri
        update { it.copy(logo = it.logo.copy(uri = uri)) }
        state = state.copy(logoNote = null, logoNoteIsError = false)
        if (previous != null && previous != uri) renderer?.discard(previous)
    }

    /** A logo was chosen and copied in. [caution] is anything about it worth fixing at the source. */
    fun logoImported(uri: String, caution: String?) {
        setLogoUri(uri)
        state = state.copy(logoNote = caution, logoNoteIsError = false)
    }

    /** Choosing a logo failed; the logo already in use, if any, is kept. */
    fun logoImportFailed(message: String) {
        state = state.copy(logoNote = message, logoNoteIsError = true)
    }

    fun setLogoWidth(fraction: Float) = update {
        it.copy(
            logo = it.logo.copy(
                widthFraction = fraction.coerceIn(LogoSettings.MinWidthFraction, LogoSettings.MaxWidthFraction),
            ),
        )
    }

    fun setLogoOpacity(opacity: Float) = update {
        it.copy(logo = it.logo.copy(opacity = opacity.coerceIn(LogoSettings.MinOpacity, 1f)))
    }

    fun setLogoCorner(corner: LogoCorner) = update { it.copy(logo = it.logo.copy(corner = corner)) }

    fun addSnippet() = update { it.copy(snippets = it.snippets + CaptionSnippet(name = "", text = "")) }

    fun updateSnippet(index: Int, snippet: CaptionSnippet) = update { settings ->
        if (index !in settings.snippets.indices) {
            settings
        } else {
            settings.copy(snippets = settings.snippets.toMutableList().also { it[index] = snippet })
        }
    }

    fun removeSnippet(index: Int) = update { settings ->
        if (index !in settings.snippets.indices) {
            settings
        } else {
            settings.copy(snippets = settings.snippets.filterIndexed { i, _ -> i != index })
        }
    }

    fun setAiBaseUrl(url: String) = update { it.copy(ai = it.ai.copy(baseUrl = url.trim())) }

    fun setAiModels(operation: AiOperation, models: List<String>) =
        update { it.copy(ai = it.ai.withModels(operation, models)) }

    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return
        state = try {
            repository.saveOpenAiApiKey(trimmed)
            state.copy(apiKeyHint = SecretRedactor.maskOpenAiApiKey(trimmed), apiKeyMessage = "Key saved.")
        } catch (e: Exception) {
            state.copy(
                apiKeyMessage = SecretRedactor.redactKnownSecrets(
                    "The key could not be saved: ${e.message ?: "unknown error"}",
                    listOf(trimmed),
                ),
            )
        }
    }

    fun setBlueskyHandle(handle: String) = update { it.copy(bluesky = it.bluesky.copy(handle = handle.trim())) }

    fun setBlueskyServer(server: String) = update { it.copy(bluesky = it.bluesky.copy(server = server.trim())) }

    fun saveBlueskyPassword(password: String) {
        val trimmed = password.trim()
        if (trimmed.isEmpty()) return
        state = try {
            repository.saveSecret(BlueskySettings.AppPasswordSecret, trimmed)
            state.copy(blueskyPasswordSaved = true, blueskyMessage = "App password saved.")
        } catch (e: Exception) {
            state.copy(
                blueskyMessage = SecretRedactor.redactKnownSecrets(
                    "The app password could not be saved: ${e.message ?: "unknown error"}",
                    listOf(trimmed),
                ),
            )
        }
    }

    fun clearBlueskyPassword() {
        repository.clearSecret(BlueskySettings.AppPasswordSecret)
        state = state.copy(blueskyPasswordSaved = false, blueskyMessage = "App password removed.")
    }

    /** Shows the outcome of a connection check next to the Bluesky settings. */
    fun showBlueskyMessage(message: String) {
        state = state.copy(blueskyMessage = message)
    }

    fun saveInstagramToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return
        try {
            repository.saveSecret(InstagramSettings.AccessTokenSecret, trimmed)
            val now = clock.nowMillis()
            update {
                it.copy(
                    instagram = InstagramSettings(
                        tokenSavedAtMillis = now,
                        tokenExpiresAtMillis = now + LongLivedTokenMillis,
                    ),
                )
            }
            state = state.copy(instagramTokenSaved = true, instagramMessage = "Token saved. Use the check below to try it.")
        } catch (e: Exception) {
            state = state.copy(
                instagramMessage = SecretRedactor.redactKnownSecrets(
                    "The token could not be saved: ${e.message ?: "unknown error"}",
                    listOf(trimmed),
                ),
            )
        }
    }

    fun clearInstagramToken() {
        repository.clearSecret(InstagramSettings.AccessTokenSecret)
        update { it.copy(instagram = InstagramSettings()) }
        state = state.copy(instagramTokenSaved = false, instagramMessage = "Token removed.")
    }

    /** Shows the outcome of a connection check, and picks up anything the check learned, such as the username. */
    fun showInstagramMessage(message: String) {
        state = state.copy(settings = repository.readSettings(), instagramMessage = message)
    }

    /** Takes project details pasted from the Firebase console, in either form the console offers. */
    fun useFirebaseConfig(pasted: String) {
        val parsed = parseFirebaseConfig(pasted)
        if (parsed == null) {
            state = state.copy(
                firebaseMessage = "That does not contain a Firebase project. Paste the whole google-services.json, " +
                    "or the firebaseConfig block shown for a web app.",
            )
            return
        }
        update { it.copy(firebaseHost = parsed.copy(email = it.firebaseHost.email)) }
        state = state.copy(firebaseMessage = "Using project ${parsed.projectId}, bucket ${parsed.storageBucket}.")
    }

    fun clearFirebaseConfig() {
        update { it.copy(firebaseHost = FirebaseHostSettings(email = it.firebaseHost.email)) }
        state = state.copy(firebaseMessage = "Project removed.")
    }

    fun setFirebaseEmail(email: String) = update { it.copy(firebaseHost = it.firebaseHost.copy(email = email.trim())) }

    fun saveFirebasePassword(password: String) {
        val trimmed = password.trim()
        if (trimmed.isEmpty()) return
        state = try {
            repository.saveSecret(FirebaseHostSettings.UploadPasswordSecret, trimmed)
            state.copy(firebasePasswordSaved = true, firebaseMessage = "Upload password saved.")
        } catch (e: Exception) {
            state.copy(
                firebaseMessage = SecretRedactor.redactKnownSecrets(
                    "The password could not be saved: ${e.message ?: "unknown error"}",
                    listOf(trimmed),
                ),
            )
        }
    }

    fun clearFirebasePassword() {
        repository.clearSecret(FirebaseHostSettings.UploadPasswordSecret)
        state = state.copy(firebasePasswordSaved = false, firebaseMessage = "Upload password removed.")
    }

    fun clearApiKey() {
        repository.clearOpenAiApiKey()
        state = state.copy(apiKeyHint = null, apiKeyMessage = "Key removed. Nothing will be sent anywhere.")
    }

    private fun update(change: (LocalAppSettings) -> LocalAppSettings) {
        val updated = change(state.settings)
        if (updated == state.settings) return
        repository.saveSettings(updated)
        state = state.copy(
            settings = updated,
            logoSize = if (updated.logo.uri == state.settings.logo.uri) {
                state.logoSize
            } else {
                updated.logo.uri?.let { renderer?.orientedSize(it) }
            },
        )
    }

    private companion object {
        // Meta's long-lived tokens last about sixty days from when they are made.
        const val LongLivedTokenMillis = 60L * 24 * 60 * 60 * 1000
    }

    private fun readState(): SettingsState {
        val settings = repository.readSettings()
        return SettingsState(
            settings = settings,
            logoSize = settings.logo.uri?.let { renderer?.orientedSize(it) },
            apiKeyHint = runCatching { repository.getOpenAiApiKey() }.getOrNull()
                ?.let(SecretRedactor::maskOpenAiApiKey),
            blueskyPasswordSaved = runCatching { repository.getSecret(BlueskySettings.AppPasswordSecret) }
                .getOrNull() != null,
            instagramTokenSaved = runCatching { repository.getSecret(InstagramSettings.AccessTokenSecret) }
                .getOrNull() != null,
            firebasePasswordSaved = runCatching { repository.getSecret(FirebaseHostSettings.UploadPasswordSecret) }
                .getOrNull() != null,
        )
    }
}
