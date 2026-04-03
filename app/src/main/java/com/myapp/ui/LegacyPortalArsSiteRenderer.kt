package com.myapp.ui

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite
import kotlin.math.max

internal object LegacyPortalArsSiteRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        site: PortalSite,
        state: FeedUiState,
        categories: List<String>,
        selectedCategory: String?,
        filteredItems: List<FeedItem>,
        pagerState: FeedPagerState,
        currentSearchQuery: String,
        onRequestSearchQuery: (String) -> Unit,
        onOpenArticle: (FeedItem) -> Unit,
        onHeadlineFocused: (Int) -> Unit,
        onPageSelected: (Int) -> Unit,
        buildArsSearchRow: (String, (String) -> Unit) -> Pair<EditText, LinearLayout>,
        buildArsLogoView: () -> View?,
        buildArsCategoryBar: (String, List<String>, String?) -> View,
        createArsMetaText: (String, Int) -> TextView,
        scaledTextSize: (Float) -> Float,
        lineSpacing: Float,
        typeface: Typeface,
        createArsFooterText: (String) -> TextView,
    ) {
        val contentWrap = LinearLayout(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(4), activity.dp(2), activity.dp(4), activity.dp(4))
        }
        container.addView(contentWrap)

        val (searchField, searchRow) =
            buildArsSearchRow(site.id) { query ->
                onRequestSearchQuery(query)
            }
        var focusTarget: View = searchField

        contentWrap.addView(
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                buildArsLogoView()?.let { logoView ->
                    addView(logoView)
                }

                addView(searchRow)
            },
        )

        contentWrap.addView(
            buildArsCategoryBar(
                site.id,
                categories,
                selectedCategory,
            ),
        )

        when {
            state.isLoading -> contentWrap.addView(createArsMetaText("loading feed...", COLOR_ARS_MUTED))
            state.error != null -> contentWrap.addView(createArsMetaText(state.error ?: "", COLOR_ERROR))
            filteredItems.isEmpty() -> {
                val message =
                    if (currentSearchQuery.isNotBlank()) {
                        "no headlines for this search"
                    } else {
                        "no headlines"
                    }
                contentWrap.addView(createArsMetaText(message, COLOR_ARS_MUTED))
            }

            else -> {
                val pageCount = ((filteredItems.size - 1) / ARS_FEED_PAGE_SIZE) + 1
                val visibleItems =
                    filteredItems.drop(pagerState.pageIndex * ARS_FEED_PAGE_SIZE).take(ARS_FEED_PAGE_SIZE)

                var selectedEntry: TextView? = null
                visibleItems.forEachIndexed { index, item ->
                    val headline =
                        createArsHeadlineEntry(
                            activity = activity,
                            title = item.title,
                            meta = item.publishedAt?.take(16),
                            scaledTextSize = scaledTextSize,
                            lineSpacing = lineSpacing,
                            typeface = typeface,
                            onClick = { onOpenArticle(item) },
                            onFocused = { onHeadlineFocused(index) },
                        )
                    contentWrap.addView(headline)
                    if (index == pagerState.selectedSlot) {
                        selectedEntry = headline
                    }
                }

                if (pageCount > 1) {
                    contentWrap.addView(
                        buildArsPaginationBar(
                            activity = activity,
                            pageCount,
                            pagerState.pageIndex,
                            scaledTextSize = scaledTextSize,
                            lineSpacing = lineSpacing,
                            typeface = typeface,
                            onPageSelected = onPageSelected,
                        ),
                    )
                }

                contentWrap.addView(
                    createArsFooterText(
                        "Ars Technica has been separating the signal from the noise for over 25 years. " +
                            "With our unique combination of technical savvy and wide-ranging interest in the " +
                            "technological arts and sciences, Ars is the trusted source in a sea of information. " +
                            "After all, you don't need to know everything, only what\u2019s important.",
                    ),
                )

                focusTarget = selectedEntry ?: searchField
            }
        }

        focusTarget.post { focusTarget.requestFocus() }
    }

    private fun createArsHeadlineEntry(
        activity: AppCompatActivity,
        title: String,
        meta: String?,
        scaledTextSize: (Float) -> Float,
        lineSpacing: Float,
        typeface: Typeface,
        onClick: () -> Unit,
        onFocused: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(1)
            }
            includeFontPadding = false
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, lineSpacing)
            setPadding(0, 0, 0, 0)
            this.typeface = typeface
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false

            fun applyState(focused: Boolean) {
                val prefix = "$BULLET_PREFIX$title"
                val linkStart = BULLET_PREFIX.length
                text =
                    SpannableString(
                        if (meta.isNullOrBlank()) {
                            prefix
                        } else {
                            "$prefix\n${meta}"
                        },
                    ).apply {
                        if (focused) {
                            setSpan(ForegroundColorSpan(COLOR_ARS_HIGHLIGHT), 0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(UnderlineSpan(), linkStart, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (!meta.isNullOrBlank()) {
                                setSpan(ForegroundColorSpan(COLOR_ARS_TEXT), prefix.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        } else {
                            setSpan(ForegroundColorSpan(COLOR_ARS_TEXT), 0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (!meta.isNullOrBlank()) {
                                setSpan(ForegroundColorSpan(COLOR_ARS_MUTED), prefix.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                    }
                setTypeface(typeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
            }

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus ->
                applyState(hasFocus)
                if (hasFocus) {
                    onFocused()
                }
            }
            applyState(false)
        }

    private fun buildArsPaginationBar(
        activity: AppCompatActivity,
        pageCount: Int,
        selectedPage: Int,
        scaledTextSize: (Float) -> Float,
        lineSpacing: Float,
        typeface: Typeface,
        onPageSelected: (Int) -> Unit,
    ): View =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(4)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL

            addView(
                createArsPagerArrow(
                    activity = activity,
                    label = "\u2039",
                    enabled = selectedPage > 0,
                    scaledTextSize = scaledTextSize,
                    lineSpacing = lineSpacing,
                    typeface = typeface,
                    onClick = { if (selectedPage > 0) onPageSelected(selectedPage - 1) },
                ),
            )

            createArsPageWindow(pageCount, selectedPage).forEach { pageIndex ->
                if (pageIndex == -1) {
                    addView(
                        TextView(activity).apply {
                            includeFontPadding = false
                            text = "\u2026"
                            textSize = scaledTextSize(11f)
                            this.typeface = typeface
                            setTextColor(COLOR_ARS_TEXT)
                            setPadding(activity.dp(3), 0, activity.dp(3), 0)
                        },
                    )
                } else {
                    addView(
                        createArsPagerLink(
                            activity = activity,
                            label = (pageIndex + 1).toString(),
                            selected = pageIndex == selectedPage,
                            scaledTextSize = scaledTextSize,
                            lineSpacing = lineSpacing,
                            typeface = typeface,
                            onClick = { onPageSelected(pageIndex) },
                        ),
                    )
                }
            }

            addView(
                createArsPagerArrow(
                    activity = activity,
                    label = "\u203a",
                    enabled = selectedPage < pageCount - 1,
                    scaledTextSize = scaledTextSize,
                    lineSpacing = lineSpacing,
                    typeface = typeface,
                    onClick = { if (selectedPage < pageCount - 1) onPageSelected(selectedPage + 1) },
                ),
            )
        }

    private fun createArsPagerArrow(
        activity: AppCompatActivity,
        label: String,
        enabled: Boolean,
        scaledTextSize: (Float) -> Float,
        lineSpacing: Float,
        typeface: Typeface,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            includeFontPadding = false
            textSize = scaledTextSize(12f)
            setLineSpacing(0f, lineSpacing)
            setPadding(activity.dp(6), 0, activity.dp(6), 0)
            this.typeface = typeface
            isFocusable = enabled
            isClickable = enabled
            isFocusableInTouchMode = false
            text = if (enabled) buildStyledText(label, COLOR_LINK, underline = true) else buildStyledText(label, COLOR_ARS_MUTED)
            if (enabled) {
                setOnClickListener { onClick() }
            }
        }

    private fun createArsPagerLink(
        activity: AppCompatActivity,
        label: String,
        selected: Boolean,
        scaledTextSize: (Float) -> Float,
        lineSpacing: Float,
        typeface: Typeface,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            includeFontPadding = false
            textSize = scaledTextSize(11f)
            setLineSpacing(0f, lineSpacing)
            setPadding(0, 0, 0, 0)
            this.typeface = typeface
            isFocusable = !selected
            isClickable = !selected
            isFocusableInTouchMode = false
            fun applyState(focused: Boolean) {
                if (selected || focused) {
                    setTypeface(typeface, Typeface.BOLD)
                    text = buildStyledText(label, COLOR_ARS_TEXT)
                } else {
                    setTypeface(typeface, Typeface.NORMAL)
                    text = buildStyledText(label, COLOR_LINK, underline = true)
                }
            }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun createArsPageWindow(
        pageCount: Int,
        selectedPage: Int,
    ): List<Int> {
        if (pageCount <= 6) {
            return (0 until pageCount).toList()
        }
        val window = linkedSetOf(0, pageCount - 1)
        for (offset in -1..1) {
            window += (selectedPage + offset).coerceIn(0, pageCount - 1)
        }
        val sorted = window.toList().sorted()
        val withEllipsis = mutableListOf<Int>()
        sorted.forEachIndexed { index, page ->
            if (index > 0 && page - sorted[index - 1] > 1) {
                withEllipsis += -1
            }
            withEllipsis += page
        }
        return withEllipsis
    }
}

