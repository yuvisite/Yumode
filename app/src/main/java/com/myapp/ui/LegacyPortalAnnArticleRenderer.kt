package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.ArticleBlockType
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite
import java.util.Locale

internal object LegacyPortalAnnArticleRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        site: PortalSite,
        state: FeedUiState,
        articleState: ArticleUiState,
        sections: List<String>,
        selectedSection: String?,
        selectedTagKey: String?,
        compactLineSpacing: Float,
        portalTypeface: Typeface?,
        buildCategoryFlowBar: (
            siteId: String,
            categories: List<String>,
            selectedCategory: String?,
            normalColor: Int,
            highlightColor: Int,
            focusBackground: Int?,
            lineSpacing: Float,
            includeAll: Boolean,
            center: Boolean,
            onCategorySelected: (String?) -> Unit,
        ) -> View,
        normalizeCategoryKey: (String) -> String,
        onSelectSection: (String) -> Unit,
        onSelectTag: (String?) -> Unit,
        addMetaText: (String, Int) -> Unit,
        addArticleText: (String, Boolean) -> Unit,
        addDivider: () -> Unit,
        restoreArticleScrollAndFocus: () -> Unit,
    ) {
        val tagLabelsByKey = linkedMapOf<String, String>()
        state.items.forEach { item ->
            item.categories.forEach { raw ->
                val key = raw.trim().lowercase(Locale.ROOT)
                if (key.isNotBlank() && !tagLabelsByKey.containsKey(key)) {
                    tagLabelsByKey[key] = raw.trim()
                }
            }
        }

        container.addView(
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                includeFontPadding = false
                gravity = Gravity.CENTER_HORIZONTAL
                text = "ANN"
                textSize = 16f
                setLineSpacing(0f, 0.9f)
                setTypeface(portalTypeface, Typeface.BOLD)
                setTextColor(COLOR_ANN_SECTION_TEXT)
                setBackgroundColor(COLOR_ANN_SECTION_BAR)
                setPadding(activity.dp(2), activity.dp(2), activity.dp(2), activity.dp(2))
            },
        )

        val sectionBar = buildCategoryFlowBar(
            site.id,
            sections,
            selectedSection,
            COLOR_ANN_SECTION_TEXT,
            Color.WHITE,
            COLOR_ANN_TILE_BG_FOCUS,
            compactLineSpacing,
            false,
            true,
            { category ->
                category?.let(onSelectSection)
            },
        )
        container.addView(
            LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.VERTICAL
                setPadding(activity.dp(6), activity.dp(2), activity.dp(6), activity.dp(2))
                setBackgroundColor(COLOR_ANN_SECTION_BAR)
                addView(sectionBar)
            },
        )

        val selectedTagLabel = selectedTagKey?.let { tagLabelsByKey[it] }
        val tagBar = buildCategoryFlowBar(
            site.id,
            tagLabelsByKey.values.toList(),
            selectedTagLabel,
            COLOR_ANN_SECTION_BAR,
            Color.WHITE,
            COLOR_ANN_SECTION_BAR,
            compactLineSpacing,
            true,
            false,
            { label ->
                val key =
                    if (label.isNullOrBlank()) {
                        null
                    } else {
                        tagLabelsByKey.entries.firstOrNull { (_, value) ->
                            normalizeCategoryKey(value) == normalizeCategoryKey(label)
                        }?.key ?: normalizeCategoryKey(label)
                    }
                onSelectTag(key)
            },
        )
        container.addView(
            LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.VERTICAL
                setPadding(activity.dp(6), activity.dp(2), activity.dp(6), activity.dp(2))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 0f
                    setColor(COLOR_ANN_TILE_BG)
                    setStroke(activity.dp(1), COLOR_ANN_TAG_BORDER)
                }
                addView(tagBar)
            },
        )

        when {
            articleState.isLoading -> addMetaText("loading article...", COLOR_MUTED)
            articleState.error != null -> addMetaText(articleState.error ?: "", COLOR_ERROR)
            articleState.article == null -> addMetaText("article unavailable", COLOR_MUTED)
            else -> {
                val article = articleState.article!!
                addArticleText(article.title, true)
                formatRssDisplayDateTime(article.publishedAt)?.let { addMetaText(it, COLOR_ANN_META_TEXT) }
                addDivider()
                article.blocks.forEach { block ->
                    when (block.type) {
                        ArticleBlockType.HEADING -> addArticleText(block.text, true)
                        ArticleBlockType.LIST_ITEM -> addArticleText("\u2022 ${block.text}", false)
                        ArticleBlockType.PARAGRAPH -> addArticleText(block.text, false)
                        ArticleBlockType.IMAGE ->
                            block.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                                container.addView(
                                    buildLegacyPortalInlineArticleImage(
                                        activity = activity,
                                        imageUrl = imageUrl,
                                        caption = block.imageCaption ?: block.text,
                                        refererUrl = article.finalUrl,
                                        style =
                                            LegacyPortalInlineImageStyle(
                                                decodeWidthPx = legacyPortalWideImageWidthPx(activity, 12),
                                                frameColor = COLOR_ANN_TAG_BORDER,
                                                mutedColor = COLOR_ANN_META_TEXT,
                                                captionColor = Color.BLACK,
                                            ),
                                    ),
                                )
                            }
                    }
                }
                restoreArticleScrollAndFocus()
            }
        }
    }
}
