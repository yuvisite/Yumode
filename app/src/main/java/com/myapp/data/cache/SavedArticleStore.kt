package com.myapp.data.cache

import com.myapp.model.ArticleBlock
import com.myapp.model.ArticleBlockType
import com.myapp.model.SanitizedArticle
import org.json.JSONArray
import org.json.JSONObject

data class SavedArticle(
    val siteId: String,
    val savedAtMs: Long,
    val article: SanitizedArticle,
)

class SavedArticleStore(
    private val cacheStore: DiskCacheStore,
) {
    fun isSaved(url: String): Boolean =
        loadIndex().contains(url)

    fun save(
        siteId: String,
        article: SanitizedArticle,
    ) {
        val keyUrl = article.finalUrl.ifBlank { article.sourceUrl }
        if (keyUrl.isBlank()) return
        val updated = loadIndex().toMutableSet().apply { add(keyUrl) }
        writeIndex(updated)
        cacheStore.write(itemKey(keyUrl), encodeSavedArticle(SavedArticle(siteId = siteId, savedAtMs = System.currentTimeMillis(), article = article)))
    }

    fun remove(url: String) {
        val updated = loadIndex().toMutableSet().apply { remove(url) }
        writeIndex(updated)
        // We don't delete the file (DiskCacheStore has no delete); removing from index hides it.
    }

    fun get(url: String): SavedArticle? =
        cacheStore.readAny(itemKey(url))?.let { decodeSavedArticle(it) }

    fun list(): List<SavedArticle> =
        loadIndex()
            .mapNotNull { url -> get(url) }
            .sortedByDescending { it.savedAtMs }

    private fun loadIndex(): Set<String> =
        cacheStore.readAny(INDEX_KEY)
            ?.let { raw ->
                runCatching {
                    val arr = JSONArray(raw)
                    buildSet(arr.length()) {
                        for (i in 0 until arr.length()) {
                            val v = arr.optString(i).orEmpty()
                            if (v.isNotBlank()) add(v)
                        }
                    }
                }.getOrNull()
            }
            ?: emptySet()

    private fun writeIndex(urls: Set<String>) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        cacheStore.write(INDEX_KEY, arr.toString())
    }

    private fun itemKey(url: String): String =
        "$ITEM_PREFIX$url"

    private fun encodeSavedArticle(saved: SavedArticle): String {
        val obj = JSONObject()
        obj.put("siteId", saved.siteId)
        obj.put("savedAtMs", saved.savedAtMs)
        obj.put("article", encodeArticle(saved.article))
        return obj.toString()
    }

    private fun decodeSavedArticle(raw: String): SavedArticle? =
        runCatching {
            val obj = JSONObject(raw)
            val siteId = obj.optString("siteId").orEmpty()
            val savedAtMs = obj.optLong("savedAtMs")
            val articleObj = obj.optJSONObject("article") ?: return@runCatching null
            val article = decodeArticle(articleObj) ?: return@runCatching null
            SavedArticle(siteId = siteId, savedAtMs = savedAtMs, article = article)
        }.getOrNull()

    private fun encodeArticle(article: SanitizedArticle): JSONObject {
        val obj = JSONObject()
        obj.put("title", article.title)
        obj.put("sourceUrl", article.sourceUrl)
        obj.put("finalUrl", article.finalUrl)
        obj.put("sourceHost", article.sourceHost)
        obj.put("publishedAt", article.publishedAt)
        obj.put("excerpt", article.excerpt)
        val blocks = JSONArray()
        article.blocks.forEach { block ->
            val b = JSONObject()
            b.put("type", block.type.name)
            b.put("text", block.text)
            blocks.put(b)
        }
        obj.put("blocks", blocks)
        return obj
    }

    private fun decodeArticle(obj: JSONObject): SanitizedArticle? {
        val title = obj.optString("title").orEmpty()
        val sourceUrl = obj.optString("sourceUrl").orEmpty()
        val finalUrl = obj.optString("finalUrl").orEmpty()
        val sourceHost = obj.optString("sourceHost").orEmpty()
        val publishedAt = obj.optString("publishedAt").takeIf { it.isNotBlank() }
        val excerpt = obj.optString("excerpt").takeIf { it.isNotBlank() }
        val blocksArr = obj.optJSONArray("blocks") ?: JSONArray()
        val blocks = buildList(blocksArr.length()) {
            for (i in 0 until blocksArr.length()) {
                val b = blocksArr.optJSONObject(i) ?: continue
                val typeName = b.optString("type").orEmpty()
                val text = b.optString("text").orEmpty()
                val type = runCatching { ArticleBlockType.valueOf(typeName) }.getOrNull() ?: ArticleBlockType.PARAGRAPH
                if (text.isNotBlank()) add(ArticleBlock(type = type, text = text))
            }
        }
        if (finalUrl.isBlank() && sourceUrl.isBlank()) return null
        return SanitizedArticle(
            title = title,
            sourceUrl = sourceUrl,
            finalUrl = finalUrl,
            sourceHost = sourceHost,
            publishedAt = publishedAt,
            excerpt = excerpt,
            blocks = blocks,
        )
    }

    private companion object {
        const val INDEX_KEY = "saved_articles:index"
        const val ITEM_PREFIX = "saved_articles:item:"
    }
}

