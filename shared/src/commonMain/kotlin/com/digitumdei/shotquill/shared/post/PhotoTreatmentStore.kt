package com.digitumdei.shotquill.shared.post

import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.social.PhotoTag
import com.digitumdei.shotquill.shared.social.PhotoTagCodec
import com.digitumdei.shotquill.shared.social.PublishTarget

/**
 * What is remembered about one post beside the database: how its photo is
 * treated, the last file made from it, the text written for Bluesky, and a
 * link to each place it has been published to directly.
 */
data class StoredTreatment(
    val treatment: PhotoTreatment,
    val renderedUri: String? = null,
    val blueskyText: String = "",
    val published: Map<PublishTarget, String> = emptyMap(),
    /**
     * The hashtags chosen for this post on each service. A service is only
     * present once its tags have been changed on this post, an empty text
     * meaning "none"; until then the usual tags from settings apply.
     */
    val tags: Map<PublishTarget, String> = emptyMap(),
    /** People tagged on the photo, for Instagram. */
    val photoTags: List<PhotoTag> = emptyList(),
    /** The Instagram collaborators box, as typed. */
    val collaborators: String = "",
)

interface PhotoTreatmentStore {
    fun get(draftId: PostDraftId): StoredTreatment?
    fun save(draftId: PostDraftId, stored: StoredTreatment)
    fun remove(draftId: PostDraftId)
}

class InMemoryPhotoTreatmentStore : PhotoTreatmentStore {
    private val entries = mutableMapOf<String, String>()

    override fun get(draftId: PostDraftId): StoredTreatment? =
        entries[draftId.value]?.let(StoredTreatmentCodec::decode)

    override fun save(draftId: PostDraftId, stored: StoredTreatment) {
        entries[draftId.value] = StoredTreatmentCodec.encode(stored)
    }

    override fun remove(draftId: PostDraftId) {
        entries.remove(draftId.value)
    }
}

/**
 * Turns a [StoredTreatment] into one line-per-field string and back. Unknown
 * or damaged fields fall back to the defaults rather than failing, so a value
 * written by an older build never stops a post from opening.
 */
object StoredTreatmentCodec {
    private const val KeyShape = "shape"
    private const val KeyZoom = "zoom"
    private const val KeyCenterX = "cx"
    private const val KeyCenterY = "cy"
    private const val KeyLook = "look"
    private const val KeyLogo = "logo"
    private const val KeyCorner = "corner"
    private const val KeyRendered = "rendered"
    private const val KeyBlueskyText = "bluesky"
    private const val KeyPublishedPrefix = "published."
    private const val KeyTagsPrefix = "tags."
    private const val KeyPhotoTags = "phototags"
    private const val KeyCollaborators = "collaborators"

    fun encode(stored: StoredTreatment): String = buildString {
        val t = stored.treatment
        appendLine("$KeyShape=${t.cropShape.wireValue}")
        appendLine("$KeyZoom=${t.zoom}")
        appendLine("$KeyCenterX=${t.centerX}")
        appendLine("$KeyCenterY=${t.centerY}")
        appendLine("$KeyLook=${t.lookEnabled}")
        appendLine("$KeyLogo=${t.logoEnabled}")
        t.logoCorner?.let { appendLine("$KeyCorner=${it.wireValue}") }
        stored.renderedUri?.let { appendLine("$KeyRendered=$it") }
        if (stored.blueskyText.isNotEmpty()) appendLine("$KeyBlueskyText=${escape(stored.blueskyText)}")
        stored.published.forEach { (target, url) -> appendLine("$KeyPublishedPrefix${target.wireValue}=$url") }
        stored.tags.forEach { (target, tags) -> appendLine("$KeyTagsPrefix${target.wireValue}=${escape(tags)}") }
        if (stored.photoTags.isNotEmpty()) appendLine("$KeyPhotoTags=${PhotoTagCodec.encode(stored.photoTags)}")
        if (stored.collaborators.isNotEmpty()) appendLine("$KeyCollaborators=${escape(stored.collaborators)}")
    }.trimEnd()

    fun decode(encoded: String): StoredTreatment {
        val fields = encoded.lines()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
        val defaults = PhotoTreatment()
        return StoredTreatment(
            treatment = PhotoTreatment(
                cropShape = fields[KeyShape]?.let(CropShape::fromWireValue) ?: defaults.cropShape,
                zoom = fields[KeyZoom]?.toFloatOrNull()
                    ?.takeIf { it.isFinite() }
                    ?.coerceIn(PhotoTreatment.MinZoom, PhotoTreatment.MaxZoom)
                    ?: defaults.zoom,
                centerX = fields[KeyCenterX].toFraction(defaults.centerX),
                centerY = fields[KeyCenterY].toFraction(defaults.centerY),
                lookEnabled = fields[KeyLook]?.toBooleanStrictOrNull() ?: defaults.lookEnabled,
                logoEnabled = fields[KeyLogo]?.toBooleanStrictOrNull() ?: defaults.logoEnabled,
                logoCorner = fields[KeyCorner]?.let(LogoCorner::fromWireValue),
            ),
            renderedUri = fields[KeyRendered]?.takeIf { it.isNotBlank() },
            blueskyText = fields[KeyBlueskyText]?.let(::unescape).orEmpty(),
            published = fields.entries.mapNotNull { (key, url) ->
                if (!key.startsWith(KeyPublishedPrefix) || url.isBlank()) {
                    null
                } else {
                    PublishTarget.fromWireValue(key.removePrefix(KeyPublishedPrefix))?.let { it to url }
                }
            }.toMap(),
            tags = fields.entries.mapNotNull { (key, tags) ->
                if (!key.startsWith(KeyTagsPrefix)) {
                    null
                } else {
                    PublishTarget.fromWireValue(key.removePrefix(KeyTagsPrefix))?.let { it to unescape(tags) }
                }
            }.toMap(),
            photoTags = fields[KeyPhotoTags]?.let(PhotoTagCodec::decode).orEmpty(),
            collaborators = fields[KeyCollaborators]?.let(::unescape).orEmpty(),
        )
    }

    // Each field sits on its own line, so line breaks inside a text are written as \n.
    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\n", "\\n")

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

    private fun String?.toFraction(fallback: Float): Float =
        this?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: fallback
}
