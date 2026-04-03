package com.myapp.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Simple HTTP client using OkHttp for public API calls (weather, currency, etc).
 * Replaces raw HttpURLConnection with proper HTTP/2, TLS, and connection pooling.
 */
object SimpleNetworkClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json, application/rss+xml, application/xml, text/xml")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) i-mode-browser-simulator")
            .build()

        val response = client.newCall(request).execute()
        return response.use { resp ->
            val code = resp.code
            val body = resp.body?.string().orEmpty()
            
            if (code !in 200..299) {
                throw IOException("HTTP $code: $body")
            }
            body
        }
    }
}
