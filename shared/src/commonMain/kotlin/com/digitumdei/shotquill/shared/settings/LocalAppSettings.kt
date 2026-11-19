package com.digitumdei.shotquill.shared.settings

import com.digitumdei.shotquill.shared.ai.AiSettings
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.social.BlueskySettings
import com.digitumdei.shotquill.shared.social.FirebaseHostSettings
import com.digitumdei.shotquill.shared.social.InstagramSettings
import com.digitumdei.shotquill.shared.social.PublishTarget

data class LocalAppSettings(
    val defaultTargetPlatform: TargetPlatform = TargetPlatform.InstagramFeedSquare,
    val defaultQualityTier: QualityTier = QualityTier.Standard,
    val defaultRealismLevel: RealismLevel = RealismLevel.Photoreal,
    val activeBrandProfileId: BrandProfileId? = null,
    val promptHistoryEnabled: Boolean = true,
    val defaultCropShape: CropShape = CropShape.Portrait,
    val look: LookSettings = LookSettings(),
    val logo: LogoSettings = LogoSettings(),
    val snippets: List<CaptionSnippet> = CaptionSnippet.Defaults,
    val ai: AiSettings = AiSettings(),
    val bluesky: BlueskySettings = BlueskySettings(),
    val instagram: InstagramSettings = InstagramSettings(),
    val firebaseHost: FirebaseHostSettings = FirebaseHostSettings(),
    /** The hashtags a new post starts with on each service. A service with no entry starts with none. */
    val usualTags: Map<PublishTarget, String> = emptyMap(),
) {
    fun usualTagsFor(target: PublishTarget): String = usualTags[target].orEmpty()

    fun withUsualTags(target: PublishTarget, tags: String): LocalAppSettings =
        copy(usualTags = if (tags.isBlank()) usualTags - target else usualTags + (target to tags))
}

interface LocalSettingsRepository {
    fun readSettings(): LocalAppSettings
    fun saveSettings(settings: LocalAppSettings)
    fun getOpenAiApiKey(): String?
    fun saveOpenAiApiKey(apiKey: String)
    fun hasOpenAiApiKey(): Boolean
    fun clearOpenAiApiKey()

    /** A stored secret such as an app password, or null when none is saved under [name]. */
    fun getSecret(name: String): String?

    /** Throws, with words for the user, when secrets cannot be stored safely on this phone. */
    fun saveSecret(name: String, value: String)
    fun clearSecret(name: String)

    /**
     * Something the user should know about where keys, passwords and tokens
     * are kept: that they cannot be stored on this phone, or that the saved
     * ones could not be read and are gone. Null when all is well.
     */
    val secretStorageNotice: String? get() = null
}

/**
 * Stores the caption snippets as one string. Each snippet is a name line and a
 * text line; newlines and backslashes inside either are escaped so a snippet
 * can itself span several lines.
 */
object SnippetCodec {
    fun encode(snippets: List<CaptionSnippet>): String =
        snippets.joinToString("\n") { "${escape(it.name)}\n${escape(it.text)}" }

    fun decode(encoded: String): List<CaptionSnippet> {
        if (encoded.isEmpty()) return emptyList()
        return encoded.split("\n")
            .chunked(2)
            .filter { it.size == 2 }
            .map { (name, text) -> CaptionSnippet(name = unescape(name), text = unescape(text)) }
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n")

    private fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '\\' && index + 1 < value.length) {
                out.append(if (value[index + 1] == 'n') '\n' else value[index + 1])
                index += 2
            } else {
                out.append(current)
                index += 1
            }
        }
        return out.toString()
    }
}
