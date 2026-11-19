package com.digitumdei.shotquill.shared.social

const val BlueskyTextLimit = 300

/**
 * The words that go to Bluesky: the text written for Bluesky when there is
 * some, otherwise the caption. Bluesky posts are short, so a long Instagram
 * caption usually needs its own shorter version.
 */
fun blueskyTextFor(caption: String, blueskyText: String): String =
    blueskyText.trim().ifEmpty { caption.trim() }

/**
 * How long Bluesky will consider [text] to be. Bluesky counts what a reader
 * sees as one character, so a letter and its accent, or an emoji built from
 * several parts, count once. This errs on the high side for unusual
 * sequences, which only ever means a slightly earlier warning.
 */
fun blueskyLength(text: String): Int {
    var count = 0
    var index = 0
    var joinNext = false
    while (index < text.length) {
        val codePoint = codePointAt(text, index)
        index += if (codePoint > 0xFFFF) 2 else 1
        when {
            codePoint == ZeroWidthJoiner -> joinNext = true
            isCombining(codePoint) -> Unit
            joinNext -> joinNext = false
            else -> count++
        }
    }
    return count
}

private const val ZeroWidthJoiner = 0x200D

private fun codePointAt(text: String, index: Int): Int {
    val high = text[index]
    if (high.isHighSurrogate() && index + 1 < text.length) {
        val low = text[index + 1]
        if (low.isLowSurrogate()) {
            return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
        }
    }
    return high.code
}

private fun isCombining(codePoint: Int): Boolean =
    codePoint in 0x0300..0x036F || // combining accents
        codePoint in 0xFE00..0xFE0F || // variation selectors
        codePoint in 0x1F3FB..0x1F3FF || // skin tone modifiers
        codePoint in 0xE0020..0xE007F // tag characters used in some flags

/** A stretch of the text that Bluesky should treat as a link or a hashtag. Offsets are in UTF-8 bytes. */
data class BlueskyFacet(
    val byteStart: Int,
    val byteEnd: Int,
    /** The address for a link; null for a hashtag. */
    val link: String? = null,
    /** The tag without its # for a hashtag; null for a link. */
    val tag: String? = null,
)

/**
 * Finds the links and hashtags in [text]. Without these markers Bluesky shows
 * them as plain, untappable text.
 */
fun blueskyFacets(text: String): List<BlueskyFacet> {
    val facets = mutableListOf<BlueskyFacet>()
    for (match in LinkPattern.findAll(text)) {
        val address = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', '"', '\'')
        if (address.length <= "https://".length) continue
        val start = byteOffset(text, match.range.first)
        facets += BlueskyFacet(
            byteStart = start,
            byteEnd = start + address.encodeToByteArray().size,
            link = address,
        )
    }
    for (match in TagPattern.findAll(text)) {
        val hash = match.groups[2] ?: continue
        val tag = hash.value.removePrefix("#")
        if (tag.all { it.isDigit() }) continue
        val start = byteOffset(text, hash.range.first)
        if (facets.any { start >= it.byteStart && start < it.byteEnd }) continue
        facets += BlueskyFacet(
            byteStart = start,
            byteEnd = start + hash.value.encodeToByteArray().size,
            tag = tag,
        )
    }
    return facets.sortedBy { it.byteStart }
}

private fun byteOffset(text: String, charIndex: Int): Int = text.substring(0, charIndex).encodeToByteArray().size

private val LinkPattern = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
private val TagPattern = Regex("(^|\\s)(#[\\p{L}\\p{N}_]+)")
