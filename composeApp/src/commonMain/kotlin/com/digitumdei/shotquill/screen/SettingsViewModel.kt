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

data class SettingsState(
    val settings: LocalAppSettings = LocalAppSettings(),
    val logoSize: PixelSize? = null,
    /** Something to tell the user about the logo they just chose, or why choosing it failed. */
    val logoNote: String? = null,
    val logoNoteIsError: Boolean = false,
    /** The saved AI API key with its middle hidden, or null when there is none. The key itself is never shown. */
    val apiKeyHint: String? = null,
    val apiKeyMessage: String? = null,
) {
    val hasApiKey: Boolean get() = apiKeyHint != null
}

/** Every change is written straight away: there is no save button to forget. */
class SettingsViewModel(
    private val repository: LocalSettingsRepository,
    private val renderer: PhotoRenderer?,
) {
    var state: SettingsState by mutableStateOf(readState())
        private set

    fun setLook(look: LookSettings) = update { it.copy(look = look) }

    fun resetLook() = update { it.copy(look = LookSettings()) }

    fun setDefaultShape(shape: CropShape) = update { it.copy(defaultCropShape = shape) }

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

    private fun readState(): SettingsState {
        val settings = repository.readSettings()
        return SettingsState(
            settings = settings,
            logoSize = settings.logo.uri?.let { renderer?.orientedSize(it) },
            apiKeyHint = runCatching { repository.getOpenAiApiKey() }.getOrNull()
                ?.let(SecretRedactor::maskOpenAiApiKey),
        )
    }
}
