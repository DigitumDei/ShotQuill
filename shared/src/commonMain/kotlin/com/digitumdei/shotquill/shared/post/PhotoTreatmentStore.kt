package com.digitumdei.shotquill.shared.post

import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PostDraftId

/** What is remembered about one post's photo: how it is treated and the last file made from it. */
data class StoredTreatment(
    val treatment: PhotoTreatment,
    val renderedUri: String? = null,
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
        )
    }

    private fun String?.toFraction(fallback: Float): Float =
        this?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: fallback
}
