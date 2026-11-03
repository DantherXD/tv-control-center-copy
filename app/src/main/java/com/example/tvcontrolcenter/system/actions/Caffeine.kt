package com.example.tvcontrolcenter.system.actions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Keeps the screen on with a 1-pixel invisible window that carries the keep-screen-on flag.
 * It lives in the accessibility service, so it needs no extra permission, and it stays until
 * switched off (or the service stops).
 */
object Caffeine {
    private var view: View? = null

    val isOn: Boolean get() = view != null

    /** Returns the new state. */
    fun toggle(service: AccessibilityService): Boolean {
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val current = view
        if (current != null) {
            runCatching { wm.removeView(current) }
            view = null
            return false
        }
        val fresh = View(service)
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        return runCatching { wm.addView(fresh, params) }
            .onSuccess { view = fresh }
            .isSuccess
    }
}
