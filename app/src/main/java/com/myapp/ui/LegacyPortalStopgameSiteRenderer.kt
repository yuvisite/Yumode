package com.myapp.ui

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite

internal object LegacyPortalStopgameSiteRenderer {
    private var stopgameLogoBitmap: Bitmap? = null

    internal fun buildLogoView(
        activity: AppCompatActivity,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
    ): View =
        loadLogoBitmap(activity)?.let { bitmap ->
            ImageView(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
        } ?: TextView(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            gravity = Gravity.CENTER
            includeFontPadding = false
            textSize = scaledTextSize(13f)
            setLineSpacing(0f, compactLineSpacing)
            this.typeface = typeface
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
            text = "STOPGAME"
        }

    private fun loadLogoBitmap(activity: AppCompatActivity): Bitmap? {
        stopgameLogoBitmap?.let { return it }
        return runCatching {
            activity.assets.open(STOPGAME_LOGO_ASSET).use { stream ->
                val opts =
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inScaled = false
                    }
                val sourceBitmap = BitmapFactory.decodeStream(stream, null, opts) ?: return null
                scaleLogoBitmapForHeader(sourceBitmap, activity.dp(112).coerceAtLeast(1)).also { scaled ->
                    stopgameLogoBitmap = scaled
                }
            }
        }.getOrNull()
    }

    internal fun buildBrandedHeader(
        activity: AppCompatActivity,
        siteId: String,
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
            }

        val header =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(COLOR_STOPGAME_HEADER)
                setPadding(activity.dp(1), activity.dp(1), activity.dp(1), activity.dp(1))
                gravity = Gravity.CENTER_HORIZONTAL
            }
        header.addView(buildLogo())

        val rssBar =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(COLOR_STOPGAME_RSS_BAR)
                setPadding(activity.dp(2), activity.dp(1), activity.dp(2), activity.dp(2))
            }
        val row =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
        categories.forEach { key ->
            row.addView(
                createStopgameCategoryChip(
                    activity = activity,
                    siteId = siteId,
                    categoryKey = key,
                    label = stopgameCategoryLabel(key),
                    selected = selectedCategory == key,
                    scaledTextSize = scaledTextSize,
                    compactLineSpacing = compactLineSpacing,
                    typeface = typeface,
                    onSelect = { onCategorySelected(key) },
                ),
            )
        }
        rssBar.addView(row)

        wrap.addView(header)
        wrap.addView(rssBar)
        return wrap
    }

    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        site: PortalSite,
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
        container.addView(
            buildBrandedHeader(
                activity = activity,
                siteId = site.id,
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
                gravity = Gravity.CENTER_HORIZONTAL
            }
        val body =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        activity.wapFeedColumnWidthPx(),
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
            }
        bodyShell.addView(body)
        container.addView(bodyShell)

        when {
            state.isLoading ->
                body.addView(
                    metaLine(activity, "loading feed...", COLOR_STOPGAME_META, scaledTextSize, compactLineSpacing, typeface),
                )

            state.error != null ->
                body.addView(
                    metaLine(activity, state.error ?: "", COLOR_ERROR, scaledTextSize, compactLineSpacing, typeface),
                )

            filteredItems.isEmpty() ->
                body.addView(
                    metaLine(activity, "no items", COLOR_STOPGAME_META, scaledTextSize, compactLineSpacing, typeface),
                )

            else -> {
                val pageCount = ((filteredItems.size - 1) / FeedPageSize) + 1
                val visible = filteredItems.drop(pagerState.pageIndex * FeedPageSize).take(FeedPageSize)
                body.addView(
                    metaLine(
                        activity,
                        "page ${pagerState.pageIndex + 1}/$pageCount  1-${visible.size} open",
                        COLOR_STOPGAME_META,
                        scaledTextSize,
                        compactLineSpacing,
                        typeface,
                    ),
                )
                var selectedEntry: View? = null
                visible.forEachIndexed { index, item ->
                    val entry =
                        addStopgameFeedRow(
                            activity = activity,
                            parent = body,
                            siteId = site.id,
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

    private fun stopgameCategoryLabel(key: String): String =
        when (key) {
            "All" -> "\u0412\u0441\u0435"
            "News" -> "\u041d\u043e\u0432\u043e\u0441\u0442\u0438"
            else -> key
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
            gravity = Gravity.CENTER
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            this.typeface = typeface
            setTextColor(color)
            this.text = text
        }

    private fun createStopgameCategoryChip(
        activity: AppCompatActivity,
        siteId: String,
        categoryKey: String,
        label: String,
        selected: Boolean,
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
                ).apply {
                    marginEnd = activity.dp(6)
                }
            includeFontPadding = false
            gravity = Gravity.CENTER
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            this.typeface = typeface
            setPadding(activity.dp(1), activity.dp(0), activity.dp(1), activity.dp(0))
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = categoryFocusTag(siteId, categoryKey)

            fun applyState(focused: Boolean) {
                val active = selected || focused
                setTextColor(Color.WHITE)
                setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
                paint.isUnderlineText = active
            }

            text = label
            setOnClickListener { onSelect() }
            setOnFocusChangeListener { _, hasFocus ->
                applyState(hasFocus)
                // Do not switch feed section on focus (D-pad / navigation).
                // Feed refresh must be confirmed by click/OK.
            }
            applyState(false)
        }

    private fun addStopgameFeedRow(
        activity: AppCompatActivity,
        parent: LinearLayout,
        siteId: String,
        number: Int,
        title: String,
        meta: String?,
        scaledTextSize: (Float) -> Float,
        compactLineSpacing: Float,
        typeface: Typeface,
        onClick: () -> Unit,
        onFocused: () -> Unit,
    ): View =
        LinearLayout(activity).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = activity.dp(0)
                }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(activity.dp(0), activity.dp(1), activity.dp(0), activity.dp(1))
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = "stopgame-feed:$siteId:$number"

            val badge =
                TextView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(activity.dp(16), activity.dp(16))
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    textSize = scaledTextSize(10f)
                    setTypeface(typeface, Typeface.BOLD)
                    text = number.toString()
                }

            val textWrap =
                LinearLayout(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = activity.dp(4)
                        }
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
                    textSize = scaledTextSize(11f)
                    setLineSpacing(0f, compactLineSpacing)
                    this.typeface = typeface
                    text = title
                    setTextColor(COLOR_STOPGAME_TEXT)
                }

            val metaView =
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    includeFontPadding = false
                    textSize = scaledTextSize(10f)
                    setLineSpacing(0f, compactLineSpacing)
                    this.typeface = typeface
                    visibility = if (meta.isNullOrBlank()) View.GONE else View.VISIBLE
                    text = meta.orEmpty()
                    setTextColor(COLOR_STOPGAME_META)
                }

            fun applyRowState(focused: Boolean) {
                setBackgroundColor(if (focused) COLOR_STOPGAME_RSS_BAR else Color.TRANSPARENT)
                titleView.setTextColor(if (focused) Color.WHITE else COLOR_STOPGAME_TEXT)
                titleView.paint.isUnderlineText = focused
                titleView.setTypeface(typeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
                metaView.setTextColor(if (focused) Color.WHITE else COLOR_STOPGAME_META)
                badge.setTextColor(if (focused) COLOR_STOPGAME_RSS_BAR else COLOR_STOPGAME_TEXT)
                badge.background =
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        setColor(if (focused) Color.WHITE else Color.TRANSPARENT)
                        setStroke(
                            activity.dp(1),
                            if (focused) COLOR_STOPGAME_RSS_BAR else COLOR_STOPGAME_TEXT,
                        )
                    }
            }

            addView(badge)
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
