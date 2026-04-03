package com.myapp.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite

internal object LegacyPortalGagadgetSiteRenderer {
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
        buildGagadgetLogoView: () -> View,
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
        createGagadgetMetaText: (String, Int) -> View,
        scaledTextSize: (Float) -> Float,
        typeface: Typeface,
        onPageSelected: (Int) -> Unit,
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
        contentWrap.addView(
            buildCategoryFlowBar(
                site.id,
                categories,
                selectedCategory,
                COLOR_GG_CAT_TEXT,
                COLOR_GG_HEADLINE,
                null,
                GAGADGET_LINE_SPACING,
                false,
                true,
            ),
        )

        when {
            state.isLoading -> contentWrap.addView(createGagadgetMetaText("loading feed...", COLOR_GG_TEXT))
            state.error != null -> contentWrap.addView(createGagadgetMetaText(state.error ?: "", COLOR_ERROR))
            filteredItems.isEmpty() -> contentWrap.addView(createGagadgetMetaText("no headlines", COLOR_GG_TEXT))
            else -> {
                val pageCount = ((filteredItems.size - 1) / GAGADGET_FEED_PAGE_SIZE) + 1
                val visibleItems =
                    filteredItems.drop(pagerState.pageIndex * GAGADGET_FEED_PAGE_SIZE).take(GAGADGET_FEED_PAGE_SIZE)
                var selectedEntry: View? = null
                visibleItems.forEachIndexed { index, item ->
                    val entry =
                        createGagadgetFeedEntry(
                            activity = activity,
                            index + 1,
                            item.title,
                            item.publishedAt?.take(16),
                            scaledTextSize = scaledTextSize,
                            typeface = typeface,
                            { onOpenArticle(item) },
                            { onHeadlineFocused(index) },
                        )
                    contentWrap.addView(entry)
                    if (index == pagerState.selectedSlot) {
                        selectedEntry = entry
                    }
                }
                if (pageCount > 1) {
                    contentWrap.addView(
                        buildGagadgetPaginationBar(
                            activity = activity,
                            pageCount = pageCount,
                            selectedPage = pagerState.pageIndex,
                            scaledTextSize = scaledTextSize,
                            typeface = typeface,
                            onPageSelected = onPageSelected,
                        ),
                    )
                }
                selectedEntry?.post { selectedEntry?.requestFocus() }
            }
        }
    }

    private fun createGagadgetFeedEntry(
        activity: AppCompatActivity,
        number: Int,
        title: String,
        meta: String?,
        scaledTextSize: (Float) -> Float,
        typeface: Typeface,
        onClick: () -> Unit,
        onFocused: () -> Unit,
    ): View =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(activity.dp(2), 0, activity.dp(2), 0)
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false

            val badgeView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(16), activity.dp(16))
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setTypeface(typeface, Typeface.BOLD)
                text = number.toString()
                setTextColor(COLOR_GG_HEADLINE)
            }

            val titleView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = activity.dp(2)
                }
                includeFontPadding = false
                textSize = scaledTextSize(11f)
                setLineSpacing(0f, GAGADGET_LINE_SPACING)
                this.typeface = typeface
                paint.isUnderlineText = true
            }

            fun applyState(focused: Boolean) {
                titleView.text = title
                titleView.setTextColor(COLOR_GG_HEADLINE)
                titleView.setTypeface(typeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
            }

            addView(badgeView)
            addView(titleView)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus ->
                applyState(hasFocus)
                if (hasFocus) {
                    onFocused()
                }
            }
            applyState(false)
        }

    private fun buildGagadgetPaginationBar(
        activity: AppCompatActivity,
        pageCount: Int,
        selectedPage: Int,
        scaledTextSize: (Float) -> Float,
        typeface: Typeface,
        onPageSelected: (Int) -> Unit,
    ): View =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(2)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            for (pageIndex in 0 until pageCount) {
                if (pageIndex > 0) {
                    addView(
                        TextView(activity).apply {
                            includeFontPadding = false
                            text = " "
                            textSize = scaledTextSize(11f)
                            this.typeface = typeface
                            setTextColor(COLOR_GG_HEADLINE)
                        },
                    )
                }
                addView(
                    createGagadgetPagerLink(
                        activity = activity,
                        label = (pageIndex + 1).toString(),
                        selected = pageIndex == selectedPage,
                        scaledTextSize = scaledTextSize,
                        typeface = typeface,
                        onClick = { onPageSelected(pageIndex) },
                    ),
                )
            }
        }

    private fun createGagadgetPagerLink(
        activity: AppCompatActivity,
        label: String,
        selected: Boolean,
        scaledTextSize: (Float) -> Float,
        typeface: Typeface,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            includeFontPadding = false
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, GAGADGET_LINE_SPACING)
            this.typeface = typeface
            isFocusable = !selected
            isClickable = !selected
            isFocusableInTouchMode = false
            fun applyState(focused: Boolean) {
                if (selected || focused) {
                    setTypeface(typeface, Typeface.BOLD)
                    text = buildStyledText(label, COLOR_GG_HEADLINE)
                } else {
                    setTypeface(typeface, Typeface.NORMAL)
                    text = buildStyledText(label, COLOR_GG_HEADLINE, underline = true)
                }
            }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }
}

