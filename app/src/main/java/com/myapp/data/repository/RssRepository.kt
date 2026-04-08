package com.myapp.data.repository

import android.util.Xml
import com.myapp.data.cache.DiskCacheStore
import com.myapp.data.cache.articleFromJson
import com.myapp.data.cache.articleToJson
import com.myapp.data.cache.feedItemsFromJson
import com.myapp.data.cache.feedItemsToJson
import com.myapp.data.network.GuardedResourceType
import com.myapp.data.network.SafeNetworkClient
import com.myapp.data.parser.HtmlArticleParser
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite
import com.myapp.model.PortalSource
import com.myapp.model.ArticleBlock
import com.myapp.model.ArticleBlockType
import com.myapp.model.SanitizedArticle
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class RssPageProperties(
    val requestedUrl: String,
    val finalUrl: String,
    val contentType: String?,
    val byteCount: Int?,
    val isSecure: Boolean,
    val fromCache: Boolean,
)

class RssRepository(
    private val cacheStore: DiskCacheStore? = null,
    private val networkClient: SafeNetworkClient = SafeNetworkClient(),
    private val articleParser: HtmlArticleParser = HtmlArticleParser(),
) {
    private val feedPagePropertiesBySite: MutableMap<String, RssPageProperties> = linkedMapOf()
    private val articlePagePropertiesByKey: MutableMap<String, RssPageProperties> = linkedMapOf()
    private val feedLocksBySite: ConcurrentHashMap<String, Any> = ConcurrentHashMap()

    fun fetchFeed(site: PortalSite): List<FeedItem> {
        val source = site.source as? PortalSource.Rss
            ?: throw IllegalArgumentException("site ${site.id} is not rss")
        val lock = feedLocksBySite.computeIfAbsent(site.id) { Any() }
        synchronized(lock) {
            val cacheKey = "rss-feed-${site.id}-${source.feedUrl.hashCode()}"

            cacheStore?.readFresh(cacheKey, FEED_CACHE_TTL_MS)
                ?.let { cachedJson ->
                    feedItemsFromJson(cachedJson)
                        .takeIf { it.isNotEmpty() }
                        ?.also {
                            feedPagePropertiesBySite[site.id] = RssPageProperties(
                                requestedUrl = source.feedUrl,
                                finalUrl = source.feedUrl,
                                contentType = "application/rss+xml",
                                byteCount = null,
                                isSecure = source.feedUrl.startsWith("https://"),
                                fromCache = true,
                            )
                        }
                }
                ?.let { return it }

            return runCatching {
                val response = networkClient.get(
                    url = source.feedUrl,
                    policy = source.policy,
                    resourceType = GuardedResourceType.FEED,
                    acceptHeader = "application/rss+xml, application/atom+xml, application/xml, text/xml",
                )
                feedPagePropertiesBySite[site.id] = RssPageProperties(
                    requestedUrl = source.feedUrl,
                    finalUrl = response.finalUrl,
                    contentType = response.contentType,
                    byteCount = response.byteCount,
                    isSecure = response.finalUrl.startsWith("https://"),
                    fromCache = false,
                )

                parseFeed(response.body)
                    .filter { item -> item.title.isNotBlank() && item.url.isNotBlank() }
                    .take(30)
                    .also { items ->
                        cacheStore?.write(cacheKey, feedItemsToJson(items))
                    }
            }.getOrElse { throwable ->
                cacheStore?.readAny(cacheKey)
                    ?.let(::feedItemsFromJson)
                    ?.takeIf { it.isNotEmpty() }
                    ?: throw throwable
            }
        }
    }

    fun fetchArticle(
        site: PortalSite,
        feedItem: FeedItem,
    ): SanitizedArticle {
        val source = site.source as? PortalSource.Rss
            ?: throw IllegalArgumentException("site ${site.id} is not rss")
        val cacheKey = "rss-article-${site.id}-${feedItem.url.hashCode()}"
        val inlineArticle = inlineArticleOrNull(feedItem)

        cacheStore?.readFresh(cacheKey, ARTICLE_CACHE_TTL_MS)
            ?.let { cachedJson ->
                articleFromJson(cachedJson).also {
                    articlePagePropertiesByKey[articleKey(site.id, feedItem)] = RssPageProperties(
                        requestedUrl = feedItem.url,
                        finalUrl = feedItem.url,
                        contentType = "text/html",
                        byteCount = null,
                        isSecure = feedItem.url.startsWith("https://"),
                        fromCache = true,
                    )
                }
            }
            ?.let { return it }

        return runCatching {
            val fetchedArticle = runCatching {
                networkClient.get(
                    url = feedItem.url,
                    policy = source.policy,
                    resourceType = GuardedResourceType.ARTICLE,
                    acceptHeader = "text/html,application/xhtml+xml",
                )
            }.getOrNull()
            if (fetchedArticle != null) {
                articlePagePropertiesByKey[articleKey(site.id, feedItem)] = RssPageProperties(
                    requestedUrl = feedItem.url,
                    finalUrl = fetchedArticle.finalUrl,
                    contentType = fetchedArticle.contentType,
                    byteCount = fetchedArticle.byteCount,
                    isSecure = fetchedArticle.finalUrl.startsWith("https://"),
                    fromCache = false,
                )
            }

            when {
                fetchedArticle != null &&
                    !isHomeRedirect(
                        requestedUrl = feedItem.url,
                        finalUrl = fetchedArticle.finalUrl,
                        homeUrl = source.homeUrl,
                    ) &&
                    source.parserSpec != null -> {
                    val parsedArticle =
                        runCatching {
                            articleParser.parse(
                                site = site,
                                feedItem = feedItem,
                                response = fetchedArticle,
                            )
                        }.getOrNull()
                    choosePreferredArticle(
                        parsedArticle = parsedArticle,
                        inlineArticle = inlineArticle,
                    ) ?: fallbackArticle(
                        feedItem = feedItem,
                        finalUrl = fetchedArticle.finalUrl,
                    )
                }

                inlineArticle != null -> inlineArticle

                fetchedArticle != null -> {
                    fallbackArticle(
                        feedItem = feedItem,
                        finalUrl = fetchedArticle.finalUrl,
                    )
                }

                else -> throw IOException("article content unavailable")
            }
        }.mapCatching { article ->
            cacheStore?.write(cacheKey, articleToJson(article))
            article
        }.getOrElse { throwable ->
            cacheStore?.readAny(cacheKey)
                ?.let(::articleFromJson)
                ?: throw throwable
        }
    }

    private fun inlineArticleOrNull(feedItem: FeedItem): SanitizedArticle? =
        if (!feedItem.inlineContentHtml.isNullOrBlank() || !feedItem.summary.isNullOrBlank()) {
            articleParser.parseInlineContent(feedItem)
        } else {
            null
        }

    private fun choosePreferredArticle(
        parsedArticle: SanitizedArticle?,
        inlineArticle: SanitizedArticle?,
    ): SanitizedArticle? {
        if (parsedArticle == null) {
            return inlineArticle
        }
        if (inlineArticle == null) {
            return parsedArticle
        }

        val parsedScore = articleRichnessScore(parsedArticle)
        val inlineScore = articleRichnessScore(inlineArticle)

        return when {
            parsedScore <= MIN_PARSED_ARTICLE_SCORE && inlineScore > parsedScore -> inlineArticle
            inlineScore >= parsedScore + INLINE_ARTICLE_SCORE_BONUS_THRESHOLD -> inlineArticle
            else -> parsedArticle
        }
    }

    private fun articleRichnessScore(article: SanitizedArticle): Int =
        article.blocks.sumOf { block ->
            val blockWeight =
                when (block.type) {
                    ArticleBlockType.HEADING -> 120
                    ArticleBlockType.LIST_ITEM -> 90
                    ArticleBlockType.PARAGRAPH -> 100
                    ArticleBlockType.IMAGE -> 140
                }
            blockWeight + block.text.length.coerceAtMost(400)
        }

    fun feedPageProperties(siteId: String): RssPageProperties? =
        feedPagePropertiesBySite[siteId]

    fun articlePageProperties(
        siteId: String,
        feedItem: FeedItem,
    ): RssPageProperties? =
        articlePagePropertiesByKey[articleKey(siteId, feedItem)]

    private fun parseFeed(xml: String): List<FeedItem> {
        val parser = Xml.newPullParser().apply {
            setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
            setInput(StringReader(xml))
        }

        val items = mutableListOf<FeedItem>()
        var current: MutableFeedItem? = null
        var feedKind: FeedKind? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.orEmpty()
                    when {
                        tag.equals("item", ignoreCase = true) -> {
                            feedKind = FeedKind.RSS
                            current = MutableFeedItem()
                        }

                        tag.equals("entry", ignoreCase = true) -> {
                            feedKind = FeedKind.ATOM
                            current = MutableFeedItem()
                        }

                        current != null -> handleStartTag(
                            parser = parser,
                            current = current,
                            feedKind = feedKind,
                        )
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name.orEmpty()
                    if (tag.equals("item", ignoreCase = true) || tag.equals("entry", ignoreCase = true)) {
                        current?.toFeedItem()?.let(items::add)
                        current = null
                    }
                }
            }
            parser.next()
        }

        return items
    }

    private fun handleStartTag(
        parser: XmlPullParser,
        current: MutableFeedItem,
        feedKind: FeedKind?,
    ) {
        val tag = parser.name.orEmpty()
        val namespace = parser.namespace.orEmpty()
        when {
            tag.equals("title", ignoreCase = true) -> current.title = parser.nextText().sanitize()
            tag.equals("link", ignoreCase = true) && feedKind == FeedKind.RSS ->
                current.link = parser.nextText().trim()

            tag.equals("link", ignoreCase = true) && feedKind == FeedKind.ATOM ->
                current.link = parser.getAttributeValue(null, "href").orEmpty().trim()

            tag.equals("guid", ignoreCase = true) -> current.guid = parser.nextText().trim()
            tag.equals("pubDate", ignoreCase = true) -> current.publishedAt = parser.nextText().sanitize()
            tag.equals("updated", ignoreCase = true) -> current.publishedAt = parser.nextText().sanitize()
            tag.equals("published", ignoreCase = true) && current.publishedAt.isBlank() ->
                current.publishedAt = parser.nextText().sanitize()

            tag.equals("description", ignoreCase = true) -> current.descriptionHtml = parser.nextText()
            tag.equals("summary", ignoreCase = true) -> current.descriptionHtml = parser.nextText()
            isInlineContentTag(tag, namespace) -> current.inlineContentHtml = parser.nextText()
            tag.equals("encoded", ignoreCase = true) && namespace == CONTENT_NAMESPACE ->
                current.inlineContentHtml = parser.nextText()

            tag.equals("category", ignoreCase = true) && namespace != MEDIA_NAMESPACE -> {
                val category = when (feedKind) {
                    FeedKind.ATOM -> parser.getAttributeValue(null, "term")?.sanitize()
                        ?: parser.nextText().sanitize()
                    else -> parser.nextText().sanitize()
                }
                if (category.isNotBlank()) {
                    current.categories += category
                }
            }
            tag.equals("id", ignoreCase = true) -> current.guid = parser.nextText().trim()
        }
    }

    private fun String.sanitize(): String =
        Parser.unescapeEntities(this, false)
            .replace('\u00A0', ' ')
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun sanitizeSummary(value: String): String? {
        val text = Jsoup.parse(value).text().sanitize()
        return text.ifBlank { null }
    }

    private fun isHomeRedirect(
        requestedUrl: String,
        finalUrl: String,
        homeUrl: String,
    ): Boolean {
        val normalizedRequested = normalizeUrl(requestedUrl)
        val normalizedFinal = normalizeUrl(finalUrl)
        val normalizedHome = normalizeUrl(homeUrl)

        if (normalizedRequested == null || normalizedFinal == null || normalizedHome == null) {
            return false
        }

        return normalizedRequested != normalizedHome && normalizedFinal == normalizedHome
    }

    private fun normalizeUrl(url: String): String? =
        runCatching {
            val parsed = URL(url)
            val scheme = parsed.protocol?.lowercase().orEmpty()
            val host = parsed.host?.lowercase().orEmpty()
            val port = parsed.port.takeIf { it > 0 && it != parsed.defaultPort }
            val normalizedPath = parsed.path
                ?.trim()
                .orEmpty()
                .trimEnd('/')
                .ifBlank { "/" }

            buildString {
                append(scheme)
                append("://")
                append(host)
                if (port != null) {
                    append(":")
                    append(port)
                }
                append(normalizedPath)
            }
        }.getOrNull()

    private fun articleKey(
        siteId: String,
        feedItem: FeedItem,
    ): String = "$siteId|${feedItem.url}"

    private fun fallbackArticle(
        feedItem: FeedItem,
        finalUrl: String,
    ): SanitizedArticle =
        SanitizedArticle(
            title = feedItem.title,
            sourceUrl = feedItem.url,
            finalUrl = finalUrl,
            sourceHost = runCatching { URL(finalUrl).host.orEmpty() }.getOrDefault(""),
            publishedAt = feedItem.publishedAt,
            excerpt = feedItem.summary,
            blocks = listOfNotNull(
                feedItem.summary
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ArticleBlock(type = ArticleBlockType.PARAGRAPH, text = it) }
                    ?: ArticleBlock(
                        type = ArticleBlockType.PARAGRAPH,
                        text = "Full article text is unavailable right now.",
                    ),
            ),
        )

    private enum class FeedKind {
        RSS,
        ATOM,
    }

    private companion object {
        const val ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
        const val CONTENT_NAMESPACE = "http://purl.org/rss/1.0/modules/content/"
        const val MEDIA_NAMESPACE = "http://search.yahoo.com/mrss/"
        const val FEED_CACHE_TTL_MS = 15L * 60L * 1000L
        const val ARTICLE_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        const val MIN_PARSED_ARTICLE_SCORE = 320
        const val INLINE_ARTICLE_SCORE_BONUS_THRESHOLD = 240
    }

    private fun isInlineContentTag(
        tag: String,
        namespace: String,
    ): Boolean =
        tag.equals("content", ignoreCase = true) &&
            namespace != MEDIA_NAMESPACE &&
            (namespace.isBlank() || namespace == ATOM_NAMESPACE)

    private inner class MutableFeedItem {
        var title: String = ""
        var link: String = ""
        var guid: String = ""
        var publishedAt: String = ""
        var descriptionHtml: String = ""
        var inlineContentHtml: String = ""
        var categories: List<String> = emptyList()

        fun toFeedItem(): FeedItem? {
            if (title.isBlank() || link.isBlank()) {
                return null
            }

            return FeedItem(
                id = guid.ifBlank { link },
                title = title,
                url = link,
                publishedAt = publishedAt.ifBlank { null },
                summary = sanitizeSummary(descriptionHtml),
                inlineContentHtml = inlineContentHtml.ifBlank { null },
                categories = categories.distinct(),
            )
        }
    }
}
