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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.model.FeedItem
import com.myapp.model.PortalSite

internal object LegacyPortalAinSiteRenderer {
    internal val COLOR_AIN_ACCENT: Int = Color.parseColor("#E5447A")
    internal val COLOR_AIN_ACCENT_SOFT: Int = Color.parseColor("#FFF0F5")
    private val COLOR_AIN_ACCENT_WASH: Int = Color.parseColor("#F7B9CB")
    private val COLOR_AIN_TEXT: Int = Color.parseColor("#18181B")
    private val COLOR_AIN_MUTED: Int = Color.parseColor("#86505F")
    private val COLOR_AIN_ROW_FILL: Int = Color.parseColor("#FFF7FA")

    internal fun buildBrandedHeader(
        activity: AppCompatActivity,
        siteId: String,
        categories: List<String>,
        selectedCategory: String?,
        scaledTextSize: (Float) -> Float,
        lineSpacing: Float,
        typeface: Typeface,
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
    ): View =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)

            addView(
                View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        activity.dp(3),
                    )
                    setBackgroundColor(COLOR_AIN_ACCENT)
                },
            )

            addView(
                LinearLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(activity.dp(5), activity.dp(4), activity.dp(5), activity.dp(4))

                    addView(
                        LinearLayout(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL

                            addView(
                                TextView(activity).apply {
                                    includeFontPadding = false
                                    text = SpannableString("AIN").apply {
                                        setSpan(
                                            ForegroundColorSpan(COLOR_AIN_ACCENT),
                                            0,
                                            1,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                        )
                                        setSpan(
                                            ForegroundColorSpan(COLOR_AIN_TEXT),
                                            1,
                                            length,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                        )
                                    }
                                    textSize = scaledTextSize(16f)
                                    setLineSpacing(0f, lineSpacing)
                                    setTypeface(typeface, Typeface.BOLD)
                                },
                            )

                            addView(
                                TextView(activity).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ).apply {
                                        marginStart = activity.dp(4)
                                    }
                                    includeFontPadding = false
                                    text = "ua"
                                    textSize = scaledTextSize(8f)
                                    setLineSpacing(0f, lineSpacing)
                                    setTypeface(typeface, Typeface.BOLD)
                                    setTextColor(Color.WHITE)
                                    setPadding(activity.dp(3), activity.dp(1), activity.dp(3), activity.dp(1))
                                    background = GradientDrawable().apply {
                                        shape = GradientDrawable.RECTANGLE
                                        cornerRadius = activity.dp(8).toFloat()
                                        setColor(COLOR_AIN_ACCENT)
                                    }
                                },
                            )
                        },
                    )

                    addView(
                        TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                topMargin = activity.dp(2)
                            }
                            includeFontPadding = false
                            text = "business  startups  tech"
                            textSize = scaledTextSize(9f)
                            setLineSpacing(0f, lineSpacing)
                            this.typeface = typeface
                            setTextColor(COLOR_AIN_MUTED)
                        },
                    )
                },
            )

            if (categories.isNotEmpty()) {
                addView(
                    LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        orientation = LinearLayout.VERTICAL
                        setPadding(activity.dp(4), activity.dp(3), activity.dp(4), activity.dp(3))
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(COLOR_AIN_ACCENT_SOFT)
                            setStroke(activity.dp(1), COLOR_AIN_ACCENT_WASH)
                        }
                        addView(
                            buildCategoryFlowBar(
                                siteId,
                                categories,
                                selectedCategory,
                                COLOR_AIN_TEXT,
                                COLOR_AIN_ACCENT,
                                COLOR_AIN_ACCENT_SOFT,
                                lineSpacing,
                                true,
                                false,
                            ),
                        )
                    },
                )
            }
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
        scaledTextSize: (Float) -> Float,
        typeface: Typeface,
        lineSpacing: Float,
        onOpenArticle: (FeedItem) -> Unit,
        onHeadlineFocused: (Int) -> Unit,
        onPageSelected: (Int) -> Unit,
    ) {
        container.addView(
            buildBrandedHeader(
                activity = activity,
                siteId = site.id,
                categories = categories,
                selectedCategory = selectedCategory,
                scaledTextSize = scaledTextSize,
                lineSpacing = lineSpacing,
                typeface = typeface,
                buildCategoryFlowBar = buildCategoryFlowBar,
            ),
        )

        val bodyShell = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.VERTICAL
            setPadding(0, activity.dp(2), 0, activity.dp(3))
            gravity = Gravity.START
        }
        val body = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                activity.wapContentWidthPx(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.VERTICAL
        }
        bodyShell.addView(body)
        container.addView(bodyShell)

        when {
            state.isLoading -> body.addView(metaLine(activity, "loading feed...", COLOR_AIN_MUTED, scaledTextSize, lineSpacing, typeface))
            state.error != null -> body.addView(metaLine(activity, state.error, COLOR_ERROR, scaledTextSize, lineSpacing, typeface))
            filteredItems.isEmpty() -> body.addView(metaLine(activity, "no items", COLOR_AIN_MUTED, scaledTextSize, lineSpacing, typeface))
            else -> {
                if (selectedCategory != null) {
                    body.addView(
                        metaLine(
                            activity,
                            "section $selectedCategory",
                            COLOR_AIN_MUTED,
                            scaledTextSize,
                            lineSpacing,
                            typeface,
                        ),
                    )
                }

                val pageCount = ((filteredItems.size - 1) / FeedPageSize) + 1
                val visibleItems = filteredItems.drop(pagerState.pageIndex * FeedPageSize).take(FeedPageSize)
                body.addView(
                    metaLine(
                        activity,
                        "page ${pagerState.pageIndex + 1}/$pageCount  1-${visibleItems.size} open",
                        COLOR_AIN_MUTED,
                        scaledTextSize,
                        lineSpacing,
                        typeface,
                    ),
                )

                var selectedEntry: View? = null
                visibleItems.forEachIndexed { index, item ->
                    val entry = createFeedEntry(
                        activity = activity,
                        number = index + 1,
                        title = item.title,
                        meta = formatRssDisplayDateTime(item.publishedAt),
                        scaledTextSize = scaledTextSize,
                        typeface = typeface,
                        lineSpacing = lineSpacing,
                        onClick = { onOpenArticle(item) },
                        onFocused = { onHeadlineFocused(index) },
                    )
                    body.addView(entry)
                    if (index == pagerState.selectedSlot) {
                        selectedEntry = entry
                    }
                }

                if (pageCount > 1) {
                    body.addView(
                        buildPaginationBar(
                            activity = activity,
                            pageCount = pageCount,
                            selectedPage = pagerState.pageIndex,
                            scaledTextSize = scaledTextSize,
                            lineSpacing = lineSpacing,
                            typeface = typeface,
                            onPageSelected = onPageSelected,
                        ),
                    )
                }

                selectedEntry?.post { selectedEntry?.requestFocus() }
            }
        }
    }

    private fun createFeedEntry(
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
            ).apply {
                topMargin = activity.dp(1)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(activity.dp(3), activity.dp(2), activity.dp(3), activity.dp(2))
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = false

            val badgeView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(18), activity.dp(18))
                gravity = Gravity.CENTER
                includeFontPadding = false
                text = number.toString()
                textSize = scaledTextSize(10f)
                setTypeface(typeface, Typeface.BOLD)
            }

            val textWrap = LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    leftMargin = activity.dp(5)
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
                text = title
            }

            val metaView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = activity.dp(1)
                }
                includeFontPadding = false
                textSize = scaledTextSize(10f)
                setLineSpacing(0f, lineSpacing)
                this.typeface = typeface
                visibility = if (meta.isNullOrBlank()) View.GONE else View.VISIBLE
                text = meta.orEmpty()
            }

            fun applyState(focused: Boolean) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (focused) COLOR_AIN_ACCENT else COLOR_AIN_ROW_FILL)
                    setStroke(activity.dp(1), if (focused) COLOR_AIN_ACCENT else COLOR_AIN_ACCENT_WASH)
                }
                badgeView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (focused) Color.WHITE else COLOR_AIN_ACCENT_SOFT)
                    setStroke(activity.dp(1), COLOR_AIN_ACCENT)
                }
                badgeView.setTextColor(if (focused) COLOR_AIN_ACCENT else COLOR_AIN_TEXT)
                titleView.setTextColor(if (focused) Color.WHITE else COLOR_AIN_TEXT)
                titleView.setTypeface(typeface, if (focused) Typeface.BOLD else Typeface.NORMAL)
                metaView.setTextColor(if (focused) Color.WHITE else COLOR_AIN_MUTED)
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

    private fun buildPaginationBar(
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
            for (pageIndex in 0 until pageCount) {
                if (pageIndex > 0) {
                    addView(
                        TextView(activity).apply {
                            includeFontPadding = false
                            text = " "
                            textSize = scaledTextSize(11f)
                            setLineSpacing(0f, lineSpacing)
                            this.typeface = typeface
                            setTextColor(COLOR_AIN_MUTED)
                        },
                    )
                }
                addView(
                    createPagerLink(
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

    private fun createPagerLink(
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
            this.typeface = typeface
            isFocusable = !selected
            isClickable = !selected
            isFocusableInTouchMode = false

            fun applyState(focused: Boolean) {
                val active = selected || focused
                setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
                text = buildStyledText(label, if (active) COLOR_AIN_ACCENT else COLOR_AIN_MUTED, underline = !active)
                setBackgroundColor(if (focused) COLOR_AIN_ACCENT_SOFT else Color.TRANSPARENT)
            }

            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
            applyState(false)
        }

    private fun metaLine(
        activity: AppCompatActivity,
        text: String,
        color: Int,
        scaledTextSize: (Float) -> Float,
        lineSpacing: Float,
        typeface: Typeface,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = activity.dp(1)
            }
            includeFontPadding = false
            this.text = text
            textSize = scaledTextSize(10f)
            setLineSpacing(0f, lineSpacing)
            setPadding(activity.dp(2), 0, activity.dp(2), 0)
            this.typeface = typeface
            setTextColor(color)
        }
}
