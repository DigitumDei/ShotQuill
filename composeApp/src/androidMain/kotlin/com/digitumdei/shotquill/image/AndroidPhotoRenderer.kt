package com.digitumdei.shotquill.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PixelRect
import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.shared.domain.cropRect
import com.digitumdei.shotquill.shared.domain.exportSize
import com.digitumdei.shotquill.shared.domain.logoPlacement
import com.digitumdei.shotquill.shared.domain.toColorMatrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Draws the shared image with the platform's own bitmap tools: decode, crop,
 * colour matrix, logo, JPEG. The file it writes carries no camera metadata,
 * so the location and device details of the original stay on the phone.
 */
class AndroidPhotoRenderer(
    private val filesDir: File,
) : PhotoRenderer {
    override fun orientedSize(uri: String): PixelSize? {
        val path = uri.toFilePath() ?: return null
        if (path.isSvgPath()) return SvgLogo.load(File(path))?.size
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return if (rotationDegrees(path) % 180 != 0) {
            PixelSize(bounds.outHeight, bounds.outWidth)
        } else {
            PixelSize(bounds.outWidth, bounds.outHeight)
        }
    }

    override fun render(request: RenderRequest): RenderOutcome {
        var source: Bitmap? = null
        var output: Bitmap? = null
        return try {
            val path = request.sourceUri.toFilePath()
                ?: return RenderOutcome.Failure("The photo is not stored on this phone.")
            val size = orientedSize(request.sourceUri)
                ?: return RenderOutcome.Failure("The photo could not be read.")
            val crop = request.treatment.cropRect(size.width, size.height)
            val target = exportSize(crop)

            val photo = decodeOriented(path, sampleSizeFor(crop.width, target.width))
                ?: return RenderOutcome.Failure("The photo could not be opened.")
            source = photo
            val sourceRect = cropWithin(photo, crop, decodeScale = photo.width.toFloat() / size.width)

            val finished = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
            output = finished
            val canvas = Canvas(finished)
            val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            if (request.treatment.lookEnabled && !request.look.isNeutral) {
                photoPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix(request.look.toColorMatrix()))
            }
            canvas.drawBitmap(
                photo,
                sourceRect,
                RectF(0f, 0f, target.width.toFloat(), target.height.toFloat()),
                photoPaint,
            )

            if (request.treatment.logoEnabled) {
                drawLogo(canvas, target, request)
            }

            val directory = File(filesDir, OutputDirectory)
            if (!directory.mkdirs() && !directory.exists()) {
                return RenderOutcome.Failure("There is nowhere to save the image.")
            }
            val file = File(directory, "${request.outputName}.jpg")
            FileOutputStream(file).use { stream ->
                if (!finished.compress(Bitmap.CompressFormat.JPEG, JpegQuality, stream)) {
                    file.delete()
                    return RenderOutcome.Failure("The image could not be written.")
                }
            }
            RenderOutcome.Success(uri = "file://${file.absolutePath}", size = target)
        } catch (_: OutOfMemoryError) {
            RenderOutcome.Failure("The photo is too large to prepare on this phone.")
        } catch (e: Exception) {
            RenderOutcome.Failure("The image could not be prepared: ${e.message ?: "unknown error"}")
        } finally {
            source?.recycle()
            output?.recycle()
        }
    }

    override fun encodeForDescription(sourceUri: String, treatment: PhotoTreatment, maxEdge: Int): ByteArray? {
        var source: Bitmap? = null
        var output: Bitmap? = null
        return try {
            val path = sourceUri.toFilePath() ?: return null
            val size = orientedSize(sourceUri) ?: return null
            val crop = treatment.cropRect(size.width, size.height)
            val scale = minOf(1f, maxEdge / maxOf(crop.width, crop.height))
            val width = (crop.width * scale).roundToInt().coerceAtLeast(1)
            val height = (crop.height * scale).roundToInt().coerceAtLeast(1)

            val photo = decodeOriented(path, sampleSizeFor(crop.width, width)) ?: return null
            source = photo
            val reduced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            output = reduced
            Canvas(reduced).drawBitmap(
                photo,
                cropWithin(photo, crop, decodeScale = photo.width.toFloat() / size.width),
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
            val bytes = ByteArrayOutputStream()
            if (reduced.compress(Bitmap.CompressFormat.JPEG, DescriptionJpegQuality, bytes)) bytes.toByteArray() else null
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        } finally {
            source?.recycle()
            output?.recycle()
        }
    }

    /** The crop, given in full-size photo pixels, as a rectangle inside the (possibly reduced) decoded bitmap. */
    private fun cropWithin(photo: Bitmap, crop: PixelRect, decodeScale: Float): Rect =
        Rect(
            (crop.left * decodeScale).roundToInt().coerceIn(0, photo.width - 1),
            (crop.top * decodeScale).roundToInt().coerceIn(0, photo.height - 1),
            ((crop.left + crop.width) * decodeScale).roundToInt().coerceIn(1, photo.width),
            ((crop.top + crop.height) * decodeScale).roundToInt().coerceIn(1, photo.height),
        )

    override fun discard(uri: String) {
        val path = uri.toFilePath() ?: return
        runCatching { File(path).delete() }
    }

    private fun drawLogo(canvas: Canvas, target: PixelSize, request: RenderRequest) {
        val logoPath = request.logo.uri?.toFilePath() ?: return
        val corner = request.treatment.logoCorner ?: request.logo.corner

        fun boundsFor(aspect: Float): RectF {
            val placement = logoPlacement(
                canvasWidth = target.width.toFloat(),
                canvasHeight = target.height.toFloat(),
                logoAspect = aspect,
                logo = request.logo,
                corner = corner,
            )
            return RectF(
                placement.left,
                placement.top,
                placement.left + placement.width,
                placement.top + placement.height,
            )
        }

        if (logoPath.isSvgPath()) {
            // A vector logo is drawn at exactly the size it will appear.
            val svgLogo = SvgLogo.load(File(logoPath)) ?: return
            val bounds = boundsFor(svgLogo.size.width.toFloat() / svgLogo.size.height)
            svgLogo.draw(canvas, bounds, request.logo.opacity)
            return
        }

        val logoBitmap = BitmapFactory.decodeFile(logoPath) ?: return
        var reduced: Bitmap? = null
        try {
            val bounds = boundsFor(logoBitmap.width.toFloat() / logoBitmap.height)
            val nearSize = halveTowards(logoBitmap, bounds.width())
            reduced = nearSize
            val logoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (request.logo.opacity.coerceIn(0f, 1f) * 255).roundToInt()
            }
            canvas.drawBitmap(nearSize, null, bounds, logoPaint)
        } finally {
            if (reduced != null && reduced !== logoBitmap) reduced.recycle()
            logoBitmap.recycle()
        }
    }

    /**
     * Shrinks a bitmap by halves until it is less than twice the size it will
     * be drawn at. Each halving averages every pixel, so fine lines survive;
     * shrinking a large image in one step would skip most of its pixels and
     * leave thin strokes ragged.
     */
    private fun halveTowards(source: Bitmap, targetWidth: Float): Bitmap {
        var current = source
        while (current.width / 2 >= targetWidth && current.width >= 4 && current.height >= 4) {
            val half = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== source) current.recycle()
            current = half
        }
        return current
    }

    /** Decodes at a reduced size when the photo is far larger than needed, then turns it upright. */
    private fun decodeOriented(path: String, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        val degrees = rotationDegrees(path)
        if (degrees == 0) return decoded
        val rotated = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            Matrix().apply { postRotate(degrees.toFloat()) },
            true,
        )
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun sampleSizeFor(cropWidth: Float, targetWidth: Int): Int {
        var sample = 1
        while (cropWidth / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }

    private fun rotationDegrees(path: String): Int =
        try {
            when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }

    private fun String.toFilePath(): String? =
        if (startsWith("file://")) removePrefix("file://").takeIf { it.isNotBlank() } else null

    private companion object {
        const val OutputDirectory = "media/edited"
        const val JpegQuality = 92
        const val DescriptionJpegQuality = 85
    }
}
