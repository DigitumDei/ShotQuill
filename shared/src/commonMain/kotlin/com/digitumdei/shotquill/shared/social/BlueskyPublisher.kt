package com.digitumdei.shotquill.shared.social

import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.SecretRedactor
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Where the Bluesky account lives and who it is. The app password is a secret and is stored separately. */
data class BlueskySettings(
    /** For example `doctrinebrewing.bsky.social`. A leading @ is ignored. */
    val handle: String = "",
    /** The account's server. Almost every account is on the default. */
    val server: String = DefaultServer,
) {
    val cleanHandle: String get() = handle.trim().removePrefix("@")

    companion object {
        const val DefaultServer = "https://bsky.social"
        const val AppPasswordSecret = "bluesky_app_password"
    }
}

/**
 * Posts one image with text to Bluesky over its public API: sign in with an
 * app password, upload the image, create the post. A new sign-in is made for
 * each post, so no session is kept on the phone.
 */
class BlueskyPublisher(
    private val settings: LocalSettingsRepository,
    private val transport: HttpTransport,
    private val clock: EpochClock = EpochClock.Default,
    private val languages: List<String> = listOf("en"),
) : PostPublisher {
    override val target: PublishTarget = PublishTarget.Bluesky

    override val isConfigured: Boolean
        get() = accountLabel != null && settings.getSecret(BlueskySettings.AppPasswordSecret) != null

    override val accountLabel: String?
        get() = settings.readSettings().bluesky.cleanHandle.takeIf { it.isNotEmpty() }?.let { "@$it" }

    // Bluesky's guide says 1,000,000 bytes in one place and 2,000,000 in
    // another. Staying under the smaller figure works either way.
    override val maxImageBytes: Int = 950_000

    override fun problemWith(text: String): String? {
        val length = blueskyLength(text)
        return if (length > BlueskyTextLimit) {
            "Bluesky posts can be $BlueskyTextLimit characters and this is $length. Write a shorter Bluesky text."
        } else {
            null
        }
    }

    override fun publish(post: PostToPublish): PublishResult {
        val bluesky = settings.readSettings().bluesky
        val handle = bluesky.cleanHandle
        val password = settings.getSecret(BlueskySettings.AppPasswordSecret)
        if (handle.isEmpty() || password == null) {
            return PublishResult.Failure("Add your Bluesky handle and an app password in settings first.")
        }
        problemWith(post.text)?.let { return PublishResult.Failure(it) }
        if (post.jpeg.size > MaxAcceptedImageBytes) {
            return PublishResult.Failure("The image is too large for Bluesky.")
        }
        val server = serverAddress(bluesky)
            ?: return PublishResult.Failure("The Bluesky server address in settings is not a web address.")

        return try {
            val session = signIn(server, handle, password)
                ?: return PublishResult.Failure("Bluesky did not accept the handle or app password.")
            val token = session.text("accessJwt")
            val did = session.text("did")
            if (token.isEmpty() || did.isEmpty()) {
                return PublishResult.Failure("Bluesky signed in but sent an incomplete reply.")
            }
            val auth = mapOf("Authorization" to "Bearer $token")

            val upload = call(
                step = "upload the image",
                password = password,
                request = HttpRequest(
                    method = "POST",
                    url = "$server/xrpc/com.atproto.repo.uploadBlob",
                    headers = auth,
                    body = post.jpeg,
                    contentType = "image/jpeg",
                ),
            )
            val blob = upload?.get("blob") as? JsonObject
                ?: return PublishResult.Failure("Bluesky did not accept the image.")

            val created = call(
                step = "create the post",
                password = password,
                request = HttpRequest(
                    method = "POST",
                    url = "$server/xrpc/com.atproto.repo.createRecord",
                    headers = auth,
                    body = buildJsonObject {
                        put("repo", did)
                        put("collection", "app.bsky.feed.post")
                        put("record", record(post, blob))
                    }.toString().encodeToByteArray(),
                    contentType = "application/json",
                ),
            )
            val recordKey = created?.text("uri").orEmpty().substringAfterLast('/', "")
            if (recordKey.isEmpty()) {
                return PublishResult.Failure("Bluesky did not confirm the post. Check your profile before trying again.")
            }
            PublishResult.Success(url = "https://bsky.app/profile/${session.text("handle").ifEmpty { handle }}/post/$recordKey")
        } catch (failure: StepFailed) {
            PublishResult.Failure(failure.message.orEmpty())
        } catch (e: Exception) {
            PublishResult.Failure(redact("Bluesky could not be reached: ${e.message ?: "no connection"}", password))
        }
    }

    override fun check(): String {
        val bluesky = settings.readSettings().bluesky
        val handle = bluesky.cleanHandle
        val password = settings.getSecret(BlueskySettings.AppPasswordSecret)
        if (handle.isEmpty() || password == null) return "Add your Bluesky handle and an app password first."
        val server = serverAddress(bluesky) ?: return "The Bluesky server address is not a web address."
        return try {
            val session = signIn(server, handle, password)
                ?: return "Bluesky did not accept the handle or app password."
            "Signed in to Bluesky as @${session.text("handle").ifEmpty { handle }}."
        } catch (failure: StepFailed) {
            failure.message.orEmpty()
        } catch (e: Exception) {
            redact("Bluesky could not be reached: ${e.message ?: "no connection"}", password)
        }
    }

    private fun serverAddress(bluesky: BlueskySettings): String? {
        val server = bluesky.server.trim().trimEnd('/')
        val isWebAddress = server.startsWith("https://", ignoreCase = true) || server.startsWith("http://", ignoreCase = true)
        return server.takeIf { isWebAddress }
    }

    /** Returns the session, or null when the handle or password was refused. */
    private fun signIn(server: String, handle: String, password: String): JsonObject? =
        call(
            step = "sign in",
            password = password,
            request = HttpRequest(
                method = "POST",
                url = "$server/xrpc/com.atproto.server.createSession",
                body = buildJsonObject {
                    put("identifier", handle)
                    put("password", password)
                }.toString().encodeToByteArray(),
                contentType = "application/json",
            ),
        )

    private fun record(post: PostToPublish, blob: JsonObject): JsonObject = buildJsonObject {
        put("\$type", "app.bsky.feed.post")
        put("text", post.text)
        put("createdAt", Instant.fromEpochMilliseconds(clock.nowMillis()).toString())
        putJsonArray("langs") { languages.forEach { add(JsonPrimitive(it)) } }
        val facets = blueskyFacets(post.text)
        if (facets.isNotEmpty()) {
            putJsonArray("facets") {
                facets.forEach { facet ->
                    addJsonObject {
                        putJsonObject("index") {
                            put("byteStart", facet.byteStart)
                            put("byteEnd", facet.byteEnd)
                        }
                        putJsonArray("features") {
                            addJsonObject {
                                if (facet.link != null) {
                                    put("\$type", "app.bsky.richtext.facet#link")
                                    put("uri", facet.link)
                                } else {
                                    put("\$type", "app.bsky.richtext.facet#tag")
                                    put("tag", facet.tag.orEmpty())
                                }
                            }
                        }
                    }
                }
            }
        }
        putJsonObject("embed") {
            put("\$type", "app.bsky.embed.images")
            putJsonArray("images") {
                addJsonObject {
                    put("alt", post.altText.trim())
                    put("image", blob)
                    putJsonObject("aspectRatio") {
                        put("width", post.imageWidth)
                        put("height", post.imageHeight)
                    }
                }
            }
        }
    }

    /**
     * Sends one request. Returns the reply, null when sign-in was refused, and
     * throws [StepFailed] with a readable reason for any other refusal.
     */
    private fun call(step: String, password: String, request: HttpRequest): JsonObject? {
        val response = transport.send(request)
        val reply = try {
            json.parseToJsonElement(response.body) as? JsonObject
        } catch (_: Exception) {
            null
        }
        if (response.isSuccess) {
            return reply ?: throw StepFailed("Bluesky sent a reply that could not be read while trying to $step.")
        }
        if (response.status == 401 && step == "sign in") return null
        val detail = reply?.text("message").orEmpty().ifEmpty { reply?.text("error").orEmpty() }
        val suffix = if (detail.isEmpty()) "" else " ($detail)"
        throw StepFailed(
            redact(
                when (response.status) {
                    401, 403 -> "Bluesky refused to $step.$suffix"
                    429 -> "Bluesky is rate-limiting this account. Try again in a few minutes.$suffix"
                    else -> "Bluesky could not $step (${response.status}).$suffix"
                },
                password,
            ),
        )
    }

    private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    private fun redact(message: String, password: String): String =
        SecretRedactor.redactKnownSecrets(message, listOf(password))

    private class StepFailed(message: String) : Exception(message)

    private companion object {
        const val MaxAcceptedImageBytes = 1_000_000
        val json = Json { ignoreUnknownKeys = true }
    }
}
