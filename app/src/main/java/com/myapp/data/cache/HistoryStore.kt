package com.myapp.data.cache

import com.myapp.model.FeedItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HistoryEntry(
    val visitedAtMs: Long,
    val kind: String,
    val siteId: String,
    val title: String,
    val url: String,
)

class HistoryStore(
    private val cacheStore: DiskCacheStore,
) {
    fun record(
        kind: String,
        siteId: String,
        title: String,
        url: String = "",
    ) {
        append(
            HistoryEntry(
                visitedAtMs = System.currentTimeMillis(),
                kind = kind,
                siteId = siteId,
                title = title,
                url = url,
            ),
        )
    }

    fun recordSite(
        siteId: String,
        title: String,
    ) {
        record(kind = "site", siteId = siteId, title = title)
    }

    fun recordArticle(
        siteId: String,
        feedItem: FeedItem,
    ) {
        record(kind = "article", siteId = siteId, title = feedItem.title, url = feedItem.url)
    }

    fun list(): List<HistoryEntry> =
        load()
            .sortedByDescending { it.visitedAtMs }
            .take(MAX_ENTRIES)

    private fun append(entry: HistoryEntry) {
        val current = load().toMutableList()
        current.add(entry)
        val trimmed =
            if (current.size > MAX_ENTRIES * 2) {
                current.sortedByDescending { it.visitedAtMs }.take(MAX_ENTRIES)
            } else {
                current
            }
        write(trimmed)
    }

    private fun load(): List<HistoryEntry> =
        cacheStore.readAny(KEY_HISTORY)
            ?.let { raw ->
                runCatching {
                    val arr = JSONArray(raw)
                    buildList(arr.length()) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val visitedAt = obj.optLong("visitedAtMs")
                            val kind = obj.optString("kind").ifBlank { "site" }
                            val siteId = obj.optString("siteId").orEmpty()
                            val title = obj.optString("title").orEmpty()
                            val url = obj.optString("url").orEmpty()
                            if (siteId.isNotBlank() && title.isNotBlank()) {
                                add(
                                    HistoryEntry(
                                        visitedAtMs = visitedAt,
                                        kind = kind,
                                        siteId = siteId,
                                        title = title,
                                        url = url,
                                    ),
                                )
                            }
                        }
                    }
                }.getOrNull()
            }
            ?: emptyList()

    private fun write(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("visitedAtMs", entry.visitedAtMs)
            obj.put("kind", entry.kind)
            obj.put("siteId", entry.siteId)
            obj.put("title", entry.title)
            obj.put("url", entry.url)
            arr.put(obj)
        }
        cacheStore.write(KEY_HISTORY, arr.toString())
    }

    companion object {
        private const val KEY_HISTORY = "browser_history"
        private const val MAX_ENTRIES = 200

        fun groupByDay(entries: List<HistoryEntry>): Map<String, List<HistoryEntry>> {
            if (entries.isEmpty()) return emptyMap()
            val now = System.currentTimeMillis()
            val todayLabel = "Today"
            val yesterdayLabel = "Yesterday"
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy MM dd", Locale.US)
            return entries.groupBy { entry ->
                val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(entry.visitedAtMs), ZoneId.systemDefault())
                val entryDay = date.toLocalDate()
                val nowDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).toLocalDate()
                val yesterday = nowDate.minusDays(1)
                when {
                    entryDay == nowDate -> todayLabel
                    entryDay == yesterday -> yesterdayLabel
                    else -> date.format(dateFormatter)
                }
            }
        }
    }
}

