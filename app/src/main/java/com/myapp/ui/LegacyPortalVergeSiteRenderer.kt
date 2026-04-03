package com.myapp.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite

internal object LegacyPortalVergeSiteRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        site: PortalSite,
        state: FeedUiState,
        categories: List<String>,
        selectedCategory: String?,
        filteredItems: List<FeedItem>,
        pagerState: FeedPagerState,
        onOpenArticle: (FeedItem) -> Unit,
        onHeadlineFocused: (Int) -> Unit,
        createVergeLogoText: () -> View,
        buildCategoryFlowBar: (
            String,
            List<String>,
            String?,
            Int,
            Int,
            Int?,
            Float,
            Boolean,
            Boolean,
        ) -> View,
        createVergeMetaText: (String, Int) -> View,
        createVergeFeedEntry: (Int, String, String?, () -> Unit, () -> Unit) -> View,
        buildVergePaginationBar: (String, Int, Int) -> View,
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
        contentWrap.addView(
            buildCategoryFlowBar(
                site.id,
                categories,
                selectedCategory,
                COLOR_VERGE_GLOW,
                COLOR_VERGE_ACCENT,
                COLOR_VERGE_PANEL,
                VERGE_LINE_SPACING,
                true,
                false,
            ),
        )

        when {
            state.isLoading -> contentWrap.addView(createVergeMetaText("loading feed...", COLOR_VERGE_MUTED))
            state.error != null -> contentWrap.addView(createVergeMetaText(state.error ?: "", COLOR_ERROR))
            filteredItems.isEmpty() -> contentWrap.addView(createVergeMetaText("no headlines", COLOR_VERGE_MUTED))
            else -> {
                val pageCount = ((filteredItems.size - 1) / FeedPageSize) + 1
                val visibleItems = filteredItems.drop(pagerState.pageIndex * FeedPageSize).take(FeedPageSize)
                var selectedEntry: View? = null
                visibleItems.forEachIndexed { index, item ->
                    val entry =
                        createVergeFeedEntry(
                            index + 1,
                            item.title,
                            item.publishedAt?.take(16),
                            { onOpenArticle(item) },
                            { onHeadlineFocused(index) },
                        )
                    contentWrap.addView(entry)
                    if (index == pagerState.selectedSlot) {
                        selectedEntry = entry
                    }
                }
                if (pageCount > 1) {
                    contentWrap.addView(buildVergePaginationBar(site.id, pageCount, pagerState.pageIndex))
                }
                selectedEntry?.post { selectedEntry?.requestFocus() }
            }
        }
    }
}

