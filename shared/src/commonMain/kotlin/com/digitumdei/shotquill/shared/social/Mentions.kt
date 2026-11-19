package com.digitumdei.shotquill.shared.social

/** What a service said when asked whether an account exists. */
sealed interface AccountLookup {
    /** The account exists. [displayName] is what it calls itself, when it has said. */
    data class Found(val handle: String, val displayName: String = "") : AccountLookup

    data object NotFound : AccountLookup

    /** The service could not be asked just now, for example with no connection. Worth asking again. */
    data object Unreachable : AccountLookup

    /** The service gives apps no way to ask. */
    data object CannotCheck : AccountLookup
}

enum class MentionStatus {
    /** The account exists. */
    Found,

    /** No such account: the mention would be dead text. */
    NotFound,

    /** The name is written in a way the service will not accept or link. */
    Problem,

    /** Being looked up right now. */
    Checking,

    /** The lookup could not be made, so nothing is known. */
    Unreachable,

    /** The name is well formed but the service offers no lookup. */
    CannotCheck,
}

/** One @name in a post and what is known about it, in words for the person writing the post. */
data class MentionCheck(
    /** The name as typed, without its @. */
    val name: String,
    val status: MentionStatus,
    val note: String,
    /** Where the account's profile can be looked at, when there is something to look at. */
    val profileUrl: String? = null,
) {
    /** True when the mention will not do what the writer expects. */
    val needsAttention: Boolean get() = status == MentionStatus.NotFound || status == MentionStatus.Problem
}

/**
 * Every @name in [text] as Bluesky would need it, lower-cased and in the order
 * written, each once. This is wider than what [blueskyFacets] links: it also
 * catches a bare `@name`, which Bluesky leaves as plain text.
 */
fun blueskyMentionNames(text: String): List<String> =
    AtNamePattern.findAll(text)
        .map { it.groupValues[2].trimEnd('.', '-', '_').lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

/** True when [name] is a whole Bluesky handle, which always looks like a domain name. */
fun isFullBlueskyHandle(name: String): Boolean = FullHandlePattern.matches(name)

/** The handle to try for [name]: itself when whole, otherwise the usual `name.bsky.social`. Null when neither can be a handle. */
fun blueskyHandleToLookUp(name: String): String? = when {
    isFullBlueskyHandle(name) -> name
    isFullBlueskyHandle("$name.$UsualBlueskyDomain") -> "$name.$UsualBlueskyDomain"
    else -> null
}

/** The handles that need asking about before [blueskyMentionChecks] can say something about every mention in [text]. */
fun blueskyHandlesToLookUp(text: String): List<String> =
    blueskyMentionNames(text).mapNotNull(::blueskyHandleToLookUp).distinct()

/**
 * What is known about each Bluesky mention in [text], given the answers so far
 * in [lookups] (keyed by handle). A handle with no answer yet reads as being
 * checked.
 */
fun blueskyMentionChecks(text: String, lookups: Map<String, AccountLookup>): List<MentionCheck> =
    blueskyMentionNames(text).map { name ->
        val handle = blueskyHandleToLookUp(name)
            ?: return@map MentionCheck(
                name = name,
                status = MentionStatus.Problem,
                note = "Not a Bluesky handle. A mention needs the whole handle, like @name.$UsualBlueskyDomain.",
            )
        val whole = handle == name
        when (val answer = lookups[handle]) {
            null -> MentionCheck(name, MentionStatus.Checking, "Checking")

            is AccountLookup.Found -> if (whole) {
                MentionCheck(
                    name = name,
                    status = MentionStatus.Found,
                    note = answer.displayName.trim().ifEmpty { "Found" },
                    profileUrl = blueskyProfileUrl(handle),
                )
            } else {
                MentionCheck(
                    name = name,
                    status = MentionStatus.Problem,
                    note = "Bluesky needs the whole handle. @$handle exists" +
                        answer.displayName.trim().let { if (it.isEmpty()) "" else " ($it)" } +
                        ": write that if it is who you mean.",
                    profileUrl = blueskyProfileUrl(handle),
                )
            }

            AccountLookup.NotFound -> if (whole) {
                MentionCheck(name, MentionStatus.NotFound, "No Bluesky account has this handle. It would post as plain text.")
            } else {
                MentionCheck(
                    name = name,
                    status = MentionStatus.Problem,
                    note = "Bluesky needs the whole handle, and there is no @$handle either.",
                )
            }

            AccountLookup.Unreachable -> MentionCheck(name, MentionStatus.Unreachable, "Could not be checked just now")

            AccountLookup.CannotCheck -> if (whole) {
                MentionCheck(name, MentionStatus.CannotCheck, "Cannot be checked", blueskyProfileUrl(handle))
            } else {
                MentionCheck(name, MentionStatus.Problem, "Bluesky needs the whole handle, like @$handle.")
            }
        }
    }

fun blueskyProfileUrl(handle: String): String = "https://bsky.app/profile/$handle"

/**
 * Each @name in [text] as Instagram reads it. Instagram gives an app that
 * posts with an Instagram sign-in no way to ask whether another account
 * exists, so a well-formed name can only be offered for looking at.
 */
fun instagramMentionChecks(text: String): List<MentionCheck> =
    AtNamePattern.findAll(text)
        .map { it.groupValues[2].trimEnd('.', '-') }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .map { name ->
            val problem = when {
                '-' in name -> "Instagram names cannot contain a hyphen, so this would link to @${name.substringBefore('-')}."
                name.length > InstagramNameLimit -> "Instagram names are at most $InstagramNameLimit characters."
                ".." in name -> "Instagram names cannot have two full stops in a row."
                name.startsWith('.') -> "Instagram names cannot start with a full stop."
                else -> null
            }
            if (problem != null) {
                MentionCheck(name, MentionStatus.Problem, problem)
            } else {
                MentionCheck(
                    name = name,
                    status = MentionStatus.CannotCheck,
                    note = "Instagram gives apps no way to check this. Tap to look at the profile.",
                    profileUrl = instagramProfileUrl(name),
                )
            }
        }
        .toList()

fun instagramProfileUrl(name: String): String = "https://www.instagram.com/${name.lowercase()}/"

const val UsualBlueskyDomain = "bsky.social"
const val InstagramNameLimit = 30

// An @ at the start of a word, then everything that could belong to a name on
// either service. An @ inside a word, as in an email address, is not a mention.
private val AtNamePattern = Regex("(^|[\\s(])@([A-Za-z0-9._-]+)")

private val FullHandlePattern = Regex(
    "(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?",
)
