package com.example.tvcontrolcenter.system.backdrop

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.PixelCopy
import android.view.Window
import com.example.tvcontrolcenter.domain.model.BlurLevels
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
 * Cost: one screenshot, a copy, three halvings to ~480 px, and a three-pass box blur (radius from the blur level) on that
 * small image: a few tens of milliseconds, off the main thread.
 */
object BackdropCapture {
    private const val TARGET_WIDTH = 480
    private const val TIMEOUT_MS = 350L

    private val executor by lazy { Executors.newSingleThreadExecutor() }

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ControlCenterAccessibilityService.instance != null

    enum class Status { READY, NEEDS_SERVICE, NEEDS_ANDROID_11 }

    /** Why a screenshot blur would or would not work right now. Shown in the editor. */
    fun status(): Status = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> Status.NEEDS_ANDROID_11
        ControlCenterAccessibilityService.instance == null -> Status.NEEDS_SERVICE
        else -> Status.READY
    }

    /** Blurred picture of whatever app is on screen, [level] 1..4. Null if off or not possible. */
    suspend fun capture(level: Int): Backdrop? {
        if (level <= 0 || !isSupported) return null
        return withTimeoutOrNull(TIMEOUT_MS) { captureNow(level) }
    }

    /**
     * Blurred picture of this app's own window. Needs no permission, so it works in the app
     * itself (Android 8+), where the accessibility screenshot is not needed.
     */
    suspend fun captureWindow(window: Window, level: Int): Backdrop? {
        if (level <= 0) return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            val view = window.decorView
            val fullW = view.width
            val fullH = view.height
            val copy = pixelCopy(window, fullW, fullH) ?: return@withTimeoutOrNull null
            withContext(Dispatchers.Default) { blurred(copy, level, fullW, fullH) }
        }
    }

    private suspend fun pixelCopy(window: Window, w: Int, h: Int): Bitmap? =
        suspendCancellableCoroutine { cont ->
            if (w <= 0 || h <= 0) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val targetH = (TARGET_WIDTH.toLong() * h / w).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(TARGET_WIDTH, targetH, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(window, bitmap, { code ->
                    if (cont.isActive) cont.resume(if (code == PixelCopy.SUCCESS) bitmap else null)
                }, Handler(Looper.getMainLooper()))
            } catch (_: IllegalArgumentException) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private suspend fun captureNow(level: Int): Backdrop? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val service = ControlCenterAccessibilityService.instance ?: return null
        val shot = screenshot(service) ?: return null
        return withContext(Dispatchers.Default) { blurred(shot, level, shot.width, shot.height) }
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

    private fun blurred(source: Bitmap, level: Int, fullWidth: Int, fullHeight: Int): Backdrop? {
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
        boxBlur(pixels, w, h, BlurLevels.boxRadius(level))
        val result = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return Backdrop(result.asImageBitmap(), fullWidth, fullHeight)
    }
}
