package com.digitumdei.shotquill

import com.digitumdei.shotquill.model.MediaCaptureResult
import com.digitumdei.shotquill.model.MediaCaptureResultSaver
import com.digitumdei.shotquill.screen.NewPostStep
import com.digitumdei.shotquill.screen.deriveNewPostStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun encodeMediaCaptureResult(result: MediaCaptureResult?): String {
    return result?.let {
        "${it.uri}|${it.mimeType ?: ""}|${it.widthPx ?: -1}|${it.heightPx ?: -1}|${it.createdAtEpochMillis}"
    } ?: ""
}

class MediaCaptureResultTest {
    @Test
    fun storesAllMetadata() {
        val result = MediaCaptureResult(
            uri = "file:///data/media/originals/photo.jpg",
            mimeType = "image/jpeg",
            widthPx = 1920,
            heightPx = 1080,
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals("file:///data/media/originals/photo.jpg", result.uri)
        assertEquals("image/jpeg", result.mimeType)
        assertEquals(1920, result.widthPx)
        assertEquals(1080, result.heightPx)
        assertEquals(1_700_000_000_000L, result.createdAtEpochMillis)
    }

    @Test
    fun handlesNullMetadata() {
        val result = MediaCaptureResult(
            uri = "file:///data/media/originals/unknown.bin",
            mimeType = null,
            widthPx = null,
            heightPx = null,
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals("file:///data/media/originals/unknown.bin", result.uri)
        assertNull(result.mimeType)
        assertNull(result.widthPx)
        assertNull(result.heightPx)
    }

    @Test
    fun copyPreservesFields() {
        val original = MediaCaptureResult(
            uri = "file:///data/media/originals/photo.jpg",
            mimeType = "image/jpeg",
            widthPx = 1920,
            heightPx = 1080,
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        val copied = original.copy(uri = "file:///data/media/originals/edited.jpg")

        assertEquals("file:///data/media/originals/edited.jpg", copied.uri)
        assertEquals(original.mimeType, copied.mimeType)
        assertEquals(original.widthPx, copied.widthPx)
        assertEquals(original.heightPx, copied.heightPx)
        assertEquals(original.createdAtEpochMillis, copied.createdAtEpochMillis)
    }

    @Test
    fun saverRoundtripRestoresAllFields() {
        val original = MediaCaptureResult(
            uri = "file:///data/media/originals/photo.jpg",
            mimeType = "image/jpeg",
            widthPx = 1920,
            heightPx = 1080,
            createdAtEpochMillis = 1_700_000_000_000L,
        )
        val saved = encodeMediaCaptureResult(original)
        val restored = MediaCaptureResultSaver.restore(saved)

        assertNotNull(restored)
        assertEquals(original.uri, restored.uri)
        assertEquals(original.mimeType, restored.mimeType)
        assertEquals(original.widthPx, restored.widthPx)
        assertEquals(original.heightPx, restored.heightPx)
        assertEquals(original.createdAtEpochMillis, restored.createdAtEpochMillis)
    }

    @Test
    fun saverRoundtripWithNullMetadataPreservesNulls() {
        val original = MediaCaptureResult(
            uri = "file:///data/media/originals/unknown.bin",
            mimeType = null,
            widthPx = null,
            heightPx = null,
            createdAtEpochMillis = 1_700_000_000_000L,
        )
        val saved = encodeMediaCaptureResult(original)
        val restored = MediaCaptureResultSaver.restore(saved)

        assertNotNull(restored)
        assertEquals(original.uri, restored.uri)
        assertNull(restored.mimeType)
        assertNull(restored.widthPx)
        assertNull(restored.heightPx)
        assertEquals(original.createdAtEpochMillis, restored.createdAtEpochMillis)
    }

    @Test
    fun saverSaveNullReturnsEmptyString() {
        val saved = encodeMediaCaptureResult(null)
        assertEquals("", saved)
    }

    @Test
    fun saverRestoreReturnsNullForEmptyString() {
        assertNull(MediaCaptureResultSaver.restore(""))
    }

    @Test
    fun saverRestoreHandlesTruncatedInputGracefully() {
        val restored = MediaCaptureResultSaver.restore("file:///only/uri.jpg")
        assertNotNull(restored)
        assertEquals("file:///only/uri.jpg", restored.uri)
        assertNull(restored.mimeType)
        assertNull(restored.widthPx)
        assertNull(restored.heightPx)
        assertEquals(0L, restored.createdAtEpochMillis)
    }

    @Test
    fun saverRestoreHandlesNonNumericTimestampGracefully() {
        val restored = MediaCaptureResultSaver.restore("file:///photo.jpg|image/jpeg|1920|1080|not-a-number")
        assertNotNull(restored)
        assertEquals("file:///photo.jpg", restored.uri)
        assertEquals("image/jpeg", restored.mimeType)
        assertEquals(1920, restored.widthPx)
        assertEquals(1080, restored.heightPx)
        assertEquals(0L, restored.createdAtEpochMillis)
    }

    @Test
    fun saverRestoreHandlesNonNumericWidthAndHeightGracefully() {
        val restored = MediaCaptureResultSaver.restore("file:///photo.jpg|image/jpeg|abc|def|1700000000000")
        assertNotNull(restored)
        assertEquals("file:///photo.jpg", restored.uri)
        assertEquals("image/jpeg", restored.mimeType)
        assertNull(restored.widthPx)
        assertNull(restored.heightPx)
        assertEquals(1_700_000_000_000L, restored.createdAtEpochMillis)
    }

    @Test
    fun saverRestoreHandlesNegativeWidthAndHeightAsNull() {
        val restored = MediaCaptureResultSaver.restore("file:///photo.jpg|image/jpeg|-1|-1|1700000000000")
        assertNotNull(restored)
        assertEquals("file:///photo.jpg", restored.uri)
        assertEquals("image/jpeg", restored.mimeType)
        assertNull(restored.widthPx)
        assertNull(restored.heightPx)
        assertEquals(1_700_000_000_000L, restored.createdAtEpochMillis)
    }

    @Test
    fun saverRestoreHandlesPipeInUriField() {
        val restored = MediaCaptureResultSaver.restore(
            "file:///img|with|pipe.jpg||-1|-1|1700000000000",
        )
        assertNotNull(restored)
    }
}

class NewPostStepTest {
    @Test
    fun stepDerivesChooseSourceWhenAllNull() {
        val step = deriveNewPostStep(captureResult = null, draftCreatedMessage = null, errorMessage = null)
        assertEquals(NewPostStep.ChooseSource, step)
    }

    @Test
    fun stepDerivesProcessingWhenCaptureResultPresent() {
        val step = deriveNewPostStep(
            captureResult = MediaCaptureResult(
                uri = "file:///test.jpg",
                mimeType = null,
                widthPx = null,
                heightPx = null,
                createdAtEpochMillis = 1L,
            ),
            draftCreatedMessage = null,
            errorMessage = null,
        )
        assertEquals(NewPostStep.Processing, step)
    }

    @Test
    fun stepDerivesCompleteWhenDraftCreatedMessagePresent() {
        val step = deriveNewPostStep(
            captureResult = null,
            draftCreatedMessage = "Draft created!",
            errorMessage = null,
        )
        assertEquals(NewPostStep.Complete, step)
    }

    @Test
    fun stepDerivesErrorWhenErrorMessagePresent() {
        val step = deriveNewPostStep(
            captureResult = null,
            draftCreatedMessage = null,
            errorMessage = "Something went wrong",
        )
        assertEquals(NewPostStep.Error, step)
    }

    @Test
    fun stepPrioritizesCompleteOverProcessing() {
        val step = deriveNewPostStep(
            captureResult = MediaCaptureResult("file:///uri", null, null, null, 1L),
            draftCreatedMessage = "Done!",
            errorMessage = null,
        )
        assertEquals(NewPostStep.Complete, step)
    }

    @Test
    fun stepPrioritizesErrorOverProcessing() {
        val step = deriveNewPostStep(
            captureResult = MediaCaptureResult("file:///uri", null, null, null, 1L),
            draftCreatedMessage = null,
            errorMessage = "Error!",
        )
        assertEquals(NewPostStep.Error, step)
    }
}
