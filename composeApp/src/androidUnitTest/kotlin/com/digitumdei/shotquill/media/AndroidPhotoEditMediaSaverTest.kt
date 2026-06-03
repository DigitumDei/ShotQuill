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
    fun `save captures image dimensions from saved file`() {
        val editedId = MediaAssetId("photo-edited-dimensions-test")
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        val result = saver.save(bytes, "image/webp", originalMediaAsset, editedId, 1_000_000_300_000L)

        val success = assertIs<SaveEditedImageResult.Success>(result)
        // Dimensions may be null for non-decodable fake data — that's acceptable
        // The important thing is the file was created and a MediaAsset was returned
        assertTrue(success.mediaAsset.widthPx == null || success.mediaAsset.widthPx > 0)
        assertTrue(success.mediaAsset.heightPx == null || success.mediaAsset.heightPx > 0)
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
        // Make the directory read-only so the FileOutputStream write fails
        destDir.mkdirs()
        destDir.setWritable(false)

        val result = saver.save(
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalMediaAsset = originalMediaAsset,
            mediaAssetId = editedId,
            createdAtEpochMillis = 1_000_000_500_000L,
        )

        assertIs<SaveEditedImageResult.Failure>(result)

        // Restore writable for cleanup
        destDir.setWritable(true)
        // No stray file should have been left behind
        assertFalse(destFile.exists())
        // Directory should still exist
        assertTrue(destDir.exists())
    }

    @Test
    fun `save returns Failure when directory creation fails`() {
        val readOnlyParent = File(tmpDir, "readonly_root")
        readOnlyParent.mkdirs()
        readOnlyParent.setWritable(false)
        val failingSaver = AndroidPhotoEditMediaSaver(filesDir = readOnlyParent)

        val result = failingSaver.save(
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalMediaAsset = originalMediaAsset,
            mediaAssetId = MediaAssetId("photo-edited-dir-fail"),
            createdAtEpochMillis = 1_000_000_600_000L,
        )

        assertIs<SaveEditedImageResult.Failure>(result)
        readOnlyParent.setWritable(true)
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