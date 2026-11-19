package com.digitumdei.shotquill.shared.social

import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.LocalAppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstagramTest {
    private val now = 1_795_000_000_000L
    private val transport = RoutedTransport()
    private val host = FakeImageHost()
    private val pauses = mutableListOf<Long>()
    private val settings = InMemoryLocalSettingsRepository(
        LocalAppSettings(instagram = InstagramSettings(tokenSavedAtMillis = now - 1_000)),
    ).apply { saveSecret(InstagramSettings.AccessTokenSecret, "IGTOKEN-abcdef-123456") }
    private val publisher = InstagramPublisher(
        settings = settings,
        transport = transport,
        imageHost = host,
        clock = object : EpochClock {
            override fun nowMillis(): Long = now
        },
        pause = { pauses += it },
    )

    private fun post(text: String = "First pour of the witbier.", width: Int = 1080, height: Int = 1350) =
        PostToPublish(text = text, jpeg = byteArrayOf(1, 2, 3), altText = " A glass of beer. ", imageWidth = width, imageHeight = height)

    private fun happyPath() {
        transport.on("/me?", """{"user_id":"1789","username":"doctrinebrewing","id":"app-scoped"}""")
        transport.on("/1789/media_publish", """{"id":"media-55"}""")
        transport.on("/1789/media", """{"id":"container-9"}""")
        transport.on("/container-9?fields=status_code", """{"status_code":"FINISHED"}""")
        transport.on("/media-55?fields=permalink", """{"permalink":"https://www.instagram.com/p/ABC123/"}""")
    }

    @Test
    fun hostsTheImageCreatesAContainerPublishesItAndCleansUp() {
        happyPath()

        val result = publisher.publish(post())

        assertEquals(PublishResult.Success("https://www.instagram.com/p/ABC123/"), result)
        assertEquals(listOf("post-$now.jpg"), host.uploaded)
        assertEquals(listOf("post-$now.jpg"), host.deleted)

        val create = transport.requests.first { it.url.endsWith("/1789/media") }
        assertEquals("POST", create.method)
        assertEquals("Bearer IGTOKEN-abcdef-123456", create.headers["Authorization"])
        assertEquals("application/json", create.contentType)
        val body = Json.parseToJsonElement(create.body!!.decodeToString()).jsonObject
        assertEquals("https://host.example/post-$now.jpg", body["image_url"]?.jsonPrimitive?.content)
        assertEquals("First pour of the witbier.", body["caption"]?.jsonPrimitive?.content)
        assertEquals("A glass of beer.", body["alt_text"]?.jsonPrimitive?.content)

        val publish = transport.requests.first { it.url.endsWith("/1789/media_publish") }
        assertEquals(
            "container-9",
            Json.parseToJsonElement(publish.body!!.decodeToString()).jsonObject["creation_id"]?.jsonPrimitive?.content,
        )
        assertEquals("doctrinebrewing", settings.readSettings().instagram.username)
        assertEquals("@doctrinebrewing", publisher.accountLabel)
        assertTrue(transport.requests.none { it.url.contains("refresh_access_token") })
    }

    @Test
    fun emptyCaptionAndAltTextAreLeftOut() {
        happyPath()

        publisher.publish(PostToPublish("", byteArrayOf(1), "  ", 1080, 1080))

        val body = Json.parseToJsonElement(
            transport.requests.first { it.url.endsWith("/1789/media") }.body!!.decodeToString(),
        ).jsonObject
        assertNull(body["caption"])
        assertNull(body["alt_text"])
    }

    @Test
    fun waitsWhileInstagramIsStillFetchingTheImage() {
        happyPath()
        transport.sequence("/container-9?fields=status_code", """{"status_code":"IN_PROGRESS"}""", """{"status_code":"IN_PROGRESS"}""", """{"status_code":"FINISHED"}""")

        assertTrue(publisher.publish(post()) is PublishResult.Success)
        assertEquals(listOf(1_500L, 1_500L), pauses)
    }

    @Test
    fun givesUpWhenTheImageIsRejectedOrNeverBecomesReady() {
        happyPath()
        transport.on("/container-9?fields=status_code", """{"status_code":"ERROR"}""")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("could not use the image"))
        assertEquals(host.uploaded, host.deleted)

        transport.on("/container-9?fields=status_code", """{"status_code":"IN_PROGRESS"}""")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("still fetching"))
        assertTrue(transport.requests.none { it.url.endsWith("/media_publish") })
    }

    @Test
    fun aMissingPermalinkFallsBackToTheProfile() {
        happyPath()
        transport.on("/media-55?fields=permalink", """{"error":{"message":"nope","code":100}}""", status = 400)

        assertEquals(PublishResult.Success("https://www.instagram.com/doctrinebrewing/"), publisher.publish(post()))
    }

    @Test
    fun anExpiredTokenIsExplainedWithoutShowingIt() {
        transport.on("/me?", """{"error":{"message":"Error validating access token IGTOKEN-abcdef-123456","type":"OAuthException","code":190}}""", status = 400)

        val message = (publisher.publish(post()) as PublishResult.Failure).message

        assertTrue(message.contains("did not accept the access token"))
        assertFalse(message.contains("IGTOKEN"))
        assertTrue(host.uploaded.isEmpty())
    }

    @Test
    fun otherRefusalsAreExplained() {
        happyPath()
        transport.on("/1789/media", """{"error":{"message":"Application request limit reached","code":4}}""", status = 400)
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("limiting requests"))
        assertEquals(host.uploaded, host.deleted)

        transport.on("/1789/media", """{"error":{"message":"Only photo or video can be accepted","code":9004}}""", status = 400)
        val refused = (publisher.publish(post()) as PublishResult.Failure).message
        assertTrue(refused.contains("could not prepare the post (400)"))
        assertTrue(refused.contains("Only photo or video"))

        transport.on("/1789/media", """{"oops":true}""")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("did not accept the image"))

        transport.on("/1789/media", """{"id":"container-9"}""")
        transport.on("/1789/media_publish", """{}""")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("did not confirm the post"))

        transport.on("/me?", "<html>gateway</html>")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("could not be read"))

        transport.on("/me?", """{"username":"nobody"}""")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("did not say which account"))

        transport.failure = IllegalStateException("Unable to resolve host graph.instagram.com")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("could not be reached"))
    }

    @Test
    fun aFailedUploadStopsBeforeInstagramIsAsked() {
        happyPath()
        host.failure = IllegalStateException("Firebase could not upload the image: permission denied")

        val message = (publisher.publish(post()) as PublishResult.Failure).message

        assertTrue(message.contains("could not be uploaded"))
        assertTrue(message.contains("permission denied"))
        assertTrue(transport.requests.none { it.url.endsWith("/1789/media") })
        assertTrue(host.deleted.isEmpty())
    }

    @Test
    fun nothingIsSentWhenThePostCannotBeAccepted() {
        assertTrue((publisher.publish(post(text = "a".repeat(2201))) as PublishResult.Failure).message.contains("2200"))
        assertTrue(
            (publisher.publish(post(text = (1..31).joinToString(" ") { "#t$it" })) as PublishResult.Failure)
                .message.contains("30 hashtags"),
        )
        assertTrue((publisher.publish(post(width = 1080, height = 1920)) as PublishResult.Failure).message.contains("4:5 to 1.91:1"))

        host.configured = false
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("image hosting"))
        assertFalse(publisher.isConfigured)

        host.configured = true
        settings.clearSecret(InstagramSettings.AccessTokenSecret)
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("access token"))
        assertNull(publisher.accountLabel)
        assertFalse(publisher.isConfigured)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun acceptsTheShapesInstagramTakes() {
        assertNull(publisher.problemWithImage(1080, 1350))
        assertNull(publisher.problemWithImage(1080, 1080))
        assertNull(publisher.problemWithImage(1910, 1000))
        assertNull(publisher.problemWithImage(0, 0))
        assertTrue(publisher.problemWithImage(1080, 1920) != null)
        assertTrue(publisher.problemWithImage(3000, 1000) != null)
        assertNull(publisher.problemWith("fine"))
        assertEquals(8_000_000, publisher.maxImageBytes)
        assertEquals(PublishTarget.Instagram, publisher.target)
        assertEquals("your Instagram account", publisher.accountLabel)
        assertTrue(publisher.isConfigured)
    }

    @Test
    fun aTokenOlderThanADayIsRenewedAndTheNewOneUsed() {
        settings.saveSettings(LocalAppSettings(instagram = InstagramSettings(tokenSavedAtMillis = now - 2 * 86_400_000L)))
        happyPath()
        transport.on("/refresh_access_token", """{"access_token":"IGTOKEN-renewed-999999","token_type":"bearer","expires_in":5184000}""")

        assertTrue(publisher.publish(post()) is PublishResult.Success)

        assertEquals("IGTOKEN-renewed-999999", settings.getSecret(InstagramSettings.AccessTokenSecret))
        val saved = settings.readSettings().instagram
        assertEquals(now, saved.tokenSavedAtMillis)
        assertEquals(now + 5_184_000_000L, saved.tokenExpiresAtMillis)
        assertEquals(
            "Bearer IGTOKEN-renewed-999999",
            transport.requests.first { it.url.endsWith("/1789/media") }.headers["Authorization"],
        )
    }

    @Test
    fun aFailedRenewalFallsBackToTheCurrentToken() {
        settings.saveSettings(LocalAppSettings(instagram = InstagramSettings(tokenSavedAtMillis = 0L)))
        happyPath()
        transport.on("/refresh_access_token", """{"error":{"message":"too young","code":10}}""", status = 400)

        assertTrue(publisher.publish(post()) is PublishResult.Success)
        assertEquals("IGTOKEN-abcdef-123456", settings.getSecret(InstagramSettings.AccessTokenSecret))
    }

    @Test
    fun aUnifiedLoginTokenPostsThroughMetasMainServerToTheAccountItWasGiven() {
        settings.saveSecret(InstagramSettings.AccessTokenSecret, "EAAunified-abcdef-123456")
        // Old enough that an Instagram login token would be renewed.
        settings.saveSettings(LocalAppSettings(instagram = InstagramSettings(tokenSavedAtMillis = 0L)))
        transport.on(
            "/me/assigned_instagram_accounts",
            """{"data":[{"id":"17841400000000001","username":"doctrinebrewing"}],"paging":{"cursors":{"before":"a","after":"b"}}}""",
        )
        transport.on("/17841400000000001/media_publish", """{"id":"media-55"}""")
        transport.on("/17841400000000001/media", """{"id":"container-9"}""")
        transport.on("/container-9?fields=status_code", """{"status_code":"FINISHED"}""")
        transport.on("/media-55?fields=permalink", """{"permalink":"https://www.instagram.com/p/XYZ789/"}""")

        val result = publisher.publish(post())

        assertEquals(PublishResult.Success("https://www.instagram.com/p/XYZ789/"), result)
        assertEquals(
            listOf(
                "GET https://graph.facebook.com/v27.0/me/assigned_instagram_accounts?fields=id,username",
                "POST https://graph.facebook.com/v27.0/17841400000000001/media",
                "GET https://graph.facebook.com/v27.0/container-9?fields=status_code",
                "POST https://graph.facebook.com/v27.0/17841400000000001/media_publish",
                "GET https://graph.facebook.com/v27.0/media-55?fields=permalink",
            ),
            transport.requests.map { "${it.method} ${it.url}" },
        )
        assertTrue(transport.requests.all { it.headers["Authorization"] == "Bearer EAAunified-abcdef-123456" })
        val body = Json.parseToJsonElement(transport.requests[1].body!!.decodeToString()).jsonObject
        assertEquals("https://host.example/post-$now.jpg", body["image_url"]?.jsonPrimitive?.content)
        assertEquals("A glass of beer.", body["alt_text"]?.jsonPrimitive?.content)
        assertEquals("doctrinebrewing", settings.readSettings().instagram.username)
        assertEquals("EAAunified-abcdef-123456", settings.getSecret(InstagramSettings.AccessTokenSecret))
        assertEquals(host.uploaded, host.deleted)
    }

    @Test
    fun aUnifiedLoginTokenKeepsToTheAccountUsedBeforeAndSaysWhenItHasNone() {
        settings.saveSecret(InstagramSettings.AccessTokenSecret, "EAAunified-abcdef-123456")
        transport.on(
            "/me/assigned_instagram_accounts",
            """{"data":[{"id":"111","username":"another_account"},{"id":"222","username":"DoctrineBrewing"},{"username":"no_id"}]}""",
        )
        assertEquals("Connected to Instagram as @another_account, and image hosting works.", publisher.check())

        settings.saveSettings(LocalAppSettings(instagram = InstagramSettings(username = "doctrinebrewing")))
        assertEquals("Connected to Instagram as @DoctrineBrewing, and image hosting works.", publisher.check())

        transport.on("/me/assigned_instagram_accounts", """{"data":[]}""")
        val none = publisher.check()
        assertTrue(none.contains("no Instagram account attached"))
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("no Instagram account attached"))
        assertTrue(host.uploaded.size == 2)

        transport.on(
            "/me/assigned_instagram_accounts",
            """{"error":{"message":"Invalid OAuth access token EAAunified-abcdef-123456","code":190}}""",
            status = 400,
        )
        val refused = publisher.check()
        assertTrue(refused.contains("did not accept the access token"))
        assertFalse(refused.contains("EAAunified"))

        assertTrue(InstagramSettings.isInstagramLoginToken(" IGAAexample "))
        assertFalse(InstagramSettings.isInstagramLoginToken("EAAexample"))
    }

    @Test
    fun checkingTriesTheTokenAndTheHostingWithoutPosting() {
        happyPath()

        assertEquals("Connected to Instagram as @doctrinebrewing, and image hosting works.", publisher.check())
        assertEquals(1, host.uploaded.size)
        assertEquals(host.uploaded, host.deleted)
        assertTrue(transport.requests.none { it.url.contains("/media") })

        host.configured = false
        assertTrue(publisher.check().contains("Image hosting is not set up yet"))

        host.configured = true
        host.failure = IllegalStateException("Firebase could not sign in to Firebase: wrong password")
        assertTrue(publisher.check().contains("wrong password"))

        host.failure = null
        transport.on("/me?", """{"error":{"message":"bad","code":190}}""", status = 400)
        assertTrue(publisher.check().contains("did not accept the access token"))

        settings.clearSecret(InstagramSettings.AccessTokenSecret)
        assertEquals("Add an Instagram access token first.", publisher.check())
    }

    @Test
    fun readsAFirebaseProjectFromEitherFormTheConsoleOffers() {
        val googleServices = """
            {
              "project_info": { "project_number": "123", "project_id": "doctrine-site", "storage_bucket": "doctrine-site.firebasestorage.app" },
              "client": [ { "client_info": { "mobilesdk_app_id": "1:123:android:abc", "android_client_info": { "package_name": "com.digitumdei.shotquill" } },
                            "api_key": [ { "current_key": "AIzaSyExample" } ] } ]
            }
        """.trimIndent()
        val expected = FirebaseHostSettings(
            apiKey = "AIzaSyExample",
            appId = "1:123:android:abc",
            projectId = "doctrine-site",
            storageBucket = "doctrine-site.firebasestorage.app",
        )
        assertEquals(expected, parseFirebaseConfig(googleServices))
        assertTrue(expected.hasProject)

        val web = """
            const firebaseConfig = {
              apiKey: "AIzaSyWeb",
              authDomain: "doctrine-site.firebaseapp.com",
              projectId: 'doctrine-site',
              storageBucket: "doctrine-site.appspot.com",
              appId: "1:123:web:def"
            };
        """.trimIndent()
        assertEquals(
            FirebaseHostSettings("AIzaSyWeb", "1:123:web:def", "doctrine-site", "doctrine-site.appspot.com"),
            parseFirebaseConfig(web),
        )

        assertNull(parseFirebaseConfig("""{"project_id":"only-this"}"""))
        assertNull(parseFirebaseConfig("not a config at all"))
        assertFalse(FirebaseHostSettings().hasProject)
    }

    @Test
    fun blueskySignInCanBeCheckedWithoutPosting() {
        val blueskySettings = InMemoryLocalSettingsRepository(
            LocalAppSettings(bluesky = BlueskySettings(handle = "doctrine.bsky.social")),
        )
        val bluesky = BlueskyPublisher(blueskySettings, transport)
        assertEquals("Add your Bluesky handle and an app password first.", bluesky.check())

        blueskySettings.saveSecret(BlueskySettings.AppPasswordSecret, "abcd-efgh-ijkl-mnop")
        transport.on("createSession", """{"accessJwt":"t","did":"did:plc:abc","handle":"doctrine.bsky.social"}""")
        assertEquals("Signed in to Bluesky as @doctrine.bsky.social.", bluesky.check())

        transport.on("createSession", """{"error":"AuthenticationRequired"}""", status = 401)
        assertEquals("Bluesky did not accept the handle or app password.", bluesky.check())

        transport.on("createSession", """{"error":"RateLimitExceeded"}""", status = 429)
        assertTrue(bluesky.check().contains("rate-limiting"))

        transport.failure = IllegalStateException("offline")
        assertTrue(bluesky.check().contains("could not be reached"))

        blueskySettings.saveSettings(LocalAppSettings(bluesky = BlueskySettings(handle = "doctrine.bsky.social", server = "nope")))
        assertEquals("The Bluesky server address is not a web address.", bluesky.check())
    }
}

/** Answers by matching part of the address, so tests do not depend on the order of calls. */
private class RoutedTransport : HttpTransport {
    private val routes = mutableListOf<Pair<String, MutableList<HttpResponse>>>()
    val requests = mutableListOf<HttpRequest>()
    var failure: Exception? = null

    fun on(fragment: String, body: String, status: Int = 200) {
        routes.removeAll { it.first == fragment }
        routes.add(0, fragment to mutableListOf(HttpResponse(status, body)))
        requests.clear()
        failure = null
    }

    fun sequence(fragment: String, vararg bodies: String) {
        routes.removeAll { it.first == fragment }
        routes.add(0, fragment to bodies.map { HttpResponse(200, it) }.toMutableList())
    }

    override fun send(request: HttpRequest): HttpResponse {
        failure?.let { throw it }
        requests += request
        // The longest matching fragment wins, so "/media_publish" is not answered by "/media".
        val replies = routes.filter { request.url.contains(it.first) }.maxByOrNull { it.first.length }?.second
            ?: return HttpResponse(404, """{"error":{"message":"no route for ${request.url}","code":100}}""")
        return if (replies.size > 1) replies.removeAt(0) else replies.first()
    }
}

private class FakeImageHost : TemporaryImageHost {
    var configured = true
    var failure: Exception? = null
    val uploaded = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override val isConfigured: Boolean get() = configured

    override fun upload(jpeg: ByteArray, fileName: String): String {
        failure?.let { throw it }
        uploaded += fileName
        return "https://host.example/$fileName"
    }

    override fun delete(fileName: String) {
        deleted += fileName
    }
}
