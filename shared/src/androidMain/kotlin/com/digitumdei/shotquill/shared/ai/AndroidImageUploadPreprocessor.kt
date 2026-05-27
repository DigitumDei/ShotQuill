package com.digitumdei.shotquill.shared.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

actual object PlatformImageUploadPreprocessor : AiImageUploadPreprocessor {
    override fun preprocess(
        image: AiImageInput,
        config: ImageUploadPreprocessingConfig,
    ): AiImageInput {
        val bitmap = decodeBitmapDownsampled(image.bytes, config.maxLongEdgePx) ?: return image
        val scaled = bitmap.scaledToMaxLongEdge(config.maxLongEdgePx)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }

        val format = if (config.providerRequiresPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val mimeType = if (config.providerRequiresPng) "image/png" else "image/jpeg"
        val fileName = image.fileName.withImageExtension(if (config.providerRequiresPng) "png" else "jpg")
        val output = ByteArrayOutputStream()
        scaled.compress(format, if (config.providerRequiresPng) 100 else config.jpegQuality, output)
        scaled.recycle()

        return AiImageInput(
            bytes = output.toByteArray(),
            mimeType = mimeType,
            fileName = fileName,
        )
    }
}

private fun decodeBitmapDownsampled(bytes: ByteArray, maxLongEdgePx: Int): Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
    val longEdge = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
    val sampleSize = if (longEdge > maxLongEdgePx) {
        var size = 1
        while (longEdge / (size * 2) >= maxLongEdgePx) {
            size *= 2
        }
        size
    } else {
        1
    }
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
}

private fun Bitmap.scaledToMaxLongEdge(maxLongEdgePx: Int): Bitmap {
    val longEdge = maxOf(width, height)
    if (longEdge <= maxLongEdgePx) return this

    val scale = maxLongEdgePx.toDouble() / longEdge.toDouble()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun String.withImageExtension(extension: String): String {
    val lastSlash = lastIndexOf('/')
    val dotIndex = lastIndexOf('.')
    return if (dotIndex > lastSlash) {
        substring(0, dotIndex + 1) + extension
    } else {
        "$this.$extension"
    }
}
