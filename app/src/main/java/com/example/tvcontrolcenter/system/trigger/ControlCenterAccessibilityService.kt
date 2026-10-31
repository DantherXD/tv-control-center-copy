package com.example.tvcontrolcenter.system.trigger

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.tvcontrolcenter.system.overlay.OverlayController

/**
 * Opens the panel on a long press of a chosen remote key, from any app.
 *
 * Android TV only gives this service the keys the platform has not already
 * claimed. Menu and Settings usually arrive; Home never does. The panel itself is
 * drawn as an accessibility overlay, which needs no extra permission.
 *
 * Short presses are passed through untouched. A long press is detected from the
 * auto-repeat events, then consumed so the focused app does not also react.
 */
class ControlCenterAccessibilityService : AccessibilityService() {

    private var controller: OverlayController? = null
    private var downTime = 0L
    private var triggeredForThisPress = false

    override fun onServiceConnected() {
        instance = this
        controller = OverlayController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        )
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in TRIGGER_KEYS) return false
        // While the panel is open it owns the keyboard, so leave its keys alone.
        if (controller?.isShowing == true) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    downTime = event.eventTime
                    triggeredForThisPress = false
                    return false
                }
                if (!triggeredForThisPress && event.eventTime - downTime >= LONG_PRESS_MILLIS) {
                    triggeredForThisPress = true
                    controller?.show()
                }
                return triggeredForThisPress
            }
            KeyEvent.ACTION_UP -> {
                val consumed = triggeredForThisPress
                triggeredForThisPress = false
                return consumed
            }
        }
        return false
    }

    /** System power menu. Android 12+ only; returns false when the platform refuses. */
    fun showPowerDialog(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

    /** Turns the screen off. Android 9+. */
    fun lockScreen(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        controller?.hide()
        controller = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        /** Keys that can open the panel on a long press. Edit to suit the remote. */
        val TRIGGER_KEYS = setOf(KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS)
        const val LONG_PRESS_MILLIS = 600L

        @Volatile
        var instance: ControlCenterAccessibilityService? = null
            private set
    }
}
