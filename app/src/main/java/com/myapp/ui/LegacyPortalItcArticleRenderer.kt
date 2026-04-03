package com.myapp.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.ArticleBlockType

internal object LegacyPortalItcArticleRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        articleState: ArticleUiState,
        publishedAtLabel: String?,
        categoryLine: View,
        articleLink: View,
        searchRow: LinearLayout,
        buildItcLogoView: () -> View?,
        createItcMetaText: (String, Int) -> View,
        createItcArticleText: (String, Boolean) -> View,
        dividerView: () -> View,
        tagLine: View?,
        footerView: View,
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

                buildItcLogoView()?.let { addView(it) }

                val catWrap = LinearLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
                    orientation = LinearLayout.VERTICAL
                }
                catWrap.addView(categoryLine)
                catWrap.addView(searchRow)
                addView(catWrap)
            },
        )
        contentWrap.addView(articleLink)

        when {
            articleState.isLoading -> contentWrap.addView(createItcMetaText("loading article...", COLOR_ITC_TEXT))
            articleState.error != null -> contentWrap.addView(createItcMetaText(articleState.error ?: "", COLOR_ERROR))
            articleState.article == null -> contentWrap.addView(createItcMetaText("article unavailable", COLOR_ITC_TEXT))
            else -> {
                val article = articleState.article!!
                contentWrap.addView(createItcArticleText(article.title, true))
                publishedAtLabel?.let { contentWrap.addView(createItcMetaText(it, COLOR_ITC_TEXT)) }
                contentWrap.addView(dividerView())
                article.blocks.forEach { block ->
                    when (block.type) {
                        ArticleBlockType.HEADING -> contentWrap.addView(createItcArticleText(block.text, true))
                        ArticleBlockType.LIST_ITEM -> contentWrap.addView(createItcArticleText("• ${block.text}", false))
                        ArticleBlockType.PARAGRAPH -> contentWrap.addView(createItcArticleText(block.text, false))
                    }
                }
                tagLine?.let { contentWrap.addView(it) }
                contentWrap.addView(footerView)
                restoreScrollAndFocus(scrollY, categoryLine, articleLink)
            }
        }
    }
}

