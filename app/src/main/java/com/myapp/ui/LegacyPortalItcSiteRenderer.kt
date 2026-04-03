package com.myapp.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite

internal object LegacyPortalItcSiteRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        site: PortalSite,
        shouldRenderSiteSummary: Boolean,
        state: FeedUiState,
        categories: List<String>,
        selectedCategory: String?,
        filteredItems: List<FeedItem>,
        pagerState: FeedPagerState,
        onRequestSearchQuery: (String) -> Unit,
        onOpenArticle: (FeedItem) -> Unit,
        onHeadlineFocused: (Int) -> Unit,
        formatItcDate: (String?) -> String?,
        buildItcSearchRow: (String, (String) -> Unit) -> Pair<EditText, LinearLayout>,
        buildItcLogoView: () -> View?,
        buildItcCategoryLine: (String, List<String>, String?) -> View,
        createItcMetaText: (String, Int) -> TextView,
        addItcFeedEntry: (Int, String, String?, () -> Unit, () -> Unit) -> View,
        createItcFooterText: (String) -> TextView,
        buildItcPaginationBar: (String, Int, Int) -> View,
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

        val (searchField, searchRow) = buildItcSearchRow(site.id) { query ->
            onRequestSearchQuery(query)
        }

        contentWrap.addView(
            LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                buildItcLogoView()?.let { logoView ->
                    addView(logoView)
                }

                val catWrap = LinearLayout(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                    orientation = LinearLayout.VERTICAL
                }

                catWrap.addView(buildItcCategoryLine(site.id, categories, selectedCategory))
                catWrap.addView(searchRow)
                addView(catWrap)
            },
        )

        if (shouldRenderSiteSummary) {
            contentWrap.addView(createItcMetaText(site.summary, COLOR_ITC_TEXT))
        }

        when {
            state.isLoading -> contentWrap.addView(createItcMetaText("loading feed...", COLOR_ITC_TEXT))
            state.error != null -> contentWrap.addView(createItcMetaText(state.error ?: "", COLOR_ERROR))
            filteredItems.isEmpty() -> contentWrap.addView(createItcMetaText("no headlines", COLOR_ITC_TEXT))

            else -> {
                val pageCount = ((filteredItems.size - 1) / FeedPageSize) + 1
                val visibleItems =
                    filteredItems.drop(pagerState.pageIndex * FeedPageSize).take(FeedPageSize)

                var selectedEntry: View? = null
                visibleItems.forEachIndexed { index, item ->
                    val entry = addItcFeedEntry(
                        index + 1,
                        item.title,
                        formatItcDate(item.publishedAt),
                        { onOpenArticle(item) },
                        { onHeadlineFocused(index) },
                    )
                    contentWrap.addView(entry)
                    if (index == pagerState.selectedSlot) {
                        selectedEntry = entry
                    }
                }

                contentWrap.addView(createItcFooterText("itc.ua"))

                if (pageCount > 1) {
                    contentWrap.addView(buildItcPaginationBar(site.id, pageCount, pagerState.pageIndex))
                }

                selectedEntry?.post { selectedEntry?.requestFocus() }
                    ?: searchField.post { searchField.requestFocus() }
            }
        }
    }
}

