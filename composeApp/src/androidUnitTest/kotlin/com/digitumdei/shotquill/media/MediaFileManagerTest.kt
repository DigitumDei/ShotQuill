package com.digitumdei.shotquill.media

import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import java.io.File

class MediaFileManagerTest {
    private val tmpDir = createTempDir("shotquill-media-test-")
    private val mgr = MediaFileManager(filesDir = tmpDir)

    @Test
    fun guessMimeTypeJpeg() {
        assertEquals("image/jpeg", MediaFileManager.guessMimeType("photo.jpg"))
        assertEquals("image/jpeg", MediaFileManager.guessMimeType("photo.jpeg"))
        assertEquals("image/jpeg", MediaFileManager.guessMimeType("photo.JPG"))
    }

    @Test
    fun guessMimeTypePng() {
        assertEquals("image/png", MediaFileManager.guessMimeType("photo.png"))
    }

    @Test
    fun guessMimeTypeWebp() {
        assertEquals("image/webp", MediaFileManager.guessMimeType("photo.webp"))
    }

    @Test
    fun guessMimeTypeGif() {
        assertEquals("image/gif", MediaFileManager.guessMimeType("photo.gif"))
    }

    @Test
    fun guessMimeTypeBmp() {
        assertEquals("image/bmp", MediaFileManager.guessMimeType("photo.bmp"))
    }

    @Test
    fun guessMimeTypeHeic() {
        assertEquals("image/heic", MediaFileManager.guessMimeType("photo.heic"))
        assertEquals("image/heic", MediaFileManager.guessMimeType("photo.heif"))
    }

    @Test
    fun guessMimeTypeUnknownFallsBackToJpeg() {
        assertEquals("image/jpeg", MediaFileManager.guessMimeType("photo.unknown"))
        assertEquals("image/jpeg", MediaFileManager.guessMimeType("photo"))
    }

    @Test
    fun mimeTypeToExtensionJpeg() {
        assertEquals(".jpg", MediaFileManager.mimeTypeToExtension("image/jpeg"))
    }

    @Test
    fun mimeTypeToExtensionCaseInsensitive() {
        assertEquals(".png", MediaFileManager.mimeTypeToExtension("IMAGE/PNG"))
        assertEquals(".webp", MediaFileManager.mimeTypeToExtension("Image/Webp"))
    }

    @Test
    fun mimeTypeToExtensionPng() {
        assertEquals(".png", MediaFileManager.mimeTypeToExtension("image/png"))
    }

    @Test
    fun mimeTypeToExtensionWebp() {
        assertEquals(".webp", MediaFileManager.mimeTypeToExtension("image/webp"))
    }

    @Test
    fun mimeTypeToExtensionGif() {
        assertEquals(".gif", MediaFileManager.mimeTypeToExtension("image/gif"))
    }

    @Test
    fun mimeTypeToExtensionBmp() {
        assertEquals(".bmp", MediaFileManager.mimeTypeToExtension("image/bmp"))
    }

    @Test
    fun mimeTypeToExtensionHeic() {
        assertEquals(".heic", MediaFileManager.mimeTypeToExtension("image/heic"))
        assertEquals(".heif", MediaFileManager.mimeTypeToExtension("image/heif"))
    }

    @Test
    fun mimeTypeToExtensionUnknownFallsBackToJpg() {
        assertEquals(".jpg", MediaFileManager.mimeTypeToExtension("application/octet-stream"))
    }

    @Test
    fun createCameraCaptureFileCreatesFileInCorrectDir() {
        val file = mgr.createCameraCaptureFile()
        assertTrue(file.absolutePath.contains("media/originals/camera"))
        assertTrue(file.name.endsWith(".jpg"))
        assertTrue(file.name.startsWith("img_"))
        assertTrue(file.name.contains("_camera_"))
        assertTrue(file.parentFile?.exists() == true)
    }

    @Test
    fun createCameraCaptureFileGeneratesUniqueNames() {
        val names = (1..10).map { mgr.createCameraCaptureFile().name }.toSet()
        assertTrue(names.size > 1)
        assertEquals(10, names.size)
    }

    @Test
    fun handleCameraCaptureProducesValidResult() = runBlocking {
        val cameraDir = File(tmpDir, "media/originals/camera")
        cameraDir.mkdirs()
        val captureFile = File(cameraDir, "capture_test.jpg")

        val result = mgr.handleCameraCapture(captureFile)

        assertTrue(result.uri.startsWith("file://"), "Expected file:// URI: ${result.uri}")
        assertTrue(result.uri.contains("capture_test.jpg"))
        assertEquals("image/jpeg", result.mimeType)
        assertTrue(result.createdAtEpochMillis > 0)
    }

    @Test
    fun handleCameraCaptureStoresFileUriNotAbsolutePath() = runBlocking {
        val cameraDir = File(tmpDir, "media/originals/camera")
        cameraDir.mkdirs()
        val captureFile = File(cameraDir, "uri_test.jpg")

        val result = mgr.handleCameraCapture(captureFile)

        assertTrue(result.uri.startsWith("file://"), "Expected file:// URI but got: ${result.uri}")
    }

    @Test
    fun handleCameraCaptureWithNonExistentFileStillProducesResult() = runBlocking {
        val captureFile = File(tmpDir, "nonexistent.jpg")

        val result = mgr.handleCameraCapture(captureFile)

        assertTrue(result.uri.startsWith("file://"))
        assertEquals("image/jpeg", result.mimeType)
        assertTrue(result.createdAtEpochMillis > 0)
    }

    @Test
    fun readImageDimensionsReturnsNullForZeroByteFile() {
        val zeroFile = File(tmpDir, "empty.jpg")
        zeroFile.createNewFile()
        val (width, height) = MediaFileManager.readImageDimensions(zeroFile.absolutePath)
        assertEquals(null, width)
        assertEquals(null, height)
    }

    @Test
    fun handleCameraCaptureReturnsCorrectFileTimestamp() = runBlocking {
        val captureFile = File(tmpDir, "timestamp_test.jpg")
        val before = System.currentTimeMillis()
        captureFile.writeBytes(byteArrayOf(0, 1, 2))
        val after = System.currentTimeMillis()

        val result = mgr.handleCameraCapture(captureFile)

        assertTrue(result.createdAtEpochMillis in before..after || result.createdAtEpochMillis == captureFile.lastModified())
    }

    @Test
    fun readMediaAssetBytesReturnsFileContents() {
        val testFile = File(tmpDir, "test_photo.jpg")
        val content = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F)
        testFile.writeBytes(content)
        val asset = MediaAsset(
            id = MediaAssetId("test-1"),
            type = MediaType.Photo,
            uri = "file://${testFile.absolutePath}",
            mimeType = "image/jpeg",
            widthPx = 100,
            heightPx = 100,
            createdAtEpochMillis = 1000L,
        )
        val result = MediaFileManager.readMediaAssetBytes(asset)
        assertTrue(result.contentEquals(content))
    }

    @Test
    fun readMediaAssetBytesThrowsForUnsupportedUriScheme() {
        val asset = MediaAsset(
            id = MediaAssetId("test-3"),
            type = MediaType.Photo,
            uri = "content://com.example.provider/photos/1",
            mimeType = "image/jpeg",
            widthPx = 100,
            heightPx = 100,
            createdAtEpochMillis = 1000L,
        )
        val ex = assertFailsWith<IllegalArgumentException> {
            MediaFileManager.readMediaAssetBytes(asset)
        }
        assertTrue(ex.message!!.contains("Unsupported URI scheme"))
        assertTrue(ex.message!!.contains("content"))
    }

    @Test
    fun readMediaAssetBytesThrowsForSchemeOnlyUri() {
        val asset = MediaAsset(
            id = MediaAssetId("test-4"),
            type = MediaType.Photo,
            uri = "file://",
            mimeType = "image/jpeg",
            widthPx = 100,
            heightPx = 100,
            createdAtEpochMillis = 1000L,
        )
        val ex = assertFailsWith<IllegalArgumentException> {
            MediaFileManager.readMediaAssetBytes(asset)
        }
        assertTrue(ex.message!!.contains("URI is missing a file path"))
    }

    @Test
    fun readMediaAssetBytesThrowsForPlainPathWithoutScheme() {
        val testFile = File(tmpDir, "test_noscheme.jpg")
        testFile.writeBytes(byteArrayOf(1, 2, 3))
        val asset = MediaAsset(
            id = MediaAssetId("test-5"),
            type = MediaType.Photo,
            uri = testFile.absolutePath,
            mimeType = "image/jpeg",
            widthPx = 100,
            heightPx = 100,
            createdAtEpochMillis = 1000L,
        )
        val ex = assertFailsWith<IllegalArgumentException> {
            MediaFileManager.readMediaAssetBytes(asset)
        }
        assertTrue(ex.message!!.contains("Unsupported URI scheme"))
    }


}
