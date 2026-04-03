package com.myapp.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.ArticleBlockType

internal object LegacyPortalVergeArticleRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        articleState: ArticleUiState,
        categoryBar: View,
        articleLink: View,
        createVergeLogoText: () -> View,
        createVergeMetaText: (String, Int) -> View,
        createVergeTitleText: (String) -> View,
        createVergeArticleText: (String, Boolean) -> View,
        dividerView: () -> View,
        restoreScrollAndFocus: (Int, View, View) -> Unit,
        scrollY: Int,
    ) {
        val contentWrap = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(4), activity.dp(3), activity.dp(4), activity.dp(4))
        }
        container.addView(contentWrap)

        contentWrap.addView(createVergeLogoText())
        contentWrap.addView(categoryBar)
        contentWrap.addView(articleLink)

        when {
            articleState.isLoading -> contentWrap.addView(createVergeMetaText("loading article...", COLOR_VERGE_MUTED))
            articleState.error != null -> contentWrap.addView(createVergeMetaText(articleState.error ?: "", COLOR_ERROR))
            articleState.article == null -> contentWrap.addView(createVergeMetaText("article unavailable", COLOR_VERGE_MUTED))
            else -> {
                val article = articleState.article!!
                contentWrap.addView(createVergeTitleText(article.title))
                article.publishedAt?.let { contentWrap.addView(createVergeMetaText(it, COLOR_VERGE_MUTED)) }
                contentWrap.addView(dividerView())
                article.blocks.forEach { block ->
                    when (block.type) {
                        ArticleBlockType.HEADING -> contentWrap.addView(createVergeArticleText(block.text, true))
                        ArticleBlockType.LIST_ITEM -> contentWrap.addView(createVergeArticleText("- ${block.text}", false))
                        ArticleBlockType.PARAGRAPH -> contentWrap.addView(createVergeArticleText(block.text, false))
                    }
                }
                restoreScrollAndFocus(scrollY, categoryBar, articleLink)
            }
        }
    }
}

