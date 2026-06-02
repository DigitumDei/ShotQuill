package com.digitumdei.shotquill.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MaskRegionTest {

    @Test
    fun createsPixelMaskRegion() {
        val region = MaskRegion(
            MaskBounds.Pixel(left = 10, top = 20, width = 100, height = 200),
        )
        val bounds = region.bounds as MaskBounds.Pixel
        assertEquals(10, bounds.left)
        assertEquals(20, bounds.top)
        assertEquals(100, bounds.width)
        assertEquals(200, bounds.height)
    }

    @Test
    fun createsNormalizedMaskRegion() {
        val region = MaskRegion(
            MaskBounds.Normalized(left = 0.1f, top = 0.2f, width = 0.5f, height = 0.5f),
        )
        val bounds = region.bounds as MaskBounds.Normalized
        assertEquals(0.1f, bounds.left)
        assertEquals(0.2f, bounds.top)
        assertEquals(0.5f, bounds.width)
        assertEquals(0.5f, bounds.height)
    }

    @Test
    fun rejectsPixelBoundsWithNegativeLeft() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Pixel(left = -1, top = 0, width = 100, height = 100)
        }
        assertEquals("Pixel left must be non-negative, got -1", failure.message)
    }

    @Test
    fun rejectsPixelBoundsWithNegativeTop() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Pixel(left = 0, top = -5, width = 100, height = 100)
        }
        assertEquals("Pixel top must be non-negative, got -5", failure.message)
    }

    @Test
    fun rejectsPixelBoundsWithZeroWidth() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Pixel(left = 0, top = 0, width = 0, height = 100)
        }
        assertEquals("Pixel width must be positive, got 0", failure.message)
    }

    @Test
    fun rejectsPixelBoundsWithNegativeHeight() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Pixel(left = 0, top = 0, width = 100, height = -1)
        }
        assertEquals("Pixel height must be positive, got -1", failure.message)
    }

    @Test
    fun rejectsNormalizedBoundsWithNegativeLeft() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Normalized(left = -0.1f, top = 0.0f, width = 0.5f, height = 0.5f)
        }
        assertEquals("Normalized left must be in range [0.0, 1.0], got -0.1", failure.message)
    }

    @Test
    fun rejectsNormalizedBoundsWithLeftAboveOne() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Normalized(left = 1.5f, top = 0.0f, width = 0.5f, height = 0.5f)
        }
        assertEquals("Normalized left must be in range [0.0, 1.0], got 1.5", failure.message)
    }

    @Test
    fun rejectsNormalizedBoundsWithTopBelowZero() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Normalized(left = 0.0f, top = -0.01f, width = 0.5f, height = 0.5f)
        }
        assertEquals("Normalized top must be in range [0.0, 1.0], got -0.01", failure.message)
    }

    @Test
    fun rejectsNormalizedBoundsWithTopAboveOne() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Normalized(left = 0.0f, top = 1.1f, width = 0.5f, height = 0.5f)
        }
        assertEquals("Normalized top must be in range [0.0, 1.0], got 1.1", failure.message)
    }

    @Test
    fun rejectsNormalizedBoundsWithWidthOutOfRange() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Normalized(left = 0.0f, top = 0.0f, width = 1.5f, height = 0.5f)
        }
        assertEquals("Normalized width must be in range (0.0, 1.0], got 1.5", failure.message)
    }

    @Test
    fun rejectsNormalizedBoundsWithHeightOutOfRange() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Normalized(left = 0.0f, top = 0.0f, width = 0.5f, height = -0.1f)
        }
        assertEquals("Normalized height must be in range (0.0, 1.0], got -0.1", failure.message)
    }

    @Test
    fun rejectsNormalizedBoundsWithZeroWidth() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Normalized(left = 0.0f, top = 0.0f, width = 0.0f, height = 0.5f)
        }
        assertEquals("Normalized width must be in range (0.0, 1.0], got 0.0", failure.message)
    }

    @Test
    fun rejectsNormalizedBoundsWithZeroHeight() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Normalized(left = 0.0f, top = 0.0f, width = 0.5f, height = 0.0f)
        }
        assertEquals("Normalized height must be in range (0.0, 1.0], got 0.0", failure.message)
    }

    @Test
    fun rejectsNormalizedBoundsOverflowingRightEdge() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Normalized(left = 0.75f, top = 0.0f, width = 0.5f, height = 0.5f)
        }
        assertEquals("Normalized right edge (left + width) must be <= 1.0, got 1.25", failure.message)
    }

    @Test
    fun rejectsNormalizedBoundsOverflowingBottomEdge() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskBounds.Normalized(left = 0.0f, top = 0.95f, width = 0.5f, height = 0.1f)
        }
        assertEquals("Normalized bottom edge (top + height) must be <= 1.0, got 1.05", failure.message)
    }

    @Test
    fun acceptsNormalizedBoundsExactlyFillingWidth() {
        val region = MaskRegion(
            MaskBounds.Normalized(left = 0.5f, top = 0.0f, width = 0.5f, height = 0.5f),
        )
        val bounds = region.bounds as MaskBounds.Normalized
        assertEquals(0.5f, bounds.left)
        assertEquals(0.5f, bounds.width)
    }

    @Test
    fun acceptsNormalizedBoundsExactlyFillingHeight() {
        val region = MaskRegion(
            MaskBounds.Normalized(left = 0.0f, top = 0.5f, width = 0.5f, height = 0.5f),
        )
        val bounds = region.bounds as MaskBounds.Normalized
        assertEquals(0.5f, bounds.top)
        assertEquals(0.5f, bounds.height)
    }

    @Test
    fun acceptsNormalizedBoundsWithFloatPrecisionRightEdge() {
        val left = 0.33333334f
        val width = 0.66666674f
        val rightEdge = left + width
        assert(rightEdge > 1.0f) { "rightEdge ($rightEdge) must exceed 1.0f to exercise tolerance" }
        val region = MaskRegion(
            MaskBounds.Normalized(left = left, top = 0.0f, width = width, height = 0.5f),
        )
        val bounds = region.bounds as MaskBounds.Normalized
        assertEquals(left, bounds.left)
        assertEquals(width, bounds.width)
    }

    @Test
    fun acceptsNormalizedBoundsWithFloatPrecisionBottomEdge() {
        val top = 0.33333334f
        val height = 0.66666674f
        val bottomEdge = top + height
        assert(bottomEdge > 1.0f) { "bottomEdge ($bottomEdge) must exceed 1.0f to exercise tolerance" }
        val region = MaskRegion(
            MaskBounds.Normalized(left = 0.0f, top = top, width = 0.5f, height = height),
        )
        val bounds = region.bounds as MaskBounds.Normalized
        assertEquals(top, bounds.top)
        assertEquals(height, bounds.height)
    }

    @Test
    fun roundTripsPixelMaskRegionThroughString() {
        val original = MaskRegion(MaskBounds.Pixel(left = 10, top = 20, width = 100, height = 200))
        val serialized = original.toString()
        val parsed = MaskRegion.parse(serialized)
        assertEquals(original, parsed)
    }

    @Test
    fun roundTripsNormalizedMaskRegionThroughString() {
        val original = MaskRegion(
            MaskBounds.Normalized(left = 0.1f, top = 0.2f, width = 0.5f, height = 0.5f),
        )
        val serialized = original.toString()
        val parsed = MaskRegion.parse(serialized)
        assertEquals(original, parsed)
    }

    @Test
    fun pixelMaskRegionRoundTripStringFormat() {
        val region = MaskRegion(MaskBounds.Pixel(left = 50, top = 75, width = 300, height = 400))
        assertEquals("pixel:50,75,300,400", region.toString())
    }

    @Test
    fun normalizedMaskRegionRoundTripStringFormat() {
        val region = MaskRegion(
            MaskBounds.Normalized(left = 0.0f, top = 0.0f, width = 1.0f, height = 1.0f),
        )
        assertEquals("normalized:0.0,0.0,1.0,1.0", region.toString())
    }

    @Test
    fun parsesPixelMaskRegionFromString() {
        val region = MaskRegion.parse("pixel:15,30,200,400")
        val bounds = region.bounds as MaskBounds.Pixel
        assertEquals(15, bounds.left)
        assertEquals(30, bounds.top)
        assertEquals(200, bounds.width)
        assertEquals(400, bounds.height)
    }

    @Test
    fun parsesNormalizedMaskRegionFromString() {
        val region = MaskRegion.parse("normalized:0.25,0.25,0.5,0.5")
        val bounds = region.bounds as MaskBounds.Normalized
        assertEquals(0.25f, bounds.left)
        assertEquals(0.25f, bounds.top)
        assertEquals(0.5f, bounds.width)
        assertEquals(0.5f, bounds.height)
    }

    @Test
    fun rejectsParseWithInvalidFormat() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskRegion.parse("invalid")
        }
        assertEquals("Invalid mask region format: invalid", failure.message)
    }

    @Test
    fun rejectsParseWithUnknownType() {
        val failure = assertFailsWith<IllegalStateException> {
            MaskRegion.parse("ellipse:0,0,100,100")
        }
        assertEquals("Unknown mask region type: ellipse", failure.message)
    }

    @Test
    fun rejectsParseWithTooFewCoordinates() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MaskRegion.parse("pixel:10,20,100")
        }
        assertEquals("Mask region requires exactly 4 coordinates, got 3", failure.message)
    }

    @Test
    fun rejectsParseWithNonIntegerPixelCoordinates() {
        assertFailsWith<NumberFormatException> {
            MaskRegion.parse("pixel:10.5,20,100,200")
        }
    }
}
