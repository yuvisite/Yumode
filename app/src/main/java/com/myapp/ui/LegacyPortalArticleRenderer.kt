package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.ArticleBlockType

internal data class LegacyPortalArticleTheme(
    val shellPaddingTopDp: Int,
    val shellPaddingBottomDp: Int,
    val shellGravity: Int = Gravity.START,
    val contentWidthPxProvider: (AppCompatActivity) -> Int,
    val titleColor: Int,
    val bodyColor: Int,
    val mutedColor: Int,
    val dividerColor: Int,
)

internal object LegacyPortalArticleRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        articleState: ArticleUiState,
        headerView: View,
        articleLink: View,
        scrollAnchorView: View,
        focusTargetView: View,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        restoreScrollAndFocus: (Int, View, View) -> Unit,
        scrollY: Int,
        theme: LegacyPortalArticleTheme,
    ) {
        container.addView(headerView)

        val bodyShell = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.VERTICAL
            setPadding(0, activity.dp(theme.shellPaddingTopDp), 0, activity.dp(theme.shellPaddingBottomDp))
            gravity = theme.shellGravity
        }

        val body = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                theme.contentWidthPxProvider(activity),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.VERTICAL
        }

        bodyShell.addView(body)
        container.addView(bodyShell)

        body.addView(articleLink)

        when {
            articleState.isLoading -> body.addView(meta(activity, "loading article...", theme.mutedColor, scaledTextSize, compactLineSpacing, typeface))
            articleState.error != null -> body.addView(meta(activity, articleState.error, COLOR_ERROR, scaledTextSize, compactLineSpacing, typeface))
            articleState.article == null -> body.addView(meta(activity, "article unavailable", theme.mutedColor, scaledTextSize, compactLineSpacing, typeface))
            else -> {
                val article = articleState.article

                body.addView(
                    TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = activity.dp(4)
                        }
                        includeFontPadding = false
                        textSize = scaledTextSize(12f)
                        setLineSpacing(0f, compactLineSpacing)
                        this.typeface = typeface
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(theme.titleColor)
                        text = article.title
                    },
                )

                formatRssDisplayDateTime(article.publishedAt)?.let { label ->
                    body.addView(meta(activity, label, theme.mutedColor, scaledTextSize, compactLineSpacing, typeface))
                }
                if (article.sourceHost.isNotBlank()) {
                    body.addView(meta(activity, article.sourceHost, theme.mutedColor, scaledTextSize, compactLineSpacing, typeface))
                }

                body.addView(
                    View(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            activity.dp(1),
                        ).apply {
                            topMargin = activity.dp(6)
                            bottomMargin = activity.dp(6)
                        }
                        setBackgroundColor(theme.dividerColor)
                    },
                )

                article.blocks.forEach { block ->
                    when (block.type) {
                        ArticleBlockType.HEADING ->
                            body.addView(bodyText(activity, block.text, true, theme.bodyColor, scaledTextSize, compactLineSpacing, typeface))

                        ArticleBlockType.LIST_ITEM ->
                            body.addView(bodyText(activity, "\u2022 ${block.text}", false, theme.bodyColor, scaledTextSize, compactLineSpacing, typeface))

                        ArticleBlockType.PARAGRAPH ->
                            body.addView(bodyText(activity, block.text, false, theme.bodyColor, scaledTextSize, compactLineSpacing, typeface))

                        ArticleBlockType.IMAGE -> {
                            block.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                                body.addView(
                                    buildLegacyPortalInlineArticleImage(
                                        activity = activity,
                                        imageUrl = imageUrl,
                                        caption = block.imageCaption ?: block.text,
                                        refererUrl = article.finalUrl,
                                        style =
                                            LegacyPortalInlineImageStyle(
                                                decodeWidthPx = theme.contentWidthPxProvider(activity),
                                                frameColor = theme.dividerColor,
                                                mutedColor = theme.mutedColor,
                                                captionColor = theme.bodyColor,
                                                frameFillColor = Color.TRANSPARENT,
                                            ),
                                        scaledTextSize = scaledTextSize,
                                        compactLineSpacing = compactLineSpacing,
                                        typeface = typeface,
                                    ),
                                )
                            }
                        }
                    }
                }

                restoreScrollAndFocus(scrollY, scrollAnchorView, focusTargetView)
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
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(2)
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
        color: Int,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(3)
            }
            includeFontPadding = false
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, compactLineSpacing)
            this.typeface = typeface
            setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(color)
            this.text = text
        }
}
