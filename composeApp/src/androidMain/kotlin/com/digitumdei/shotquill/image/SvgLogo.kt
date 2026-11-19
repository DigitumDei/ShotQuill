package com.digitumdei.shotquill.image

import android.graphics.Canvas
import android.graphics.RectF
import com.caverock.androidsvg.SVG
import com.digitumdei.shotquill.shared.domain.PixelSize
import java.io.File
import kotlin.math.roundToInt

/**
 * A logo kept as a vector. It is drawn straight onto the canvas at whatever
 * size is asked for, so its edges are computed for that exact size instead of
 * being scaled from a fixed grid of pixels.
 */
internal class SvgLogo private constructor(
    private val svg: SVG,
    /** The logo's proportions. Only the ratio matters: a vector has no pixel size of its own. */
    val size: PixelSize,
) {
    fun draw(canvas: Canvas, bounds: RectF, opacity: Float) {
        val alpha = (opacity.coerceIn(0f, 1f) * 255).roundToInt()
        val checkpoint = canvas.saveLayerAlpha(bounds, alpha)
        try {
            svg.renderToCanvas(canvas, bounds)
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }

    companion object {
        /** Returns null when the file is not an SVG that can be drawn, or has no usable size. */
        fun load(file: File): SvgLogo? =
            try {
                val svg = file.inputStream().use { SVG.getFromInputStream(it) }
                val viewBox = svg.documentViewBox
                val width = viewBox?.width() ?: svg.documentWidth
                val height = viewBox?.height() ?: svg.documentHeight
                if (width <= 0f || height <= 0f) {
                    null
                } else {
                    // Scaling to an arbitrary box needs a view box and a size
                    // that follows the box rather than the file's own units.
                    if (viewBox == null) svg.setDocumentViewBox(0f, 0f, width, height)
                    svg.setDocumentWidth("100%")
                    svg.setDocumentHeight("100%")
                    val scale = ProportionScale / maxOf(width, height)
                    SvgLogo(
                        svg = svg,
                        size = PixelSize(
                            width = (width * scale).roundToInt().coerceAtLeast(1),
                            height = (height * scale).roundToInt().coerceAtLeast(1),
                        ),
                    )
                }
            } catch (_: Exception) {
                null
            }

        // Proportions are reported on a 1000-unit scale so rounding to whole
        // numbers does not distort the shape of small documents.
        private const val ProportionScale = 1000f
    }
}
