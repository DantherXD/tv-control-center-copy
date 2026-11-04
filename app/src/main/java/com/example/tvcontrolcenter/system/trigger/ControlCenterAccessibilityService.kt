package com.example.tvcontrolcenter.system.trigger

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.util.Log
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
 * The service also holds the screenshot permission (Android 11+), which the panel uses to
 * show a blurred copy of the screen inside its glass. See BackdropCapture.
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

    /**
     * Writes the package and activity of every screen that opens to the log, tag TCC, while the
     * "Log the screen that opens" setting is on. Lets a control be checked against what it really
     * launched, without a computer attached to the TV.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!settings().getBoolean(KEY_ACTIVITY_LOG, false)) return
        Log.i(TAG, "${event.packageName}/${event.className}")
    }

    // The same preferences file PreferencesLayoutRepository writes; a service cannot use injection.
    private fun settings() = getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

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

        private const val TAG = "TCC"
        private const val SETTINGS_FILE = "control_center_layout"
        private const val KEY_ACTIVITY_LOG = "activity_log"
        const val LONG_PRESS_MILLIS = 600L

        @Volatile
        var instance: ControlCenterAccessibilityService? = null
            private set
    }
}
