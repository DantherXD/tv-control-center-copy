package com.example.tvcontrolcenter.system.backdrop

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asImageBitmap
import com.example.tvcontrolcenter.system.trigger.ControlCenterAccessibilityService
import com.example.tvcontrolcenter.ui.glass.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Takes a picture of whatever is on screen and blurs it, so the panel can show real frosted
 * glass over any app.
 *
 * Needs Android 11+ and the accessibility service (it holds the screenshot permission).
 * Gives null otherwise, and also for protected video, a busy system, or a slow device; the
 * panel then falls back to its own frosted look. The whole thing is capped at
 * [TIMEOUT_MS] so opening never waits long.
 *
 * Cost: one screenshot, a copy, three halvings to ~480 px, and a six-pass box blur on that
 * small image: a few tens of milliseconds, off the main thread.
 */
object BackdropCapture {
    private const val TARGET_WIDTH = 480
    private const val BLUR_RADIUS = 5
    private const val TIMEOUT_MS = 350L

    private val executor by lazy { Executors.newSingleThreadExecutor() }

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ControlCenterAccessibilityService.instance != null

    suspend fun capture(): Backdrop? {
        if (!isSupported) return null
        return withTimeoutOrNull(TIMEOUT_MS) { captureNow() }
    }

    private suspend fun captureNow(): Backdrop? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val service = ControlCenterAccessibilityService.instance ?: return null
        val shot = screenshot(service) ?: return null
        return withContext(Dispatchers.Default) { blurred(shot) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun screenshot(service: AccessibilityService): Bitmap? =
        suspendCancellableCoroutine { cont ->
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val buffer = result.hardwareBuffer
                            val bitmap = try {
                                // A hardware bitmap cannot be read; copy it to ordinary memory.
                                Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                    ?.copy(Bitmap.Config.ARGB_8888, false)
                            } catch (_: RuntimeException) {
                                null
                            } finally {
                                buffer.close()
                            }
                            if (cont.isActive) cont.resume(bitmap) else bitmap?.recycle()
                        }

                        override fun onFailure(errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                )
            } catch (_: RuntimeException) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun blurred(source: Bitmap): Backdrop? {
        val fullWidth = source.width
        val fullHeight = source.height
        if (fullWidth < 2 || fullHeight < 2) return null

        // Halve until near the target: cheaper and smoother than one big jump.
        var current = source
        while (current.width / 2 >= TARGET_WIDTH) {
            val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== source) current.recycle()
            current = next
        }
        val targetHeight = (TARGET_WIDTH.toLong() * fullHeight / fullWidth).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(current, TARGET_WIDTH, targetHeight, true)
        if (current !== source && current !== small) current.recycle()
        if (source !== small) source.recycle()

        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        boxBlur(pixels, w, h, BLUR_RADIUS)
        val result = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return Backdrop(result.asImageBitmap(), fullWidth, fullHeight)
    }
}
