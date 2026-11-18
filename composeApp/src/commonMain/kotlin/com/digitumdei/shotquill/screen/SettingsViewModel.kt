package com.digitumdei.shotquill.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.digitumdei.shotquill.image.PhotoRenderer
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.shared.settings.LocalAppSettings
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository

data class SettingsState(
    val settings: LocalAppSettings = LocalAppSettings(),
    val logoSize: PixelSize? = null,
    /** True while an OpenAI key saved by an earlier version is still on the device. */
    val hasLegacyApiKey: Boolean = false,
)

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
        if (previous != null && previous != uri) renderer?.discard(previous)
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

    fun clearLegacyApiKey() {
        repository.clearOpenAiApiKey()
        state = state.copy(hasLegacyApiKey = false)
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
            hasLegacyApiKey = runCatching { repository.hasOpenAiApiKey() }.getOrDefault(false),
        )
    }
}
