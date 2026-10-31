package com.example.tvcontrolcenter

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tvcontrolcenter.ui.demo.DemoScreen
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.theme.ControlCenterTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Launcher entry point and demo host. Single activity; the panel is plain Compose
 * inside it. The same panel runs as a system overlay, see system/overlay.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ControlCenterViewModel by viewModels {
        com.example.tvcontrolcenter.di.ControlCenterEntryPoint.from(this).viewModelFactory()
    }

    private var panelVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ControlCenterTheme {
                DemoScreen(
                    viewModel = viewModel,
                    panelVisible = panelVisible,
                    onPanelVisibleChange = { panelVisible = it },
                )
            }
        }
    }

    // Long-press Menu opens the panel while this app is in front.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && !panelVisible) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            panelVisible = true
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }
}
