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
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite
import java.util.Locale

internal object LegacyPortalAnnSiteRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        site: PortalSite,
        state: FeedUiState,
        sections: List<String>,
        selectedSection: String?,
        selectedTagKey: String?,
        pagerState: FeedPagerState,
        pageSize: Int,
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
        filterFeedSearch: (String, List<FeedItem>) -> List<FeedItem>,
        createHeadlineEntry: (Int, String, String, () -> Unit, () -> Unit) -> View,
        onOpenArticle: (FeedItem) -> Unit,
        onHeadlineFocused: (Int, Int) -> Unit,
        addPlainText: (text: String, sizeSp: Float, color: Int, verticalPaddingDp: Int, bottomMarginDp: Int) -> Unit,
        requestFocus: (View?) -> Unit,
    ) {
        val tagLabelsByKey = linkedMapOf<String, String>()
        state.items.forEach { item ->
            item.categories.forEach { raw ->
                val norm = raw.trim().lowercase(Locale.ROOT)
                if (norm.isNotBlank() && !tagLabelsByKey.containsKey(norm)) {
                    tagLabelsByKey[norm] = raw.trim()
                }
            }
        }
        if (tagLabelsByKey.isEmpty()) {
            state.items.forEach { item ->
                item.primaryCategory()?.let { category ->
                    val norm = category.trim().lowercase(Locale.ROOT)
                    if (norm.isNotBlank() && !tagLabelsByKey.containsKey(norm)) {
                        tagLabelsByKey[norm] = category.trim()
                    }
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

        val selectedTagLabel = selectedTagKey?.let { key -> tagLabelsByKey[key] }
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
                if (label.isNullOrBlank()) {
                    onSelectTag(null)
                } else {
                    val pickedKey = tagLabelsByKey.entries.firstOrNull { (_, value) ->
                        normalizeCategoryKey(value) == normalizeCategoryKey(label)
                    }?.key
                    onSelectTag(pickedKey ?: normalizeCategoryKey(label))
                }
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

        val tagFilteredItems =
            if (selectedTagKey == null) {
                state.items
            } else {
                state.items.filter { item ->
                    item.categories.any { category -> category.trim().lowercase(Locale.ROOT) == selectedTagKey } ||
                        item.primaryCategory()?.trim()?.lowercase(Locale.ROOT) == selectedTagKey
                }
            }

        val searchedItems = filterFeedSearch(site.id, tagFilteredItems)
        when {
            state.isLoading -> addPlainText("loading feed...", 10f, COLOR_ANN_META_TEXT, 1, 1)
            state.error != null -> addPlainText(state.error ?: "", 10f, COLOR_ERROR, 1, 1)
            searchedItems.isEmpty() -> addPlainText("no items", 10f, COLOR_ANN_META_TEXT, 1, 1)
            else -> {
                val pageCount = ((searchedItems.size - 1) / pageSize) + 1
                val visibleItems = searchedItems.drop(pagerState.pageIndex * pageSize).take(pageSize)

                addPlainText(
                    "page ${pagerState.pageIndex + 1}/$pageCount  1-${visibleItems.size} open",
                    10f,
                    COLOR_ANN_META_TEXT,
                    0,
                    0,
                )

                var selectedEntry: View? = null
                visibleItems.forEachIndexed { index, item ->
                    val entry = createHeadlineEntry(
                        index + 1,
                        item.title,
                        formatRssDisplayDateTime(item.publishedAt).orEmpty(),
                        { onOpenArticle(item) },
                        { onHeadlineFocused(index, searchedItems.size) },
                    )
                    if (index == pagerState.selectedSlot) {
                        selectedEntry = entry
                    }
                }
                requestFocus(selectedEntry)
            }
        }
    }
}
