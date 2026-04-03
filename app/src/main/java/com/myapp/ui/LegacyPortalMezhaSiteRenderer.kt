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

internal object LegacyPortalMezhaSiteRenderer {
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
        createMezhaLogoText: () -> View,
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
        createMezhaMetaText: (String, Int) -> View,
        scaledTextSize: (Float) -> Float,
        typeface: Typeface,
        lineSpacing: Float,
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

        contentWrap.addView(createMezhaLogoText())
        contentWrap.addView(
            buildCategoryFlowBar(
                site.id,
                categories,
                selectedCategory,
                Color.BLACK,
                Color.BLACK,
                COLOR_MEZHA_HEADLINE,
                MEZHA_LINE_SPACING,
                true,
                true,
            ),
        )

        when {
            state.isLoading -> contentWrap.addView(createMezhaMetaText("loading feed...", Color.BLACK))
            state.error != null -> contentWrap.addView(createMezhaMetaText(state.error ?: "", COLOR_ERROR))
            filteredItems.isEmpty() -> contentWrap.addView(createMezhaMetaText("no headlines", Color.BLACK))
            else -> {
                val pageCount = ((filteredItems.size - 1) / FeedPageSize) + 1
                val visibleItems = filteredItems.drop(pagerState.pageIndex * FeedPageSize).take(FeedPageSize)
                var selectedEntry: View? = null
                visibleItems.forEachIndexed { index, item ->
                    val entry =
                        createMezhaFeedEntry(
                            activity = activity,
                            index + 1,
                            item.title,
                            item.publishedAt?.take(16),
                            scaledTextSize = scaledTextSize,
                            typeface = typeface,
                            lineSpacing = lineSpacing,
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
                        buildMezhaPaginationBar(
                            activity = activity,
                            pageCount = pageCount,
                            selectedPage = pagerState.pageIndex,
                            scaledTextSize = scaledTextSize,
                            typeface = typeface,
                            lineSpacing = lineSpacing,
                            onPageSelected = onPageSelected,
                        ),
                    )
                }
                selectedEntry?.post { selectedEntry?.requestFocus() }
            }
        }
    }

    private fun createMezhaFeedEntry(
        activity: AppCompatActivity,
        number: Int,
        title: String,
        meta: String?,
        scaledTextSize: (Float) -> Float,
        typeface: Typeface,
        lineSpacing: Float,
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
                setTextColor(COLOR_MEZHA_HEADLINE)
            }

            val textWrap = LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    leftMargin = activity.dp(2)
                }
                orientation = LinearLayout.VERTICAL
            }

            val titleView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                includeFontPadding = false
                textSize = scaledTextSize(11f)
                setLineSpacing(0f, lineSpacing)
                this.typeface = typeface
                paint.isUnderlineText = true
            }

            val metaView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setLineSpacing(0f, lineSpacing)
                this.typeface = typeface
                visibility = if (meta.isNullOrBlank()) View.GONE else View.VISIBLE
                text = meta.orEmpty()
                setTextColor(Color.BLACK)
            }

            fun applyState(focused: Boolean) {
                badgeView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (focused) COLOR_MEZHA_HEADLINE else Color.TRANSPARENT)
                    setStroke(activity.dp(1), COLOR_MEZHA_HEADLINE)
                }
                badgeView.setTextColor(if (focused) Color.BLACK else COLOR_MEZHA_HEADLINE)
                titleView.text = title
                titleView.setTextColor(COLOR_MEZHA_HEADLINE)
                titleView.setTypeface(typeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
                metaView.setTextColor(Color.BLACK)
            }

            addView(badgeView)
            textWrap.addView(titleView)
            textWrap.addView(metaView)
            addView(textWrap)

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus ->
                applyState(hasFocus)
                if (hasFocus) {
                    onFocused()
                }
            }
            applyState(false)
        }

    private fun buildMezhaPaginationBar(
        activity: AppCompatActivity,
        pageCount: Int,
        selectedPage: Int,
        scaledTextSize: (Float) -> Float,
        typeface: Typeface,
        lineSpacing: Float,
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
                            setTextColor(COLOR_MEZHA_HEADLINE)
                        },
                    )
                }
                addView(
                    createMezhaPagerLink(
                        activity = activity,
                        label = (pageIndex + 1).toString(),
                        selected = pageIndex == selectedPage,
                        scaledTextSize = scaledTextSize,
                        typeface = typeface,
                        lineSpacing = lineSpacing,
                        onClick = { onPageSelected(pageIndex) },
                    ),
                )
            }
        }

    private fun createMezhaPagerLink(
        activity: AppCompatActivity,
        label: String,
        selected: Boolean,
        scaledTextSize: (Float) -> Float,
        typeface: Typeface,
        lineSpacing: Float,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            includeFontPadding = false
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, lineSpacing)
            this.typeface = typeface
            isFocusable = !selected
            isClickable = !selected
            isFocusableInTouchMode = false
            fun applyState(focused: Boolean) {
                if (selected || focused) {
                    setTypeface(typeface, Typeface.BOLD)
                    text = buildStyledText(label, COLOR_MEZHA_HEADLINE)
                } else {
                    setTypeface(typeface, Typeface.NORMAL)
                    text = buildStyledText(label, COLOR_MEZHA_HEADLINE, underline = true)
                }
            }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }
}

