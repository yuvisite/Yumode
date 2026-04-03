package com.myapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

internal object LegacyPortalToastRenderer {
    internal fun render(
        activity: AppCompatActivity,
        container: FrameLayout,
        text: String,
        portalTypeface: Typeface,
        compactLineSpacing: Float,
        scaledTextSize: (Float) -> Float,
    ) {
        container.addView(
            TextView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = activity.dp(12)
                }
                setPadding(activity.dp(10), activity.dp(6), activity.dp(10), activity.dp(6))
                includeFontPadding = false
                this.text = text
                textSize = scaledTextSize(12f)
                setLineSpacing(0f, compactLineSpacing)
                typeface = portalTypeface
                setTextColor(Color.WHITE)
                background =
                    GradientDrawable().apply {
                        setColor(Color.parseColor("#3A3A3A"))
                        setStroke(activity.dp(1), Color.parseColor("#9A9A9A"))
                        cornerRadius = activity.dp(2).toFloat()
                    }
            },
        )
    }
}

