package com.digitumdei.shotquill

import com.digitumdei.shotquill.model.MediaCaptureResult
import com.digitumdei.shotquill.screen.NewPostStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaCaptureResultTest {
    @Test
    fun storesAllMetadata() {
        val result = MediaCaptureResult(
            uri = "file://test/photo.jpg",
            mimeType = "image/jpeg",
            widthPx = 1920,
            heightPx = 1080,
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals("file://test/photo.jpg", result.uri)
        assertEquals("image/jpeg", result.mimeType)
        assertEquals(1920, result.widthPx)
        assertEquals(1080, result.heightPx)
        assertEquals(1_700_000_000_000L, result.createdAtEpochMillis)
    }

    @Test
    fun handlesNullMetadata() {
        val result = MediaCaptureResult(
            uri = "file://test/unknown.bin",
            mimeType = null,
            widthPx = null,
            heightPx = null,
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals("file://test/unknown.bin", result.uri)
        assertNull(result.mimeType)
        assertNull(result.widthPx)
        assertNull(result.heightPx)
    }
}

class NewPostStepTest {
    @Test
    fun enumMembersRemainStable() {
        assertEquals(
            listOf("ChooseSource", "Processing", "Complete", "Error"),
            NewPostStep.entries.map { it.name },
        )
    }
}
