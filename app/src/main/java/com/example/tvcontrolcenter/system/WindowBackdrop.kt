package com.example.tvcontrolcenter.system

import android.os.Build
import android.view.WindowManager

private const val BLUR_RADIUS_PX = 32

/**
 * Fallback glass for panel windows that have no screenshot to work with: on Android 12+,
 * ask the system to blur everything behind the window, where the device allows it.
 *
 * Do not use this together with a screenshot backdrop. The panel then shows the app sharp
 * around the glass, and blurred only inside it, like tvOS. The dim behind the panel is
 * drawn by the panel itself.
 */
internal fun WindowManager.LayoutParams.applyBlurFallback(windowManager: WindowManager, blur: Boolean) {
    val canBlur = blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        windowManager.isCrossWindowBlurEnabled
    if (canBlur) {
        flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        blurBehindRadius = BLUR_RADIUS_PX
    }
}
