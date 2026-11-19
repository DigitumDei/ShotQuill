package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.LocalAppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiIntegrationTest {
    private val transport = RecordingTransport()
    private val client = ChatCompletionsClient(transport)
    private val jpeg = byteArrayOf(1, 2, 3)

    private fun describe(baseUrl: String = AiSettings.DefaultBaseUrl): AiResult =
        client.describeImage(baseUrl, apiKey = "sk-or-secret-key-123456", model = "vendor/model", instructions = "Describe it.", jpeg = jpeg)

    @Test
    fun postsTheInstructionsAndTheImageToTheChatCompletionsAddress() {
        transport.reply = AiHttpResponse(200, """{"model":"vendor/model-2026","choices":[{"message":{"content":"A glass of beer."}}]}""")

        val result = describe()

        assertEquals(AiResult.Success(text = "A glass of beer.", model = "vendor/model-2026"), result)
        assertEquals("https://openrouter.ai/api/v1/chat/completions", transport.url)
        assertEquals("Bearer sk-or-secret-key-123456", transport.headers["Authorization"])
        val body = Json.parseToJsonElement(transport.body).jsonObject
        assertEquals("vendor/model", body["model"]?.jsonPrimitive?.content)
        val content = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        assertEquals("Describe it.", content[0].jsonObject["text"]?.jsonPrimitive?.content)
        assertEquals(
            "data:image/jpeg;base64,AQID",
            content[1].jsonObject["image_url"]!!.jsonObject["url"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun acceptsRepliesMadeOfTextPartsAndTidiesThem() {
        transport.reply = AiHttpResponse(
            200,
            """{"choices":[{"message":{"content":[{"type":"text","text":"\"A glass\n of beer"},{"type":"text","text":"on a bar.\""}]}}]}""",
        )

        assertEquals(AiResult.Success(text = "A glass of beer on a bar.", model = "vendor/model"), describe())
    }

    @Test
    fun anEmptyOrUnreadableReplyIsAFailure() {
        transport.reply = AiHttpResponse(200, """{"choices":[{"message":{"content":"   "}}]}""")
        assertIs<AiResult.Failure>(describe())

        transport.reply = AiHttpResponse(200, """{"choices":[]}""")
        assertIs<AiResult.Failure>(describe())

        transport.reply = AiHttpResponse(200, "<html>gateway</html>")
        assertTrue((describe() as AiResult.Failure).message.contains("could not be read"))
    }

    @Test
    fun errorsAreExplainedInPlainWords() {
        fun messageFor(status: Int, body: String = """{"error":{"message":"detail from service"}}"""): String {
            transport.reply = AiHttpResponse(status, body)
            return (describe() as AiResult.Failure).message
        }

        assertTrue(messageFor(401).contains("did not accept the API key"))
        assertTrue(messageFor(402).contains("run out of credit"))
        assertTrue(messageFor(404).contains("did not recognise the model"))
        assertTrue(messageFor(429).contains("busy"))
        assertTrue(messageFor(500).contains("(500)"))
        assertTrue(messageFor(401).contains("detail from service"))
        assertTrue(messageFor(500, """{"error":"plain text error"}""").contains("plain text error"))
        assertFalse(messageFor(500, "not json").contains("("  + "not json"))
    }

    @Test
    fun theApiKeyNeverAppearsInAMessage() {
        transport.reply = AiHttpResponse(401, """{"error":{"message":"Invalid key sk-or-secret-key-123456"}}""")
        assertFalse((describe() as AiResult.Failure).message.contains("secret-key"))

        transport.failure = IllegalStateException("Unable to resolve host for sk-or-secret-key-123456")
        val unreachable = (describe() as AiResult.Failure).message
        assertTrue(unreachable.contains("could not be reached"))
        assertFalse(unreachable.contains("secret-key"))
    }

    @Test
    fun aBadAddressIsRejectedBeforeAnythingIsSent() {
        assertTrue((describe(baseUrl = "openrouter.ai/api/v1") as AiResult.Failure).message.contains("not a web address"))
        assertNull(transport.url)
    }

    @Test
    fun buildsTheAddressFromDifferentWaysOfWritingTheBase() {
        assertEquals("https://openrouter.ai/api/v1/chat/completions", chatCompletionsUrl(" https://openrouter.ai/api/v1/ "))
        assertEquals("https://api.openai.com/v1/chat/completions", chatCompletionsUrl("https://api.openai.com/v1"))
        assertEquals("http://192.168.1.5:1234/v1/chat/completions", chatCompletionsUrl("http://192.168.1.5:1234/v1/chat/completions"))
        assertNull(chatCompletionsUrl(""))
        assertNull(chatCompletionsUrl("https://"))
        assertNull(chatCompletionsUrl("https://bad host/v1"))
        assertNull(chatCompletionsUrl("ftp://example.com"))
    }

    @Test
    fun modelListsFallBackToTheDefaultsAndAreNormalised() {
        val defaults = AiSettings()
        assertEquals(AiOperation.AltText.defaultModels, defaults.modelsFor(AiOperation.AltText))
        assertEquals(AiSettings.DefaultBaseUrl, defaults.baseUrl)

        val edited = defaults.withModels(AiOperation.AltText, listOf(" a/one ", "", "b/two", "a/one"))
        assertEquals(listOf("a/one", "b/two"), edited.modelsFor(AiOperation.AltText))

        val cleared = edited.withModels(AiOperation.AltText, emptyList())
        assertEquals(AiOperation.AltText.defaultModels, cleared.modelsFor(AiOperation.AltText))

        assertEquals(listOf("a/one", "b/two", "c/three"), parseModelList("a/one\n b/two ,c/three\n\n"))
        assertEquals(AiOperation.AltText, AiOperation.fromWireValue("alt_text"))
        assertNull(AiOperation.fromWireValue("captions"))
    }

    @Test
    fun altTextIsNotAttemptedWithoutAKey() {
        val settings = InMemoryLocalSettingsRepository()
        val writer = AltTextWriter(settings, client)

        assertFalse(writer.isConfigured)
        assertTrue((writer.write(jpeg, caption = "") as AiResult.Failure).message.contains("API key"))
        assertNull(transport.url)
    }

    @Test
    fun altTextUsesTheConfiguredServiceAndTheFirstModelUnlessAnotherIsChosen() {
        val settings = InMemoryLocalSettingsRepository(
            LocalAppSettings(
                ai = AiSettings(baseUrl = "https://example.test/v1")
                    .withModels(AiOperation.AltText, listOf("first/model", "second/model")),
            ),
        )
        settings.saveAiApiKey("key-abcdef-123456")
        val writer = AltTextWriter(settings, client)
        transport.reply = AiHttpResponse(200, """{"choices":[{"message":{"content":"Two bottles on a table."}}]}""")

        assertTrue(writer.isConfigured)
        assertEquals(listOf("first/model", "second/model"), writer.models())

        assertEquals(AiResult.Success("Two bottles on a table.", "first/model"), writer.write(jpeg, caption = ""))
        assertEquals("https://example.test/v1/chat/completions", transport.url)
        assertEquals("Bearer key-abcdef-123456", transport.headers["Authorization"])

        assertEquals(
            AiResult.Success("Two bottles on a table.", "second/model"),
            writer.write(jpeg, caption = "", model = "second/model"),
        )
        assertEquals(AiResult.Success("Two bottles on a table.", "first/model"), writer.write(jpeg, "", model = " "))
    }

    @Test
    fun theCaptionIsOfferedOnlyAsContext() {
        val bare = altTextInstructions("  ")
        val withCaption = altTextInstructions("  First pour of the Howling Wheat.  ")

        assertTrue(bare.contains("Describe only what is visible"))
        assertFalse(bare.contains("Caption:"))
        assertTrue(withCaption.endsWith("Caption: First pour of the Howling Wheat."))
        assertTrue(withCaption.contains("Do not repeat it"))
    }

    @Test
    fun repliesAreTidiedIntoOneLine() {
        assertEquals("A glass of beer.", cleanReply("  \"A glass\n\nof   beer.\"  "))
        assertEquals("A glass of beer.", cleanReply("“A glass of beer.”"))
        assertEquals("", cleanReply("   "))
    }
}

private class RecordingTransport : AiHttpTransport {
    var reply = AiHttpResponse(200, """{"choices":[{"message":{"content":"ok"}}]}""")
    var failure: Exception? = null
    var url: String? = null
    var headers: Map<String, String> = emptyMap()
    var body: String = ""

    override fun postJson(url: String, headers: Map<String, String>, body: String): AiHttpResponse {
        this.url = url
        this.headers = headers
        this.body = body
        failure?.let { throw it }
        return reply
    }
}
