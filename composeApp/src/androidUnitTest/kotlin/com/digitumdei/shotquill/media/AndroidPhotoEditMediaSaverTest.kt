package com.digitumdei.shotquill.media

import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.workflow.SaveEditedImageResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.io.File
import java.io.IOException

class AndroidPhotoEditMediaSaverTest {
    private val tmpDir = createTempDir("shotquill-edited-media-test-")
    private val saver = AndroidPhotoEditMediaSaver(filesDir = tmpDir)
    private val originalMediaAsset = MediaAsset(
        id = MediaAssetId("media-original-1"),
        type = MediaType.Photo,
        uri = "file://${tmpDir.absolutePath}/media/originals/camera/img_original.jpg",
        mimeType = "image/jpeg",
        widthPx = 1920,
        heightPx = 1080,
        createdAtEpochMillis = 1_000_000_000_000L,
    )

    @Test
    fun `save writes bytes to edited media directory and returns Success with MediaAsset`() {
        val editedId = MediaAssetId("photo-edited-1700000000000-0")
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val now = 1_000_000_100_000L

        val result = saver.save(bytes, "image/jpeg", originalMediaAsset, editedId, now)

        val success = assertIs<SaveEditedImageResult.Success>(result)
        assertEquals(MediaType.EditedPhoto, success.mediaAsset.type)
        assertEquals(editedId, success.mediaAsset.id)
        assertEquals("image/jpeg", success.mediaAsset.mimeType)
        assertTrue(success.mediaAsset.uri.startsWith("file://"))
        assertTrue(success.mediaAsset.uri.contains("media/edited/"))
        assertTrue(success.mediaAsset.uri.endsWith(".jpg"))
        assertEquals(now, success.mediaAsset.createdAtEpochMillis)

        val savedFile = File(success.mediaAsset.uri.removePrefix("file://"))
        assertTrue(savedFile.exists())
        assertTrue(savedFile.readBytes().contentEquals(bytes))
    }

    @Test
    fun `save derives file extension from mime type`() {
        val editedId = MediaAssetId("photo-edited-png-test")
        val bytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte())

        val result = saver.save(bytes, "image/png", originalMediaAsset, editedId, 1_000_000_200_000L)

        val success = assertIs<SaveEditedImageResult.Success>(result)
        assertTrue(success.mediaAsset.uri.endsWith(".png"))
    }

    @Test
    fun `save decodes image dimensions from real png content`() {
        val editedId = MediaAssetId("photo-edited-dimensions-real")
        val width = 7
        val height = 3
        val pngBytes = createValidPng(width, height)

        val result = saver.save(pngBytes, "image/png", originalMediaAsset, editedId, 1_000_000_300_000L)

        val success = assertIs<SaveEditedImageResult.Success>(result)
        assertEquals(width, success.mediaAsset.widthPx)
        assertEquals(height, success.mediaAsset.heightPx)
        assertEquals("image/png", success.mediaAsset.mimeType)
        assertTrue(success.mediaAsset.uri.endsWith(".png"))

        val savedFile = File(success.mediaAsset.uri.removePrefix("file://"))
        assertTrue(savedFile.exists())
        assertTrue(savedFile.readBytes().contentEquals(pngBytes))
    }

    private fun createValidPng(width: Int, height: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()

        output.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val ihdrData = ByteArray(13)
        ihdrData[0] = (width shr 24).toByte()
        ihdrData[1] = (width shr 16).toByte()
        ihdrData[2] = (width shr 8).toByte()
        ihdrData[3] = width.toByte()
        ihdrData[4] = (height shr 24).toByte()
        ihdrData[5] = (height shr 16).toByte()
        ihdrData[6] = (height shr 8).toByte()
        ihdrData[7] = height.toByte()
        ihdrData[8] = 8
        ihdrData[9] = 2
        ihdrData[10] = 0
        ihdrData[11] = 0
        ihdrData[12] = 0

        writePngChunk(output, "IHDR", ihdrData)

        val raw = ByteArray(height * (1 + width * 3))
        var pos = 0
        for (y in 0 until height) {
            raw[pos++] = 0
            for (x in 0 until width) {
                raw[pos++] = ((x * 255) / maxOf(1, width - 1)).toByte()
                raw[pos++] = ((y * 255) / maxOf(1, height - 1)).toByte()
                raw[pos++] = 64.toByte()
            }
        }

        val deflater = java.util.zip.Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val compressed = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            compressed.write(buf, 0, deflater.deflate(buf))
        }
        deflater.end()

        writePngChunk(output, "IDAT", compressed.toByteArray())
        writePngChunk(output, "IEND", ByteArray(0))

        return output.toByteArray()
    }

    private fun writePngChunk(output: java.io.ByteArrayOutputStream, type: String, data: ByteArray) {
        output.write((data.size shr 24).toByte())
        output.write((data.size shr 16).toByte())
        output.write((data.size shr 8).toByte())
        output.write(data.size.toByte())

        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = java.util.zip.CRC32()
        crc.update(typeBytes)
        crc.update(data)

        output.write(typeBytes)
        output.write(data)

        val crcVal = crc.value
        output.write((crcVal shr 24).toByte())
        output.write((crcVal shr 16).toByte())
        output.write((crcVal shr 8).toByte())
        output.write(crcVal.toByte())
    }

    @Test
    fun `save never overwrites original source file`() {
        val originalFile = File(tmpDir, "source_original.jpg")
        originalFile.writeBytes(byteArrayOf(0, 1, 2))
        val originalAsset = originalMediaAsset.copy(uri = "file://${originalFile.absolutePath}")
        val editedId = MediaAssetId("photo-edited-no-overwrite-test")

        val result = saver.save(
            bytes = byteArrayOf(3, 4, 5),
            mimeType = "image/jpeg",
            originalMediaAsset = originalAsset,
            mediaAssetId = editedId,
            createdAtEpochMillis = 1_000_000_400_000L,
        )

        val success = assertIs<SaveEditedImageResult.Success>(result)
        assertTrue(success.mediaAsset.uri.startsWith("file://"))
        // Original file must be untouched
        assertEquals(3, originalFile.readBytes().size)
        assertEquals(0, originalFile.readBytes()[0])

        // Edited file is different
        val editedFile = File(success.mediaAsset.uri.removePrefix("file://"))
        assertTrue(editedFile.exists())
        assertTrue(editedFile.absolutePath != originalFile.absolutePath)
    }

    @Test
    fun `save cleans up partially written file on failure`() {
        val editedId = MediaAssetId("photo-edited-failure-test")
        val destDir = File(tmpDir, "media/edited")
        val destFile = File(destDir, "${editedId.value}.jpg")
        destDir.mkdirs()

        class FailingWriteSaver(filesDir: File) : AndroidPhotoEditMediaSaver(filesDir) {
            override fun writeBytesToFile(bytes: ByteArray, file: File) {
                file.writeBytes(byteArrayOf(0, 0, 0))
                throw IOException("Simulated write failure")
            }
        }

        val failingSaver = FailingWriteSaver(filesDir = tmpDir)

        val result = failingSaver.save(
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalMediaAsset = originalMediaAsset,
            mediaAssetId = editedId,
            createdAtEpochMillis = 1_000_000_500_000L,
        )

        assertIs<SaveEditedImageResult.Failure>(result)

        // No stray file should have been left behind
        assertFalse(destFile.exists())
        // Directory should still exist
        assertTrue(destDir.exists())
    }

    @Test
    fun `save returns Failure when directory creation fails`() {
        val readOnlyParent = File(tmpDir, "readonly_root")
        readOnlyParent.writeBytes(byteArrayOf(0))
        val failingSaver = AndroidPhotoEditMediaSaver(filesDir = readOnlyParent)

        val result = failingSaver.save(
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalMediaAsset = originalMediaAsset,
            mediaAssetId = MediaAssetId("photo-edited-dir-fail"),
            createdAtEpochMillis = 1_000_000_600_000L,
        )

        assertIs<SaveEditedImageResult.Failure>(result)
    }

    @Test
    fun `save with webp mime type creates webp file`() {
        val editedId = MediaAssetId("photo-edited-webp-test")
        val bytes = byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte())

        val result = saver.save(bytes, "image/webp", originalMediaAsset, editedId, 1_000_000_700_000L)

        val success = assertIs<SaveEditedImageResult.Success>(result)
        assertTrue(success.mediaAsset.uri.endsWith(".webp"))
        assertEquals("image/webp", success.mediaAsset.mimeType)
    }

    @Test
    fun `save uses mediaAssetId as filename`() {
        val editedId = MediaAssetId("photo-edited-unique-filename")
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        val result = saver.save(bytes, "image/jpeg", originalMediaAsset, editedId, 1_000_000_800_000L)

        val success = assertIs<SaveEditedImageResult.Success>(result)
        val savedFile = File(success.mediaAsset.uri.removePrefix("file://"))
        assertEquals("photo-edited-unique-filename.jpg", savedFile.name)
    }

    @Test
    fun `save fails when destination file already exists`() {
        val editedId = MediaAssetId("photo-edited-existing-dest")
        val destDir = File(tmpDir, "media/edited")
        destDir.mkdirs()
        val destFile = File(destDir, "${editedId.value}.jpg")
        destFile.writeBytes(byteArrayOf(9, 9, 9))
        val originalBytes = destFile.readBytes()

        val result = saver.save(
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalMediaAsset = originalMediaAsset,
            mediaAssetId = editedId,
            createdAtEpochMillis = 1_000_000_900_000L,
        )

        assertIs<SaveEditedImageResult.Failure>(result)
        // Existing file must be untouched
        assertTrue(destFile.exists())
        assertTrue(destFile.readBytes().contentEquals(originalBytes))
    }

    @Test
    fun `save fails when original source uri matches destination path`() {
        val editedId = MediaAssetId("photo-edited-self-collision")
        val destDir = File(tmpDir, "media/edited")
        destDir.mkdirs()
        val destFile = File(destDir, "${editedId.value}.jpg")
        // The original asset points to the exact path the saver would write to
        val collidingAsset = originalMediaAsset.copy(uri = "file://${destFile.absolutePath}")

        val result = saver.save(
            bytes = byteArrayOf(4, 5, 6),
            mimeType = "image/jpeg",
            originalMediaAsset = collidingAsset,
            mediaAssetId = editedId,
            createdAtEpochMillis = 1_000_000_950_000L,
        )

        assertIs<SaveEditedImageResult.Failure>(result)
        // No file should have been created
        assertFalse(destFile.exists())
    }
}
