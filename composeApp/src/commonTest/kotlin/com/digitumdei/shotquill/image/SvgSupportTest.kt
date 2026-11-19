package com.digitumdei.shotquill.image

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SvgSupportTest {
    @Test
    fun recognisesSvgByItsContent() {
        assertTrue(looksLikeSvg("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".encodeToByteArray()))
        assertTrue(
            looksLikeSvg(
                "﻿<?xml version=\"1.0\"?>\n<!-- Created with Inkscape -->\n<SVG width=\"10\"/>".encodeToByteArray(),
            ),
        )
    }

    @Test
    fun doesNotMistakeOtherFilesForSvg() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + "<svg".encodeToByteArray()
        assertFalse(looksLikeSvg(png))
        assertFalse(looksLikeSvg("<html><body>not a drawing</body></html>".encodeToByteArray()))
        assertFalse(looksLikeSvg("plain text mentioning <svg".encodeToByteArray()))
        assertFalse(looksLikeSvg(ByteArray(0)))
    }

    @Test
    fun svgPathsAreRecognisedByExtension() {
        assertTrue("file:///brand/logo_1.svg".isSvgPath())
        assertTrue("LOGO.SVG".isSvgPath())
        assertFalse("file:///brand/logo_1.png".isSvgPath())
        assertFalse("file:///brand/svg".isSvgPath())
    }

    @Test
    fun aPlainPathOnlySvgNeedsNoCaution() {
        assertNull(svgCautions("<svg><path d=\"M0 0L1 1\"/><g><textPath/></g></svg>"))
    }

    @Test
    fun liveTextAndFiltersAreCalledOut() {
        val text = svgCautions("<svg><text x=\"1\">Doctrine</text></svg>")
        val filter = svgCautions("<svg><defs><filter id=\"blur\"/></defs></svg>")
        val both = svgCautions("<svg><TEXT>a</TEXT><filter\n id=\"f\"/></svg>")

        assertTrue(text.orEmpty().contains("Object to Path"))
        assertFalse(text.orEmpty().contains("filters"))
        assertTrue(filter.orEmpty().contains("filters"))
        assertTrue(both.orEmpty().contains("Object to Path") && both.orEmpty().contains("filters"))
    }
}
