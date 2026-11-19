package com.digitumdei.shotquill.shared.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The shapes a post can be cropped to. [aspect] is width divided by height;
 * null means "keep the photo's own shape".
 */
enum class CropShape(val wireValue: String, val label: String, val aspect: Float?) {
    Portrait("portrait_4_5", "4:5", 4f / 5f),
    Square("square_1_1", "1:1", 1f),
    Story("story_9_16", "9:16", 9f / 16f),
    Original("original", "Original", null);

    companion object {
        fun fromWireValue(value: String): CropShape? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class LogoCorner(val wireValue: String) {
    TopStart("top_start"),
    TopEnd("top_end"),
    BottomStart("bottom_start"),
    BottomEnd("bottom_end");

    companion object {
        fun fromWireValue(value: String): LogoCorner? = entries.firstOrNull { it.wireValue == value }
    }
}

/** A rectangle in pixels, measured from the top-left of whatever it sits in. */
data class PixelRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Everything that is done to one photo before it is shared. All of it is plain
 * geometry and colour arithmetic: nothing here asks a model to produce pixels.
 *
 * [centerX] and [centerY] are the middle of the crop as a fraction of the photo
 * (0 = left/top edge, 1 = right/bottom edge). [zoom] of 1 is the largest crop of
 * the chosen shape that fits in the photo.
 */
data class PhotoTreatment(
    val cropShape: CropShape = CropShape.Portrait,
    val zoom: Float = MinZoom,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val lookEnabled: Boolean = true,
    val logoEnabled: Boolean = true,
    /** Null means "use the corner chosen in settings". */
    val logoCorner: LogoCorner? = null,
) {
    companion object {
        const val MinZoom = 1f
        const val MaxZoom = 4f
    }
}

fun PhotoTreatment.cropRect(imageWidth: Int, imageHeight: Int): PixelRect {
    require(imageWidth > 0 && imageHeight > 0) { "Image size must be positive" }
    val imageW = imageWidth.toFloat()
    val imageH = imageHeight.toFloat()
    val aspect = cropShape.aspect ?: (imageW / imageH)
    val baseWidth: Float
    val baseHeight: Float
    if (imageW / imageH > aspect) {
        baseHeight = imageH
        baseWidth = imageH * aspect
    } else {
        baseWidth = imageW
        baseHeight = imageW / aspect
    }
    val safeZoom = zoom.coerceIn(PhotoTreatment.MinZoom, PhotoTreatment.MaxZoom)
    val width = baseWidth / safeZoom
    val height = baseHeight / safeZoom
    val cx = (centerX * imageW).coerceIn(width / 2f, imageW - width / 2f)
    val cy = (centerY * imageH).coerceIn(height / 2f, imageH - height / 2f)
    return PixelRect(left = cx - width / 2f, top = cy - height / 2f, width = width, height = height)
}

/**
 * Applies a drag and pinch. [panXFraction] and [panYFraction] are how far the
 * finger moved as a fraction of the visible crop (dragging right is positive,
 * which slides the photo right and so moves the crop left).
 */
fun PhotoTreatment.panZoomed(
    panXFraction: Float,
    panYFraction: Float,
    zoomChange: Float,
    imageWidth: Int,
    imageHeight: Int,
): PhotoTreatment {
    val before = cropRect(imageWidth, imageHeight)
    val newZoom = (zoom * zoomChange).coerceIn(PhotoTreatment.MinZoom, PhotoTreatment.MaxZoom)
    val centerXPx = before.left + before.width / 2f - panXFraction * before.width
    val centerYPx = before.top + before.height / 2f - panYFraction * before.height
    val moved = copy(
        zoom = newZoom,
        centerX = centerXPx / imageWidth,
        centerY = centerYPx / imageHeight,
    )
    val after = moved.cropRect(imageWidth, imageHeight)
    return moved.copy(
        centerX = (after.left + after.width / 2f) / imageWidth,
        centerY = (after.top + after.height / 2f) / imageHeight,
    )
}

fun PhotoTreatment.withShape(shape: CropShape): PhotoTreatment =
    copy(cropShape = shape, zoom = PhotoTreatment.MinZoom, centerX = 0.5f, centerY = 0.5f)

data class PixelSize(val width: Int, val height: Int)

/** The size the shared image is written at: never wider than [maxWidth], never upscaled. */
fun exportSize(crop: PixelRect, maxWidth: Int = InstagramWidthPx): PixelSize {
    val width = min(maxWidth, max(1, crop.width.roundToInt()))
    val height = max(1, (width * crop.height / crop.width).roundToInt())
    return PixelSize(width, height)
}

const val InstagramWidthPx = 1080

/**
 * The one consistent look. Each value runs from -1 to 1 with 0 meaning "leave
 * the photo alone"; [fade] runs from 0 to 1.
 */
data class LookSettings(
    val warmth: Float = 0.2f,
    val contrast: Float = 0.15f,
    val saturation: Float = -0.1f,
    val fade: Float = 0.1f,
) {
    val isNeutral: Boolean
        get() = warmth == 0f && contrast == 0f && saturation == 0f && fade == 0f

    companion object {
        val Neutral = LookSettings(warmth = 0f, contrast = 0f, saturation = 0f, fade = 0f)
    }
}

/**
 * The look as a 4x5 colour matrix in row order (R, G, B, A rows; the fifth
 * column is an offset on a 0-255 scale). Android and Compose both read this
 * layout, so the preview and the exported file use the same numbers.
 */
fun LookSettings.toColorMatrix(): FloatArray {
    val s = 1f + saturation.coerceIn(-1f, 1f)
    val lr = 0.213f
    val lg = 0.715f
    val lb = 0.072f
    val saturationMatrix = floatArrayOf(
        lr * (1 - s) + s, lg * (1 - s), lb * (1 - s), 0f, 0f,
        lr * (1 - s), lg * (1 - s) + s, lb * (1 - s), 0f, 0f,
        lr * (1 - s), lg * (1 - s), lb * (1 - s) + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    val c = 1f + contrast.coerceIn(-1f, 1f) * 0.5f
    val contrastOffset = (1f - c) * 127.5f
    val contrastMatrix = floatArrayOf(
        c, 0f, 0f, 0f, contrastOffset,
        0f, c, 0f, 0f, contrastOffset,
        0f, 0f, c, 0f, contrastOffset,
        0f, 0f, 0f, 1f, 0f,
    )

    val w = warmth.coerceIn(-1f, 1f)
    val warmthMatrix = floatArrayOf(
        1f + 0.12f * w, 0f, 0f, 0f, 0f,
        0f, 1f + 0.02f * w, 0f, 0f, 0f,
        0f, 0f, 1f - 0.12f * w, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    val lift = fade.coerceIn(0f, 1f) * 40f
    val fadeScale = 1f - lift / 255f
    val fadeMatrix = floatArrayOf(
        fadeScale, 0f, 0f, 0f, lift,
        0f, fadeScale, 0f, 0f, lift,
        0f, 0f, fadeScale, 0f, lift,
        0f, 0f, 0f, 1f, 0f,
    )

    return concatColorMatrices(
        fadeMatrix,
        concatColorMatrices(warmthMatrix, concatColorMatrices(contrastMatrix, saturationMatrix)),
    )
}

val IdentityColorMatrix: FloatArray
    get() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

/** Returns the matrix that applies [first] and then [second]. */
internal fun concatColorMatrices(second: FloatArray, first: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 5) {
            var sum = 0f
            for (k in 0 until 4) {
                sum += second[row * 5 + k] * first[k * 5 + col]
            }
            if (col == 4) sum += second[row * 5 + 4]
            out[row * 5 + col] = sum
        }
    }
    return out
}

/** Where the brand mark goes. Fractions are of the shared image's width. */
data class LogoSettings(
    /** A file URI for the imported logo, or null when none has been chosen. */
    val uri: String? = null,
    val widthFraction: Float = 0.22f,
    val opacity: Float = 0.9f,
    val marginFraction: Float = 0.04f,
    val corner: LogoCorner = LogoCorner.BottomEnd,
    /**
     * How dark the soft shadow under the logo is, from 0 (no shadow) to 1.
     * Drawn by the app, so it is the same on every post and works for any
     * logo: a shadow drawn into an SVG as a filter is not something the app
     * can render.
     */
    val shadow: Float = 0f,
) {
    companion object {
        const val MinWidthFraction = 0.08f
        const val MaxWidthFraction = 0.5f
        const val MinOpacity = 0.2f
    }
}

/**
 * The shadow to draw under a logo, in pixels of whatever the logo is being
 * drawn on. The same numbers serve the preview and the finished image.
 */
data class LogoShadow(
    /** How far the shadow is spread, as the standard deviation of the blur. */
    val blurSigma: Float,
    val offsetX: Float,
    val offsetY: Float,
    /**
     * How dark, from 0 to 1. The logo's own strength is not included: logo and
     * shadow are put together first and then faded as one, so the shadow never
     * shows through a logo that is slightly see-through.
     */
    val alpha: Float,
) {
    /**
     * The same spread in the "radius" that Android's and Compose's blurs are
     * given, which they turn back into a standard deviation the opposite way.
     */
    val blurRadius: Float get() = ((blurSigma - 0.5f) / RadiusToSigma).coerceAtLeast(MinBlurRadius)

    private companion object {
        const val RadiusToSigma = 0.57735f
        const val MinBlurRadius = 0.1f
    }
}

/**
 * The shadow for a logo drawn [logoWidth] pixels wide, or null when the shadow
 * is switched off. Its size follows the logo's, so it looks the same at any
 * size: a small soft shadow down and to the right, in the proportions of a
 * drop shadow as a drawing program makes one.
 */
fun logoShadow(logo: LogoSettings, logoWidth: Float): LogoShadow? {
    val darkness = logo.shadow.coerceIn(0f, 1f)
    if (darkness <= 0f || logoWidth <= 0f) return null
    return LogoShadow(
        blurSigma = logoWidth * ShadowBlurFraction,
        offsetX = logoWidth * ShadowOffsetFraction,
        offsetY = logoWidth * ShadowOffsetFraction,
        alpha = darkness,
    )
}

// A blur of 2.5 and an offset of 2 on a logo about 400 wide.
private const val ShadowBlurFraction = 0.0065f
private const val ShadowOffsetFraction = 0.005f

fun logoPlacement(
    canvasWidth: Float,
    canvasHeight: Float,
    logoAspect: Float,
    logo: LogoSettings,
    corner: LogoCorner = logo.corner,
): PixelRect {
    val width = canvasWidth * logo.widthFraction.coerceIn(LogoSettings.MinWidthFraction, LogoSettings.MaxWidthFraction)
    val height = if (logoAspect > 0f) width / logoAspect else width
    val margin = canvasWidth * logo.marginFraction.coerceAtLeast(0f)
    val left = when (corner) {
        LogoCorner.TopStart, LogoCorner.BottomStart -> margin
        LogoCorner.TopEnd, LogoCorner.BottomEnd -> canvasWidth - margin - width
    }
    val top = when (corner) {
        LogoCorner.TopStart, LogoCorner.TopEnd -> margin
        LogoCorner.BottomStart, LogoCorner.BottomEnd -> canvasHeight - margin - height
    }
    return PixelRect(left = left, top = top, width = width, height = height)
}

/** A piece of text kept in settings and dropped into a caption with one tap. */
data class CaptionSnippet(
    val name: String,
    val text: String,
) {
    companion object {
        val Defaults: List<CaptionSnippet> = listOf(
            CaptionSnippet(name = "18+", text = "Not for persons under the age of 18."),
        )
    }
}

data class CaptionStats(
    val characters: Int,
    val hashtags: Int,
) {
    val overCharacterLimit: Boolean get() = characters > InstagramCaptionLimit
    val overHashtagLimit: Boolean get() = hashtags > InstagramHashtagLimit

    companion object {
        const val InstagramCaptionLimit = 2200
        const val InstagramHashtagLimit = 30
    }
}

private val HashtagPattern = Regex("(^|\\s)#[\\p{L}\\p{N}_]+")

fun captionStats(caption: String): CaptionStats =
    CaptionStats(characters = caption.length, hashtags = HashtagPattern.findAll(caption).count())

/**
 * Adds a dictated phrase to [caption]. The phrase follows on the same line
 * after a space, and starts with a capital when it opens the caption, a new
 * line or a new sentence. The spoken words themselves are left as they came.
 */
fun appendDictation(caption: String, spoken: String): String {
    val phrase = spoken.trim()
    if (phrase.isEmpty()) return caption
    val startsFresh = caption.isEmpty() || caption.endsWith('\n')
    val startsSentence = startsFresh || caption.trimEnd().lastOrNull() in SentenceEnders
    val text = if (startsSentence) phrase.replaceFirstChar { it.uppercaseChar() } else phrase
    val separator = if (startsFresh || caption.last().isWhitespace()) "" else " "
    return caption + separator + text
}

private val SentenceEnders = setOf('.', '!', '?')

/** Adds [snippet] on its own line at the end of [caption], without doubling blank lines. */
fun appendSnippet(caption: String, snippet: String): String {
    val addition = snippet.trim()
    if (addition.isEmpty()) return caption
    val base = caption.trimEnd()
    return if (base.isEmpty()) addition else "$base\n\n$addition"
}
