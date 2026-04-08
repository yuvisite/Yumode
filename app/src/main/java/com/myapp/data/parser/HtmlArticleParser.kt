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
import org.jsoup.parser.Parser
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
        val blocks = extractBlocks(root, spec?.parserId, response.finalUrl)
        val articleTitle = resolveTitle(document, feedItem, spec?.titleSelectors.orEmpty())

        return SanitizedArticle(
            title = articleTitle,
            sourceUrl = feedItem.url,
            finalUrl = response.finalUrl,
            sourceHost = URL(response.finalUrl).host.orEmpty(),
            publishedAt = feedItem.publishedAt,
            excerpt = feedItem.summary,
            blocks = if (blocks.isNotEmpty()) blocks else fallbackBlocks(root.text()),
        )
    }

    fun parseInlineContent(
        feedItem: FeedItem,
    ): SanitizedArticle {
        val html = feedItem.inlineContentHtml ?: feedItem.summary.orEmpty()
        val document = Jsoup.parseBodyFragment(html)
        val root = document.body()
        val blocks = extractBlocks(root, parserId = null, baseUrl = feedItem.url)

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
        baseUrl: String,
    ): List<ArticleBlock> {
        val blocks = mutableListOf<ArticleBlock>()
        val seen = linkedSetOf<String>()
        val imageCount = intArrayOf(0)
        val elements = root.select("h2, h3, h4, p, li, blockquote, img, figure, picture")

        for (element in elements) {
            // Stop if we have too many blocks
            if (blocks.size >= 80) break

            when (element.tagName().lowercase()) {
                "img" ->
                    appendImageBlock(
                        blocks = blocks,
                        seen = seen,
                        imageCount = imageCount,
                        element = element,
                        baseUrl = baseUrl,
                        preferredCaption = element.parent()?.selectFirst("figcaption")?.text()?.cleanText(),
                    )

                "figure", "picture" ->
                    appendImageBlock(
                        blocks = blocks,
                        seen = seen,
                        imageCount = imageCount,
                        element = element,
                        baseUrl = baseUrl,
                        preferredCaption = element.selectFirst("figcaption")?.text()?.cleanText(),
                    )

                else -> {
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
            }
        }

        return blocks
    }

    private fun appendImageBlock(
        blocks: MutableList<ArticleBlock>,
        seen: MutableSet<String>,
        imageCount: IntArray,
        element: Element,
        baseUrl: String,
        preferredCaption: String?,
    ) {
        if (imageCount[0] >= 20) {
            return
        }

        val imageElement = when (element.tagName().lowercase()) {
            "img" -> element
            else -> element.selectFirst("img, source") ?: return
        }

        val imageUrl = extractImageUrl(element, imageElement)
        if (imageUrl.isBlank()) {
            return
        }

        val resolvedUrl = resolveImageUrl(imageUrl, baseUrl)
        val caption = preferredCaption ?: imageElement.attr("alt").cleanText()
        val width = imageElement.attr("width").toIntOrNull() ?: 0
        val height = imageElement.attr("height").toIntOrNull() ?: 0
        if (isTrackingPixel(width, height, resolvedUrl) || isLikelyAuthorPortrait(element, imageElement, resolvedUrl, caption, width, height)) {
            return
        }

        if (!seen.add(resolvedUrl)) {
            return
        }

        blocks +=
            ArticleBlock(
                type = ArticleBlockType.IMAGE,
                text = caption,
                imageUrl = resolvedUrl,
                imageCaption = caption.takeIf { it.isNotBlank() },
            )
        imageCount[0]++
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

    private fun resolveImageUrl(imageUrl: String, baseUrl: String): String {
        return when {
            imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> imageUrl
            imageUrl.startsWith("//") -> "https:$imageUrl"
            imageUrl.startsWith("/") -> {
                try {
                    val url = URL(baseUrl)
                    URL(url.protocol, url.host, url.port, "").toString().trimEnd('/') + imageUrl
                } catch (e: Exception) {
                    imageUrl
                }
            }
            else -> {
                try {
                    val baseDir = if (baseUrl.endsWith("/")) baseUrl else baseUrl.substringBeforeLast("/") + "/"
                    baseDir + imageUrl
                } catch (e: Exception) {
                    baseUrl.trimEnd('/') + "/" + imageUrl
                }
            }
        }
    }

    private fun isTrackingPixel(width: Int, height: Int, url: String): Boolean {
        // Skip 1x1 images (tracking pixels)
        if (width == 1 && height == 1) return true
        
        // Skip common tracking pixel domains/paths
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("track") || 
               lowerUrl.contains("pixel") ||
               lowerUrl.contains("beacon") ||
               lowerUrl.contains("analytics")
    }

    private fun isLikelyAuthorPortrait(
        element: Element,
        imageElement: Element,
        resolvedUrl: String,
        caption: String,
        width: Int,
        height: Int,
    ): Boolean {
        val contextSignals =
            buildString {
                append(element.tagName())
                append(' ')
                append(element.className())
                append(' ')
                append(element.id())
                append(' ')
                append(imageElement.className())
                append(' ')
                append(imageElement.id())
                append(' ')
                append(imageElement.attr("itemprop"))
                append(' ')
                append(imageElement.attr("role"))
                append(' ')
                append(imageElement.attr("data-testid"))
                append(' ')
                append(imageElement.attr("aria-label"))

                element.parents()
                    .asSequence()
                    .take(4)
                    .forEach { parent ->
                        append(' ')
                        append(parent.tagName())
                        append(' ')
                        append(parent.className())
                        append(' ')
                        append(parent.id())
                        append(' ')
                        append(parent.attr("itemprop"))
                        append(' ')
                        append(parent.attr("role"))
                        append(' ')
                        append(parent.attr("data-testid"))
                        append(' ')
                        append(parent.attr("aria-label"))
                    }
            }.lowercase()

        val metadataSignals =
            buildString {
                append(caption)
                append(' ')
                append(imageElement.attr("alt"))
                append(' ')
                append(imageElement.attr("title"))
                append(' ')
                append(resolvedUrl)
            }.lowercase()

        val hasStrongAuthorContext = contextSignals.containsAny(AUTHOR_CONTEXT_KEYWORDS)
        if (hasStrongAuthorContext) {
            return true
        }

        val hasPortraitUrlSignal = resolvedUrl.lowercase().containsAny(AUTHOR_URL_KEYWORDS)
        val hasPortraitTextSignal = metadataSignals.containsAny(AUTHOR_TEXT_KEYWORDS)
        val looksLikePortrait = looksLikeSmallPortrait(width, height)

        // URL hints are too noisy on their own because many CDNs use words like
        // "avatar" in unrelated image paths. Outside explicit byline/author DOM
        // context, require real portrait text plus portrait-ish dimensions.
        return (hasPortraitTextSignal && looksLikePortrait) ||
            (hasPortraitUrlSignal && hasPortraitTextSignal)
    }

    private fun looksLikeSmallPortrait(
        width: Int,
        height: Int,
    ): Boolean {
        if (width <= 0 || height <= 0) {
            return false
        }

        val maxSide = maxOf(width, height)
        val minSide = minOf(width, height)
        if (maxSide > 360 || minSide < 32) {
            return false
        }

        val ratio = maxSide.toFloat() / minSide.toFloat()
        return ratio <= 2.8f
    }

    private fun extractImageUrl(
        root: Element,
        imageElement: Element,
    ): String =
        sequenceOf(
            pickSrcsetCandidate(root.attr("srcset")),
            pickSrcsetCandidate(root.attr("data-srcset")),
            pickSrcsetCandidate(root.attr("data-lazy-srcset")),
            pickSrcsetCandidate(imageElement.attr("srcset")),
            pickSrcsetCandidate(imageElement.attr("data-srcset")),
            pickSrcsetCandidate(imageElement.attr("data-lazy-srcset")),
            imageElement.attr("src"),
            imageElement.attr("data-src"),
            imageElement.attr("data-lazy-src"),
            imageElement.attr("data-original"),
            root.attr("src"),
            root.attr("data-src"),
        ).firstOrNull { candidate ->
            candidate.isUsableImageUrl()
        }.orEmpty()

    private fun pickSrcsetCandidate(srcset: String): String {
        if (srcset.isBlank()) {
            return ""
        }

        val candidates =
            srcset.split(",")
                .mapNotNull { entry ->
                    val parts = entry.trim().split(Regex("\\s+"))
                    val url = parts.firstOrNull()?.trim()?.trim('"', '\'').orEmpty()
                    if (!url.isUsableImageUrl()) {
                        return@mapNotNull null
                    }

                    val descriptor = parts.getOrNull(1).orEmpty()
                    SrcsetCandidate(
                        url = url,
                        width = descriptor.removeSuffix("w").toIntOrNull(),
                        density = descriptor.removeSuffix("x").toFloatOrNull(),
                    )
                }

        if (candidates.isEmpty()) {
            return ""
        }

        val widthCandidates = candidates.filter { it.width != null }.sortedBy { it.width }
        if (widthCandidates.isNotEmpty()) {
            return widthCandidates.firstOrNull { (it.width ?: 0) >= 640 }?.url ?: widthCandidates.last().url
        }

        val densityCandidates = candidates.filter { it.density != null }.sortedBy { it.density }
        if (densityCandidates.isNotEmpty()) {
            return densityCandidates.firstOrNull { (it.density ?: 0f) >= 1.5f }?.url ?: densityCandidates.last().url
        }

        return candidates.first().url
    }

    private fun String.isUsableImageUrl(): Boolean =
        isNotBlank() &&
            !startsWith("data:", ignoreCase = true) &&
            this != "#"

    private fun String.containsAny(keywords: Set<String>): Boolean =
        keywords.any { keyword -> contains(keyword) }

    private fun String.cleanText(): String =
        Parser.unescapeEntities(this, false)
            .replace('\u00A0', ' ')
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class SrcsetCandidate(
        val url: String,
        val width: Int? = null,
        val density: Float? = null,
    )

    private companion object {
        val AUTHOR_CONTEXT_KEYWORDS =
            setOf(
                "author",
                "byline",
                "avatar",
                "headshot",
                "journalist",
                "reporter",
                "writer",
                "columnist",
                "contributor",
                "bio",
                "biography",
                "staff-photo",
                "staffprofile",
            )

        val AUTHOR_URL_KEYWORDS =
            setOf(
                "/profile",
                "/profiles",
                "/author",
                "/authors",
                "/writer",
                "/writers",
                "/reporter",
                "/journalist",
                "/contributor",
                "/columnist",
                "/headshot",
                "/headshots",
                "/bio",
                "/bios",
                "/staff/",
                "staff-photo",
            )

        val AUTHOR_TEXT_KEYWORDS =
            setOf(
                "author",
                "byline",
                "avatar",
                "profile photo",
                "headshot",
                "staff photo",
                "writer",
                "reporter",
                "journalist",
                "columnist",
                "contributor",
            )
    }
}
