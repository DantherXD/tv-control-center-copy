package com.example.tvcontrolcenter.system.backdrop

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.PixelCopy
import android.view.Window
import android.view.accessibility.AccessibilityWindowInfo
import com.example.tvcontrolcenter.domain.model.BlurLevels
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asImageBitmap
import com.example.tvcontrolcenter.system.trigger.ControlCenterAccessibilityService
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.glass.lumaMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Takes a picture of whatever is on screen and blurs it, so the panel can show real frosted
 * glass over any app.
 *
 * Needs Android 11+ and the accessibility service (it holds the screenshot permission).
 * Gives null otherwise, and also for protected video, a busy system, or a slow device; the
 * panel then falls back to its own frosted look. The whole thing is capped at
 * [TIMEOUT_MS] so opening never waits long.
 *
 * Cost: one screenshot, a sharp 960 px copy for the real lens, a 480 px copy, and a three-pass box blur
 * (radius from the blur level) on the small copy: a few tens of milliseconds, off the main thread.
 */
object BackdropCapture {
    private const val TAG = "TCC"

    /** Sharp enough for the real lens, small enough to stay cheap on a TV. */
    private const val SHARP_WIDTH = 960

    /** The blurred copy the fallback drawing and the brightness map use. */
    private const val BLUR_WIDTH = 480
    private const val TIMEOUT_MS = 350L
    private const val PATIENT_TIMEOUT_MS = 1_200L
    private const val PATIENT_TRIES = 3
    private const val PATIENT_PAUSE_MS = 150L

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

    /**
     * True when the panel can sample only the app underneath, repeatedly, without catching the
     * panel itself. Android 14 added `takeScreenshotOfWindow`; earlier versions can only take a
     * whole-display picture, which would include the panel.
     */
    val isLiveWindowSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ControlCenterAccessibilityService.instance != null

    enum class LiveStatus { READY, OFF, NEEDS_BLUR, NEEDS_SERVICE, NEEDS_ANDROID_14 }

    /** Why real live glass would or would not work right now. Shown in the editor. */
    fun liveStatus(liveGlass: Boolean, level: Int): LiveStatus {
        if (!liveGlass) return LiveStatus.OFF
        if (level <= 0) return LiveStatus.NEEDS_BLUR
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return LiveStatus.NEEDS_ANDROID_14
        val service = ControlCenterAccessibilityService.instance ?: return LiveStatus.NEEDS_SERVICE
        // An update does not rebuild a running service's AccessibilityServiceInfo. Until the
        // user switches it off and on, getWindows() is empty even though the service is bound.
        val hasWindows = runCatching { service.windows.isNotEmpty() }.getOrDefault(false)
        return if (hasWindows) LiveStatus.READY else LiveStatus.NEEDS_SERVICE
    }

    /** Full-size picture of the screen for saving. Null if not possible (no service, Android 10-, protected video). */
    suspend fun screenshotFull(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val service = ControlCenterAccessibilityService.instance ?: return null
        return withTimeoutOrNull(2_000L) { screenshot(service) }
    }

    /**
     * A fresh frame of the app underneath the panel, for real live glass.
     *
     * Uses `takeScreenshotOfWindow` (Android 14+) on the largest non-overlay window, so the
     * panel is never part of the picture. Android rate-limits these screenshots to about three
     * per second; the caller refreshes the glass at that pace. Null when there is no app window,
     * the app is protected, or the platform refused the picture.
     */
    suspend fun captureLiveWindow(level: Int): Backdrop? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.w(TAG, "live glass: Android 14 is required")
            return null
        }
        if (level <= 0) {
            Log.w(TAG, "live glass: blur is off")
            return null
        }
        val service = ControlCenterAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "live glass: accessibility service is not connected")
            return null
        }
        val target = liveWindow(service)
        if (target == null) {
            Log.w(TAG, "live glass: no app window (${service.windows.size} windows)")
            return null
        }
        val shot = withTimeoutOrNull(1_100L) { screenshotWindow(service, target.id) }
        if (shot == null) {
            Log.w(TAG, "live glass: window ${target.id} refused the screenshot")
            return null
        }
        return withContext(Dispatchers.Default) { windowBackdrop(service, shot, screenBounds(target), level) }
    }

    /** Blurred picture of whatever app is on screen, [level] 1..4. Null if off or not possible. */
    suspend fun capture(level: Int, patient: Boolean = false): Backdrop? {
        if (level <= 0 || !isSupported) return null
        if (!patient) return withTimeoutOrNull(TIMEOUT_MS) { captureNow(level) }
        // Video and moving scenes make the system slow or refuse a screenshot: wait longer, try again.
        repeat(PATIENT_TRIES) {
            val picture = withTimeoutOrNull(PATIENT_TIMEOUT_MS) { captureNow(level) }
            if (picture != null) return picture
            delay(PATIENT_PAUSE_MS)
        }
        return null
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
            val targetH = (SHARP_WIDTH.toLong() * h / w).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(SHARP_WIDTH, targetH, Bitmap.Config.ARGB_8888)
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
        screenshotResult { callback ->
            service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun screenshotWindow(service: AccessibilityService, windowId: Int): Bitmap? =
        screenshotResult { callback ->
            service.takeScreenshotOfWindow(windowId, executor, callback)
        }

    /** Shared callback plumbing for the display and single-window screenshot APIs. */
    private suspend fun screenshotResult(
        request: (AccessibilityService.TakeScreenshotCallback) -> Unit,
    ): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            request(
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

    /**
     * The app window the panel is covering. The panel owns focus, so it is the active window and
     * is skipped first; that also avoids relying on [AccessibilityWindowInfo.getRoot], which can
     * be null for the app below a full-screen accessibility overlay on some TV builds. The
     * largest remaining application/system window is the one underneath. Android returns small
     * system windows too (navigation, status), so size is the useful test.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun liveWindow(service: AccessibilityService): AccessibilityWindowInfo? = runCatching {
        val ownPackage = service.packageName
        val all = service.windows
        val active = all.firstOrNull { it.isActive }
        val activeIsPanel = active != null && (
            active.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
                runCatching { active.root?.packageName }.getOrNull() == ownPackage
            )
        val skipId = if (active != null && activeIsPanel) active.id else -1
        val candidates = all.asSequence()
            .filter { it.id != skipId }
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
            .map { it to screenBounds(it) }
            .filter { (_, bounds) -> bounds.width() > 0 && bounds.height() > 0 }
            .toList()
        // Keep a foreign window when its root is readable; a null root is allowed as a last
        // resort because the active panel has already been excluded.
        val foreign = candidates.filter { (window, _) ->
            runCatching { window.root?.packageName }.getOrNull()?.let { it != ownPackage } ?: true
        }
        val pool = if (foreign.isEmpty()) candidates else foreign
        pool.maxByOrNull { (_, bounds) -> bounds.width().toLong() * bounds.height() }?.first
    }.getOrNull()

    /** `AccessibilityWindowInfo` exposes its bounds through out-parameters only. */
    private fun screenBounds(window: AccessibilityWindowInfo): Rect =
        Rect().also { window.getBoundsInScreen(it) }

    /**
     * A window screenshot only contains that window, while the panel samples in screen
     * coordinates. Put it back on a full-screen transparent canvas at its real position, then
     * blur that like any other backdrop. A full-screen window is used as-is, which is the normal
     * TV case and avoids an 8 MB copy every frame.
     */
    private fun windowBackdrop(
        service: AccessibilityService,
        shot: Bitmap,
        bounds: Rect,
        level: Int,
    ): Backdrop? {
        val metrics = service.resources.displayMetrics
        val fullWidth = metrics.widthPixels.coerceAtLeast(1)
        val fullHeight = metrics.heightPixels.coerceAtLeast(1)
        if (shot.width <= 0 || shot.height <= 0) return null
        if (bounds.left == 0 && bounds.top == 0 && shot.width == fullWidth && shot.height == fullHeight) {
            return blurred(shot, level, fullWidth, fullHeight)
        }
        // Frame it at the sharp copy's size, not at 4K: a 4K canvas every frame would cost
        // 33 MB and provoke GC on the boxes this app targets.
        val scale = SHARP_WIDTH.toFloat() / fullWidth
        val frameW = SHARP_WIDTH
        val frameH = (fullHeight * scale).roundToInt().coerceAtLeast(1)
        val framed = Bitmap.createBitmap(frameW, frameH, Bitmap.Config.ARGB_8888)
        Canvas(framed).drawBitmap(
            shot,
            null,
            Rect(
                (bounds.left * scale).roundToInt(),
                (bounds.top * scale).roundToInt(),
                ((bounds.left + bounds.width()) * scale).roundToInt(),
                ((bounds.top + bounds.height()) * scale).roundToInt(),
            ),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        shot.recycle()
        return blurred(framed, level, fullWidth, fullHeight)
    }

    private fun blurred(source: Bitmap, level: Int, fullWidth: Int, fullHeight: Int): Backdrop? {
        if (fullWidth < 2 || fullHeight < 2) return null

        // Keep a sharp copy for the lens. PixelCopy and the screenshot path already aim at
        // SHARP_WIDTH, but a smaller window can arrive at its own size.
        val targetHeight = (SHARP_WIDTH.toLong() * fullHeight / fullWidth).toInt().coerceAtLeast(1)
        val sharp = if (source.width > SHARP_WIDTH || source.height > targetHeight) {
            Bitmap.createScaledBitmap(source, SHARP_WIDTH, targetHeight, true).also {
                if (it !== source) source.recycle()
            }
        } else {
            source
        }

        // The fallback and its brightness map only need the small blurred copy.
        val blurHeight = (BLUR_WIDTH.toLong() * sharp.height / sharp.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(sharp, BLUR_WIDTH, blurHeight, true)
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== sharp) small.recycle()
        boxBlur(pixels, w, h, BlurLevels.boxRadius(level))
        val result = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        // The brightness map comes from the blurred pixels, so it matches what the glass shows.
        return Backdrop(
            image = result.asImageBitmap(),
            width = fullWidth,
            height = fullHeight,
            luma = lumaMapOf(pixels, w, h),
            sharp = sharp.asImageBitmap(),
        )
    }
}
