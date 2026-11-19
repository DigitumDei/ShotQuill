package com.digitumdei.shotquill.shared.social

/** A plain HTTP request. [body] may be text encoded as bytes or a file such as an image. */
class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
)

data class HttpResponse(
    val status: Int,
    val body: String,
) {
    val isSuccess: Boolean get() = status in 200..299
}

/** Sends one request and returns what came back. Throws when the server cannot be reached at all. */
fun interface HttpTransport {
    fun send(request: HttpRequest): HttpResponse
}

/** The places a post can be published to directly, without going through another app. */
enum class PublishTarget(val wireValue: String, val label: String) {
    Instagram("instagram", "Instagram"),
    Bluesky("bluesky", "Bluesky");

    companion object {
        fun fromWireValue(value: String): PublishTarget? = entries.firstOrNull { it.wireValue == value }
    }
}

/** A finished post: the image exactly as rendered, and the words that go with it. */
class PostToPublish(
    val text: String,
    val jpeg: ByteArray,
    val altText: String,
    val imageWidth: Int,
    val imageHeight: Int,
)

sealed interface PublishResult {
    /** [url] opens the published post in a browser or the service's own app. */
    data class Success(val url: String) : PublishResult

    /** [message] is written for the person using the app and never contains a password or token. */
    data class Failure(val message: String) : PublishResult
}

/**
 * Publishes to one service. The app only ever publishes what the user has
 * written and asked to post; nothing here reads, replies or schedules.
 */
interface PostPublisher {
    val target: PublishTarget

    /** True once the account details for this service have been entered. */
    val isConfigured: Boolean

    /** The account that will be posted as, for showing before the user confirms. Null when not configured. */
    val accountLabel: String?

    /** The largest image, in bytes, the service accepts. */
    val maxImageBytes: Int

    /** Why [text] cannot be posted as it stands, or null when it can. */
    fun problemWith(text: String): String?

    /** Why an image of this size cannot be posted, or null when it can. */
    fun problemWithImage(width: Int, height: Int): String? = null

    /**
     * Blocking. Tries the saved account details without posting anything and
     * says, in words for the user, whether they work.
     */
    fun check(): String

    /**
     * Blocking. Asks the service whether the account [name] exists, so a
     * mention can be checked while the post is still being written. Nothing
     * is posted, and services that offer no such lookup say so.
     */
    fun findAccount(name: String): AccountLookup = AccountLookup.CannotCheck

    /**
     * Blocking. Accounts the service offers for a name that is still being
     * typed. Empty when it has none or offers no such lookup.
     */
    fun suggestAccounts(typed: String): List<AccountSuggestion> = emptyList()

    /**
     * Blocking. Accounts this account has mentioned in its own earlier posts,
     * as far as the service will say. Empty when it will not.
     */
    fun pastMentions(): List<String> = emptyList()

    /** Blocking: call off the main thread. */
    fun publish(post: PostToPublish): PublishResult
}
