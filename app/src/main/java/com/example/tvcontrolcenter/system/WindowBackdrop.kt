package com.example.tvcontrolcenter.system

import android.os.Build
import android.view.WindowManager

private const val BLUR_RADIUS_PX = 32
private const val DIM_WITH_BLUR = 0.16f
private const val DIM_WITHOUT_BLUR = 0.34f

/**
 * Dims the screen behind a panel window and, on Android 12+ where the device allows it,
 * asks the system to blur it. Android never lets an app read other apps' pixels, so the
 * system blur is the only real "glass over the running app" there is. Without it the
 * panel still looks frosted (see FrostedGlass) and the dim is a little stronger.
 */
internal fun WindowManager.LayoutParams.applyBackdrop(windowManager: WindowManager, blur: Boolean) {
    flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
    val canBlur = blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        windowManager.isCrossWindowBlurEnabled
    if (canBlur) {
        flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        blurBehindRadius = BLUR_RADIUS_PX
        dimAmount = DIM_WITH_BLUR
    } else {
        dimAmount = DIM_WITHOUT_BLUR
    }
}
