package com.myapp.model

enum class ArticleBlockType {
    HEADING,
    PARAGRAPH,
    LIST_ITEM,
    IMAGE,
}

data class FeedItem(
    val id: String,
    val title: String,
    val url: String,
    val publishedAt: String?,
    val summary: String?,
    val inlineContentHtml: String? = null,
    val categories: List<String> = emptyList(),
)

fun FeedItem.primaryCategory(): String? =
    categories.firstOrNull { it.isNotBlank() }

data class ArticleBlock(
    val type: ArticleBlockType,
    val text: String,
    val imageUrl: String? = null,
    val imageCaption: String? = null,
)

data class SanitizedArticle(
    val title: String,
    val sourceUrl: String,
    val finalUrl: String,
    val sourceHost: String,
    val publishedAt: String?,
    val excerpt: String?,
    val blocks: List<ArticleBlock>,
)
