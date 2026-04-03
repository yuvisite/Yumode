package com.myapp.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.ArticleBlockType
import com.myapp.model.PortalSite

internal object LegacyPortalArsArticleRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        site: PortalSite,
        siteId: String,
        articleState: ArticleUiState,
        categoryBar: View,
        articleLink: View,
        searchRow: LinearLayout,
        buildArsLogoView: () -> View?,
        createArsMetaText: (String, Int) -> View,
        createArsArticleText: (String, Boolean) -> View,
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
            setPadding(activity.dp(4), activity.dp(2), activity.dp(4), activity.dp(4))
        }
        container.addView(contentWrap)

        contentWrap.addView(
            LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                buildArsLogoView()?.let { addView(it) }
                addView(searchRow)
            },
        )
        contentWrap.addView(categoryBar)
        contentWrap.addView(articleLink)

        when {
            articleState.isLoading -> contentWrap.addView(createArsMetaText("loading article...", COLOR_ARS_MUTED))
            articleState.error != null -> contentWrap.addView(createArsMetaText(articleState.error ?: "", COLOR_ERROR))
            articleState.article == null -> contentWrap.addView(createArsMetaText("article unavailable", COLOR_ARS_MUTED))
            else -> {
                val article = articleState.article!!
                contentWrap.addView(createArsArticleText(article.title, true))
                article.publishedAt?.let { contentWrap.addView(createArsMetaText(it, COLOR_ARS_MUTED)) }
                contentWrap.addView(dividerView())
                article.blocks.forEach { block ->
                    when (block.type) {
                        ArticleBlockType.HEADING -> contentWrap.addView(createArsArticleText(block.text, true))
                        ArticleBlockType.LIST_ITEM -> contentWrap.addView(createArsArticleText("• ${block.text}", false))
                        ArticleBlockType.PARAGRAPH -> contentWrap.addView(createArsArticleText(block.text, false))
                    }
                }
                restoreScrollAndFocus(scrollY, categoryBar, articleLink)
            }
        }
    }
}

