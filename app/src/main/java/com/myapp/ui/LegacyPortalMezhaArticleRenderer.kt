package com.myapp.ui

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.ArticleBlockType

internal object LegacyPortalMezhaArticleRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        articleState: ArticleUiState,
        categoryBar: View,
        articleLink: View,
        createMezhaLogoText: () -> View,
        createMezhaMetaText: (String, Int) -> View,
        createMezhaTitleText: (String) -> View,
        createMezhaBodyText: (String, Boolean) -> View,
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

        contentWrap.addView(createMezhaLogoText())
        contentWrap.addView(categoryBar)
        contentWrap.addView(articleLink)

        when {
            articleState.isLoading -> contentWrap.addView(createMezhaMetaText("loading article...", Color.BLACK))
            articleState.error != null -> contentWrap.addView(createMezhaMetaText(articleState.error ?: "", COLOR_ERROR))
            articleState.article == null -> contentWrap.addView(createMezhaMetaText("article unavailable", Color.BLACK))
            else -> {
                val article = articleState.article!!
                contentWrap.addView(createMezhaTitleText(article.title))
                article.publishedAt?.let { contentWrap.addView(createMezhaMetaText(it, Color.BLACK)) }
                contentWrap.addView(dividerView())
                article.blocks.forEach { block ->
                    when (block.type) {
                        ArticleBlockType.HEADING -> contentWrap.addView(createMezhaBodyText(block.text, true))
                        ArticleBlockType.LIST_ITEM -> contentWrap.addView(createMezhaBodyText("• ${block.text}", false))
                        ArticleBlockType.PARAGRAPH -> contentWrap.addView(createMezhaBodyText(block.text, false))
                        ArticleBlockType.IMAGE ->
                            block.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                                contentWrap.addView(
                                    buildLegacyPortalInlineArticleImage(
                                        activity = activity,
                                        imageUrl = imageUrl,
                                        caption = block.imageCaption ?: block.text,
                                        refererUrl = article.finalUrl,
                                        style =
                                            LegacyPortalInlineImageStyle(
                                                decodeWidthPx = legacyPortalWideImageWidthPx(activity, 8),
                                                frameColor = Color.BLACK,
                                                mutedColor = Color.BLACK,
                                                captionColor = Color.BLACK,
                                            ),
                                    ),
                                )
                            }
                    }
                }
                restoreScrollAndFocus(scrollY, categoryBar, articleLink)
            }
        }
    }
}
