package com.digitumdei.shotquill.shared.ai

import java.net.HttpURLConnection
import java.net.URL

/** Posts JSON with the platform's own HTTP client, so no networking library is needed. */
class UrlConnectionAiHttpTransport(
    private val connectTimeoutMillis: Int = 20_000,
    private val readTimeoutMillis: Int = 90_000,
) : AiHttpTransport {
    override fun postJson(url: String, headers: Map<String, String>, body: String): AiHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return AiHttpResponse(status = status, body = text)
        } finally {
            connection.disconnect()
        }
    }
}
