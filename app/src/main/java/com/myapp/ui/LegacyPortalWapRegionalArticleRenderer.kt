package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.ArticleBlockType

internal object LegacyPortalWapRegionalArticleRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        articleState: ArticleUiState,
        palette: WapRegionalPalette,
        brandedHeader: View,
        articleLink: View,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        centerContent: Boolean,
        restoreScrollAndFocus: (Int, View, View) -> Unit,
        scrollY: Int,
    ) {
        container.setBackgroundColor(palette.bg)
        container.addView(brandedHeader)

        val bodyShell =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
                setPadding(0, activity.dp(1), 0, activity.dp(2))
                gravity = if (centerContent) Gravity.CENTER_HORIZONTAL else Gravity.START
            }
        val body =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        activity.wapContentWidthPx(),
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
            }
        bodyShell.addView(body)
        container.addView(bodyShell)

        body.addView(articleLink)

        when {
            articleState.isLoading ->
                body.addView(meta(activity, "loading article...", palette.muted, scaledTextSize, compactLineSpacing, typeface))

            articleState.error != null ->
                body.addView(meta(activity, articleState.error ?: "", COLOR_ERROR, scaledTextSize, compactLineSpacing, typeface))

            articleState.article == null ->
                body.addView(meta(activity, "article unavailable", palette.muted, scaledTextSize, compactLineSpacing, typeface))

            else -> {
                val article = articleState.article!!
                body.addView(
                    TextView(activity).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                topMargin = activity.dp(4)
                            }
                        includeFontPadding = false
                        textSize = scaledTextSize(12f)
                        setLineSpacing(0f, compactLineSpacing)
                        this.typeface = typeface
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(palette.text)
                        text = article.title
                    },
                )
                formatRssDisplayDateTime(article.publishedAt)?.let { label ->
                    body.addView(meta(activity, label, palette.muted, scaledTextSize, compactLineSpacing, typeface))
                }
                body.addView(
                    android.view.View(activity).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                activity.dp(1),
                            ).apply {
                                topMargin = activity.dp(6)
                                bottomMargin = activity.dp(6)
                            }
                        setBackgroundColor(palette.articleDivider)
                    },
                )
                article.blocks.forEach { block ->
                    when (block.type) {
                        ArticleBlockType.HEADING ->
                            body.addView(bodyText(activity, block.text, true, palette, scaledTextSize, compactLineSpacing, typeface))

                        ArticleBlockType.LIST_ITEM ->
                            body.addView(bodyText(activity, "\u2022 ${block.text}", false, palette, scaledTextSize, compactLineSpacing, typeface))

                        ArticleBlockType.PARAGRAPH ->
                            body.addView(bodyText(activity, block.text, false, palette, scaledTextSize, compactLineSpacing, typeface))
                    }
                }
                restoreScrollAndFocus(scrollY, brandedHeader, articleLink)
            }
        }
    }

    private fun meta(
        activity: AppCompatActivity,
        text: String,
        color: Int,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
    ): TextView =
        TextView(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = activity.dp(1)
                }
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            this.typeface = typeface
            setTextColor(color)
            this.text = text
        }

    private fun bodyText(
        activity: AppCompatActivity,
        text: String,
        bold: Boolean,
        palette: WapRegionalPalette,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
    ): TextView =
        TextView(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = activity.dp(4)
                }
            includeFontPadding = false
            textSize = scaledTextSize(if (bold) 11f else 10.5f)
            setLineSpacing(0f, compactLineSpacing)
            this.typeface = typeface
            setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(palette.text)
            this.text = text
        }
}
