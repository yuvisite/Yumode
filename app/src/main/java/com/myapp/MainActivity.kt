package com.myapp

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myapp.ui.LegacyPortalController
import com.myapp.ui.SoftKeyBarState

class MainActivity : AppCompatActivity() {
    private lateinit var controller: LegacyPortalController
    private lateinit var leftSoftKeyView: TextView
    private lateinit var centerSoftKeyView: TextView
    private lateinit var rightSoftKeyView: TextView
    private lateinit var softKeyBar: LinearLayout
    private val mainHandler = Handler(Looper.getMainLooper())
    private var loadingOverlay: View? = null
    private var loadingDotsRunnable: Runnable? = null
    private var loadingHideRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.decorView.setBackgroundColor(Color.WHITE)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(contentLayout)
        }

        val overlayLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
        }

        val contentHost = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            addView(scrollView)
            addView(overlayLayout)
        }
        loadingOverlay = buildStartupLoadingOverlay().also { contentHost.addView(it) }

        softKeyBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#3A3A3A"), Color.parseColor("#0E0E0E")),
                ).apply {
                    setStroke(dp(1), Color.parseColor("#6A6A6A"))
                }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34),
            )
            setPadding(dp(4), dp(2), dp(4), dp(2))
            gravity = Gravity.CENTER_VERTICAL
        }

        leftSoftKeyView = createSoftKeyLabel()
        centerSoftKeyView = createSoftKeyLabel()
        rightSoftKeyView = createSoftKeyLabel()

        softKeyBar.addView(leftSoftKeyView)
        softKeyBar.addView(centerSoftKeyView)
        softKeyBar.addView(rightSoftKeyView)

        rootLayout.addView(contentHost)
        rootLayout.addView(softKeyBar)

        setContentView(rootLayout)
        controller = LegacyPortalController(
            activity = this,
            scrollView = scrollView,
            container = contentLayout,
            overlayContainer = overlayLayout,
            onSoftKeysChanged = {
                invalidateOptionsMenu()
                renderSoftKeyBar(it)
            },
        )
        startWithLoadingOverlay()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        handleSoftKeyPressVisual(event)
        if (controller.handleKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        loadingDotsRunnable?.let(mainHandler::removeCallbacks)
        loadingHideRunnable?.let(mainHandler::removeCallbacks)
        controller.dispose()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return controller.populateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        controller.populateOptionsMenu(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (controller.handleOptionsMenuItem(item.itemId)) {
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    private fun renderSoftKeyBar(state: SoftKeyBarState) {
        leftSoftKeyView.text = state.left.label
        centerSoftKeyView.text =
            state.center.label.ifBlank {
                val focused = currentFocus
                if (focused?.isClickable == true) "Select" else ""
            }
        rightSoftKeyView.text = state.right.label
        leftSoftKeyView.alpha = if (state.left.label.isBlank()) 0.4f else 1f
        centerSoftKeyView.alpha = if (centerSoftKeyView.text.isNullOrBlank()) 0.4f else 1f
        rightSoftKeyView.alpha = if (state.right.label.isBlank()) 0.4f else 1f
    }

    private fun createSoftKeyLabel(): TextView =
        TextView(this).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f,
                )
            setTextColor(Color.WHITE)
            textSize = 14f
            // Force true visual centering inside the segment (also for side buttons).
            this.gravity = Gravity.CENTER
            isAllCaps = false
            setSingleLine(true)
            includeFontPadding = false
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#2B2B2B"), Color.parseColor("#111111")),
                ).apply {
                    setStroke(dp(1), Color.parseColor("#7A7A7A"))
                }
            // No extra vertical padding so text is exactly centered by gravity.
            setPadding(dp(6), 0, dp(6), 0)
        }

    private fun handleSoftKeyPressVisual(event: KeyEvent) {
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val released = event.action == KeyEvent.ACTION_UP
        if (!pressed && !released) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_SOFT_LEFT,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_CALL,
            KeyEvent.KEYCODE_F1,
            -> setSoftKeyPressed(leftSoftKeyView, pressed)

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> setSoftKeyPressed(centerSoftKeyView, pressed)

            KeyEvent.KEYCODE_SOFT_RIGHT,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_CLEAR,
            KeyEvent.KEYCODE_F2,
            -> setSoftKeyPressed(rightSoftKeyView, pressed)
        }
        if (released) {
            if (event.keyCode == KeyEvent.KEYCODE_F1 ||
                event.keyCode == KeyEvent.KEYCODE_F2 ||
                event.keyCode == KeyEvent.KEYCODE_SOFT_LEFT ||
                event.keyCode == KeyEvent.KEYCODE_SOFT_RIGHT
            ) {
                // Keep release repaint explicit on hardware softkeys.
                leftSoftKeyView.invalidate()
                centerSoftKeyView.invalidate()
                rightSoftKeyView.invalidate()
            }
        }
    }

    private fun setSoftKeyPressed(view: TextView, pressed: Boolean) {
        view.isPressed = pressed
        view.alpha = if (pressed) 0.72f else 1f
        view.translationY = if (pressed) dp(1).toFloat() else 0f
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun startWithLoadingOverlay() {
        val overlay = loadingOverlay
        if (overlay == null) {
            controller.start()
            return
        }
        softKeyBar.visibility = View.GONE
        val dotsView = overlay.findViewWithTag("loading-dots") as? TextView
        val frames = arrayOf("● ○ ○", "○ ● ○", "○ ○ ●")
        var frameIndex = 0
        val dotsRunnable =
            object : Runnable {
                override fun run() {
                    dotsView?.text = frames[frameIndex]
                    frameIndex = (frameIndex + 1) % frames.size
                    mainHandler.postDelayed(this, 220L)
                }
            }
        loadingDotsRunnable = dotsRunnable
        mainHandler.post(dotsRunnable)
        
        // Start UI rendering in background
        controller.start(skipHomeFeedPrefetch = true)
        
        // Track when home feeds finish loading and minimum overlay duration
        val startTime = System.currentTimeMillis()
        var preloadComplete = false
        var minimumDurationReached = false
        
        // Show overlay for minimum time
        mainHandler.postDelayed({
            minimumDurationReached = true
            if (preloadComplete) {
                hideLoadingOverlay(overlay)
            }
        }, STARTUP_OVERLAY_MIN_DURATION_MS)
        
        // Preload home feeds - hide overlay when complete
        controller.preloadHomeFeedsForStartup {
            preloadComplete = true
            if (minimumDurationReached) {
                hideLoadingOverlay(overlay)
            }
        }
    }

    private companion object {
        const val STARTUP_OVERLAY_MIN_DURATION_MS = 800L
    }

    private fun hideLoadingOverlay(
        overlay: View,
    ) {
        loadingDotsRunnable?.let(mainHandler::removeCallbacks)
        loadingDotsRunnable = null
        loadingHideRunnable?.let(mainHandler::removeCallbacks)
        loadingHideRunnable = null
        overlay.visibility = View.GONE
        softKeyBar.visibility = View.VISIBLE
    }

    private fun buildStartupLoadingOverlay(): View =
        FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.WHITE)
            isClickable = true

            addView(
                View(this@MainActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        dp(40),
                        dp(40),
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    ).apply { topMargin = dp(32) }
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(Color.parseColor("#C7181B"))
                            setStroke(dp(1), Color.parseColor("#8A8A8A"))
                        }
                },
            )

            addView(
                TextView(this@MainActivity).apply {
                    tag = "loading-dots"
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                    ).apply { bottomMargin = dp(42) }
                    includeFontPadding = false
                    text = "● ○ ○"
                    textSize = 16f
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextColor(Color.parseColor("#5B5B5B"))
                },
            )
        }
}
