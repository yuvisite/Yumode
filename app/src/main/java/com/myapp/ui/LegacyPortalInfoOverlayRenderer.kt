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

/**
 * ENDED-style info panel (no buttons), centered like system dialogs.
 * Intended for short 1s feedback like Added/Deleted/Canceled.
 */
internal object LegacyPortalInfoOverlayRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: FrameLayout,
        appLabel: CharSequence,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
        message: String,
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

        val panel =
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

        panel.addView(
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
                text = message
                isSingleLine = true
                textSize = scaledTextSize(16f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                setTextColor(Color.BLACK)
            },
        )

        // Flexible spacer to keep geometry stable (like renderEnded).
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
}

