package com.digitumdei.shotquill.shared.social

import com.digitumdei.shotquill.shared.domain.CaptionStats
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.captionStats
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.SecretRedactor
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What is remembered about the Instagram connection. The access token is a secret and is stored separately. */
data class InstagramSettings(
    /** Filled in from Instagram the first time the token is used. */
    val username: String = "",
    val tokenSavedAtMillis: Long = 0L,
    /** When the token stops working unless it is renewed. Zero when not known. */
    val tokenExpiresAtMillis: Long = 0L,
) {
    companion object {
        const val AccessTokenSecret = "instagram_access_token"

        /**
         * Meta hands out two kinds of token. The older "Instagram login" kind
         * begins with IG, belongs to the Instagram account, lasts sixty days
         * and can be renewed. The "Unified login" kind that new Meta apps get
         * belongs to a business portfolio, is used on a different server, and
         * lasts for as long as was chosen when it was made.
         */
        fun isInstagramLoginToken(token: String): Boolean = token.trim().startsWith("IG")
    }
}

/**
 * Somewhere on the public internet to put an image for a moment. Instagram
 * does not accept an upload: it fetches the image itself from a web address,
 * so the image has to be reachable there while the post is being made.
 */
interface TemporaryImageHost {
    val isConfigured: Boolean

    /** Blocking. Returns the public address of the uploaded image; throws with a readable message on failure. */
    fun upload(jpeg: ByteArray, fileName: String): String

    /** Blocking. Removes an uploaded image. Failures are ignored by callers. */
    fun delete(fileName: String)
}

/**
 * Posts one image with a caption to an Instagram professional account through
 * Meta's publishing API: put the image at a public address, ask Instagram to
 * fetch it into a container, publish the container, then remove the image.
 */
class InstagramPublisher(
    private val settings: LocalSettingsRepository,
    private val transport: HttpTransport,
    private val imageHost: TemporaryImageHost,
    private val clock: EpochClock = EpochClock.Default,
    /** Waits between checks while Instagram is still fetching the image. */
    private val pause: (millis: Long) -> Unit = {},
    /** Where an "Instagram login" token is used. */
    private val graphUrl: String = "https://graph.instagram.com",
    /** Where a "Unified login" token is used. */
    private val unifiedGraphUrl: String = "https://graph.facebook.com/v27.0",
) : PostPublisher {
    override val target: PublishTarget = PublishTarget.Instagram

    override val isConfigured: Boolean
        get() = settings.getSecret(InstagramSettings.AccessTokenSecret) != null && imageHost.isConfigured

    override val accountLabel: String?
        get() = if (settings.getSecret(InstagramSettings.AccessTokenSecret) == null) {
            null
        } else {
            settings.readSettings().instagram.username.takeIf { it.isNotBlank() }?.let { "@$it" }
                ?: "your Instagram account"
        }

    override val maxImageBytes: Int = 8_000_000

    override fun problemWith(text: String): String? {
        val stats = captionStats(text)
        return when {
            stats.overCharacterLimit ->
                "Instagram captions can be ${CaptionStats.InstagramCaptionLimit} characters and this is ${stats.characters}."

            stats.overHashtagLimit ->
                "Instagram allows ${CaptionStats.InstagramHashtagLimit} hashtags and this has ${stats.hashtags}."

            else -> null
        }
    }

    override fun problemWithImage(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val aspect = width.toFloat() / height
        return if (aspect < MinAspect - AspectTolerance || aspect > MaxAspect + AspectTolerance) {
            "Instagram's posting service takes shapes from 4:5 to 1.91:1. Choose 4:5 or 1:1, or use Share for this one."
        } else {
            null
        }
    }

    override fun check(): String {
        val token = settings.getSecret(InstagramSettings.AccessTokenSecret)
            ?: return "Add an Instagram access token first."
        var current = token
        return try {
            current = refreshIfDue(token)
            val account = account(current)
            if (!imageHost.isConfigured) {
                "Connected to Instagram as @${account.username}. Image hosting is not set up yet."
            } else {
                val name = "check-${clock.nowMillis()}.jpg"
                imageHost.upload(TinyJpeg, name)
                runCatching { imageHost.delete(name) }
                "Connected to Instagram as @${account.username}, and image hosting works."
            }
        } catch (failure: StepFailed) {
            redact(failure.message.orEmpty(), token, current)
        } catch (e: Exception) {
            redact(e.message ?: "The check could not be completed.", token, current)
        }
    }

    override fun publish(post: PostToPublish): PublishResult {
        val savedToken = settings.getSecret(InstagramSettings.AccessTokenSecret)
            ?: return PublishResult.Failure("Add an Instagram access token in settings first.")
        if (!imageHost.isConfigured) {
            return PublishResult.Failure("Set up image hosting in settings first: Instagram fetches the image from the web.")
        }
        problemWith(post.text)?.let { return PublishResult.Failure(it) }
        problemWithImage(post.imageWidth, post.imageHeight)?.let { return PublishResult.Failure(it) }

        var token = savedToken
        var uploadedName: String? = null
        return try {
            token = refreshIfDue(savedToken)
            val account = account(token)
            val graph = graphFor(token)

            val fileName = "post-${clock.nowMillis()}.jpg"
            val imageUrl = try {
                imageHost.upload(post.jpeg, fileName)
            } catch (e: Exception) {
                throw StepFailed("The image could not be uploaded for Instagram to fetch: ${e.message ?: "unknown error"}")
            }
            uploadedName = fileName

            val container = post(
                token,
                "$graph/${account.id}/media",
                buildJsonObject {
                    put("image_url", imageUrl)
                    if (post.text.isNotBlank()) put("caption", post.text)
                    if (post.altText.isNotBlank()) put("alt_text", post.altText.trim())
                },
                step = "prepare the post",
            ).text("id").ifEmpty { throw StepFailed("Instagram did not accept the image.") }

            waitUntilReady(token, container)

            val mediaId = post(
                token,
                "$graph/${account.id}/media_publish",
                buildJsonObject { put("creation_id", container) },
                step = "publish the post",
            ).text("id").ifEmpty {
                throw StepFailed("Instagram did not confirm the post. Check your profile before trying again.")
            }

            val permalink = runCatching {
                get(token, "$graph/$mediaId?fields=permalink", step = "find the post").text("permalink")
            }.getOrDefault("")
            PublishResult.Success(permalink.ifEmpty { "https://www.instagram.com/${account.username}/" })
        } catch (failure: StepFailed) {
            PublishResult.Failure(redact(failure.message.orEmpty(), savedToken, token))
        } catch (e: Exception) {
            PublishResult.Failure(
                redact("Instagram could not be reached: ${e.message ?: "no connection"}", savedToken, token),
            )
        } finally {
            uploadedName?.let { name -> runCatching { imageHost.delete(name) } }
        }
    }

    private class Account(val id: String, val username: String)

    // Learned the first time the token is used and kept for this run of the
    // app, so checking a name does not ask who the account is every time.
    @Volatile
    private var knownAccount: Account? = null

    // Set once Meta has shown it will not answer lookups of other accounts
    // for this kind of token, so the app stops asking.
    @Volatile
    private var lookupsRefused = false

    /**
     * Asks Meta about another account by name. Meta documents this lookup
     * (Business Discovery) only for the older Facebook login, and it only
     * knows professional accounts, so the answer is either "found" or
     * "cannot say": a name is never reported as not existing.
     */
    override fun findAccount(name: String): AccountLookup {
        val wanted = name.trim().removePrefix("@").lowercase()
        val token = settings.getSecret(InstagramSettings.AccessTokenSecret) ?: return AccountLookup.CannotCheck
        if (lookupsRefused || InstagramSettings.isInstagramLoginToken(token)) return AccountLookup.CannotCheck
        if (!LookupNamePattern.matches(wanted)) return AccountLookup.CannotCheck
        return try {
            val account = knownAccount ?: account(token)
            val response = transport.send(
                HttpRequest(
                    method = "GET",
                    url = "$unifiedGraphUrl/${account.id}?fields=business_discovery.username($wanted){username,name}",
                    headers = bearer(token),
                ),
            )
            val reply = runCatching { json.parseToJsonElement(response.body) as? JsonObject }.getOrNull()
            val found = reply?.get("business_discovery") as? JsonObject
            when {
                response.isSuccess && found != null -> AccountLookup.Found(
                    handle = found.text("username").ifEmpty { wanted },
                    displayName = found.text("name"),
                )

                else -> {
                    val error = reply?.get("error") as? JsonObject
                    val code = (error?.get("code") as? JsonPrimitive)?.content?.toIntOrNull()
                    val subcode = (error?.get("error_subcode") as? JsonPrimitive)?.content?.toIntOrNull()
                    // "No such professional account" is an answer about this one
                    // name. Anything else means the lookup itself is not on offer.
                    val aboutThisName = code == UnknownAccountCode || subcode == UnknownAccountSubcode
                    if (!response.isSuccess && !aboutThisName) lookupsRefused = true
                    AccountLookup.CannotCheck
                }
            }
        } catch (_: Exception) {
            AccountLookup.CannotCheck
        }
    }

    /** The names this account has mentioned in the captions of its recent posts, when Meta will show them. */
    override fun pastMentions(): List<String> {
        val token = settings.getSecret(InstagramSettings.AccessTokenSecret) ?: return emptyList()
        return try {
            val account = knownAccount ?: account(token)
            val reply = get(
                token,
                "${graphFor(token)}/${account.id}/media?fields=caption&limit=$PastPostsToRead",
                step = "read earlier posts",
            )
            (reply["data"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.text("caption") }
                .flatMap { mentionNamesFor(PublishTarget.Instagram, it) }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun graphFor(token: String): String =
        if (InstagramSettings.isInstagramLoginToken(token)) graphUrl else unifiedGraphUrl

    private fun account(token: String): Account {
        val found = if (InstagramSettings.isInstagramLoginToken(token)) ownAccount(token) else assignedAccount(token)
        val current = settings.readSettings()
        if (found.username.isNotEmpty() && current.instagram.username != found.username) {
            settings.saveSettings(current.copy(instagram = current.instagram.copy(username = found.username)))
        }
        return Account(id = found.id, username = found.username.ifEmpty { current.instagram.username })
            .also { knownAccount = it }
    }

    /** An "Instagram login" token belongs to one account and can simply be asked whose it is. */
    private fun ownAccount(token: String): Account {
        val me = get(token, "$graphUrl/me?fields=user_id,username", step = "sign in")
        val id = me.text("user_id").ifEmpty { me.text("id") }
        if (id.isEmpty()) throw StepFailed("Instagram signed in but did not say which account this is.")
        return Account(id = id, username = me.text("username"))
    }

    /**
     * A "Unified login" token belongs to a business portfolio, so it is asked
     * which Instagram accounts it has been given. When there are several, the
     * one used before is kept; otherwise the first.
     */
    private fun assignedAccount(token: String): Account {
        val reply = get(token, "$unifiedGraphUrl/me/assigned_instagram_accounts?fields=id,username", step = "sign in")
        val accounts = (reply["data"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .map { Account(id = it.text("id"), username = it.text("username")) }
            .filter { it.id.isNotEmpty() }
        if (accounts.isEmpty()) {
            throw StepFailed(
                "Meta accepted the access token but it has no Instagram account attached. In Meta's developer " +
                    "console, add your account under Generate access tokens and make a new token.",
            )
        }
        val usedBefore = settings.readSettings().instagram.username
        return accounts.firstOrNull { usedBefore.isNotEmpty() && it.username.equals(usedBefore, ignoreCase = true) }
            ?: accounts.first()
    }

    /** Instagram fetches the image in the background, so the container may not be ready at once. */
    private fun waitUntilReady(token: String, container: String) {
        repeat(MaxStatusChecks) { attempt ->
            val status = get(token, "${graphFor(token)}/$container?fields=status_code", step = "check the image")
                .text("status_code")
            when (status) {
                "", "FINISHED", "PUBLISHED" -> return
                "ERROR", "EXPIRED" -> throw StepFailed(
                    "Instagram could not use the image. It must be a JPEG it can fetch from the hosting address.",
                )
            }
            if (attempt < MaxStatusChecks - 1) pause(StatusCheckDelayMillis)
        }
        throw StepFailed("Instagram is still fetching the image. Try again in a minute.")
    }

    /**
     * Long-lived "Instagram login" tokens last about sixty days and can be
     * renewed once they are a day old. Renewing whenever one is due keeps the
     * connection alive for as long as the app is used now and then. A failed
     * renewal is not fatal: the current token is simply used as it is. A
     * "Unified login" token cannot be renewed from the phone and is left alone.
     */
    private fun refreshIfDue(token: String): String {
        if (!InstagramSettings.isInstagramLoginToken(token)) return token
        val now = clock.nowMillis()
        val instagram = settings.readSettings().instagram
        if (now - instagram.tokenSavedAtMillis < RefreshAfterMillis) return token
        return try {
            val reply = get(
                token,
                "$graphUrl/refresh_access_token?grant_type=ig_refresh_token&access_token=$token",
                step = "renew the access token",
            )
            val renewed = reply.text("access_token").ifEmpty { return token }
            val expiresInSeconds = (reply["expires_in"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
            settings.saveSecret(InstagramSettings.AccessTokenSecret, renewed)
            val current = settings.readSettings()
            settings.saveSettings(
                current.copy(
                    instagram = current.instagram.copy(
                        tokenSavedAtMillis = now,
                        tokenExpiresAtMillis = if (expiresInSeconds > 0) now + expiresInSeconds * 1000 else 0L,
                    ),
                ),
            )
            renewed
        } catch (_: Exception) {
            token
        }
    }

    private fun get(token: String, url: String, step: String): JsonObject =
        send(token, HttpRequest(method = "GET", url = url, headers = bearer(token)), step)

    private fun post(token: String, url: String, body: JsonObject, step: String): JsonObject =
        send(
            token,
            HttpRequest(
                method = "POST",
                url = url,
                headers = bearer(token),
                body = body.toString().encodeToByteArray(),
                contentType = "application/json",
            ),
            step,
        )

    private fun bearer(token: String) = mapOf("Authorization" to "Bearer $token")

    private fun send(token: String, request: HttpRequest, step: String): JsonObject {
        val response = transport.send(request)
        val reply = try {
            json.parseToJsonElement(response.body) as? JsonObject
        } catch (_: Exception) {
            null
        }
        if (response.isSuccess) {
            return reply ?: throw StepFailed("Instagram sent a reply that could not be read while trying to $step.")
        }
        val error = reply?.get("error") as? JsonObject
        val code = (error?.get("code") as? JsonPrimitive)?.content?.toIntOrNull()
        val detail = error?.text("message").orEmpty()
        val suffix = if (detail.isEmpty()) "" else " ($detail)"
        throw StepFailed(
            redact(
                when {
                    code == InvalidTokenCode || response.status == 401 ->
                        "Instagram did not accept the access token. It may have expired: make a new one in " +
                            "Meta's developer console and save it in settings.$suffix"

                    code in RateLimitCodes || response.status == 429 ->
                        "Instagram is limiting requests from this account. Try again later.$suffix"

                    else -> "Instagram could not $step (${response.status}).$suffix"
                },
                token,
            ),
        )
    }

    private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()

    /** A renewal swaps the token part way through, so both the old and the new one are kept out of messages. */
    private fun redact(message: String, vararg tokens: String): String =
        SecretRedactor.redactKnownSecrets(message, tokens.distinct())

    private class StepFailed(message: String) : Exception(message)

    private companion object {
        const val MinAspect = 4f / 5f
        const val MaxAspect = 1.91f
        const val AspectTolerance = 0.01f
        const val MaxStatusChecks = 12
        const val StatusCheckDelayMillis = 1_500L
        const val RefreshAfterMillis = 24L * 60 * 60 * 1000
        const val InvalidTokenCode = 190
        const val UnknownAccountCode = 110
        const val UnknownAccountSubcode = 2207013
        const val PastPostsToRead = 50
        val LookupNamePattern = Regex("[a-z0-9._]{1,30}")
        val RateLimitCodes = setOf(4, 17, 32, 613)
        val json = Json { ignoreUnknownKeys = true }

        // A few bytes standing in for an image when only checking that hosting accepts an upload.
        val TinyJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    }
}

/**
 * The Firebase project used for temporary image hosting, entered on the phone
 * rather than bundled, because the repository is public. The upload password
 * is a secret and is stored separately.
 */
data class FirebaseHostSettings(
    val apiKey: String = "",
    val appId: String = "",
    val projectId: String = "",
    val storageBucket: String = "",
    /** The Firebase sign-in used only for uploads. */
    val email: String = "",
) {
    val hasProject: Boolean
        get() = apiKey.isNotBlank() && appId.isNotBlank() && projectId.isNotBlank() && storageBucket.isNotBlank()

    companion object {
        const val UploadPasswordSecret = "firebase_upload_password"
    }
}

/**
 * Reads a Firebase project's details out of text pasted from the Firebase
 * console. Both forms the console offers are understood: the contents of
 * `google-services.json`, and the `firebaseConfig` snippet shown for web apps.
 * Returns null when any of the four values is missing.
 */
fun parseFirebaseConfig(text: String): FirebaseHostSettings? {
    fun value(vararg names: String): String =
        names.firstNotNullOfOrNull { name ->
            Regex("[\"']?${Regex.escape(name)}[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']").find(text)?.groupValues?.get(1)
        }.orEmpty().trim()

    val parsed = FirebaseHostSettings(
        apiKey = value("current_key", "apiKey"),
        appId = value("mobilesdk_app_id", "appId"),
        projectId = value("project_id", "projectId"),
        storageBucket = value("storage_bucket", "storageBucket"),
    )
    return parsed.takeIf { it.hasProject }
}
