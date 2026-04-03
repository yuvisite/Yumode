package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

internal object LegacyPortalMenuRenderer {
    private const val MENU_UI_SCALE: Float = 1.20f

    private fun menuTextSize(baseSp: Float, scaledTextSize: (Float) -> Float): Float =
        scaledTextSize(baseSp * MENU_UI_SCALE).coerceAtMost(baseSp + 1.5f)

    internal fun render(
        activity: AppCompatActivity,
        container: FrameLayout,
        entries: List<String>,
        enabled: List<Boolean>,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        initialFocusIndex: Int,
        onSelect: (Int) -> Unit,
        onItemFocused: (Int) -> Unit,
        requestInitialFocus: (android.view.View) -> Unit,
        onScrollViewCreated: (ScrollView) -> Unit,
    ) {
        val backdrop =
            FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.WHITE)
                isClickable = true
                isFocusable = false
            }

        val panel =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.TOP,
                )
                background =
                    GradientDrawable().apply {
                        setColor(Color.WHITE)
                        setStroke(activity.dp(1), Color.parseColor("#A4A4A4"))
                    }
            }

        panel.addView(
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, activity.dp(1), 0, activity.dp(1))
                text = "Menu"
                textSize = menuTextSize(13.8f, scaledTextSize)
                setLineSpacing(0f, 1f)
                typeface = portalTypeface
                setTextColor(Color.BLACK)
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.parseColor("#ECECEC"), Color.parseColor("#D0D0D0")),
                    ).apply {
                        setStroke(activity.dp(1), Color.parseColor("#B6B6B6"))
                    }
            },
        )

        val listScroll =
            ScrollView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
                isFillViewport = true
                isFocusable = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setBackgroundColor(Color.WHITE)
            }

        val listWrap =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = 0
                    rightMargin = 0
                    topMargin = 0
                    bottomMargin = 0
                }
                background =
                    GradientDrawable().apply {
                        setColor(Color.WHITE)
                    }
            }

        entries.forEachIndexed { index, title ->
            val isEnabled = enabled.getOrNull(index) ?: true
            val shortcut =
                when (index) {
                    in 0..8 -> "${index + 1}"
                    9 -> "0"
                    10 -> "*"
                    else -> "-"
                }
            val row =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        bottomMargin = activity.dp(0)
                    }
                    minimumHeight = activity.dp(22)
                    gravity = Gravity.CENTER_VERTICAL
                    isFocusable = true
                    isClickable = isEnabled
                    isFocusableInTouchMode = false
                    tag = "overlay-menu-item:$index"
                    setPadding(activity.dp(3), activity.dp(1), activity.dp(3), activity.dp(1))
                    background =
                        GradientDrawable().apply {
                            setColor(Color.WHITE)
                        }

                    val badgeSize = activity.dp(18)
                    val badge =
                        TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                                rightMargin = activity.dp(4)
                            }
                            gravity = Gravity.CENTER
                            textAlignment = View.TEXT_ALIGNMENT_CENTER
                            includeFontPadding = false
                            minWidth = badgeSize
                            minHeight = badgeSize
                            text = shortcut
                            textSize = menuTextSize(9.5f, scaledTextSize)
                            setLineSpacing(0f, 1f)
                            typeface = portalTypeface
                            setTextColor(if (isEnabled) Color.parseColor("#2B2B2B") else Color.parseColor("#8A8A8A"))
                            background =
                                GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    intArrayOf(Color.parseColor("#F0F0F0"), Color.parseColor("#CFCFCF")),
                                ).apply {
                                    setStroke(activity.dp(1), Color.parseColor("#9A9A9A"))
                                }
                        }

                    val label =
                        TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            gravity = Gravity.CENTER_VERTICAL
                            includeFontPadding = false
                            text = title
                            textSize = menuTextSize(13.2f, scaledTextSize)
                            setLineSpacing(0f, 1f)
                            typeface = portalTypeface
                            setTextColor(if (isEnabled) Color.BLACK else Color.parseColor("#777777"))
                            isSingleLine = true
                        }

                    addView(badge)
                    addView(label)

                    fun applyState(focused: Boolean) {
                        if (focused && isEnabled) {
                            label.setTextColor(Color.WHITE)
                            background =
                                GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    intArrayOf(Color.parseColor("#333333"), Color.parseColor("#252525")),
                                )
                            badge.setTextColor(Color.WHITE)
                            badge.background =
                                GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    intArrayOf(Color.parseColor("#9C9C9C"), Color.parseColor("#6F6F6F")),
                                ).apply {
                                    setStroke(activity.dp(1), Color.parseColor("#4A4A4A"))
                                }
                        } else {
                            background =
                                GradientDrawable().apply {
                                    setColor(Color.WHITE)
                                }
                            label.setTextColor(if (isEnabled) Color.BLACK else Color.parseColor("#777777"))
                            badge.setTextColor(if (isEnabled) Color.parseColor("#2B2B2B") else Color.parseColor("#8A8A8A"))
                            badge.background =
                                GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    intArrayOf(Color.parseColor("#F0F0F0"), Color.parseColor("#CFCFCF")),
                                ).apply {
                                    setStroke(activity.dp(1), Color.parseColor("#9A9A9A"))
                                }
                        }
                    }

                    setOnClickListener { if (isEnabled) onSelect(index) }
                    setOnFocusChangeListener { _, hasFocus ->
                        applyState(hasFocus)
                        if (hasFocus) {
                            onItemFocused(index)
                        }
                    }
                    applyState(false)
                }
            listWrap.addView(row)
            if (index == initialFocusIndex.coerceAtLeast(0)) {
                row.post { requestInitialFocus(row) }
            }
        }

        listScroll.addView(listWrap)
        onScrollViewCreated(listScroll)
        panel.addView(listScroll)
        backdrop.addView(panel)
        container.addView(backdrop)
    }
}
