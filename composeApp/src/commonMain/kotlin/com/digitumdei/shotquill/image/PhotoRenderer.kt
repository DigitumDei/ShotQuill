package com.digitumdei.shotquill.image

import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PixelSize

data class RenderRequest(
    val sourceUri: String,
    val treatment: PhotoTreatment,
    val look: LookSettings,
    val logo: LogoSettings,
    /** File name without extension for the image that is written. */
    val outputName: String,
)

sealed interface RenderOutcome {
    data class Success(val uri: String, val size: PixelSize) : RenderOutcome
    data class Failure(val message: String) : RenderOutcome
}

/**
 * Produces the image that is shared: the photo cropped, graded and stamped
 * exactly as the preview shows it. Implementations do this with ordinary
 * bitmap drawing on the device.
 */
interface PhotoRenderer {
    /** The photo's size as it is displayed, that is after any camera rotation is applied. */
    fun orientedSize(uri: String): PixelSize?

    fun render(request: RenderRequest): RenderOutcome

    /**
     * The photo cropped as it will be shared, without the look or the logo, as
     * a small JPEG suitable for sending to be described. Null when the photo
     * cannot be read.
     */
    fun encodeForDescription(sourceUri: String, treatment: PhotoTreatment, maxEdge: Int = 1024): ByteArray?

    /**
     * The rendered image at [uri] as JPEG bytes no larger than [maxBytes],
     * recompressed only as far as needed. Null when it cannot be read or made
     * to fit.
     */
    fun jpegWithin(uri: String, maxBytes: Int): ByteArray?

    /** Deletes a previously rendered image. Missing files are ignored. */
    fun discard(uri: String)

    /** How much room the file at [uri] takes on the phone, in bytes. Zero when it is not there. */
    fun storedBytes(uri: String): Long = 0L
}
