package com.digitumdei.shotquill.share

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `share launches chooser and returns true for a valid shareable image`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(recordingContext)
        val imageFile = File(applicationContext.filesDir, "media/originals/shareable.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        }

        val result = launcher.share("file://${imageFile.absolutePath}", "Caption text")

        assertTrue(result)
        val chooser = assertNotNull(recordingContext.startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val shareIntent = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(shareIntent)
        assertEquals(Intent.ACTION_SEND, shareIntent.action)
        assertEquals("image/*", shareIntent.type)
        assertNotNull(shareIntent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java))
    }

    @Test
    fun `share returns false when the share intent cannot be constructed`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(recordingContext)
        val outsideFile = File.createTempFile("shotquill-share-", ".jpg").apply {
            writeBytes(byteArrayOf(0x04, 0x05, 0x06))
        }

        try {
            val result = launcher.share("file://${outsideFile.absolutePath}", "Caption text")

            assertFalse(result)
            assertNull(recordingContext.startedIntent)
        } finally {
            outsideFile.delete()
        }
    }

    @Test
    fun `share returns false when the image file does not exist`() {
        val recordingContext = RecordingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(recordingContext)
        val missingFile = File(applicationContext.filesDir, "media/originals/missing.jpg")

        val result = launcher.share("file://${missingFile.absolutePath}", "Caption text")

        assertFalse(result)
        assertNull(recordingContext.startedIntent)
    }

    @Test
    fun `share returns false when the chooser cannot be launched`() {
        val throwingContext = ThrowingContext(applicationContext)
        val launcher = AndroidPostShareLauncher(throwingContext)
        val imageFile = File(applicationContext.filesDir, "media/originals/launch-failure.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x07, 0x08, 0x09))
        }

        val result = launcher.share("file://${imageFile.absolutePath}", "Caption text")

        assertFalse(result)
        assertNotNull(throwingContext.startedIntent)
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
