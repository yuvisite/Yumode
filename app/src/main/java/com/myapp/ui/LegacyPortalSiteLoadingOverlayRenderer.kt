package com.myapp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

internal enum class SiteLoadingOverlayStage {
    RECEIVING,
    WHITE_SCREEN,
}

internal object LegacyPortalSiteLoadingOverlayRenderer {
    private const val PHONE_ICON_ASSET = "sites/loading/phone.png"
    private const val PAGE_ICON_ASSET = "sites/loading/page.png"

    private val colorStripTop: Int = Color.parseColor("#50565E")
    private val colorStripBottom: Int = Color.parseColor("#14181E")
    private val colorStripBorder: Int = Color.parseColor("#6E747B")
    private val colorIcon: Int = Color.parseColor("#D7DBE0")
    private val colorTileOn: Int = Color.parseColor("#E9EDF2")
    private val colorTileOff: Int = Color.parseColor("#747C85")
    private val colorLabel: Int = Color.parseColor("#E3E6EA")
    private val loadingIconBitmaps: MutableMap<String, Bitmap?> = mutableMapOf()

    internal fun render(
        activity: AppCompatActivity,
        container: FrameLayout,
        portalTypeface: Typeface,
        scaledTextSize: (Float) -> Float,
        stage: SiteLoadingOverlayStage,
        tileFrame: Int,
    ) {
        when (stage) {
            SiteLoadingOverlayStage.RECEIVING ->
                renderReceiving(
                    activity = activity,
                    container = container,
                    portalTypeface = portalTypeface,
                    scaledTextSize = scaledTextSize,
                    tileFrame = tileFrame,
                )

            SiteLoadingOverlayStage.WHITE_SCREEN ->
                container.addView(
                    View(activity).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(Color.WHITE)
                        isClickable = true
                    },
                )
        }
    }

    private fun renderReceiving(
        activity: AppCompatActivity,
        container: FrameLayout,
        portalTypeface: Typeface,
        scaledTextSize: (Float) -> Float,
        tileFrame: Int,
    ) {
        container.addView(
            View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.argb(96, 20, 24, 30))
                isClickable = true
            },
        )

        val strip =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    FrameLayout.LayoutParams(
                        LegacyPortalExitRenderer.dialogWidth(activity, preferredWidthDp = 264, horizontalMarginDp = 4),
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM,
                    ).apply {
                        bottomMargin = activity.dp(24)
                    }
                setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10))
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(colorStripTop, colorStripBottom),
                    ).apply {
                        cornerRadius = activity.dp(2).toFloat()
                        setStroke(activity.dp(1), colorStripBorder)
                    }
            }

        val topRow =
            LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        topRow.addView(
            buildLoadingIcon(
                activity = activity,
                assetPath = PHONE_ICON_ASSET,
                fallback = { buildPhoneIconFallback(activity) },
            ),
        )
        topRow.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            },
        )

        val tilesWrap =
            LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
        val activeIndex = 4 - (tileFrame % 5)
        repeat(5) { index ->
            tilesWrap.addView(
                View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(activity.dp(11), activity.dp(11)).apply {
                        if (index > 0) {
                            leftMargin = activity.dp(6)
                        }
                    }
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(if (index == activeIndex) colorTileOn else colorTileOff)
                        }
                },
            )
        }
        topRow.addView(tilesWrap)
        topRow.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            },
        )
        topRow.addView(
            buildLoadingIcon(
                activity = activity,
                assetPath = PAGE_ICON_ASSET,
                fallback = { buildPageIconFallback(activity) },
            ),
        )

        strip.addView(topRow)
        strip.addView(
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = activity.dp(8)
                }
                gravity = Gravity.CENTER
                includeFontPadding = false
                text = "Connecting"
                textSize = scaledTextSize(11f)
                setTypeface(portalTypeface, Typeface.NORMAL)
                setTextColor(colorLabel)
            },
        )

        container.addView(strip)
    }

    private fun buildLoadingIcon(
        activity: AppCompatActivity,
        assetPath: String,
        fallback: () -> View,
    ): View {
        val bitmap = loadLoadingIconBitmap(activity, assetPath) ?: return fallback()
        return FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(26), activity.dp(34))
            addView(
                ImageView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
                    setPadding(activity.dp(1), activity.dp(1), activity.dp(1), activity.dp(1))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                    setImageBitmap(bitmap)
                },
            )
        }
    }

    private fun loadLoadingIconBitmap(
        activity: AppCompatActivity,
        assetPath: String,
    ): Bitmap? =
        loadingIconBitmaps.getOrPut(assetPath) {
            runCatching {
                activity.assets.open(assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }

    private fun buildPhoneIconFallback(activity: AppCompatActivity): View =
        FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(22), activity.dp(32))

            addView(
                View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        activity.dp(14),
                        activity.dp(24),
                        Gravity.CENTER_HORIZONTAL or Gravity.TOP,
                    )
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(Color.TRANSPARENT)
                            setStroke(activity.dp(2), colorIcon)
                        }
                },
            )

            addView(
                View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        activity.dp(4),
                        activity.dp(2),
                        Gravity.CENTER_HORIZONTAL or Gravity.TOP,
                    ).apply {
                        topMargin = activity.dp(4)
                    }
                    setBackgroundColor(colorIcon)
                },
            )
        }

    private fun buildPageIconFallback(activity: AppCompatActivity): View =
        FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(24), activity.dp(28))

            addView(
                View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        activity.dp(18),
                        activity.dp(22),
                        Gravity.CENTER,
                    )
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(Color.TRANSPARENT)
                            setStroke(activity.dp(2), colorIcon)
                        }
                },
            )

            addView(
                View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        activity.dp(6),
                        activity.dp(6),
                        Gravity.TOP or Gravity.END,
                    ).apply {
                        topMargin = activity.dp(2)
                        rightMargin = activity.dp(2)
                    }
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(colorStripTop)
                        }
                },
            )
        }
}
