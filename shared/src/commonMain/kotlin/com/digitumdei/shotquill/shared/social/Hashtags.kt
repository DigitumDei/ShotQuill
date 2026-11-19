package com.digitumdei.shotquill.shared.social

/**
 * Tidies what was typed into a hashtags box into one line of tags. A word
 * without a leading # gets one, so `craftbeer bskybeer` works as well as
 * `#craftbeer #bskybeer`. Mentions beginning with @ are left alone, since on
 * Bluesky it is common to keep one or two with the tags.
 */
fun normalizeTags(typed: String): String =
    typed.split(Regex("[\\s,]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "#" && it != "@" }
        .joinToString(" ") { word -> if (word.startsWith("#") || word.startsWith("@")) word else "#$word" }

/** The words of a post followed by its hashtags, set apart by a blank line. Either part may be empty. */
fun withTags(text: String, tags: String): String {
    val words = text.trim()
    val tagLine = normalizeTags(tags)
    return when {
        tagLine.isEmpty() -> words
        words.isEmpty() -> tagLine
        else -> "$words\n\n$tagLine"
    }
}
