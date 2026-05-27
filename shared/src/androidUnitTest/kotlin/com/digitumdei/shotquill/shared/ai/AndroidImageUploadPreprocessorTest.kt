package com.digitumdei.shotquill.shared.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidImageUploadPreprocessorTest {
    @Test
    fun downscalesReencodesJpegAndStripsMetadataByWritingFreshBitmap() {
        val input = AiImageInput(
            bytes = fixtureJpeg(width = 2400, height = 1200),
            mimeType = "image/jpeg",
            fileName = "fixture.jpeg",
        )

        val output = PlatformImageUploadPreprocessor.preprocess(input, ImageUploadPreprocessingConfig())

        val decoded = BitmapFactory.decodeByteArray(output.bytes, 0, output.bytes.size)
        assertEquals("image/jpeg", output.mimeType)
        assertEquals("fixture.jpg", output.fileName)
        assertEquals(1568, decoded.width)
        assertEquals(784, decoded.height)
        assertTrue(output.bytes.isNotEmpty())
    }

    @Test
    fun canPreservePngWhenProviderRequiresPng() {
        val input = AiImageInput(
            bytes = fixtureJpeg(width = 48, height = 32),
            mimeType = "image/jpeg",
            fileName = "mask.jpeg",
        )

        val output = PlatformImageUploadPreprocessor.preprocess(
            input,
            ImageUploadPreprocessingConfig(providerRequiresPng = true),
        )

        val decoded = BitmapFactory.decodeByteArray(output.bytes, 0, output.bytes.size)
        assertEquals("image/png", output.mimeType)
        assertEquals("mask.png", output.fileName)
        assertEquals(48, decoded.width)
        assertEquals(32, decoded.height)
    }

    private fun fixtureJpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        bitmap.recycle()
        return output.toByteArray()
    }
}
