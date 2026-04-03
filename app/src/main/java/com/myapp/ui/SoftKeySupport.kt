package com.myapp.ui

import android.view.KeyEvent

internal data class SoftKeyAction(
    val label: String = "",
    val onPress: (() -> Unit)? = null,
)

internal data class SoftKeyBarState(
    val left: SoftKeyAction = SoftKeyAction(),
    val center: SoftKeyAction = SoftKeyAction(),
    val right: SoftKeyAction = SoftKeyAction(),
) {
    fun handleKeyCode(keyCode: Int): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_SOFT_LEFT,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_CALL,
            KeyEvent.KEYCODE_F1,
            -> left.onPress?.let {
                it()
                true
            } ?: false

            KeyEvent.KEYCODE_SOFT_RIGHT,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_CLEAR,
            KeyEvent.KEYCODE_F2,
            -> right.onPress?.let {
                it()
                true
            } ?: false

            else -> false
        }
}

internal object YumodeHardwareKeys {
    var handler: ((Int) -> Boolean)? = null
}
