package com.digitumdei.shotquill.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoOrientationTest {
    @Test
    fun plainRotationsAreReadFromTheExifValue() {
        assertEquals(PhotoOrientation(), photoOrientationFor(1))
        assertEquals(PhotoOrientation(degrees = 180), photoOrientationFor(3))
        assertEquals(PhotoOrientation(degrees = 90), photoOrientationFor(6))
        assertEquals(PhotoOrientation(degrees = 270), photoOrientationFor(8))
    }

    @Test
    fun mirroredOrientationsKeepTheirMirror() {
        assertEquals(PhotoOrientation(degrees = 0, mirrored = true), photoOrientationFor(2))
        assertEquals(PhotoOrientation(degrees = 180, mirrored = true), photoOrientationFor(4))
        assertEquals(PhotoOrientation(degrees = 90, mirrored = true), photoOrientationFor(5))
        assertEquals(PhotoOrientation(degrees = 270, mirrored = true), photoOrientationFor(7))
    }

    @Test
    fun unknownValuesMeanUpright() {
        assertEquals(PhotoOrientation(), photoOrientationFor(0))
        assertEquals(PhotoOrientation(), photoOrientationFor(9))
        assertEquals(PhotoOrientation(), photoOrientationFor(-1))
    }

    @Test
    fun onlyQuarterTurnsSwapTheSides() {
        assertTrue((1..8).filter { photoOrientationFor(it).swapsSides } == listOf(5, 6, 7, 8))
        assertTrue(photoOrientationFor(1).isUpright)
        assertFalse(photoOrientationFor(2).isUpright)
        assertFalse(photoOrientationFor(6).isUpright)
    }

    /**
     * Follows one pixel through "rotate clockwise, then mirror" and checks it
     * lands where the EXIF standard says that orientation puts it.
     */
    @Test
    fun rotateThenMirrorMatchesTheStandardForEveryOrientation() {
        val width = 4
        val height = 3
        val x = 1
        val y = 2
        val expected = mapOf(
            1 to (x to y),
            2 to (width - 1 - x to y),
            3 to (width - 1 - x to height - 1 - y),
            4 to (x to height - 1 - y),
            5 to (y to x),
            6 to (height - 1 - y to x),
            7 to (height - 1 - y to width - 1 - x),
            8 to (y to width - 1 - x),
        )

        expected.forEach { (exif, where) ->
            val orientation = photoOrientationFor(exif)
            var point = x to y
            var size = width to height
            repeat(orientation.degrees / 90) {
                point = (size.second - 1 - point.second) to point.first
                size = size.second to size.first
            }
            if (orientation.mirrored) point = (size.first - 1 - point.first) to point.second
            assertEquals(where, point, "EXIF orientation $exif")
        }
    }
}
