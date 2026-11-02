package com.example.tvcontrolcenter.system.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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
import com.example.tvcontrolcenter.system.applyBlurFallback
import com.example.tvcontrolcenter.system.backdrop.BackdropCapture
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.system.openCapabilitySettings
import com.example.tvcontrolcenter.ui.panel.ControlCenterHost
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.theme.ControlCenterTheme

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
 * On Android 12+ the window asks for blur-behind (the "Blur behind" setting). Devices with
 * cross-window blur enabled then show a real blurred view of the running app behind the
 * frosted panel; others get a stronger dim. Android does not let an overlay read the pixels
 * behind it, so refraction of the running app is not possible. See README.
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
    private var windowBlurOn = false

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
                    // The window is on screen but draws nothing yet, so the screenshot shows the
                    // app alone. The panel opens as soon as the picture is ready (or has failed).
                    LaunchedEffect(Unit) {
                        val settings = ControlCenterEntryPoint.from(context).layoutRepository().settings.value
                        val level = settings.effectiveBlur
                        backdrop = BackdropCapture.capture(level)
                        // No picture (old Android, DRM video, slow device) -> ask the system to blur the screen.
                        if (backdrop == null && settings.blurMethod != BlurMethod.SCREENSHOT && !windowBlurOn) {
                            val p = windowParams
                            val v = view
                            if (p != null && v != null && p.applyBlurFallback(windowManager, BlurLevels.windowRadius(level))) {
                                windowBlurOn = true
                                runCatching { windowManager.updateViewLayout(v, p) }
                            }
                        }
                        if (wanted) {
                            everVisible = true
                            visible = true
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
                        scrimAlpha = if (backdrop != null) SCRIM_WITH_BACKDROP else SCRIM_WITHOUT_BACKDROP,
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
            // System window blur only when there is no screenshot to blur ourselves.
            val wantsWindow = s.blurMethod != BlurMethod.SCREENSHOT && !BackdropCapture.isSupported
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

    /** Starts the close animation; the window is removed when it finishes. */
    fun hide() {
        wanted = false
        if (view == null) return
        if (everVisible) visible = false else remove()
    }

    private companion object {
        const val SCRIM_WITH_BACKDROP = 0.22f
        const val SCRIM_WITHOUT_BACKDROP = 0.32f
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
