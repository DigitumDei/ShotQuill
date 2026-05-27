package com.digitumdei.shotquill.shared.ai

import java.net.HttpURLConnection
import java.net.URL

class UrlConnectionOpenAiHttpTransport(
    private val connectTimeoutMillis: Int = 30_000,
    private val readTimeoutMillis: Int = 60_000,
) : OpenAiHttpTransport {
    override fun execute(request: OpenAiHttpRequest): OpenAiHttpResult =
        try {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doInput = true
                doOutput = request.bodyBytes.isNotEmpty()
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }

            if (request.bodyBytes.isNotEmpty()) {
                connection.outputStream.use { it.write(request.bodyBytes) }
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            OpenAiHttpResult.Success(statusCode = statusCode, body = body)
        } catch (failure: Exception) {
            OpenAiHttpResult.NetworkFailure(message = failure.message)
        }
}
