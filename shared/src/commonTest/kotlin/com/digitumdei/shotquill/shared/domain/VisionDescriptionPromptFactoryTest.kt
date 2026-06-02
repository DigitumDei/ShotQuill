package com.digitumdei.shotquill.shared.domain

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class VisionDescriptionPromptFactoryTest {
    @Test
    fun buildsPromptWithRequiredVisionDetailsAndUploadConstraints() {
        val prompt = VisionDescriptionPromptFactory.buildPrompt(
            MediaAsset(
                id = MediaAssetId("media-1"),
                type = MediaType.Photo,
                uri = "file://photo.jpg",
                mimeType = "image/jpeg",
                widthPx = 3024,
                heightPx = 4032,
                createdAtEpochMillis = 1_700_000_000_000L,
            ),
        )

        assertContains(prompt, "Main subject")
        assertContains(prompt, "Setting and background")
        assertContains(prompt, "Visible text or logos")
        assertContains(prompt, "Important details to preserve")
        assertContains(prompt, "Mood and context")
        assertContains(prompt, "Crop or framing notes")
        assertContains(prompt, "Asset id: media-1")
        assertContains(prompt, "MIME type: image/jpeg")
        assertContains(prompt, "Pixel size: 3024 x 4032")
        assertContains(prompt, "maximum 1568 px long edge")
        assertContains(prompt, "JPEG quality 85")
        assertContains(prompt, "Strip EXIF metadata")
    }

    @Test
    fun platformPresetsAreAccessibleFromImageWorkflow() {
        val preset = TargetPlatform.InstagramFeedSquare.platformPreset
        assertNotNull(preset.aspectRatio)
    }
}
