package com.example.tvcontrolcenter.ui.demo

import android.provider.Settings
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.system.openCapabilitySettings
import com.example.tvcontrolcenter.system.overlay.OverlayService
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import com.example.tvcontrolcenter.ui.background.AcrylicBackground
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.panel.ControlCenterHost
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.panel.GlassTile
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.PanelType
import com.example.tvcontrolcenter.ui.theme.ProvideUiScale

/**
 * Demo scene: a moving acrylic background behind the glass, a clock, and four buttons.
 * Press OK on the background, or long-press Menu, to open the panel.
 */
@Composable
fun DemoScreen(
    viewModel: ControlCenterViewModel,
    panelVisible: Boolean,
    backdrop: Backdrop?,
    onPanelVisibleChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val rootRequester = remember { FocusRequester() }

    Box(Modifier.fillMaxSize()) {
        AcrylicBackground(Modifier.fillMaxSize())

        // Fits the screen but ignores the user's panel size: this is just stage dressing.
        ProvideUiScale(1f) {
            // Sharp, colourful shelves so the blur inside the glass is easy to see.
            Shelf(Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 24.dp))
            // Everything below sits behind the panel and is inert while it is open.
            if (!panelVisible) {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BasicText(
                        stringResource(R.string.demo_hint),
                        style = PanelType.hint.copy(color = Color.White),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SetupButton(R.string.demo_edit, onEdit)
                        SetupButton(R.string.enable_a11y) { context.openCapabilitySettings(Capability.ACCESSIBILITY_SERVICE) }
                        SetupButton(R.string.grant_overlay) {
                            if (Settings.canDrawOverlays(context)) OverlayService.toggle(context)
                            else context.openCapabilitySettings(Capability.OVERLAY)
                        }
                        SetupButton(R.string.grant_listener) { context.openCapabilitySettings(Capability.NOTIFICATION_LISTENER) }
                    }
                }
                // Invisible focus target so OK on the background opens the panel.
                Box(
                    Modifier
                        .size(1.dp)
                        .focusRequester(rootRequester)
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                                onPanelVisibleChange(true); true
                            } else false
                        }
                        .focusable(),
                )
                LaunchedEffect(Unit) { runCatching { rootRequester.requestFocus() } }
            }
        }

        ControlCenterHost(
            viewModel = viewModel,
            visible = panelVisible,
            onDismissRequest = { onPanelVisibleChange(false) },
            onClosed = {},
            onCapability = { context.openCapabilitySettings(it) },
            modifier = Modifier.fillMaxSize(),
            onOpenEditor = onEdit,
            backdrop = backdrop,
            scrimAlpha = 0.25f,
        )
    }
}

@Composable
private fun SetupButton(labelRes: Int, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    val label = stringResource(labelRes)
    GlassTile(
        label = label,
        cornerRadius = 22.dp,
        focusRequester = requester,
        focusProps = {},
        onActivate = onClick,
        modifier = Modifier.size(width = 190.dp, height = 44.dp),
    ) {
        BasicText(
            label,
            Modifier.align(Alignment.Center),
            style = PanelType.label.copy(color = GlassTokens.textPrimary),
            maxLines = 1,
        )
    }
}

/** Fake app content: rows of sharp cards that the panel's glass blurs. Not focusable. */
@Composable
private fun Shelf(modifier: Modifier) {
    val colors = listOf(
        listOf(Color(0xFFFF8A3D), Color(0xFFE23D6B)), listOf(Color(0xFF3DDCFF), Color(0xFF3D5BFF)),
        listOf(Color(0xFFB8FF5A), Color(0xFF1FA67A)), listOf(Color(0xFFFFE15A), Color(0xFFFF7A3D)),
        listOf(Color(0xFFD28BFF), Color(0xFF6A3DFF)),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(5) { col ->
                    Box(
                        Modifier
                            .size(width = 150.dp, height = 84.dp)
                            .background(Brush.linearGradient(colors[(row + col) % colors.size]), RoundedCornerShape(10.dp)),
                    ) {
                        BasicText(
                            "Title ${row * 5 + col + 1}",
                            Modifier.align(Alignment.BottomStart).padding(8.dp),
                            style = PanelType.label.copy(color = Color.White),
                        )
                    }
                }
            }
        }
    }
}
