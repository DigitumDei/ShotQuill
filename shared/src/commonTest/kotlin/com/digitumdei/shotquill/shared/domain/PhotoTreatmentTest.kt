package com.digitumdei.shotquill.shared.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoTreatmentTest {
    @Test
    fun portraitCropOfALandscapePhotoUsesTheFullHeight() {
        val crop = PhotoTreatment(cropShape = CropShape.Portrait).cropRect(4000, 3000)

        assertClose(2400f, crop.width)
        assertClose(3000f, crop.height)
        assertClose(800f, crop.left)
        assertClose(0f, crop.top)
    }

    @Test
    fun squareCropOfAPortraitPhotoUsesTheFullWidth() {
        val crop = PhotoTreatment(cropShape = CropShape.Square).cropRect(3000, 4000)

        assertClose(3000f, crop.width)
        assertClose(3000f, crop.height)
        assertClose(0f, crop.left)
        assertClose(500f, crop.top)
    }

    @Test
    fun originalShapeKeepsTheWholePhoto() {
        val crop = PhotoTreatment(cropShape = CropShape.Original).cropRect(4000, 3000)

        assertEquals(PixelRect(0f, 0f, 4000f, 3000f), crop)
    }

    @Test
    fun zoomShrinksTheCropAroundItsCentre() {
        val crop = PhotoTreatment(cropShape = CropShape.Square, zoom = 2f).cropRect(1000, 1000)

        assertEquals(PixelRect(250f, 250f, 500f, 500f), crop)
    }

    @Test
    fun cropNeverLeavesThePhoto() {
        val crop = PhotoTreatment(cropShape = CropShape.Square, zoom = 2f, centerX = 0f, centerY = 1f)
            .cropRect(1000, 1000)

        assertEquals(PixelRect(0f, 500f, 500f, 500f), crop)
    }

    @Test
    fun zoomOutsideTheAllowedRangeIsClamped() {
        val tooFar = PhotoTreatment(cropShape = CropShape.Square, zoom = 50f).cropRect(1000, 1000)
        val tooWide = PhotoTreatment(cropShape = CropShape.Square, zoom = 0.1f).cropRect(1000, 1000)

        assertClose(1000f / PhotoTreatment.MaxZoom, tooFar.width)
        assertClose(1000f, tooWide.width)
    }

    @Test
    fun cropRejectsAnEmptyImage() {
        assertFailsWith<IllegalArgumentException> { PhotoTreatment().cropRect(0, 100) }
    }

    @Test
    fun draggingRightMovesTheCropLeft() {
        val start = PhotoTreatment(cropShape = CropShape.Portrait)
        val moved = start.panZoomed(
            panXFraction = 0.25f,
            panYFraction = 0f,
            zoomChange = 1f,
            imageWidth = 4000,
            imageHeight = 3000,
        )

        val crop = moved.cropRect(4000, 3000)
        assertClose(200f, crop.left)
        assertClose(0f, crop.top)
    }

    @Test
    fun draggingPastTheEdgeStopsAtTheEdgeAndStoresTheClampedCentre() {
        val moved = PhotoTreatment(cropShape = CropShape.Portrait).panZoomed(
            panXFraction = 5f,
            panYFraction = 0f,
            zoomChange = 1f,
            imageWidth = 4000,
            imageHeight = 3000,
        )

        assertClose(0f, moved.cropRect(4000, 3000).left)
        assertClose(1200f / 4000f, moved.centerX)
    }

    @Test
    fun pinchingZoomsWithinTheLimits() {
        val start = PhotoTreatment(cropShape = CropShape.Square)

        assertClose(2f, start.panZoomed(0f, 0f, 2f, 1000, 1000).zoom)
        assertClose(PhotoTreatment.MaxZoom, start.panZoomed(0f, 0f, 100f, 1000, 1000).zoom)
        assertClose(PhotoTreatment.MinZoom, start.panZoomed(0f, 0f, 0.01f, 1000, 1000).zoom)
    }

    @Test
    fun changingShapeResetsTheFraming() {
        val changed = PhotoTreatment(zoom = 3f, centerX = 0.1f, centerY = 0.9f, lookEnabled = false)
            .withShape(CropShape.Story)

        assertEquals(CropShape.Story, changed.cropShape)
        assertEquals(PhotoTreatment.MinZoom, changed.zoom)
        assertEquals(0.5f, changed.centerX)
        assertEquals(0.5f, changed.centerY)
        assertFalse(changed.lookEnabled)
    }

    @Test
    fun exportIsCappedAtInstagramWidthAndKeepsTheShape() {
        assertEquals(PixelSize(1080, 1350), exportSize(PixelRect(0f, 0f, 2400f, 3000f)))
        assertEquals(PixelSize(1080, 1920), exportSize(PixelRect(0f, 0f, 2160f, 3840f)))
    }

    @Test
    fun exportNeverEnlargesASmallCrop() {
        assertEquals(PixelSize(400, 500), exportSize(PixelRect(0f, 0f, 400f, 500f)))
    }

    @Test
    fun cropShapesRoundTripThroughTheirWireValues() {
        CropShape.entries.forEach { assertEquals(it, CropShape.fromWireValue(it.wireValue)) }
        LogoCorner.entries.forEach { assertEquals(it, LogoCorner.fromWireValue(it.wireValue)) }
        assertNull(CropShape.fromWireValue("circle"))
        assertNull(LogoCorner.fromWireValue("middle"))
    }

    @Test
    fun neutralLookLeavesColoursAlone() {
        assertTrue(LookSettings.Neutral.isNeutral)
        assertFalse(LookSettings().isNeutral)
        val matrix = LookSettings.Neutral.toColorMatrix()

        IdentityColorMatrix.forEachIndexed { index, expected -> assertClose(expected, matrix[index]) }
    }

    @Test
    fun warmthRaisesRedAndLowersBlue() {
        val matrix = LookSettings.Neutral.copy(warmth = 1f).toColorMatrix()

        assertTrue(matrix[0] > 1f, "red gain")
        assertTrue(matrix[12] < 1f, "blue gain")
    }

    @Test
    fun fullDesaturationGivesEveryChannelTheSameGrey() {
        val matrix = LookSettings.Neutral.copy(saturation = -1f).toColorMatrix()

        assertContentEquals(matrix.sliceArray(0..2), matrix.sliceArray(5..7))
        assertContentEquals(matrix.sliceArray(0..2), matrix.sliceArray(10..12))
        assertClose(1f, matrix[0] + matrix[1] + matrix[2])
    }

    @Test
    fun contrastPivotsAroundMidGrey() {
        val matrix = LookSettings.Neutral.copy(contrast = 1f).toColorMatrix()

        assertClose(127.5f, matrix[0] * 127.5f + matrix[4])
        assertTrue(matrix[0] > 1f)
    }

    @Test
    fun fadeLiftsBlackAndKeepsWhite() {
        val matrix = LookSettings.Neutral.copy(fade = 1f).toColorMatrix()

        assertClose(40f, matrix[4])
        assertClose(255f, matrix[0] * 255f + matrix[4])
    }

    @Test
    fun matricesComposeInOrder() {
        val double = floatArrayOf(
            2f, 0f, 0f, 0f, 0f,
            0f, 2f, 0f, 0f, 0f,
            0f, 0f, 2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val addTen = floatArrayOf(
            1f, 0f, 0f, 0f, 10f,
            0f, 1f, 0f, 0f, 10f,
            0f, 0f, 1f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f,
        )

        val addThenDouble = concatColorMatrices(second = double, first = addTen)
        val doubleThenAdd = concatColorMatrices(second = addTen, first = double)

        assertClose(20f, addThenDouble[4])
        assertClose(10f, doubleThenAdd[4])
        assertClose(2f, addThenDouble[0])
    }

    @Test
    fun logoSitsInTheChosenCornerWithAMargin() {
        val logo = LogoSettings(uri = "file:///logo.png", widthFraction = 0.2f, marginFraction = 0.05f)

        val bottomEnd = logoPlacement(1000f, 1250f, logoAspect = 2f, logo = logo, corner = LogoCorner.BottomEnd)
        assertEquals(PixelRect(left = 750f, top = 1100f, width = 200f, height = 100f), bottomEnd)

        val topStart = logoPlacement(1000f, 1250f, logoAspect = 2f, logo = logo, corner = LogoCorner.TopStart)
        assertEquals(PixelRect(left = 50f, top = 50f, width = 200f, height = 100f), topStart)

        val topEnd = logoPlacement(1000f, 1250f, logoAspect = 2f, logo = logo, corner = LogoCorner.TopEnd)
        assertEquals(750f, topEnd.left)
        assertEquals(50f, topEnd.top)

        val bottomStart = logoPlacement(1000f, 1250f, logoAspect = 2f, logo = logo, corner = LogoCorner.BottomStart)
        assertEquals(50f, bottomStart.left)
        assertEquals(1100f, bottomStart.top)
    }

    @Test
    fun logoSizeIsKeptWithinSensibleBounds() {
        val huge = logoPlacement(1000f, 1000f, 1f, LogoSettings(widthFraction = 3f))
        val tiny = logoPlacement(1000f, 1000f, 1f, LogoSettings(widthFraction = 0f))
        val noAspect = logoPlacement(1000f, 1000f, 0f, LogoSettings(widthFraction = 0.2f))

        assertClose(1000f * LogoSettings.MaxWidthFraction, huge.width)
        assertClose(1000f * LogoSettings.MinWidthFraction, tiny.width)
        assertClose(noAspect.width, noAspect.height)
    }

    @Test
    fun captionStatsCountCharactersAndHashtags() {
        val stats = captionStats("Brew day #doctrine #craft_beer, not#atag\n#kzn")

        assertEquals(45, stats.characters)
        assertEquals(3, stats.hashtags)
        assertFalse(stats.overCharacterLimit)
        assertFalse(stats.overHashtagLimit)
    }

    @Test
    fun captionStatsFlagInstagramLimits() {
        val long = captionStats("a".repeat(CaptionStats.InstagramCaptionLimit + 1))
        val tagged = captionStats((1..31).joinToString(" ") { "#tag$it" })

        assertTrue(long.overCharacterLimit)
        assertTrue(tagged.overHashtagLimit)
    }

    @Test
    fun snippetsAreAddedOnTheirOwnLine() {
        assertEquals("Hello", appendSnippet("", "  Hello "))
        assertEquals("First line\n\n#tags", appendSnippet("First line\n\n\n", "#tags"))
        assertEquals("Unchanged", appendSnippet("Unchanged", "   "))
    }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }
}
