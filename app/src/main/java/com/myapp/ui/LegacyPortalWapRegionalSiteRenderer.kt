package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite
import com.myapp.model.SiteTheme

internal data class WapRegionalPalette(
    val bg: Int,
    val header: Int,
    val headerText: Int,
    val navBar: Int,
    val navMuted: Int,
    val text: Int,
    val muted: Int,
    val focus: Int,
    val rowIndex: Int,
    val articleDivider: Int,
)

private enum class RegionalIndexStyle {
    BRACKET_Serif,
    DOT_MONO,
    BOX_SQUARE,
}

private data class RegionalFeedRowStyle(
    val indexStyle: RegionalIndexStyle,
    val titlePrefix: String,
    val titleTextSp: Float,
    val rowPaddingTopDp: Int,
    val rowPaddingBottomDp: Int,
)

private fun feedRowStyle(theme: SiteTheme): RegionalFeedRowStyle =
    when (theme) {
        SiteTheme.KYIV_VLADA ->
            RegionalFeedRowStyle(
                indexStyle = RegionalIndexStyle.BRACKET_Serif,
                titlePrefix = "> ",
                titleTextSp = 11f,
                rowPaddingTopDp = 2,
                rowPaddingBottomDp = 2,
            )
        SiteTheme.UA_44 ->
            RegionalFeedRowStyle(
                indexStyle = RegionalIndexStyle.DOT_MONO,
                titlePrefix = "- ",
                titleTextSp = 10.5f,
                rowPaddingTopDp = 1,
                rowPaddingBottomDp = 1,
            )
        SiteTheme.VGORODE ->
            RegionalFeedRowStyle(
                indexStyle = RegionalIndexStyle.BOX_SQUARE,
                titlePrefix = "\u00bb ",
                titleTextSp = 11f,
                rowPaddingTopDp = 1,
                rowPaddingBottomDp = 2,
            )
        else ->
            RegionalFeedRowStyle(
                indexStyle = RegionalIndexStyle.DOT_MONO,
                titlePrefix = "\u00bb ",
                titleTextSp = 11f,
                rowPaddingTopDp = 1,
                rowPaddingBottomDp = 1,
            )
    }

internal object LegacyPortalWapRegionalSiteRenderer {
    internal fun paletteFor(theme: SiteTheme): WapRegionalPalette? =
        when (theme) {
            SiteTheme.KYIV_VLADA ->
                WapRegionalPalette(
                    bg = COLOR_KV_BG,
                    header = COLOR_KV_HEADER,
                    headerText = COLOR_KV_HEADER_TEXT,
                    navBar = COLOR_KV_NAV_BAR,
                    navMuted = COLOR_KV_NAV_MUTED,
                    text = COLOR_KV_TEXT,
                    muted = COLOR_KV_MUTED,
                    focus = COLOR_KV_FOCUS,
                    rowIndex = COLOR_KV_ROW_INDEX,
                    articleDivider = Color.parseColor("#C4BDBA"),
                )

            SiteTheme.UA_44 ->
                WapRegionalPalette(
                    bg = COLOR_UA44_BG,
                    header = COLOR_UA44_HEADER,
                    headerText = COLOR_UA44_HEADER_TEXT,
                    navBar = COLOR_UA44_NAV_BAR,
                    navMuted = COLOR_UA44_NAV_MUTED,
                    text = COLOR_UA44_TEXT,
                    muted = COLOR_UA44_MUTED,
                    focus = COLOR_UA44_FOCUS,
                    rowIndex = COLOR_UA44_ROW_INDEX,
                    articleDivider = Color.parseColor("#A5D6A7"),
                )

            SiteTheme.VGORODE ->
                WapRegionalPalette(
                    bg = COLOR_VG_BG,
                    header = COLOR_VG_HEADER,
                    headerText = COLOR_VG_HEADER_TEXT,
                    navBar = COLOR_VG_NAV_BAR,
                    navMuted = COLOR_VG_NAV_MUTED,
                    text = COLOR_VG_TEXT,
                    muted = COLOR_VG_MUTED,
                    focus = COLOR_VG_FOCUS,
                    rowIndex = COLOR_VG_ROW_INDEX,
                    articleDivider = Color.parseColor("#90CAF9"),
                )

            else -> null
        }

    internal fun buildLogoView(
        activity: AppCompatActivity,
        theme: SiteTheme,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
    ): View =
        when (theme) {
            SiteTheme.KYIV_VLADA ->
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    includeFontPadding = false
                    textSize = scaledTextSize(13f)
                    setLineSpacing(0f, compactLineSpacing)
                    setTypeface(Typeface.SERIF, Typeface.BOLD)
                    gravity = Gravity.CENTER_HORIZONTAL
                    val label = "КиевВласть"
                    val span = SpannableString(label)
                    val vIndex = label.indexOf('В')
                    if (vIndex >= 0) {
                        span.setSpan(
                            ForegroundColorSpan(COLOR_KV_ORANGE_V),
                            vIndex,
                            vIndex + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                    text = span
                    setTextColor(COLOR_KV_HEADER_TEXT)
                }

            SiteTheme.UA_44 ->
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    includeFontPadding = false
                    textSize = scaledTextSize(14f)
                    setLineSpacing(0f, compactLineSpacing)
                    this.typeface = typeface
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(COLOR_UA44_HEADER_TEXT)
                    text = "44.ua"
                }

            SiteTheme.VGORODE ->
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    includeFontPadding = false
                    textSize = scaledTextSize(13f)
                    setLineSpacing(0f, compactLineSpacing)
                    this.typeface = typeface
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(COLOR_VG_HEADER_TEXT)
                    text = "В ГОРОДЕ"
                }

            else ->
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    includeFontPadding = false
                    textSize = scaledTextSize(12f)
                    setTextColor(Color.WHITE)
                    text = "feed"
                }
        }

    internal fun buildBrandedHeader(
        activity: AppCompatActivity,
        siteId: String,
        siteTheme: SiteTheme,
        palette: WapRegionalPalette,
        categories: List<String>,
        selectedCategory: String?,
        buildLogo: () -> View,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        onCategorySelected: (String?) -> Unit,
    ): View {
        val wrap =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(palette.bg)
            }

        val header =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(palette.header)
                setPadding(activity.dp(3), activity.dp(2), activity.dp(3), activity.dp(2))
                gravity = Gravity.CENTER_HORIZONTAL
            }
        header.addView(buildLogo())

        val navBar =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(palette.navBar)
                setPadding(activity.dp(2), activity.dp(1), activity.dp(2), activity.dp(2))
            }

        navBar.addView(
            buildFlowCategoryNav(
                activity = activity,
                siteId = siteId,
                siteTheme = siteTheme,
                palette = palette,
                categories = categories,
                selectedCategory = selectedCategory,
                scaledTextSize = scaledTextSize,
                compactLineSpacing = compactLineSpacing,
                typeface = typeface,
                onCategorySelected = onCategorySelected,
            ),
        )

        wrap.addView(header)
        wrap.addView(navBar)
        return wrap
    }

    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        site: PortalSite,
        palette: WapRegionalPalette,
        state: FeedUiState,
        categories: List<String>,
        selectedCategory: String?,
        filteredItems: List<FeedItem>,
        pagerState: FeedPagerState,
        buildLogo: () -> View,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        onCategorySelected: (String?) -> Unit,
        onOpenArticle: (FeedItem) -> Unit,
        onHeadlineFocused: (Int) -> Unit,
    ) {
        container.setBackgroundColor(palette.bg)
        container.addView(
            buildBrandedHeader(
                activity = activity,
                siteId = site.id,
                siteTheme = site.theme,
                palette = palette,
                categories = categories,
                selectedCategory = selectedCategory,
                buildLogo = buildLogo,
                scaledTextSize = scaledTextSize,
                compactLineSpacing = compactLineSpacing,
                typeface = typeface,
                onCategorySelected = onCategorySelected,
            ),
        )

        val bodyShell =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
                setPadding(0, activity.dp(1), 0, activity.dp(2))
                // On phones use left alignment to avoid large side gutters.
                gravity = Gravity.START
            }
        val body =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        activity.wapContentWidthPx(),
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
            }
        bodyShell.addView(body)
        container.addView(bodyShell)

        when {
            state.isLoading ->
                body.addView(metaLine(activity, "loading feed...", palette.muted, scaledTextSize, compactLineSpacing, typeface))

            state.error != null ->
                body.addView(metaLine(activity, state.error ?: "", COLOR_ERROR, scaledTextSize, compactLineSpacing, typeface))

            filteredItems.isEmpty() ->
                body.addView(metaLine(activity, "no items", palette.muted, scaledTextSize, compactLineSpacing, typeface))

            else -> {
                val pageCount = ((filteredItems.size - 1) / FeedPageSize) + 1
                val visible = filteredItems.drop(pagerState.pageIndex * FeedPageSize).take(FeedPageSize)
                body.addView(
                    metaLine(
                        activity,
                        "page ${pagerState.pageIndex + 1}/$pageCount  1-${visible.size} open",
                        palette.muted,
                        scaledTextSize,
                        compactLineSpacing,
                        typeface,
                    ),
                )
                var selectedEntry: View? = null
                visible.forEachIndexed { index, item ->
                    val entry =
                        addFeedRow(
                            activity = activity,
                            parent = body,
                            siteId = site.id,
                            siteTheme = site.theme,
                            palette = palette,
                            number = index + 1,
                            title = item.title,
                            meta = formatRssDisplayDateTime(item.publishedAt),
                            scaledTextSize = scaledTextSize,
                            compactLineSpacing = compactLineSpacing,
                            typeface = typeface,
                            onClick = { onOpenArticle(item) },
                            onFocused = { onHeadlineFocused(index) },
                        )
                    if (index == pagerState.selectedSlot) {
                        selectedEntry = entry
                    }
                }
                selectedEntry?.post { selectedEntry?.requestFocus() }
            }
        }
    }

    private fun buildFlowCategoryNav(
        activity: AppCompatActivity,
        siteId: String,
        siteTheme: SiteTheme,
        palette: WapRegionalPalette,
        categories: List<String>,
        selectedCategory: String?,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        onCategorySelected: (String?) -> Unit,
    ): View {
        val maxRowWidth = (activity.resources.displayMetrics.widthPixels - activity.dp(6)).coerceAtLeast(1)
        val gap = activity.dp(4).toFloat()

        val rows =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
            }

        fun newNavRow(): LinearLayout =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }

        var currentRow = newNavRow()
        var currentWidth = 0f

        fun commitRow() {
            if (currentRow.childCount > 0) {
                rows.addView(currentRow)
                currentRow = newNavRow()
                currentWidth = 0f
            }
        }

        fun addSegment(view: View) {
            view.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            val widthPx = view.measuredWidth
            val spacer = if (currentRow.childCount > 0) gap else 0f
            val need = widthPx.toFloat() + spacer
            if (currentWidth > 0f && currentWidth + need > maxRowWidth) {
                commitRow()
            }
            if (currentRow.childCount > 0) {
                currentRow.addView(
                    View(activity).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(activity.dp(4), ViewGroup.LayoutParams.WRAP_CONTENT)
                    },
                )
                currentWidth += gap
            }
            currentRow.addView(view)
            currentWidth += widthPx.toFloat()
        }

        val allLabel = if (selectedCategory == null) "Все*" else "Все"
        addSegment(
            createCategoryChip(
                activity = activity,
                siteId = siteId,
                categoryKey = "__all__",
                label = allLabel,
                selected = selectedCategory == null,
                palette = palette,
                siteTheme = siteTheme,
                scaledTextSize = scaledTextSize,
                compactLineSpacing = compactLineSpacing,
                typeface = typeface,
                onSelect = { onCategorySelected(null) },
            ),
        )

        categories.forEach { key ->
            addSegment(
                dotSeparator(
                    activity = activity,
                    siteTheme = siteTheme,
                    palette = palette,
                    scaledTextSize = scaledTextSize,
                    compactLineSpacing = compactLineSpacing,
                    typeface = typeface,
                ),
            )
            addSegment(
                createCategoryChip(
                    activity = activity,
                    siteId = siteId,
                    categoryKey = key,
                    label = if (selectedCategory == key) "$key*" else key,
                    selected = selectedCategory == key,
                    palette = palette,
                    siteTheme = siteTheme,
                    scaledTextSize = scaledTextSize,
                    compactLineSpacing = compactLineSpacing,
                    typeface = typeface,
                    onSelect = { onCategorySelected(key) },
                ),
            )
        }

        if (currentRow.childCount > 0) {
            rows.addView(currentRow)
        }

        return rows
    }

    private fun dotSeparator(
        activity: AppCompatActivity,
        siteTheme: SiteTheme,
        palette: WapRegionalPalette,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
    ): TextView =
        TextView(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            includeFontPadding = false
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            this.typeface = typeface
            setTextColor(palette.navMuted)
            text =
                when (siteTheme) {
                    SiteTheme.KYIV_VLADA -> " | "
                    SiteTheme.UA_44 -> " / "
                    else -> " \u00b7 "
                }
        }

    private fun metaLine(
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
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
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

    private fun createCategoryChip(
        activity: AppCompatActivity,
        siteId: String,
        categoryKey: String,
        label: String,
        selected: Boolean,
        palette: WapRegionalPalette,
        siteTheme: SiteTheme,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        onSelect: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            includeFontPadding = false
            textSize =
                scaledTextSize(
                    when (siteTheme) {
                        SiteTheme.UA_44 -> 9.5f
                        SiteTheme.KYIV_VLADA -> 10f
                        else -> 10f
                    },
                )
            setLineSpacing(0f, compactLineSpacing)
            this.typeface = typeface
            setPadding(
                when (siteTheme) {
                    SiteTheme.UA_44 -> activity.dp(2)
                    SiteTheme.VGORODE -> activity.dp(1)
                    else -> activity.dp(1)
                },
                activity.dp(0),
                when (siteTheme) {
                    SiteTheme.UA_44 -> activity.dp(2)
                    else -> activity.dp(1)
                },
                activity.dp(0),
            )
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = if (categoryKey == "__all__") categoryFocusTag(siteId, "All") else categoryFocusTag(siteId, categoryKey)

            fun applyState(focused: Boolean) {
                val active = selected || focused
                setTextColor(if (active) Color.WHITE else palette.navMuted)
                setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
                paint.isUnderlineText = active
            }

            text = label
            setOnClickListener { onSelect() }
            setOnFocusChangeListener { _, hasFocus ->
                applyState(hasFocus)
            }
            applyState(false)
        }

    private fun addFeedRow(
        activity: AppCompatActivity,
        parent: LinearLayout,
        siteId: String,
        siteTheme: SiteTheme,
        palette: WapRegionalPalette,
        number: Int,
        title: String,
        meta: String?,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        onClick: () -> Unit,
        onFocused: () -> Unit,
    ): View {
        val rowStyle = feedRowStyle(siteTheme)
        val indexStyle = rowStyle.indexStyle

        return LinearLayout(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(
                activity.dp(2),
                activity.dp(rowStyle.rowPaddingTopDp),
                activity.dp(0),
                activity.dp(rowStyle.rowPaddingBottomDp),
            )
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = "wap-regional-feed:$siteId:$number"

            val box = activity.dp(20).coerceAtLeast(18)
            val indexPrefix =
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            when (indexStyle) {
                                RegionalIndexStyle.BOX_SQUARE -> box
                                else -> ViewGroup.LayoutParams.WRAP_CONTENT
                            },
                            when (indexStyle) {
                                RegionalIndexStyle.BOX_SQUARE -> box
                                else -> ViewGroup.LayoutParams.WRAP_CONTENT
                            },
                        ).apply {
                            marginEnd = activity.dp(2)
                        }
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setLineSpacing(0f, compactLineSpacing)
                    when (indexStyle) {
                        RegionalIndexStyle.BRACKET_Serif -> {
                            textSize = scaledTextSize(9f)
                            setTypeface(Typeface.SERIF, Typeface.BOLD)
                            text = "[$number]"
                            setTextColor(palette.rowIndex)
                        }
                        RegionalIndexStyle.DOT_MONO -> {
                            textSize = scaledTextSize(10f)
                            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                            text = "$number."
                            setTextColor(palette.rowIndex)
                        }
                        RegionalIndexStyle.BOX_SQUARE -> {
                            textSize = scaledTextSize(9f)
                            setTypeface(typeface, Typeface.BOLD)
                            text = number.toString()
                            val stroke = palette.rowIndex
                            setTextColor(stroke)
                            background =
                                GradientDrawable().apply {
                                    shape = GradientDrawable.RECTANGLE
                                    setStroke(activity.dp(1), stroke)
                                    setColor(Color.TRANSPARENT)
                                }
                        }
                    }
                }

            val textWrap =
                LinearLayout(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    orientation = LinearLayout.VERTICAL
                }

            val titleView =
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    includeFontPadding = false
                    textSize = scaledTextSize(rowStyle.titleTextSp)
                    setLineSpacing(0f, compactLineSpacing)
                    this.typeface = typeface
                    text = "${rowStyle.titlePrefix}$title"
                    setTextColor(palette.text)
                }

            val metaSize =
                when (siteTheme) {
                    SiteTheme.UA_44 -> 9.5f
                    SiteTheme.KYIV_VLADA -> 9.5f
                    else -> 10f
                }
            val metaPrefix =
                when (siteTheme) {
                    SiteTheme.UA_44 -> "~ "
                    SiteTheme.KYIV_VLADA -> ""
                    else -> ""
                }
            val metaView =
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    includeFontPadding = false
                    textSize = scaledTextSize(metaSize)
                    setLineSpacing(0f, compactLineSpacing)
                    this.typeface = typeface
                    visibility = if (meta.isNullOrBlank()) View.GONE else View.VISIBLE
                    text = metaPrefix + meta.orEmpty()
                    setTextColor(palette.muted)
                }

            fun applyIndexPlain(focused: Boolean) {
                indexPrefix.setTextColor(if (focused) Color.WHITE else palette.rowIndex)
                indexPrefix.background = null
            }

            fun applyIndexBox(focused: Boolean) {
                val stroke = if (focused) Color.WHITE else palette.rowIndex
                indexPrefix.setTextColor(if (focused) Color.WHITE else palette.rowIndex)
                indexPrefix.background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setStroke(activity.dp(1), stroke)
                        setColor(if (focused) palette.focus else Color.TRANSPARENT)
                    }
            }

            fun applyRowState(focused: Boolean) {
                setBackgroundColor(if (focused) palette.focus else Color.TRANSPARENT)
                titleView.setTextColor(if (focused) Color.WHITE else palette.text)
                titleView.paint.isUnderlineText = focused
                titleView.setTypeface(typeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
                metaView.setTextColor(if (focused) Color.WHITE else palette.muted)
                when (indexStyle) {
                    RegionalIndexStyle.BOX_SQUARE -> applyIndexBox(focused)
                    else -> applyIndexPlain(focused)
                }
            }

            addView(indexPrefix)
            textWrap.addView(titleView)
            textWrap.addView(metaView)
            addView(textWrap)

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus ->
                applyRowState(hasFocus)
                if (hasFocus) {
                    onFocused()
                }
            }
            applyRowState(false)
            parent.addView(this)
        }
    }
}
