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

internal object LegacyPortalPlaygroundSiteRenderer {
    private val colorPlaygroundNavMuted: Int = Color.parseColor("#FFCDD2")
    private var playgroundLogoBitmap: Bitmap? = null

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
            includeFontPadding = false
            textSize = scaledTextSize(14f)
            setLineSpacing(0f, compactLineSpacing)
            this.typeface = typeface
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            text = "PLAYGROUND"
        }

    private fun loadLogoBitmap(activity: AppCompatActivity): Bitmap? {
        playgroundLogoBitmap?.let { return it }
        return runCatching {
            activity.assets.open(PLAYGROUND_LOGO_ASSET).use { stream ->
                val opts =
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inScaled = false
                    }
                val sourceBitmap = BitmapFactory.decodeStream(stream, null, opts) ?: return null
                scaleLogoBitmapForHeader(sourceBitmap, activity.dp(55).coerceAtLeast(1)).also { scaled ->
                    playgroundLogoBitmap = scaled
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
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(COLOR_PLAYGROUND_LOGO_BAND)
                setPadding(activity.dp(2), activity.dp(1), activity.dp(2), activity.dp(1))
                gravity = Gravity.BOTTOM
            }
        header.addView(buildLogo())
        header.addView(
            TextView(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginStart = activity.dp(8)
                    }
                includeFontPadding = false
                textSize = scaledTextSize(8f)
                setLineSpacing(0f, compactLineSpacing)
                this.typeface = typeface
                gravity = Gravity.START or Gravity.BOTTOM
                setTextColor(Color.WHITE)
                text =
                    "\u041a\u043e\u043c\u043f\u044c\u044e\u0442\u0435\u0440\u043d\u044b\u0435 \u0438\u0433\u0440\u044b, " +
                    "\u043f\u0430\u0442\u0447\u0438, \u043c\u043e\u0434\u044b"
            },
        )

        val navBar =
            LinearLayout(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(COLOR_PLAYGROUND_NAV_BAR)
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
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
        categories.forEachIndexed { index, key ->
            if (index > 0) {
                row.addView(
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
                        setTextColor(colorPlaygroundNavMuted)
                        text = " \u00b7 "
                    },
                )
            }
            row.addView(
                createPlaygroundCategoryChip(
                    activity = activity,
                    siteId = siteId,
                    categoryKey = key,
                    label = playgroundCategoryLabel(key),
                    selected = selectedCategory == key,
                    scaledTextSize = scaledTextSize,
                    compactLineSpacing = compactLineSpacing,
                    typeface = typeface,
                    onSelect = { onCategorySelected(key) },
                ),
            )
        }
        navBar.addView(row)

        wrap.addView(header)
        wrap.addView(navBar)
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
                // Left align after reducing side gutters.
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
                body.addView(
                    metaLine(activity, "loading feed...", COLOR_PLAYGROUND_MUTED, scaledTextSize, compactLineSpacing, typeface),
                )

            state.error != null ->
                body.addView(
                    metaLine(activity, state.error ?: "", COLOR_ERROR, scaledTextSize, compactLineSpacing, typeface),
                )

            filteredItems.isEmpty() ->
                body.addView(
                    metaLine(activity, "no items", COLOR_PLAYGROUND_MUTED, scaledTextSize, compactLineSpacing, typeface),
                )

            else -> {
                val pageCount = ((filteredItems.size - 1) / FeedPageSize) + 1
                val visible = filteredItems.drop(pagerState.pageIndex * FeedPageSize).take(FeedPageSize)
                body.addView(
                    metaLine(
                        activity,
                        "page ${pagerState.pageIndex + 1}/$pageCount  1-${visible.size} open",
                        COLOR_PLAYGROUND_MUTED,
                        scaledTextSize,
                        compactLineSpacing,
                        typeface,
                    ),
                )
                var selectedEntry: View? = null
                visible.forEachIndexed { index, item ->
                    val entry =
                        addPlaygroundFeedRow(
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

    private fun playgroundCategoryLabel(key: String): String =
        when (key) {
            "News" -> "\u041d\u043e\u0432\u043e\u0441\u0442\u0438"
            "Articles" -> "\u0421\u0442\u0430\u0442\u044c\u0438"
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
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, compactLineSpacing)
            this.typeface = typeface
            setTextColor(color)
            this.text = text
        }

    private fun createPlaygroundCategoryChip(
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
                setTextColor(if (active) Color.WHITE else colorPlaygroundNavMuted)
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

    private fun addPlaygroundFeedRow(
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
            setPadding(activity.dp(2), activity.dp(1), activity.dp(0), activity.dp(1))
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false
            tag = "playground-feed:$siteId:$number"

            val indexPrefix =
                TextView(activity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            marginEnd = activity.dp(2)
                        }
                    includeFontPadding = false
                    textSize = scaledTextSize(10f)
                    setLineSpacing(0f, compactLineSpacing)
                    setTypeface(typeface, Typeface.BOLD)
                    text = "$number)"
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
                    textSize = scaledTextSize(11f)
                    setLineSpacing(0f, compactLineSpacing)
                    this.typeface = typeface
                    text = "\u00bb $title"
                    setTextColor(COLOR_PLAYGROUND_TEXT)
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
                    setTextColor(COLOR_PLAYGROUND_MUTED)
                }

            fun applyRowState(focused: Boolean) {
                setBackgroundColor(if (focused) COLOR_PLAYGROUND_FOCUS else Color.TRANSPARENT)
                titleView.setTextColor(if (focused) Color.WHITE else COLOR_PLAYGROUND_TEXT)
                titleView.paint.isUnderlineText = focused
                titleView.setTypeface(typeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
                metaView.setTextColor(if (focused) Color.WHITE else COLOR_PLAYGROUND_MUTED)
                indexPrefix.setTextColor(if (focused) Color.WHITE else COLOR_PLAYGROUND_NAV_BAR)
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
