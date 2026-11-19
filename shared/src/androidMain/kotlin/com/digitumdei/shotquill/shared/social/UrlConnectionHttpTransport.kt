package com.digitumdei.shotquill.shared.social

import java.net.HttpURLConnection
import java.net.URL

/** Sends requests with the platform's own HTTP client, so no networking library is needed. */
class UrlConnectionHttpTransport(
    private val connectTimeoutMillis: Int = 20_000,
    private val readTimeoutMillis: Int = 90_000,
) : HttpTransport {
    override fun send(request: HttpRequest): HttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "application/json")
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val body = request.body
            if (body != null) {
                connection.doOutput = true
                request.contentType?.let { connection.setRequestProperty("Content-Type", it) }
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(status = status, body = text)
        } finally {
            connection.disconnect()
        }
    }
}
