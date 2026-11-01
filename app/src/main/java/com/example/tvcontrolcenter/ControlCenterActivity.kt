package com.example.tvcontrolcenter

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.tvcontrolcenter.di.ControlCenterEntryPoint
import com.example.tvcontrolcenter.system.applyBackdrop
import com.example.tvcontrolcenter.system.openCapabilitySettings
import com.example.tvcontrolcenter.ui.panel.ControlCenterHost
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.theme.ControlCenterTheme

/**
 * Opens the Control Center on top of whatever app is in front.
 *
 * The window is transparent, so the app underneath stays visible behind the frosted panel
 * (blurred on Android 12+ devices that allow window blur). Start it from anywhere: a remote-button
 * remapper, the launcher row, or
 *
 *     adb shell am start -a com.example.tvcontrolcenter.action.OPEN
 *
 * Starting it again while it is open closes it. It finishes by itself when it loses the
 * screen, so it never lingers in recents.
 *
 * Trade-off: unlike the accessibility overlay, the app underneath receives onPause, so some
 * players pause while the panel is open. The overlay path (long-press Menu with the
 * accessibility service on) does not pause anything.
 */
class ControlCenterActivity : ComponentActivity() {

    private val viewModel: ControlCenterViewModel by viewModels {
        ControlCenterEntryPoint.from(this).viewModelFactory()
    }

    // Not named `visible`: its setter would clash with Activity.setVisible(Boolean).
    private var panelOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        setContent {
            ControlCenterTheme {
                ControlCenterHost(
                    viewModel = viewModel,
                    visible = panelOpen,
                    onDismissRequest = { panelOpen = false },
                    onClosed = { if (!panelOpen) finish() },
                    onCapability = { openCapabilitySettings(it) },
                    modifier = Modifier.fillMaxSize(),
                    onOpenEditor = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_EDIT, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
        }
        panelOpen = true
    }

    /** singleTask re-launch: a second start closes the panel, a start while closing reopens it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        panelOpen = !panelOpen
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    private fun configureWindow() {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val blur = ControlCenterEntryPoint.from(this).layoutRepository().settings.value.blurBehind
        val params = window.attributes
        params.applyBackdrop(windowManager, blur)
        window.attributes = params
    }
}
