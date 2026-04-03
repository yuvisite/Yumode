package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

internal object LegacyPortalConfirmRenderer {
    internal fun renderYesNoPrompt(
        activity: AppCompatActivity,
        container: FrameLayout,
        appLabel: CharSequence,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        title: String,
        message: String,
        yesLabel: String,
        noLabel: String,
        selectYes: Boolean,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        val showTitle = title.isNotBlank()

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
                val w = LegacyPortalExitRenderer.dialogWidth(activity, preferredWidthDp = 264, horizontalMarginDp = 4)
                val h = activity.dp(360)
                val shadow = activity.dp(6)
                layoutParams = FrameLayout.LayoutParams(
                    w + shadow,
                    h + shadow,
                ).apply { gravity = Gravity.CENTER }
            }

        shadowWrap.addView(
            View(activity).apply {
                val w = LegacyPortalExitRenderer.dialogWidth(activity, preferredWidthDp = 264, horizontalMarginDp = 4)
                val h = activity.dp(360)
                val shadow = activity.dp(6)
                layoutParams = FrameLayout.LayoutParams(w, h).apply {
                    leftMargin = shadow
                    topMargin = shadow
                }
                setBackgroundColor(Color.BLACK)
            },
        )

        val dialog =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    FrameLayout.LayoutParams(
                        LegacyPortalExitRenderer.dialogWidth(activity, preferredWidthDp = 264, horizontalMarginDp = 4),
                        activity.dp(360),
                    ).apply { gravity = Gravity.CENTER }
                background = LegacyPortalExitRenderer.createPanelDrawable(activity, Color.WHITE, Color.parseColor("#222222"))
                setPadding(0, 0, 0, activity.dp(6))
            }

        dialog.addView(
            LegacyPortalExitRenderer.buildHeader(
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

        if (showTitle) {
            dialog.addView(
                TextView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = activity.dp(46)
                        bottomMargin = activity.dp(8)
                    }
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    text = title
                    isSingleLine = true
                    textSize = scaledTextSize(16f)
                    setLineSpacing(0f, compactLineSpacing)
                    typeface = portalTypeface
                    setTextColor(Color.BLACK)
                },
            )
        }

        if (message.isNotBlank()) {
            dialog.addView(
                TextView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (!showTitle) {
                            topMargin = activity.dp(60)
                        }
                        leftMargin = activity.dp(12)
                        rightMargin = activity.dp(12)
                        bottomMargin = activity.dp(28)
                    }
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    text = message
                    textSize = scaledTextSize(14f)
                    setLineSpacing(0f, compactLineSpacing)
                    typeface = portalTypeface
                    setTextColor(Color.BLACK)
                },
            )
        }

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
            LegacyPortalExitRenderer.createChoiceButton(
                activity = activity,
                label = yesLabel,
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
            LegacyPortalExitRenderer.createChoiceButton(
                activity = activity,
                label = noLabel,
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
}

