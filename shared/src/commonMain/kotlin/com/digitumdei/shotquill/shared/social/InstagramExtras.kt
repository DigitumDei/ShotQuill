package com.digitumdei.shotquill.shared.social

/**
 * Someone tagged on the photo. [x] and [y] say where, as fractions of the
 * finished image measured from its top-left corner, which is how Instagram
 * takes them.
 */
data class PhotoTag(val username: String, val x: Float = 0.5f, val y: Float = 0.5f)

const val InstagramCollaboratorLimit = 3
const val InstagramPhotoTagLimit = 20

/** [typed] as an Instagram name, without its @ and in lower case, or null when it could not be one. */
fun instagramNameOrNull(typed: String): String? =
    typed.trim().removePrefix("@").lowercase().takeIf {
        InstagramNamePattern.matches(it) && ".." !in it && !it.startsWith('.') && !it.endsWith('.')
    }

/** The names typed into the collaborators box, as typed but without their @, each once. */
fun parseCollaborators(typed: String): List<String> =
    typed.split(Regex("[\\s,]+"))
        .map { it.trim().removePrefix("@") }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

/** Why the collaborators as typed cannot be sent, or null when they can. */
fun collaboratorsProblem(typed: String): String? {
    val names = parseCollaborators(typed)
    val bad = names.firstOrNull { instagramNameOrNull(it) == null }
    return when {
        bad != null -> "\"$bad\" is not an Instagram name."
        names.size > InstagramCollaboratorLimit ->
            "Instagram allows up to $InstagramCollaboratorLimit collaborators and this has ${names.size}."

        else -> null
    }
}

/** Photo tags as one line of text, and back. A damaged entry is dropped rather than failing. */
object PhotoTagCodec {
    fun encode(tags: List<PhotoTag>): String = tags.joinToString(";") { "${it.username}:${it.x}:${it.y}" }

    fun decode(encoded: String): List<PhotoTag> =
        encoded.split(';').mapNotNull { entry ->
            val parts = entry.split(':')
            val name = parts.getOrNull(0)?.let(::instagramNameOrNull) ?: return@mapNotNull null
            val x = parts.getOrNull(1)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            val y = parts.getOrNull(2)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            PhotoTag(name, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
}

private val InstagramNamePattern = Regex("[a-z0-9._]{1,30}")
