package com.myapp.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.ArticleBlockType

internal object LegacyPortalGagadgetArticleRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        articleState: ArticleUiState,
        categoryBar: View,
        articleLink: View,
        buildGagadgetLogoView: () -> View,
        createGagadgetMetaText: (String, Int) -> View,
        createGagadgetTitleText: (String) -> View,
        createGagadgetArticleText: (String, Boolean) -> View,
        dividerView: () -> View,
        formatGagadgetDateTime: (String?) -> String?,
        restoreScrollAndFocus: (Int, View, View) -> Unit,
        scrollY: Int,
    ) {
        val contentWrap = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(4), activity.dp(2), activity.dp(4), activity.dp(4))
        }
        container.addView(contentWrap)

        contentWrap.addView(buildGagadgetLogoView())
        contentWrap.addView(categoryBar)
        contentWrap.addView(articleLink)

        when {
            articleState.isLoading -> contentWrap.addView(createGagadgetMetaText("loading article...", COLOR_GG_TEXT))
            articleState.error != null -> contentWrap.addView(createGagadgetMetaText(articleState.error ?: "", COLOR_ERROR))
            articleState.article == null -> contentWrap.addView(createGagadgetMetaText("article unavailable", COLOR_GG_TEXT))
            else -> {
                val article = articleState.article!!
                formatGagadgetDateTime(article.publishedAt)?.let {
                    contentWrap.addView(createGagadgetMetaText(it, COLOR_GG_TEXT))
                }
                contentWrap.addView(createGagadgetTitleText(article.title))
                contentWrap.addView(dividerView())
                article.blocks.forEach { block ->
                    when (block.type) {
                        ArticleBlockType.HEADING -> contentWrap.addView(createGagadgetArticleText(block.text, true))
                        ArticleBlockType.LIST_ITEM -> contentWrap.addView(createGagadgetArticleText("• ${block.text}", false))
                        ArticleBlockType.PARAGRAPH -> contentWrap.addView(createGagadgetArticleText(block.text, false))
                    }
                }
                restoreScrollAndFocus(scrollY, categoryBar, articleLink)
            }
        }
    }
}

