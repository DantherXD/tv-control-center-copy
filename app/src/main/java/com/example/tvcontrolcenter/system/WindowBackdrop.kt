package com.example.tvcontrolcenter.system

import android.content.Context
import android.os.Build
import android.view.WindowManager

/**
 * Whole-screen blur: on Android 12+ ask the system to blur everything behind the window,
 * where the device allows it. Used by the "Whole screen" blur method, and by Auto when no
 * screenshot can be taken. Returns true if the blur was requested.
 *
 * Do not combine with a screenshot backdrop: the panel then shows the app sharp around the
 * glass and blurred only inside it, like tvOS.
 */
internal fun WindowManager.LayoutParams.applyBlurFallback(windowManager: WindowManager, radiusPx: Int): Boolean {
    val canBlur = radiusPx > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        windowManager.isCrossWindowBlurEnabled
    if (!canBlur) return false
    flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
    blurBehindRadius = radiusPx
    return true
}

/**
 * True when the system will really blur what is behind the window: Android 12+ with
 * cross-window blur switched on. Whole screen only works then; otherwise the panel needs a
 * screenshot, or it falls back to the glass card.
 */
internal fun wholeScreenBlurSupported(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled
