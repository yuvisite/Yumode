package com.myapp.data.browser

import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

/**
 * In-memory LRU page cache with byte-size limit.
 */
class PageCache(
    private val maxSizeBytes: Int = DEFAULT_MAX_SIZE_BYTES,
) {
    data class CacheStats(
        val totalPages: Int,
        val usedSizeBytes: Int,
        val maxSizeBytes: Int,
    )

    private data class Entry(
        val html: String,
        val sizeBytes: Int,
    )

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {}
    private var usedSizeBytes: Int = 0

    fun get(url: String): String? = entries[url]?.html

    fun contains(url: String): Boolean = entries.containsKey(url)

    fun put(
        url: String,
        html: String,
        isNoCache: Boolean = false,
    ) {
        if (isNoCache) {
            return
        }
        val size = html.toByteArray(StandardCharsets.UTF_8).size
        if (size > maxSizeBytes) {
            remove(url)
            return
        }

        val previous = entries.put(url, Entry(html = html, sizeBytes = size))
        if (previous != null) {
            usedSizeBytes -= previous.sizeBytes
        }
        usedSizeBytes += size
        trimToSize()
    }

    fun clear() {
        entries.clear()
        usedSizeBytes = 0
    }

    fun getStats(): CacheStats =
        CacheStats(
            totalPages = entries.size,
            usedSizeBytes = usedSizeBytes,
            maxSizeBytes = maxSizeBytes,
        )

    private fun trimToSize() {
        if (usedSizeBytes <= maxSizeBytes) {
            return
        }
        val iterator = entries.entries.iterator()
        while (usedSizeBytes > maxSizeBytes && iterator.hasNext()) {
            val eldest = iterator.next().value
            usedSizeBytes -= eldest.sizeBytes
            iterator.remove()
        }
    }

    private fun remove(url: String) {
        val removed = entries.remove(url) ?: return
        usedSizeBytes -= removed.sizeBytes
    }

    companion object {
        const val DEFAULT_MAX_SIZE_BYTES: Int = 100 * 1024
    }
}
