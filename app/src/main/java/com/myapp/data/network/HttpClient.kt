package com.myapp.data.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * DEPRECATED: Use [SimpleNetworkClient] instead.
 * 
 * This uses raw HttpURLConnection which lacks HTTP/2, proper TLS handling, and connection pooling.
 * SimpleNetworkClient provides OkHttp-backed network calls with better error handling and performance.
 */
@Deprecated(
    message = "Use SimpleNetworkClient instead",
    replaceWith = ReplaceWith("SimpleNetworkClient.get(url)")
)
object HttpClient {
    fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json, application/rss+xml, application/xml, text/xml")
        }

        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                throw IOException("HTTP $statusCode: $body")
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}
