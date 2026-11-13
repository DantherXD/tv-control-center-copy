package com.example.tvcontrolcenter.system.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.tvcontrolcenter.MainActivity
import com.example.tvcontrolcenter.di.ControlCenterEntryPoint
import com.example.tvcontrolcenter.domain.model.BlurLevels
import com.example.tvcontrolcenter.domain.model.BlurMethod
import com.example.tvcontrolcenter.domain.model.PanelSettings
import com.example.tvcontrolcenter.system.applyBlurFallback
import com.example.tvcontrolcenter.system.backdrop.BackdropCapture
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.system.openCapabilitySettings
import com.example.tvcontrolcenter.ui.panel.ControlCenterHost
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.theme.ControlCenterTheme
import kotlinx.coroutines.delay

/**
 * Lifecycle, saved-state and ViewModel owner for a ComposeView that lives in a
 * Service window instead of an Activity. A fresh host is created per opening, so
 * closing the panel clears the ViewModel and stops all polling.
 */
internal class OverlayHost : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() { registry.currentState = Lifecycle.State.RESUMED }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * Shows the panel in a full-screen window on top of whatever app is running.
 *
 * [windowType] is TYPE_ACCESSIBILITY_OVERLAY from the accessibility service (no extra
 * permission) or TYPE_APPLICATION_OVERLAY from [OverlayService] (needs SYSTEM_ALERT_WINDOW).
 *
 * Live glass on Android 14+ takes repeated single-window screenshots of the app underneath, so
 * the panel is never captured and the real lens stays live. Older versions use the system's
 * blur-behind when it is enabled, then a screenshot. See README.
 */
class OverlayController(
    private val context: Context,
    private val windowType: Int,
    private val onGone: () -> Unit = {},
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: ComposeView? = null
    private var host: OverlayHost? = null
    private var visible by mutableStateOf(false)
    private var backdrop by mutableStateOf<Backdrop?>(null)
    private var windowParams: WindowManager.LayoutParams? = null
    private var windowBlurOn by mutableStateOf(false)
    private var liveFrames by mutableStateOf(false)

    /** False once [hide] was called, so a screenshot that arrives late cannot reopen the panel. */
    private var wanted = false
    private var everVisible = false

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return
        wanted = true
        everVisible = false
        backdrop = null
        windowBlurOn = false
        liveFrames = false
        val newHost = OverlayHost().also { it.resume() }
        val viewModel = ViewModelProvider(
            newHost,
            ControlCenterEntryPoint.from(context).viewModelFactory(),
        )[ControlCenterViewModel::class.java]

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(newHost)
            setViewTreeSavedStateRegistryOwner(newHost)
            setViewTreeViewModelStoreOwner(newHost)
            isFocusable = true
            setContent {
                ControlCenterTheme {
                    // The window is on screen but draws nothing yet, so the first capture sees
                    // the app alone. The panel opens once there is a picture, or at once when
                    // live window capture is available (it never includes this overlay).
                    LaunchedEffect(Unit) {
                        val settings = ControlCenterEntryPoint.from(context).layoutRepository().settings.value
                        val level = settings.effectiveBlur
                        val live = settings.liveGlass && level > 0 && BackdropCapture.isLiveWindowSupported
                        if (live) {
                            liveFrames = true
                            Log.i(TAG, "live glass: on (level=$level)")
                            // One clean picture before the panel is on screen. It is the fallback
                            // if live capture is refused, and a whole-display capture now would
                            // include the panel.
                            backdrop = BackdropCapture.capture(level, settings.patientCapture)
                            if (wanted) {
                                everVisible = true
                                visible = true
                            }
                            var misses = 0
                            var announced = false
                            // Android limits single-window screenshots to roughly three a second.
                            // Keep refreshing at that pace while the panel is open, and fall back
                            // after a few misses (protected video, window gone, service revoked).
                            // The delay also spaces the first window shot after the clean picture.
                            while (wanted && liveFrames && misses < LIVE_MISSES_BEFORE_FALLBACK) {
                                delay(LIVE_FRAME_DELAY_MS)
                                if (!wanted) break
                                val frame = BackdropCapture.captureLiveWindow(level)
                                if (frame != null) {
                                    if (!announced) {
                                        Log.i(TAG, "live glass: first frame received")
                                        announced = true
                                    }
                                    backdrop = frame
                                    misses = 0
                                } else {
                                    misses++
                                }
                            }
                            if (wanted && liveFrames && misses >= LIVE_MISSES_BEFORE_FALLBACK) {
                                liveFrames = false
                                Log.w(TAG, "live glass: fallback after $misses missed frames")
                                // Keep the clean pre-open picture and ask for live system blur. A
                                // display screenshot now would photograph the open panel.
                                if (settings.blurMethod != BlurMethod.SCREENSHOT) applySystemBlur(level)
                            }
                        } else {
                            Log.i(
                                TAG,
                                "live glass: off (enabled=${settings.liveGlass}, level=$level, " +
                                    "supported=${BackdropCapture.isLiveWindowSupported})",
                            )
                            applyFallback(settings, level)
                            if (wanted) {
                                everVisible = true
                                visible = true
                            }
                        }
                    }
                    ControlCenterHost(
                        viewModel = viewModel,
                        visible = visible,
                        onDismissRequest = { visible = false },
                        onClosed = { remove() },
                        onCapability = { context.openCapabilitySettings(it) },
                        onOpenEditor = {
                            context.startActivity(
                                Intent(context, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_EDIT, true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        backdrop = backdrop,
                        scrimAlpha = if (backdrop != null || windowBlurOn) SCRIM_WITH_BACKDROP else SCRIM_WITHOUT_BACKDROP,
                        windowBlur = windowBlurOn,
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val s = ControlCenterEntryPoint.from(context).layoutRepository().settings.value
            // Real live glass uses window screenshots; system blur is the fallback when this
            // Android version cannot capture a single window. Without Live glass, a missing
            // screenshot also asks the system to blur.
            val liveCapture = s.liveGlass && BackdropCapture.isLiveWindowSupported
            val wantsWindow = if (s.liveGlass) {
                !liveCapture
            } else {
                s.blurMethod != BlurMethod.SCREENSHOT && !BackdropCapture.isSupported
            }
            if (wantsWindow && applyBlurFallback(windowManager, BlurLevels.windowRadius(s.effectiveBlur))) windowBlurOn = true
        }
        windowParams = params

        view = composeView
        host = newHost
        try {
            windowManager.addView(composeView, params)
        } catch (e: RuntimeException) {
            // Missing overlay permission or a bad token: leave no half-built state behind.
            view = null
            host = null
            newHost.destroy()
            onGone()
            return
        }
        composeView.requestFocus()
    }

    /** Screenshot, then system blur if there is none. Used when live capture is unavailable. */
    private suspend fun applyFallback(settings: PanelSettings, level: Int) {
        backdrop = BackdropCapture.capture(level, settings.patientCapture)
        if (backdrop == null && settings.blurMethod != BlurMethod.SCREENSHOT) applySystemBlur(level)
    }

    /** True when the system accepted a live blur request for this overlay window. */
    private fun applySystemBlur(level: Int): Boolean {
        val p = windowParams ?: return false
        val v = view ?: return false
        if (!p.applyBlurFallback(windowManager, BlurLevels.windowRadius(level))) return false
        windowBlurOn = true
        runCatching { windowManager.updateViewLayout(v, p) }
        return true
    }

    /** Starts the close animation; the window is removed when it finishes. */
    fun hide() {
        wanted = false
        if (view == null) return
        if (everVisible) visible = false else remove()
    }

    private companion object {
        const val TAG = "TCC"
        const val SCRIM_WITH_BACKDROP = 0.22f
        const val SCRIM_WITHOUT_BACKDROP = 0.32f

        /** Android refuses accessibility screenshots closer together than about 333 ms. */
        const val LIVE_FRAME_DELAY_MS = 380L
        const val LIVE_MISSES_BEFORE_FALLBACK = 3
    }

    private fun remove() {
        val v = view ?: return
        runCatching { windowManager.removeViewImmediate(v) }
        v.visibility = View.GONE
        host?.destroy()
        view = null
        host = null
        onGone()
    }
}
