package com.myapp.data.network

import com.myapp.model.SitePolicy
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

enum class GuardedResourceType {
    FEED,
    ARTICLE,
}

data class GuardedResponse(
    val finalUrl: String,
    val body: String,
    val contentType: String?,
    val byteCount: Int,
)

/**
 * HTTPS/RSS fetcher using OkHttp for more reliable TLS negotiation than
 * [java.net.HttpURLConnection] on some devices (fewer handshake resets).
 */
class SafeNetworkClient(
    private val client: OkHttpClient = defaultClient(),
) {
    fun get(
        url: String,
        policy: SitePolicy,
        resourceType: GuardedResourceType,
        acceptHeader: String,
    ): GuardedResponse {
        var currentUrl = url
        var redirects = 0

        while (true) {
            validateUrl(currentUrl, policy)

            val request =
                Request.Builder()
                    .url(currentUrl)
                    .get()
                    .header("Accept", acceptHeader)
                    .header("Accept-Encoding", "identity")
                    .header("Accept-Language", DEFAULT_ACCEPT_LANGUAGE)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()

            val response = client.newCall(request).execute()
            try {
                val code = response.code

                if (code in REDIRECT_CODES) {
                    val location = response.header("Location")
                        ?: throw IOException("redirect without location")
                    if (redirects >= policy.maxRedirects) {
                        throw IOException("too many redirects")
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects += 1
                    continue
                }

                if (code !in 200..299) {
                    throw IOException("HTTP $code")
                }

                val stream = response.body?.byteStream()
                    ?: throw IOException("empty response body")
                val contentType = response.header("Content-Type")
                val readResult =
                    stream.use {
                        readBoundedText(
                            stream = it,
                            contentType = contentType,
                            maxBytes = maxBytesFor(resourceType, policy),
                        )
                    }

                validateUrl(currentUrl, policy)

                return GuardedResponse(
                    finalUrl = currentUrl,
                    body = readResult.text,
                    contentType = contentType,
                    byteCount = readResult.byteCount,
                )
            } finally {
                response.close()
            }
        }
    }

    private fun maxBytesFor(
        resourceType: GuardedResourceType,
        policy: SitePolicy,
    ): Int =
        when (resourceType) {
            GuardedResourceType.FEED -> policy.maxFeedBytes
            GuardedResourceType.ARTICLE -> policy.maxArticleBytes
        }

    private fun validateUrl(
        url: String,
        policy: SitePolicy,
    ) {
        val parsed = URL(url)
        val scheme = parsed.protocol?.lowercase().orEmpty()
        val host = parsed.host?.lowercase().orEmpty()

        if (scheme !in policy.allowedSchemes) {
            throw IOException("blocked scheme: $scheme")
        }
        if (!isAllowedHost(host, policy.allowedHosts)) {
            throw IOException("blocked host: $host")
        }
    }

    private fun isAllowedHost(
        host: String,
        allowedHosts: Set<String>,
    ): Boolean =
        allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }

    private fun readBoundedText(
        stream: java.io.InputStream,
        contentType: String?,
        maxBytes: Int,
    ): ReadTextResult {
        val charset = extractCharset(contentType)
        val buffer = ByteArray(8_192)
        var totalRead = 0
        val output = ByteArrayOutputStream()

        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            totalRead += count
            if (totalRead > maxBytes) {
                throw IOException("response too large")
            }
            output.write(buffer, 0, count)
        }

        return ReadTextResult(
            text = output.toString(charset.name()),
            byteCount = totalRead,
        )
    }

    private fun extractCharset(contentType: String?): Charset {
        val charsetToken = contentType
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            ?.ifBlank { null }

        return runCatching { Charset.forName(charsetToken ?: "UTF-8") }
            .getOrDefault(Charsets.UTF_8)
    }

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)
                .build()

        const val DEFAULT_ACCEPT_LANGUAGE = "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 8.1.0; Sharp 806SH) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36 Yumode/0.1"
    }

    private data class ReadTextResult(
        val text: String,
        val byteCount: Int,
    )
}
