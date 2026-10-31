package com.example.tvcontrolcenter.ui.demo

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.system.openCapabilitySettings
import com.example.tvcontrolcenter.system.overlay.OverlayService
import com.example.tvcontrolcenter.ui.glass.LocalGlassBackdrop
import com.example.tvcontrolcenter.ui.glass.rememberGlassBackdrop
import com.example.tvcontrolcenter.ui.panel.ControlCenterHost
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.panel.GlassTile
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import com.example.tvcontrolcenter.ui.theme.PanelType
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Demo scene: a forest wallpaper behind the glass, a status bar, and three setup
 * buttons. Press OK on the wallpaper, or long-press Menu, to open the panel.
 */
@Composable
fun DemoScreen(
    viewModel: ControlCenterViewModel,
    panelVisible: Boolean,
    onPanelVisibleChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val backdrop = rememberGlassBackdrop(R.drawable.wallpaper)
    val rootRequester = remember { FocusRequester() }

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { backdrop?.screenSize = it }
                .drawBehind {
                    if (backdrop != null) {
                        val r = backdrop.wallpaperRect()
                        drawImage(
                            backdrop.sharp,
                            dstOffset = IntOffset(r.left.toInt(), r.top.toInt()),
                            dstSize = IntSize(r.width.toInt(), r.height.toInt()),
                        )
                    } else drawRect(Color(0xFF0F3D2E))
                },
        ) {
            StatusBar(Modifier.align(Alignment.TopEnd))

            // Everything below sits behind the panel and is inert while it is open.
            if (!panelVisible) {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BasicText(
                        stringResource(R.string.demo_hint),
                        style = PanelType.hint.copy(color = Color.White),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SetupButton(R.string.enable_a11y) { context.openCapabilitySettings(Capability.ACCESSIBILITY_SERVICE) }
                        SetupButton(R.string.grant_overlay) {
                            if (Settings.canDrawOverlays(context)) OverlayService.toggle(context)
                            else context.openCapabilitySettings(Capability.OVERLAY)
                        }
                        SetupButton(R.string.grant_listener) { context.openCapabilitySettings(Capability.NOTIFICATION_LISTENER) }
                    }
                }
                // Invisible focus target so OK on the "wallpaper" opens the panel.
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

            ControlCenterHost(
                viewModel = viewModel,
                visible = panelVisible,
                onDismissRequest = { onPanelVisibleChange(false) },
                onClosed = {},
                onCapability = { context.openCapabilitySettings(it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SetupButton(labelRes: Int, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    val label = stringResource(labelRes)
    GlassTile(
        label = label,
        cornerRadius = 30.dp,
        focusRequester = requester,
        focusProps = {},
        onActivate = onClick,
        modifier = Modifier.size(width = 220.dp, height = 60.dp),
    ) {
        BasicText(
            label,
            Modifier.align(Alignment.Center),
            style = PanelType.label.copy(color = GlassTokens.textPrimary),
        )
    }
}

@Composable
private fun StatusBar(modifier: Modifier = Modifier) {
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm") }
    var time by remember { mutableStateOf(LocalTime.now().format(formatter)) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalTime.now().format(formatter)
            kotlinx.coroutines.delay(10_000)
        }
    }
    Row(
        modifier.padding(top = PanelDimens.screenInsetV, end = PanelDimens.screenInsetH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(time, style = PanelType.clock.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
    }
}
