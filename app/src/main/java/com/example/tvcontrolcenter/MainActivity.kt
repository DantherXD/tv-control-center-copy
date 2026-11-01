package com.example.tvcontrolcenter

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tvcontrolcenter.ui.demo.DemoScreen
import com.example.tvcontrolcenter.ui.edit.EditScreen
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.theme.ControlCenterTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Launcher entry point. Shows the demo scene and, from there or from the panel's Edit
 * control, the layout editor. The same panel runs as a system overlay, see system/overlay.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ControlCenterViewModel by viewModels {
        com.example.tvcontrolcenter.di.ControlCenterEntryPoint.from(this).viewModelFactory()
    }

    private var panelVisible by mutableStateOf(false)
    private var editing by mutableStateOf(false)

    /** Started straight into the editor by the panel's Edit control: closing it leaves the app. */
    private var openedForEdit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntent(intent)
        setContent {
            ControlCenterTheme {
                if (editing) {
                    EditScreen(viewModel = viewModel, onDone = { closeEditor() })
                } else {
                    DemoScreen(
                        viewModel = viewModel,
                        panelVisible = panelVisible,
                        onPanelVisibleChange = { panelVisible = it },
                        onEdit = { editing = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_EDIT, false) == true) {
            openedForEdit = true
            editing = true
            panelVisible = false
        }
    }

    private fun closeEditor() {
        if (openedForEdit) finish() else editing = false
    }

    // Long-press Menu opens the panel while this app is in front.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && !panelVisible && !editing) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && !editing) {
            panelVisible = true
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    companion object {
        /** Boolean extra: open straight into the layout editor. */
        const val EXTRA_EDIT = "com.example.tvcontrolcenter.extra.EDIT"
    }
}
