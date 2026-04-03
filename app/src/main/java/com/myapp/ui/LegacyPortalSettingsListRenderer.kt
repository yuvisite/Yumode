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

internal data class SettingsListItem(
    val title: String,
    val subtitle: String? = null,
    val onClick: (() -> Unit)? = null,
)

internal data class SettingsListSection(
    val title: String,
    val items: List<SettingsListItem>,
)

internal enum class SettingsListVisualStyle {
    REGULAR,
    MENU_DENSE,
}

internal object LegacyPortalSettingsListRenderer {
    private const val MENU_DENSE_TEXT_SCALE: Float = 0.90f
    private const val MENU_HEADER_TEXT_SCALE: Float = 0.98f
    private val COLOR_DENSE_PANEL_BG: Int = Color.parseColor("#EFEFEF")
    private val COLOR_DENSE_PANEL_STROKE: Int = Color.parseColor("#B6B6B6")
    private val COLOR_DENSE_ROW_BG: Int = Color.parseColor("#FFFFFF")
    private val COLOR_DENSE_ROW_DIVIDER: Int = Color.parseColor("#D5D5D5")
    private val COLOR_DENSE_FOCUS_BG: Int = Color.parseColor("#2F2F2F")
    private val COLOR_DENSE_FOCUS_BG_BOTTOM: Int = Color.parseColor("#232323")

    private fun denseTextSize(baseSp: Float, scaledTextSize: (Float) -> Float): Float =
        scaledTextSize(baseSp * MENU_DENSE_TEXT_SCALE).coerceAtMost(baseSp + 1.5f)

    private fun menuHeaderTextSize(baseSp: Float, scaledTextSize: (Float) -> Float): Float =
        scaledTextSize(baseSp * MENU_HEADER_TEXT_SCALE).coerceAtMost(baseSp + 1.5f)

    internal fun render(
        activity: AppCompatActivity,
        container: LinearLayout,
        sections: List<SettingsListSection>,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        uiScale: Float = 1f,
        visualStyle: SettingsListVisualStyle = SettingsListVisualStyle.REGULAR,
        denseScale: Float = 1f,
        showRowDividers: Boolean = false,
    ) {
        if (sections.isEmpty()) return
        val contentParent =
            if (visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                buildDensePanel(activity).also { panel ->
                    container.addView(panel)
                }.getChildAt(1) as LinearLayout
            } else {
                container
            }
        var focusOrder = 0
        sections.forEach { section ->
            contentParent.addView(
                buildSectionHeader(
                    activity = activity,
                    text = section.title,
                    typeface = portalTypeface,
                    compactLineSpacing = compactLineSpacing,
                    scaledTextSize = scaledTextSize,
                    uiScale = uiScale,
                    visualStyle = visualStyle,
                    denseScale = denseScale,
                ),
            )
            section.items.forEach { item ->
                contentParent.addView(
                    buildRow(
                        activity = activity,
                        item = item,
                        portalTypeface = portalTypeface,
                        compactLineSpacing = compactLineSpacing,
                        scaledTextSize = scaledTextSize,
                        uiScale = uiScale,
                        focusTag = "${CATEGORY_FOCUS_TAG_PREFIX}settings-list:$focusOrder",
                        visualStyle = visualStyle,
                        denseScale = denseScale,
                        showDivider = showRowDividers,
                    ),
                )
                focusOrder += 1
            }
        }
        // Ensure deterministic initial focus so first DPAD press doesn't wrap-jump.
        container.post { requestFirstFocusableDescendant(container) }
    }

    private fun buildDensePanel(activity: AppCompatActivity): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            background =
                GradientDrawable().apply {
                    setColor(COLOR_DENSE_PANEL_BG)
                    setStroke(activity.dp(1), COLOR_DENSE_PANEL_STROKE)
                }

            addView(
                View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                    )
                },
            )

            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        leftMargin = activity.dp(1)
                        rightMargin = activity.dp(1)
                        topMargin = activity.dp(1)
                        bottomMargin = activity.dp(1)
                    }
                },
            )
        }

    private fun buildSectionHeader(
        activity: AppCompatActivity,
        text: String,
        typeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        uiScale: Float,
        visualStyle: SettingsListVisualStyle,
        denseScale: Float,
    ): View =
        TextView(activity).apply {
            layoutParams =
                if (visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                } else {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = activity.dp(1)
                    }
                }
            if (visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                setPadding(0, activity.dp(1), 0, activity.dp(1))
                includeFontPadding = false
            } else {
                // Centered header should not be visually shifted by horizontal padding.
                setPadding(0, activity.dp((3 * uiScale).toInt().coerceAtLeast(3)), 0, activity.dp((3 * uiScale).toInt().coerceAtLeast(3)))
                includeFontPadding = true
            }
            gravity = Gravity.CENTER
            this.text = text
            textSize =
                if (visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                    menuHeaderTextSize(11.5f * denseScale, scaledTextSize)
                } else {
                    scaledTextSize(11f * uiScale)
                }
            setLineSpacing(0f, 1f)
            setTypeface(
                typeface,
                if (visualStyle == SettingsListVisualStyle.MENU_DENSE) Typeface.NORMAL else Typeface.BOLD,
            )
            setTextColor(if (visualStyle == SettingsListVisualStyle.MENU_DENSE) Color.BLACK else Color.WHITE)
            isFocusable = false
            isClickable = false
            isFocusableInTouchMode = false
            background =
                if (visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.parseColor("#ECECEC"), Color.parseColor("#D0D0D0")),
                    ).apply {
                        setStroke(activity.dp(1), COLOR_DENSE_PANEL_STROKE)
                    }
                } else {
                    GradientDrawable().apply {
                        setColor(Color.parseColor("#5C5C5C"))
                        setStroke(activity.dp(1), Color.parseColor("#3C3C3C"))
                    }
                }
        }

    private fun buildRow(
        activity: AppCompatActivity,
        item: SettingsListItem,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        uiScale: Float,
        focusTag: String,
        visualStyle: SettingsListVisualStyle,
        denseScale: Float,
        showDivider: Boolean,
    ): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val hasSubtitle = !item.subtitle.isNullOrBlank()
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            minimumHeight =
                when (visualStyle) {
                    SettingsListVisualStyle.MENU_DENSE -> activity.dp(if (hasSubtitle) (24 * denseScale).toInt().coerceAtLeast(20) else (20 * denseScale).toInt().coerceAtLeast(18))
                    SettingsListVisualStyle.REGULAR -> activity.dp((30 * uiScale).toInt().coerceAtLeast(30))
                }
            if (visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                val horizontalPadding = activity.dp((2 * denseScale).toInt().coerceAtLeast(2))
                setPadding(horizontalPadding, 0, horizontalPadding, 0)
            } else {
                setPadding(
                    activity.dp((8 * uiScale).toInt().coerceAtLeast(8)),
                    activity.dp((5 * uiScale).toInt().coerceAtLeast(5)),
                    activity.dp((8 * uiScale).toInt().coerceAtLeast(8)),
                    activity.dp((5 * uiScale).toInt().coerceAtLeast(5)),
                )
            }
            tag = focusTag
            isFocusable = true
            isClickable = item.onClick != null
            isFocusableInTouchMode = false
            background =
                if (visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                    GradientDrawable().apply {
                        setColor(COLOR_DENSE_ROW_BG)
                    }
                } else {
                    null
                }
            setOnClickListener { item.onClick?.invoke() }

            val titleView =
                TextView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    includeFontPadding = visualStyle != SettingsListVisualStyle.MENU_DENSE
                    text = item.title
                    textSize =
                        if (visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                            denseTextSize(10.5f * denseScale, scaledTextSize)
                        } else {
                            scaledTextSize(12f * uiScale)
                        }
                    setLineSpacing(0f, 1f)
                    typeface = portalTypeface
                    setTextColor(Color.BLACK)
                    maxLines = if (visualStyle == SettingsListVisualStyle.MENU_DENSE && !hasSubtitle) 1 else 2
                }
            addView(
                titleView,
            )

            var subtitleView: TextView? = null
            item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                subtitleView =
                    TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = 0
                        }
                        includeFontPadding = visualStyle != SettingsListVisualStyle.MENU_DENSE
                        text = subtitle
                        textSize =
                            if (visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                                denseTextSize(10.5f * denseScale, scaledTextSize)
                            } else {
                                scaledTextSize(10f * uiScale)
                            }
                        setLineSpacing(0f, 1f)
                        typeface = portalTypeface
                        setTextColor(if (visualStyle == SettingsListVisualStyle.MENU_DENSE) Color.BLACK else Color.parseColor("#555555"))
                        maxLines = if (visualStyle == SettingsListVisualStyle.MENU_DENSE) 1 else 2
                    }
                addView(subtitleView)
            }

            val dividerView =
                if (showDivider && visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                    View(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            activity.dp(1),
                        ).apply {
                            topMargin = activity.dp(1)
                        }
                        setBackgroundColor(COLOR_DENSE_ROW_DIVIDER)
                    }.also { addView(it) }
                } else {
                    null
                }

            fun applyFocus(hasFocus: Boolean) {
                if (hasFocus) {
                    background =
                        GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(COLOR_DENSE_FOCUS_BG, COLOR_DENSE_FOCUS_BG_BOTTOM),
                        )
                    titleView.setTextColor(Color.WHITE)
                    subtitleView?.setTextColor(Color.WHITE)
                    dividerView?.setBackgroundColor(Color.parseColor("#5A5A5A"))
                } else {
                    background =
                        if (visualStyle == SettingsListVisualStyle.MENU_DENSE) {
                            GradientDrawable().apply {
                                setColor(COLOR_DENSE_ROW_BG)
                            }
                        } else {
                            null
                        }
                    titleView.setTextColor(Color.BLACK)
                    subtitleView?.setTextColor(if (visualStyle == SettingsListVisualStyle.MENU_DENSE) Color.BLACK else Color.parseColor("#555555"))
                    dividerView?.setBackgroundColor(COLOR_DENSE_ROW_DIVIDER)
                }
            }

            setOnFocusChangeListener { _, hasFocus -> applyFocus(hasFocus) }
            applyFocus(false)
        }
}
