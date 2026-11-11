package com.example.tvcontrolcenter.system.trigger

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
        controller = OverlayController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        )
    }

    /**
     * Opens or closes the overlay from the launcher/activity entry. Using this instead of a
     * separate activity means the app underneath is not paused, and live glass can sample it.
     * Returns false when the service has no controller yet, so the caller can use its fallback.
     */
    fun togglePanel(): Boolean {
        val c = controller ?: return false
        if (c.isShowing) c.hide() else c.show()
        return true
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

    /**
     * Switches a radio the only way an ordinary app still may: the system's own switch, pressed
     * for you. From Android 10 [android.net.wifi.WifiManager.setWifiEnabled] returns false for
     * every app that is not a device owner, profile owner or system build, and
     * `BluetoothAdapter.enable` needs a signature permission, so the panel's toggle drives the
     * settings screen instead: open it, click the switch that is not already in the asked state,
     * then close the screen again.
     *
     * [keywords] name the radio in the screen's text, so the right switch is preferred. False when
     * the screen will not open, which leaves the caller free to show the screen instead.
     */
    fun switchRadio(action: String, on: Boolean, keywords: Array<String>): Boolean {
        val started = runCatching {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!started) return false
        var tries = 0
        val poll = object : Runnable {
            override fun run() {
                tries++
                val root = runCatching { rootInActiveWindow }.getOrNull()
                val switch = root?.let { findRadioSwitch(it, on, keywords) }
                if (switch != null) {
                    runCatching { switch.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                    // Let the radio start moving before the screen is closed again.
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, CLICK_SETTLE_MILLIS)
                    return
                }
                if (tries < MAX_TRIES) handler.postDelayed(this, POLL_MILLIS)
            }
        }
        handler.postDelayed(poll, FIRST_POLL_MILLIS)
        return true
    }

    /**
     * The switch on the screen that is not already in [on]'s state. One the [keywords] name wins;
     * a screen with no checkable state at all is searched for a switch-like control that the
     * keywords name, so an unusual TV build still has a chance.
     */
    private fun findRadioSwitch(
        root: AccessibilityNodeInfo,
        on: Boolean,
        keywords: Array<String>,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        var unchecked: AccessibilityNodeInfo? = null
        var named: AccessibilityNodeInfo? = null
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isCheckable && node.isEnabled && node.isChecked != on) {
                if (unchecked == null) unchecked = node
                if (named == null && names(node, keywords)) named = node
            }
            val className = node.className?.toString().orEmpty()
            if (named == null && SWITCH_CLASSES.any { className.contains(it) } && names(node, keywords)) {
                named = node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return named ?: unchecked
    }

    /** True when the node, or the row it sits in, mentions one of [keywords]. */
    private fun names(node: AccessibilityNodeInfo, keywords: Array<String>): Boolean {
        val parent = node.parent
        val lines = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            parent?.text?.toString(),
            parent?.contentDescription?.toString(),
        )
        return lines.any { line -> keywords.any { line.contains(it, ignoreCase = true) } }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
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

        /** The settings screen needs a moment to lay its switch out. */
        private const val FIRST_POLL_MILLIS = 700L
        private const val POLL_MILLIS = 250L
        private const val MAX_TRIES = 12
        private const val CLICK_SETTLE_MILLIS = 500L

        private val SWITCH_CLASSES = listOf("Switch", "ToggleButton", "CheckBox")

        @Volatile
        var instance: ControlCenterAccessibilityService? = null
            private set
    }
}
