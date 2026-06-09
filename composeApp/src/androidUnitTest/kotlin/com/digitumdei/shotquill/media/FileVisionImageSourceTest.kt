package com.digitumdei.shotquill.media

import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.workflow.SourceImageResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.io.File

class FileVisionImageSourceTest {
    private val tmpDir = createTempDir("shotquill-vision-source-test-")
    private val source = FileVisionImageSource()

    @Test
    fun `load succeeds for valid file URI with existing file`() {
        val testFile = File(tmpDir, "test_vision.jpg")
        val content = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        testFile.writeBytes(content)
        val asset = MediaAsset(
            id = MediaAssetId("vision-src-1"),
            type = MediaType.Photo,
            uri = "file://${testFile.absolutePath}",
            mimeType = "image/jpeg",
            widthPx = 100,
            heightPx = 100,
            createdAtEpochMillis = 1000L,
        )

        val result = source.load(asset)

        assertIs<SourceImageResult.Success>(result)
        val image = (result as SourceImageResult.Success).image
        assertTrue(image.bytes.contentEquals(content))
        assertEquals("image/jpeg", image.mimeType)
        assertEquals("test_vision.jpg", image.fileName)
    }

    @Test
    fun `load returns Failure for content URI scheme`() {
        val asset = MediaAsset(
            id = MediaAssetId("vision-src-2"),
            type = MediaType.Photo,
            uri = "content://com.example.provider/photos/42",
            mimeType = "image/jpeg",
            widthPx = 100,
            heightPx = 100,
            createdAtEpochMillis = 1000L,
        )

        val result = source.load(asset)

        assertIs<SourceImageResult.Failure>(result)
        assertTrue(result.message.contains("Unsupported URI scheme"))
        assertTrue(result.message.contains("content"))
    }

    @Test
    fun `load returns Failure for URI without scheme`() {
        val testFile = File(tmpDir, "test_noscheme.jpg")
        testFile.writeBytes(byteArrayOf(1, 2, 3))
        val asset = MediaAsset(
            id = MediaAssetId("vision-src-3"),
            type = MediaType.Photo,
            uri = testFile.absolutePath,
            mimeType = "image/jpeg",
            widthPx = 100,
            heightPx = 100,
            createdAtEpochMillis = 1000L,
        )

        val result = source.load(asset)

        assertIs<SourceImageResult.Failure>(result)
        assertTrue(result.message.contains("Unsupported URI scheme"))
    }

    @Test
    fun `load returns Failure for scheme-only file URI`() {
        val asset = MediaAsset(
            id = MediaAssetId("vision-src-4"),
            type = MediaType.Photo,
            uri = "file://",
            mimeType = "image/jpeg",
            widthPx = 100,
            heightPx = 100,
            createdAtEpochMillis = 1000L,
        )

        val result = source.load(asset)

        assertIs<SourceImageResult.Failure>(result)
        assertTrue(result.message.contains("missing a file path"))
    }

    @Test
    fun `load returns Failure for missing file`() {
        val missingFile = File(tmpDir, "nonexistent.jpg")
        val asset = MediaAsset(
            id = MediaAssetId("vision-src-5"),
            type = MediaType.Photo,
            uri = "file://${missingFile.absolutePath}",
            mimeType = "image/jpeg",
            widthPx = 100,
            heightPx = 100,
            createdAtEpochMillis = 1000L,
        )

        val result = source.load(asset)

        assertIs<SourceImageResult.Failure>(result)
        assertTrue(result.message.contains("Source file not found"))
    }

    @Test
    fun `load infers mime type from file name when asset mimeType is null`() {
        val testFile = File(tmpDir, "inferred.png")
        testFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50.toByte()))
        val asset = MediaAsset(
            id = MediaAssetId("vision-src-6"),
            type = MediaType.Photo,
            uri = "file://${testFile.absolutePath}",
            mimeType = null,
            widthPx = 100,
            heightPx = 100,
            createdAtEpochMillis = 1000L,
        )

        val result = source.load(asset)

        assertIs<SourceImageResult.Success>(result)
        val image = (result as SourceImageResult.Success).image
        assertEquals("image/png", image.mimeType)
    }
}
