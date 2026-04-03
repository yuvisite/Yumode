package com.myapp.ui

import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Typeface
import android.widget.FrameLayout

internal sealed interface LegacyPortalPromptOverlay {
    data class Confirm(
        val title: String,
        val message: String,
        val yesLabel: String,
        val noLabel: String,
        val onYes: () -> Unit,
        val onNo: () -> Unit,
    ) : LegacyPortalPromptOverlay
}

internal fun handleLegacyPortalPromptKey(
    overlay: LegacyPortalPromptOverlay,
    keyCode: Int,
    isYesSelected: Boolean,
    onSelectYesChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onActivate: () -> Unit,
): Boolean =
    when (overlay) {
        is LegacyPortalPromptOverlay.Confirm ->
            when (keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_CLEAR,
                KeyEvent.KEYCODE_ESCAPE,
                -> {
                    onBack()
                    true
                }

                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_LEFT,
                -> {
                    if (!isYesSelected) {
                        onSelectYesChanged(true)
                    }
                    true
                }

                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                -> {
                    if (isYesSelected) {
                        onSelectYesChanged(false)
                    }
                    true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> {
                    onActivate()
                    true
                }

                else -> false
            }
    }

internal fun renderLegacyPortalPromptOverlay(
    overlay: LegacyPortalPromptOverlay,
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
    when (overlay) {
        is LegacyPortalPromptOverlay.Confirm ->
            LegacyPortalConfirmRenderer.renderYesNoPrompt(
                activity = activity,
                container = container,
                appLabel = appLabel,
                portalTypeface = portalTypeface,
                compactLineSpacing = compactLineSpacing,
                scaledTextSize = scaledTextSize,
                title = overlay.title,
                message = overlay.message,
                yesLabel = overlay.yesLabel,
                noLabel = overlay.noLabel,
                selectYes = selectYes,
                onConfirm = onConfirm,
                onCancel = onCancel,
            )
    }
}

