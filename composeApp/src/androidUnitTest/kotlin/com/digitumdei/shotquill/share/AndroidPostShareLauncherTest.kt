package com.digitumdei.shotquill.share

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class AndroidPostShareLauncherTest {
    private val applicationContext: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `share launches chooser and returns success for a valid shareable image`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = recordingLauncher(recordingContext)
        val imageFile = File(applicationContext.filesDir, "media/originals/shareable.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        }

        val result = launcher.share("file://${imageFile.absolutePath}", "Caption text")

        assertTrue(result.success)
        assertNotNull(result.destinationUri)
        assertNull(result.errorMessage)
        val chooser = assertNotNull(recordingContext.startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val shareIntent = chooser.parcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(shareIntent)
        assertEquals(Intent.ACTION_SEND, shareIntent.action)
        assertEquals("image/*", shareIntent.type)
        assertNotNull(shareIntent.parcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(shareIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `share launches chooser for a percent-encoded file uri`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = recordingLauncher(recordingContext)
        val imageFile = File(applicationContext.filesDir, "media/originals/share file.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x0A, 0x0B, 0x0C))
        }

        val encodedUri = "file://${imageFile.absolutePath.replace(" ", "%20")}"
        val result = launcher.share(encodedUri, "Caption text")

        assertTrue(result.success)
        assertNotNull(result.destinationUri)
        assertNull(result.errorMessage)
        val chooser = assertNotNull(recordingContext.startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val shareIntent = chooser.parcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(shareIntent)
        assertEquals(Intent.ACTION_SEND, shareIntent.action)
        assertEquals("image/*", shareIntent.type)
        assertNotNull(shareIntent.parcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(shareIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `share launches chooser for a raw file uri containing a literal percent`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = recordingLauncher(recordingContext)
        val imageFile = File(applicationContext.filesDir, "media/originals/bad%2.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x0D, 0x0E, 0x0F))
        }

        val result = launcher.share("file://${imageFile.absolutePath}", "Caption text")

        assertTrue(result.success)
        assertNotNull(result.destinationUri)
        assertNull(result.errorMessage)
        val chooser = assertNotNull(recordingContext.startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val shareIntent = chooser.parcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(shareIntent)
        assertEquals(Intent.ACTION_SEND, shareIntent.action)
        assertEquals("image/*", shareIntent.type)
        assertNotNull(shareIntent.parcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(shareIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `share launches chooser and returns success for text only share`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(recordingContext)

        val result = launcher.share(null, "Caption text")

        assertTrue(result.success)
        assertNull(result.destinationUri)
        assertNull(result.errorMessage)
        val chooser = assertNotNull(recordingContext.startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val shareIntent = chooser.parcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(shareIntent)
        assertEquals(Intent.ACTION_SEND, shareIntent.action)
        assertEquals("text/plain", shareIntent.type)
        assertNull(shareIntent.parcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }

    @Test
    fun `share returns failure when the share payload cannot be constructed`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(recordingContext) {
            throw IllegalArgumentException("outside configured share roots")
        }
        val outsideFile = File.createTempFile("shotquill-share-", ".jpg").apply {
            writeBytes(byteArrayOf(0x04, 0x05, 0x06))
        }

        try {
            val result = launcher.share("file://${outsideFile.absolutePath}", "Caption text")

            assertTrue(!result.success)
            assertEquals("outside configured share roots", result.errorMessage)
            assertNull(recordingContext.startedIntent)
        } finally {
            outsideFile.delete()
        }
    }

    @Test
    fun `share returns failure with specific message for invalid image path`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(recordingContext)

        val result = launcher.share("content://example/not-a-real-image.jpg", "Caption text")

        assertTrue(!result.success)
        assertEquals(
            "Image URI does not reference a local file: content://example/not-a-real-image.jpg",
            result.errorMessage,
        )
        assertNull(recordingContext.startedIntent)
    }

    @Test
    fun `share returns failure with specific message when the image file does not exist`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(recordingContext)
        val missingFile = File(applicationContext.filesDir, "media/originals/missing.jpg")

        val result = launcher.share("file://${missingFile.absolutePath}", "Caption text")

        assertTrue(!result.success)
        assertTrue(
            result.errorMessage?.startsWith("Unable to resolve image file: file://") == true,
            "Expected error to start with 'Unable to resolve image file:' but got: ${result.errorMessage}",
        )
        assertNull(recordingContext.startedIntent)
    }

    @Test
    fun `share returns failure with specific message when the file uri cannot be resolved`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(recordingContext)
        val unresolvedFile = File(applicationContext.filesDir, "media/originals/missing%2.jpg")

        val result = launcher.share("file://${unresolvedFile.absolutePath}", "Caption text")

        assertTrue(!result.success)
        assertTrue(
            result.errorMessage?.startsWith("Unable to resolve image file: file://") == true,
            "Expected error to start with 'Unable to resolve image file:' but got: ${result.errorMessage}",
        )
        assertNull(recordingContext.startedIntent)
    }

    @Test
    fun `share returns failure when the image path is a directory`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = recordingLauncher(recordingContext)
        val dir = File(applicationContext.filesDir, "media/originals").apply {
            mkdirs()
        }

        val result = launcher.share("file://${dir.absolutePath}", "Caption text")

        assertTrue(!result.success)
        assertTrue(
            result.errorMessage?.startsWith("Unable to resolve image file: file://") == true,
            "Expected error to start with 'Unable to resolve image file:' but got: ${result.errorMessage}",
        )
        assertNull(recordingContext.startedIntent)
    }

    @Test
    fun `share returns failure with specific message when the chooser cannot be launched`() {
        val throwingContext = ThrowingContext(applicationContext)
        val launcher = recordingLauncher(throwingContext)
        val imageFile = File(applicationContext.filesDir, "media/originals/launch-failure.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x07, 0x08, 0x09))
        }

        val result = launcher.share("file://${imageFile.absolutePath}", "Caption text")

        assertTrue(!result.success)
        assertEquals("chooser launch failed", result.errorMessage)
        assertEquals(
            "content://com.digitumdei.shotquill.fileprovider/launch-failure.jpg",
            result.destinationUri,
        )
        assertNotNull(throwingContext.startedIntent)
    }

    @Test
    fun `share resolves the content URI once before launch and carries it through`() {
        var callCount = 0
        val trackingContentUri: (File) -> Uri = { file ->
            callCount++
            if (callCount > 1) throw IllegalStateException("contentUriForFile must not be called after launch")
            Uri.parse("content://com.digitumdei.shotquill.fileprovider/${file.name}")
        }
        val recordingContext = RecordingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(
            context = recordingContext,
            contentUriForFile = trackingContentUri,
        )
        val imageFile = File(applicationContext.filesDir, "media/originals/once-resolved.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x0F, 0x1E, 0x2D))
        }

        val result = launcher.share("file://${imageFile.absolutePath}", "Caption text")

        assertEquals(1, callCount)
        assertTrue(result.success)
        assertEquals("content://com.digitumdei.shotquill.fileprovider/once-resolved.jpg", result.destinationUri)
        assertNull(result.errorMessage)
        assertNotNull(recordingContext.startedIntent)
    }

    @Test
    fun `share passes caption text in the share intent extra`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = recordingLauncher(recordingContext)
        val imageFile = File(applicationContext.filesDir, "media/originals/caption-test.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x11, 0x22, 0x33))
        }

        val result = launcher.share("file://${imageFile.absolutePath}", "My caption with #hashtags")

        assertTrue(result.success)
        val chooser = assertNotNull(recordingContext.startedIntent)
        val shareIntent = chooser.parcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(shareIntent)
        assertEquals("My caption with #hashtags", shareIntent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `textOnlyShare passes caption text in the share intent extra`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(recordingContext)

        val result = launcher.share(null, "Just a caption")

        assertTrue(result.success)
        val chooser = assertNotNull(recordingContext.startedIntent)
        val shareIntent = chooser.parcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(shareIntent)
        assertEquals("Just a caption", shareIntent.getStringExtra(Intent.EXTRA_TEXT))
    }

    private fun recordingLauncher(context: Context): AndroidPostShareLauncher {
        return AndroidPostShareLauncher(context) { imageFile ->
            Uri.parse("content://com.digitumdei.shotquill.fileprovider/${imageFile.name}")
        }
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : Parcelable> Intent.parcelableExtra(name: String): T? {
        return getParcelableExtra(name) as? T
    }

    private open class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            startedIntent = intent
        }
    }

    private class ThrowingContext(base: Context) : RecordingContext(base) {
        override fun startActivity(intent: Intent) {
            startedIntent = intent
            throw IllegalStateException("chooser launch failed")
        }
    }
}
