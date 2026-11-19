package com.digitumdei.shotquill.image

/** True for a file name or URI that ends in .svg. */
fun String.isSvgPath(): Boolean = endsWith(".svg", ignoreCase = true)

/**
 * Whether [bytes] is the start of an SVG document. Decided from the content
 * rather than the file name, because file pickers do not always report a
 * useful type for SVG files.
 */
fun looksLikeSvg(bytes: ByteArray): Boolean {
    val head = bytes.decodeToString(0, minOf(bytes.size, SniffLength))
    val text = head.trimStart('﻿', ' ', '\t', '\r', '\n')
    return text.startsWith("<") && text.contains("<svg", ignoreCase = true)
}

/**
 * Things in an SVG that will not come out the way they look in the drawing
 * program, described so they can be fixed there. Null when nothing was found.
 */
fun svgCautions(svgText: String): String? {
    val cautions = buildList {
        if (LiveText.containsMatchIn(svgText)) {
            add(
                "It contains live text, which may be drawn in a different font here. " +
                    "In Inkscape, select the text and use Path > Object to Path.",
            )
        }
        if (Filter.containsMatchIn(svgText)) {
            add("It uses filters such as blur or shadow, which are not drawn.")
        }
    }
    return cautions.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

private const val SniffLength = 4096
private val LiveText = Regex("<text[\\s>]", RegexOption.IGNORE_CASE)
private val Filter = Regex("<filter[\\s>]", RegexOption.IGNORE_CASE)
