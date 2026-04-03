package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

internal object LegacyPortalExitRenderer {
    internal fun renderQuitPrompt(
        activity: AppCompatActivity,
        container: FrameLayout,
        appLabel: CharSequence,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        selectYes: Boolean,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        container.addView(
            View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.parseColor("#40FFFFFF"))
                isClickable = true
            },
        )

        val shadowWrap =
            FrameLayout(activity).apply {
                val w = dialogWidth(activity, preferredWidthDp = 264, horizontalMarginDp = 4)
                val h = activity.dp(360)
                val shadow = activity.dp(6)
                layoutParams = FrameLayout.LayoutParams(
                    w + shadow,
                    h + shadow,
                ).apply { gravity = Gravity.CENTER }
            }

        shadowWrap.addView(
            View(activity).apply {
                val w = dialogWidth(activity, preferredWidthDp = 264, horizontalMarginDp = 4)
                val h = activity.dp(360)
                val shadow = activity.dp(6)
                layoutParams = FrameLayout.LayoutParams(w, h).apply {
                    leftMargin = shadow
                    topMargin = shadow
                }
                // Solid classic shadow block (bottom-right).
                setBackgroundColor(Color.BLACK)
            },
        )

        val dialog =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    FrameLayout.LayoutParams(
                        dialogWidth(activity, preferredWidthDp = 264, horizontalMarginDp = 4),
                        activity.dp(360),
                    ).apply { gravity = Gravity.CENTER }
                background = createPanelDrawable(activity, Color.WHITE, Color.parseColor("#222222"))
                setPadding(0, 0, 0, activity.dp(6))
            }

        dialog.addView(
            buildHeader(
                activity = activity,
                appLabel = appLabel,
                portalTypeface = portalTypeface,
                compactLineSpacing = compactLineSpacing,
                scaledTextSize = scaledTextSize,
                badgeText = "!",
                badgeBackground = Color.parseColor("#E9BE2B"),
                badgeTextColor = Color.parseColor("#5B4200"),
                // Old OS style red bar: mostly solid red, only a thin lighter band at the very top.
                headerStart = Color.parseColor("#E23A3F"), // slightly lighter red
                headerEnd = Color.parseColor("#C7181B"),   // solid deep red for most of the bar
                headerTextColor = Color.WHITE,
            ),
        )

        dialog.addView(
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = activity.dp(60)
                    bottomMargin = activity.dp(76)
                }
                gravity = Gravity.CENTER
                includeFontPadding = false
                text = "Quit software?"
                isSingleLine = true
                textSize = scaledTextSize(16f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                setTextColor(Color.BLACK)
            },
        )

        // Flexible spacer so buttons stay visible at the bottom
        // even when we increase whitespace for i-mode style.
        dialog.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            },
        )

        val yesButton =
            createChoiceButton(
                activity = activity,
                label = "YES",
                portalTypeface = portalTypeface,
                compactLineSpacing = compactLineSpacing,
                scaledTextSize = scaledTextSize,
                focusedBackground = intArrayOf(Color.parseColor("#D8E1FF"), Color.parseColor("#9FB3FF")),
                normalBackground = intArrayOf(Color.parseColor("#F8F8F8"), Color.parseColor("#E7E7E7")),
                selected = selectYes,
                bottomMarginDp = 4,
                onClick = onConfirm,
            )

        val noButton =
            createChoiceButton(
                activity = activity,
                label = "NO",
                portalTypeface = portalTypeface,
                compactLineSpacing = compactLineSpacing,
                scaledTextSize = scaledTextSize,
                focusedBackground = intArrayOf(Color.parseColor("#D8E1FF"), Color.parseColor("#9FB3FF")),
                normalBackground = intArrayOf(Color.parseColor("#F8F8F8"), Color.parseColor("#E7E7E7")),
                selected = !selectYes,
                bottomMarginDp = 8,
                onClick = onCancel,
            )

        dialog.addView(yesButton)
        dialog.addView(noButton)
        shadowWrap.addView(dialog)
        container.addView(shadowWrap)
    }

    internal fun renderEnded(
        activity: AppCompatActivity,
        container: FrameLayout,
        appLabel: CharSequence,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
    ) {
        container.addView(
            View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.parseColor("#40FFFFFF"))
                isClickable = true
            },
        )

        val shadowWrap =
            FrameLayout(activity).apply {
                val w = dialogWidth(activity, preferredWidthDp = 264, horizontalMarginDp = 4)
                val h = activity.dp(360)
                val shadow = activity.dp(6)
                layoutParams = FrameLayout.LayoutParams(
                    w + shadow,
                    h + shadow,
                ).apply { gravity = Gravity.CENTER }
            }

        shadowWrap.addView(
            View(activity).apply {
                val w = dialogWidth(activity, preferredWidthDp = 264, horizontalMarginDp = 4)
                val h = activity.dp(360)
                val shadow = activity.dp(6)
                layoutParams = FrameLayout.LayoutParams(w, h).apply {
                    leftMargin = shadow
                    topMargin = shadow
                }
                setBackgroundColor(Color.BLACK)
            },
        )

        val panel =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    FrameLayout.LayoutParams(
                        dialogWidth(activity, preferredWidthDp = 264, horizontalMarginDp = 4),
                        activity.dp(360),
                    ).apply { gravity = Gravity.CENTER }
                background = createPanelDrawable(activity, Color.WHITE, Color.parseColor("#222222"))
                setPadding(0, 0, 0, activity.dp(6))
            }

        panel.addView(
            buildHeader(
                activity = activity,
                appLabel = appLabel,
                portalTypeface = portalTypeface,
                compactLineSpacing = compactLineSpacing,
                scaledTextSize = scaledTextSize,
                badgeText = "i",
                badgeBackground = Color.parseColor("#F7F7F7"),
                badgeTextColor = Color.parseColor("#5C74B8"),
                headerStart = Color.parseColor("#F5F5F5"),
                headerEnd = Color.parseColor("#D9D9D9"),
                headerTextColor = Color.BLACK,
            ),
        )

        panel.addView(
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = activity.dp(60)
                    bottomMargin = activity.dp(76)
                }
                gravity = Gravity.CENTER
                includeFontPadding = false
                text = "Ended"
                textSize = scaledTextSize(17f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                setTextColor(Color.BLACK)
            },
        )

        // Flexible spacer keeps the ended panel geometry stable without pushing content off-screen.
        panel.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            },
        )

        shadowWrap.addView(panel)
        container.addView(shadowWrap)
    }

    internal fun buildHeader(
        activity: AppCompatActivity,
        appLabel: CharSequence,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        badgeText: String,
        badgeBackground: Int,
        badgeTextColor: Int,
        headerStart: Int,
        headerEnd: Int,
        headerTextColor: Int,
    ): View =
        FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                activity.dp(33),
            )
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(headerStart, headerEnd),
                ).apply {
                    cornerRadii =
                        floatArrayOf(
                            activity.dp(4).toFloat(), activity.dp(4).toFloat(),
                            activity.dp(4).toFloat(), activity.dp(4).toFloat(),
                            0f, 0f,
                            0f, 0f,
                        )
                    setStroke(activity.dp(1), Color.parseColor("#8A8A8A"))
                }

            addView(
                TextView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    text = appLabel.toString()
                    textSize = scaledTextSize(18f)
                    setLineSpacing(0f, compactLineSpacing)
                    typeface = portalTypeface
                    setTextColor(
                        if (headerTextColor == Color.WHITE) Color.parseColor("#F2F2F2") else headerTextColor,
                    )
                    // Small symmetric padding so it visually centers on the whole header,
                    // not on a narrower padded block.
                    setPadding(activity.dp(6), 0, activity.dp(6), 0)
                },
            )

            addView(
                TextView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        activity.dp(22),
                        activity.dp(22),
                        Gravity.START or Gravity.CENTER_VERTICAL,
                    ).apply { leftMargin = activity.dp(8) }
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    text = badgeText
                    textSize = scaledTextSize(13f)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(badgeTextColor)
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 0f
                            setColor(badgeBackground)
                            setStroke(activity.dp(1), Color.parseColor("#8E8E8E"))
                        }
                },
            )
        }

    internal fun createChoiceButton(
        activity: AppCompatActivity,
        label: String,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        focusedBackground: IntArray,
        normalBackground: IntArray,
        selected: Boolean,
        bottomMarginDp: Int,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = activity.dp(18)
                rightMargin = activity.dp(18)
                bottomMargin = activity.dp(bottomMarginDp)
            }
            minHeight = activity.dp(40)
            gravity = Gravity.CENTER
            includeFontPadding = false
            text = label
            textSize = scaledTextSize(18f)
            setLineSpacing(0f, compactLineSpacing)
            typeface = portalTypeface
            setTextColor(Color.BLACK)
            isFocusable = false
            isClickable = true
            isFocusableInTouchMode = false

            fun applyState(isSelected: Boolean) {
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        if (isSelected) focusedBackground else normalBackground,
                    ).apply {
                        shape = GradientDrawable.RECTANGLE
                        setStroke(activity.dp(1), Color.parseColor("#A0A0A0"))
                    }
            }

            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
            setOnClickListener { onClick() }
            applyState(selected)
        }

    internal fun dialogWidth(
        activity: AppCompatActivity,
        preferredWidthDp: Int,
        horizontalMarginDp: Int,
    ): Int {
        val preferredWidth = activity.dp(preferredWidthDp)
        val maxWidth = activity.resources.displayMetrics.widthPixels - activity.dp(horizontalMarginDp * 2)
        val targetByScreen = (activity.resources.displayMetrics.widthPixels * 0.92f).toInt()
        return maxOf(preferredWidth, targetByScreen).coerceAtMost(maxWidth)
    }

    internal fun createPanelDrawable(
        activity: AppCompatActivity,
        fillColor: Int,
        strokeColor: Int,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadii =
                floatArrayOf(
                    activity.dp(4).toFloat(), activity.dp(4).toFloat(),
                    activity.dp(4).toFloat(), activity.dp(4).toFloat(),
                    0f, 0f,
                    0f, 0f,
                )
            // Subtle edge so white panel separates from white background.
            setStroke(activity.dp(1), Color.parseColor("#C8C8C8"))
        }
}
