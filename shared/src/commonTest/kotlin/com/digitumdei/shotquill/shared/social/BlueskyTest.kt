package com.digitumdei.shotquill.shared.social

import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.LocalAppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlueskyTest {
    private val transport = ScriptedTransport()
    private val settings = InMemoryLocalSettingsRepository(
        LocalAppSettings(bluesky = BlueskySettings(handle = " @doctrine.bsky.social ")),
    ).apply { saveSecret(BlueskySettings.AppPasswordSecret, "abcd-efgh-ijkl-mnop") }
    private val publisher = BlueskyPublisher(
        settings = settings,
        transport = transport,
        clock = object : EpochClock {
            override fun nowMillis(): Long = 1_795_000_000_000L
        },
    )

    private fun post(text: String = "First pour. #witbier https://doctrinebrewing.co.za", jpeg: ByteArray = byteArrayOf(1, 2, 3)) =
        PostToPublish(text = text, jpeg = jpeg, altText = " A glass of beer. ", imageWidth = 1080, imageHeight = 1350)

    private fun happyPath() {
        transport.replies += HttpResponse(200, """{"accessJwt":"token-123","did":"did:plc:abc","handle":"doctrine.bsky.social"}""")
        transport.replies += HttpResponse(200, """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafy"},"mimeType":"image/jpeg","size":3}}""")
        transport.replies += HttpResponse(200, """{"uri":"at://did:plc:abc/app.bsky.feed.post/3kabc","cid":"cid1"}""")
    }

    @Test
    fun signsInUploadsTheImageAndCreatesThePost() {
        happyPath()

        val result = publisher.publish(post())

        assertEquals(PublishResult.Success("https://bsky.app/profile/doctrine.bsky.social/post/3kabc"), result)
        val (signIn, upload, create) = transport.requests

        assertEquals("https://bsky.social/xrpc/com.atproto.server.createSession", signIn.url)
        val credentials = signIn.json()
        assertEquals("doctrine.bsky.social", credentials["identifier"]?.jsonPrimitive?.content)
        assertEquals("abcd-efgh-ijkl-mnop", credentials["password"]?.jsonPrimitive?.content)

        assertEquals("https://bsky.social/xrpc/com.atproto.repo.uploadBlob", upload.url)
        assertEquals("image/jpeg", upload.contentType)
        assertEquals("Bearer token-123", upload.headers["Authorization"])
        assertEquals(listOf<Byte>(1, 2, 3), upload.body?.toList())

        assertEquals("https://bsky.social/xrpc/com.atproto.repo.createRecord", create.url)
        val body = create.json()
        assertEquals("did:plc:abc", body["repo"]?.jsonPrimitive?.content)
        assertEquals("app.bsky.feed.post", body["collection"]?.jsonPrimitive?.content)
        val record = body["record"]!!.jsonObject
        assertEquals("First pour. #witbier https://doctrinebrewing.co.za", record["text"]?.jsonPrimitive?.content)
        assertEquals("2026-11-18T11:06:40Z", record["createdAt"]?.jsonPrimitive?.content)
        assertEquals("en", record["langs"]!!.jsonArray.single().jsonPrimitive.content)
        val image = record["embed"]!!.jsonObject["images"]!!.jsonArray.single().jsonObject
        assertEquals("A glass of beer.", image["alt"]?.jsonPrimitive?.content)
        assertEquals("bafy", image["image"]!!.jsonObject["ref"]!!.jsonObject["\$link"]?.jsonPrimitive?.content)
        assertEquals(1080, image["aspectRatio"]!!.jsonObject["width"]?.jsonPrimitive?.int)
        assertEquals(1350, image["aspectRatio"]!!.jsonObject["height"]?.jsonPrimitive?.int)
        val facets = record["facets"]!!.jsonArray
        assertEquals(2, facets.size)
        assertEquals(
            "witbier",
            facets[0].jsonObject["features"]!!.jsonArray.single().jsonObject["tag"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "https://doctrinebrewing.co.za",
            facets[1].jsonObject["features"]!!.jsonArray.single().jsonObject["uri"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun plainTextIsPostedWithoutFacets() {
        happyPath()

        publisher.publish(post(text = "Just a quiet pint."))

        assertNull(transport.requests[2].json()["record"]!!.jsonObject["facets"])
    }

    @Test
    fun reportsWhoWillPostAndWhetherItIsSetUp() {
        assertTrue(publisher.isConfigured)
        assertEquals("@doctrine.bsky.social", publisher.accountLabel)
        assertEquals(PublishTarget.Bluesky, publisher.target)

        settings.clearSecret(BlueskySettings.AppPasswordSecret)
        assertFalse(publisher.isConfigured)
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("app password"))

        settings.saveSecret(BlueskySettings.AppPasswordSecret, "abcd-efgh-ijkl-mnop")
        settings.saveSettings(LocalAppSettings(bluesky = BlueskySettings(handle = " ")))
        assertFalse(publisher.isConfigured)
        assertNull(publisher.accountLabel)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun aRefusedSignInIsExplained() {
        transport.replies += HttpResponse(401, """{"error":"AuthenticationRequired","message":"Invalid identifier or password"}""")

        val result = publisher.publish(post())

        assertEquals(PublishResult.Failure("Bluesky did not accept the handle or app password."), result)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun failuresPartWayAreExplainedWithoutThePassword() {
        transport.replies += HttpResponse(200, """{"accessJwt":"token-123","did":"did:plc:abc","handle":"doctrine.bsky.social"}""")
        transport.replies += HttpResponse(400, """{"error":"BlobTooLarge","message":"too big for abcd-efgh-ijkl-mnop"}""")
        val tooLarge = (publisher.publish(post()) as PublishResult.Failure).message
        assertTrue(tooLarge.contains("upload the image (400)"))
        assertFalse(tooLarge.contains("abcd-efgh"))

        transport.reset()
        transport.replies += HttpResponse(429, """{"error":"RateLimitExceeded"}""")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("rate-limiting"))

        transport.reset()
        transport.replies += HttpResponse(200, """{"accessJwt":"token-123","did":"did:plc:abc"}""")
        transport.replies += HttpResponse(401, """{"error":"ExpiredToken"}""")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("refused to upload the image"))

        transport.reset()
        transport.replies += HttpResponse(200, "<html>proxy</html>")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("could not be read"))

        transport.reset()
        transport.replies += HttpResponse(200, """{"accessJwt":"","did":""}""")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("incomplete reply"))

        transport.reset()
        happyPath()
        transport.replies[1] = HttpResponse(200, """{"notblob":true}""")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("did not accept the image"))

        transport.reset()
        happyPath()
        transport.replies[2] = HttpResponse(200, """{"cid":"cid1"}""")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("did not confirm the post"))

        transport.reset()
        transport.failure = IllegalStateException("Unable to resolve host bsky.social")
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("could not be reached"))
    }

    @Test
    fun nothingIsSentWhenThePostCannotBeAccepted() {
        assertTrue((publisher.publish(post(text = "a".repeat(301))) as PublishResult.Failure).message.contains("300"))
        assertTrue(
            (publisher.publish(post(jpeg = ByteArray(1_000_001))) as PublishResult.Failure).message.contains("too large"),
        )
        settings.saveSettings(LocalAppSettings(bluesky = BlueskySettings(handle = "doctrine.bsky.social", server = "bsky.social")))
        assertTrue((publisher.publish(post()) as PublishResult.Failure).message.contains("not a web address"))
        assertTrue(transport.requests.isEmpty())
        assertNull(publisher.problemWith("a".repeat(300)))
        assertTrue(publisher.maxImageBytes < 1_000_000)
    }

    @Test
    fun aCustomServerIsUsedWhenSet() {
        settings.saveSettings(
            LocalAppSettings(bluesky = BlueskySettings(handle = "me.example.org", server = "https://pds.example.org/")),
        )
        happyPath()
        transport.replies[0] = HttpResponse(200, """{"accessJwt":"t","did":"did:plc:abc"}""")

        val result = publisher.publish(post())

        assertEquals("https://pds.example.org/xrpc/com.atproto.server.createSession", transport.requests[0].url)
        assertEquals(PublishResult.Success("https://bsky.app/profile/me.example.org/post/3kabc"), result)
    }

    @Test
    fun blueskyTextFallsBackToTheCaption() {
        assertEquals("Short one.", blueskyTextFor(caption = " Long caption. ", blueskyText = " Short one. "))
        assertEquals("Long caption.", blueskyTextFor(caption = " Long caption. ", blueskyText = "  "))
        assertEquals("", blueskyTextFor(caption = "", blueskyText = ""))
    }

    @Test
    fun lengthCountsWhatAReaderSees() {
        assertEquals(0, blueskyLength(""))
        assertEquals(5, blueskyLength("hello"))
        assertEquals(4, blueskyLength("café"))
        assertEquals(1, blueskyLength("🍺"))
        assertEquals(1, blueskyLength("👍🏽"))
        assertEquals(1, blueskyLength("👨‍🍳"))
        assertEquals(2, blueskyLength("❤️!"))
        assertEquals(1, blueskyLength("\uD83C"))
    }

    @Test
    fun findsLinksAndHashtagsByByteOffset() {
        val text = "Café #KZNbeer, see https://example.com/beer. #2026 #craft_beer"
        val facets = blueskyFacets(text)

        assertEquals(listOf("KZNbeer", null, "craft_beer"), facets.map { it.tag })
        assertEquals("https://example.com/beer", facets[1].link)
        val bytes = text.encodeToByteArray()
        assertEquals("#KZNbeer", bytes.copyOfRange(facets[0].byteStart, facets[0].byteEnd).decodeToString())
        assertEquals("https://example.com/beer", bytes.copyOfRange(facets[1].byteStart, facets[1].byteEnd).decodeToString())
        assertEquals("#craft_beer", bytes.copyOfRange(facets[2].byteStart, facets[2].byteEnd).decodeToString())
    }

    @Test
    fun ignoresThingsThatOnlyLookLikeLinksOrTags() {
        assertTrue(blueskyFacets("no#tag here, 50% off, https:// alone").isEmpty())
        assertEquals(listOf("https://a.example/#section"), blueskyFacets("at https://a.example/#section now").map { it.link })
    }

    @Test
    fun tagsAreTidiedAndGetAHashWhereOneIsMissing() {
        assertEquals("#craftbeer #bskybeer", normalizeTags("  craftbeer,  #bskybeer \n"))
        assertEquals("#beersky @bskybeer.bsky.social", normalizeTags("#beersky @bskybeer.bsky.social"))
        assertEquals("", normalizeTags("  # @ , "))
        assertEquals("", normalizeTags(""))
    }

    @Test
    fun tagsFollowTheWordsAfterABlankLine() {
        assertEquals("First pour.\n\n#craftbeer #kzn", withTags(" First pour. ", "craftbeer #kzn"))
        assertEquals("First pour.", withTags("First pour.", "   "))
        assertEquals("#craftbeer", withTags("  ", "craftbeer"))
        assertEquals("", withTags("", ""))
    }

    @Test
    fun fullHandlesAreFoundAsMentionsButBareNamesAreNot() {
        val text = "Cheers @BskyBeer.bsky.social and (@doctrinebrewing.co.za) not @bskybeer or me@example.com #beersky"
        val facets = blueskyFacets(text)

        assertEquals(listOf("bskybeer.bsky.social", "doctrinebrewing.co.za"), facets.mapNotNull { it.mention })
        assertEquals(listOf("beersky"), facets.mapNotNull { it.tag })
        val bytes = text.encodeToByteArray()
        assertEquals("@BskyBeer.bsky.social", bytes.copyOfRange(facets[0].byteStart, facets[0].byteEnd).decodeToString())
    }

    @Test
    fun mentionsAreLookedUpAndOnlyTheOnesFoundAreLinked() {
        transport.replies += HttpResponse(200, """{"accessJwt":"token-123","did":"did:plc:abc","handle":"doctrine.bsky.social"}""")
        transport.replies += HttpResponse(200, """{"did":"did:plc:beer"}""")
        transport.replies += HttpResponse(400, """{"error":"InvalidRequest","message":"Unable to resolve handle"}""")
        transport.replies += HttpResponse(200, """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafy"},"mimeType":"image/jpeg","size":3}}""")
        transport.replies += HttpResponse(200, """{"uri":"at://did:plc:abc/app.bsky.feed.post/3kabc","cid":"cid1"}""")

        val result = publisher.publish(post(text = "Hi @bskybeer.bsky.social and @nobody.example.com #beersky"))

        assertTrue(result is PublishResult.Success)
        assertEquals(
            "https://bsky.social/xrpc/com.atproto.identity.resolveHandle?handle=bskybeer.bsky.social",
            transport.requests[1].url,
        )
        assertEquals("GET", transport.requests[1].method)
        val facets = transport.requests[4].json()["record"]!!.jsonObject["facets"]!!.jsonArray
        assertEquals(2, facets.size)
        val mention = facets[0].jsonObject["features"]!!.jsonArray.single().jsonObject
        assertEquals("app.bsky.richtext.facet#mention", mention["\$type"]?.jsonPrimitive?.content)
        assertEquals("did:plc:beer", mention["did"]?.jsonPrimitive?.content)
        assertEquals(
            "beersky",
            facets[1].jsonObject["features"]!!.jsonArray.single().jsonObject["tag"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun publishTargetsRoundTripThroughTheirWireValues() {
        PublishTarget.entries.forEach { assertEquals(it, PublishTarget.fromWireValue(it.wireValue)) }
        assertNull(PublishTarget.fromWireValue("myspace"))
        assertTrue(HttpResponse(204, "").isSuccess)
        assertFalse(HttpResponse(404, "").isSuccess)
    }
}

private fun HttpRequest.json(): JsonObject = Json.parseToJsonElement(body!!.decodeToString()).jsonObject

private class ScriptedTransport : HttpTransport {
    val replies = mutableListOf<HttpResponse>()
    val requests = mutableListOf<HttpRequest>()
    var failure: Exception? = null

    fun reset() {
        replies.clear()
        requests.clear()
        failure = null
    }

    override fun send(request: HttpRequest): HttpResponse {
        failure?.let { throw it }
        requests += request
        return replies.getOrNull(requests.size - 1) ?: HttpResponse(500, """{"error":"unexpected request"}""")
    }
}
