package com.example.tvcontrolcenter.system.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
import com.example.tvcontrolcenter.system.applyBackdrop
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

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return
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
            val blur = ControlCenterEntryPoint.from(context).layoutRepository().settings.value.blurBehind
            applyBackdrop(windowManager, blur)
        }

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
        visible = true
        composeView.requestFocus()
    }

    /** Starts the close animation; the window is removed when it finishes. */
    fun hide() {
        if (view != null) visible = false
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
