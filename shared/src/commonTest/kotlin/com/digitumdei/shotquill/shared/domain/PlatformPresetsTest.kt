package com.digitumdei.shotquill.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformPresetsTest {

    @Test
    fun providesDefaultPresetForEveryPlatform() {
        TargetPlatform.entries.forEach { platform ->
            val preset = PlatformPreset.defaults[platform]
            assertNotNull(preset, "Missing default preset for $platform")
            assertEquals(platform, preset.platform)
        }
    }

    @Test
    fun instagramFeedSquarePreset() {
        val preset = PlatformPreset.defaults[TargetPlatform.InstagramFeedSquare]!!

        assertEquals(AspectRatio(1, 1), preset.aspectRatio)
        assertEquals(1080, preset.recommendedWidthPx)
        assertEquals(1080, preset.recommendedHeightPx)
        assertEquals(FramingBehavior.Fit, preset.defaultFramingBehavior)
    }

    @Test
    fun instagramPortraitPreset() {
        val preset = PlatformPreset.defaults[TargetPlatform.InstagramPortrait]!!

        assertEquals(AspectRatio(4, 5), preset.aspectRatio)
        assertEquals(1080, preset.recommendedWidthPx)
        assertEquals(1350, preset.recommendedHeightPx)
        assertEquals(FramingBehavior.Fit, preset.defaultFramingBehavior)
    }

    @Test
    fun instagramStoryReelPreset() {
        val preset = PlatformPreset.defaults[TargetPlatform.InstagramStoryReel]!!

        assertEquals(AspectRatio(9, 16), preset.aspectRatio)
        assertEquals(1080, preset.recommendedWidthPx)
        assertEquals(1920, preset.recommendedHeightPx)
        assertEquals(FramingBehavior.Fill, preset.defaultFramingBehavior)
    }

    @Test
    fun facebookPostPreset() {
        val preset = PlatformPreset.defaults[TargetPlatform.FacebookPost]!!

        assertEquals(AspectRatio(191, 100), preset.aspectRatio)
        assertEquals(1200, preset.recommendedWidthPx)
        assertEquals(630, preset.recommendedHeightPx)
        assertEquals(FramingBehavior.Fit, preset.defaultFramingBehavior)
    }

    @Test
    fun blueskyPostPreset() {
        val preset = PlatformPreset.defaults[TargetPlatform.BlueskyPost]!!

        assertEquals(AspectRatio(3, 2), preset.aspectRatio)
        assertEquals(1200, preset.recommendedWidthPx)
        assertEquals(800, preset.recommendedHeightPx)
        assertEquals(FramingBehavior.Fit, preset.defaultFramingBehavior)
    }

    @Test
    fun originalPresetPreservesSourceDimensions() {
        val preset = PlatformPreset.defaults[TargetPlatform.Original]!!

        assertNull(preset.aspectRatio, "Original preset should not constrain aspect ratio")
        assertNull(preset.recommendedWidthPx, "Original preset should not constrain width")
        assertNull(preset.recommendedHeightPx, "Original preset should not constrain height")
        assertEquals(FramingBehavior.NoResize, preset.defaultFramingBehavior)
    }

    @Test
    fun rejectsInvalidAspectRatio() {
        val zeroWidth = assertFailsWith<IllegalArgumentException> {
            AspectRatio(width = 0, height = 1)
        }
        val negativeHeight = assertFailsWith<IllegalArgumentException> {
            AspectRatio(width = 16, height = -1)
        }

        assertTrue(zeroWidth.message!!.startsWith("AspectRatio width"))
        assertTrue(negativeHeight.message!!.startsWith("AspectRatio height"))
    }

    @Test
    fun allowsNullDimensionsForPreset() {
        val preset = PlatformPreset(
            platform = TargetPlatform.Original,
            displayName = "Test",
            aspectRatio = null,
            recommendedWidthPx = null,
            recommendedHeightPx = null,
            defaultFramingBehavior = FramingBehavior.NoResize,
        )

        assertNull(preset.aspectRatio)
        assertNull(preset.recommendedWidthPx)
        assertNull(preset.recommendedHeightPx)
    }

    @Test
    fun rejectsNullPresetWithNegativeDimensions() {
        assertFailsWith<IllegalArgumentException> {
            PlatformPreset(
                platform = TargetPlatform.BlueskyPost,
                displayName = "Test",
                aspectRatio = AspectRatio(3, 2),
                recommendedWidthPx = -1,
                recommendedHeightPx = 800,
                defaultFramingBehavior = FramingBehavior.Fit,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PlatformPreset(
                platform = TargetPlatform.BlueskyPost,
                displayName = "Test",
                aspectRatio = AspectRatio(3, 2),
                recommendedWidthPx = 1200,
                recommendedHeightPx = 0,
                defaultFramingBehavior = FramingBehavior.Fit,
            )
        }
    }

    @Test
    fun rejectsBlankPresetDisplayName() {
        assertFailsWith<IllegalArgumentException> {
            PlatformPreset(
                platform = TargetPlatform.BlueskyPost,
                displayName = " ",
                aspectRatio = AspectRatio(3, 2),
                recommendedWidthPx = 1200,
                recommendedHeightPx = 800,
                defaultFramingBehavior = FramingBehavior.Fit,
            )
        }
    }

    @Test
    fun framingBehaviorMembersAndWireValuesRemainStable() {
        assertEquals(
            listOf(
                "Fit" to "fit",
                "Fill" to "fill",
                "Stretch" to "stretch",
                "NoResize" to "no_resize",
            ),
            FramingBehavior.entries.map { it.name to it.wireValue },
        )
    }

    @Test
    fun mapsFramingBehaviorFromWireValue() {
        assertEquals(FramingBehavior.Fit, FramingBehavior.fromWireValue("fit"))
        assertEquals(FramingBehavior.Fill, FramingBehavior.fromWireValue("fill"))
        assertEquals(FramingBehavior.Stretch, FramingBehavior.fromWireValue("stretch"))
        assertEquals(FramingBehavior.NoResize, FramingBehavior.fromWireValue("no_resize"))
        assertNull(FramingBehavior.fromWireValue("unknown"))
    }
}
