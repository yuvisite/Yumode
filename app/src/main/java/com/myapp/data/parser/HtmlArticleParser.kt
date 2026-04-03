package com.myapp.data.parser

import com.myapp.data.network.GuardedResponse
import com.myapp.model.ArticleBlock
import com.myapp.model.ArticleBlockType
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite
import com.myapp.model.PortalSource
import com.myapp.model.SanitizedArticle
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL

class HtmlArticleParser {
    fun parse(
        site: PortalSite,
        feedItem: FeedItem,
        response: GuardedResponse,
    ): SanitizedArticle {
        val source = site.source as? PortalSource.Rss
            ?: error("RSS parser used for non-RSS site")
        val spec = source.parserSpec
        val document = Jsoup.parse(response.body, response.finalUrl)

        document.select("script, style, noscript, iframe, form, svg, nav, footer, aside").remove()
        spec?.removeSelectors
            ?.flatMap { document.select(it) }
            ?.forEach { it.remove() }

        val root = selectContentRoot(document, spec?.contentSelectors.orEmpty())
        val blocks = extractBlocks(root, spec?.parserId)
        val articleTitle = resolveTitle(document, feedItem, spec?.titleSelectors.orEmpty())

        return SanitizedArticle(
            title = articleTitle,
            sourceUrl = feedItem.url,
            finalUrl = response.finalUrl,
            sourceHost = URL(response.finalUrl).host.orEmpty(),
            publishedAt = feedItem.publishedAt,
            excerpt = feedItem.summary,
            blocks = if (blocks.isNotEmpty()) blocks else fallbackBlocks(document.body()?.text().orEmpty()),
        )
    }

    fun parseInlineContent(
        feedItem: FeedItem,
    ): SanitizedArticle {
        val html = feedItem.inlineContentHtml ?: feedItem.summary.orEmpty()
        val document = Jsoup.parseBodyFragment(html)
        val root = document.body()
        val blocks = extractBlocks(root, parserId = null)

        return SanitizedArticle(
            title = feedItem.title,
            sourceUrl = feedItem.url,
            finalUrl = feedItem.url,
            sourceHost = runCatching { URL(feedItem.url).host.orEmpty() }.getOrDefault(""),
            publishedAt = feedItem.publishedAt,
            excerpt = feedItem.summary,
            blocks = if (blocks.isNotEmpty()) blocks else fallbackBlocks(root.text()),
        )
    }

    private fun resolveTitle(
        document: Document,
        feedItem: FeedItem,
        selectors: List<String>,
    ): String {
        val selectorTitle = selectors
            .asSequence()
            .mapNotNull { selector -> document.selectFirst(selector)?.text()?.cleanText() }
            .firstOrNull { it.isNotBlank() }

        return listOf(
            feedItem.title.cleanText(),
            selectorTitle,
            document.selectFirst("meta[property=og:title]")?.attr("content")?.cleanText(),
            document.title().cleanText(),
        )
            .firstOrNull { !it.isNullOrBlank() }
            ?: "article"
    }

    private fun selectContentRoot(
        document: Document,
        preferredSelectors: List<String>,
    ): Element {
        val selectors = preferredSelectors + listOf(
            "article",
            "main article",
            "[itemprop=articleBody]",
            ".entry-content",
            ".post-content",
            ".article__text",
            ".post__text",
            ".news-text",
            "main",
        )

        return selectors
            .flatMap { selector -> document.select(selector) }
            .distinct()
            .maxByOrNull { candidate ->
                val textLength = candidate.text().length
                val paragraphCount = candidate.select("p").size
                textLength + paragraphCount * 120
            }
            ?: document.body()
    }

    private fun extractBlocks(
        root: Element,
        parserId: String?,
    ): List<ArticleBlock> {
        val blocks = mutableListOf<ArticleBlock>()
        val seen = linkedSetOf<String>()
        val elements = root.select("h2, h3, h4, p, li, blockquote")

        for (element in elements) {
            val text = element.text().cleanText().take(500)
            if (shouldStopExtraction(parserId, text, element)) {
                break
            }
            if (text.length < 10 || !seen.add(text)) {
                continue
            }

            val type = when (element.tagName().lowercase()) {
                "h2", "h3", "h4" -> ArticleBlockType.HEADING
                "li" -> ArticleBlockType.LIST_ITEM
                else -> ArticleBlockType.PARAGRAPH
            }
            blocks += ArticleBlock(type = type, text = text)
        }

        return blocks.take(80)
    }

    private fun shouldStopExtraction(
        parserId: String?,
        text: String,
        element: Element,
    ): Boolean {
        val normalized = text.lowercase()
        return when (parserId) {
            "4pda" ->
                normalized in setOf(
                    "комментарии",
                    "читать комментарии",
                    "обсуждение",
                    "читайте также",
                    "похожие новости",
                    "похожие статьи",
                ) &&
                    element.tagName().lowercase() in setOf("h2", "h3", "h4", "p", "blockquote")

            else -> false
        }
    }

    private fun fallbackBlocks(text: String): List<ArticleBlock> =
        text.cleanText()
            .split(". ")
            .map { it.trim() }
            .filter { it.length >= 20 }
            .take(12)
            .map { sentence ->
                val normalized = if (sentence.endsWith(".")) sentence else "$sentence."
                ArticleBlock(ArticleBlockType.PARAGRAPH, normalized)
            }

    private fun String.cleanText(): String =
        replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
}
