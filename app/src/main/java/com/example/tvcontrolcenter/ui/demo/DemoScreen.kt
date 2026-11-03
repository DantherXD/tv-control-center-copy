package com.example.tvcontrolcenter.ui.demo

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.domain.model.GridPacker
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.system.openCapabilitySettings
import com.example.tvcontrolcenter.system.overlay.OverlayService
import com.example.tvcontrolcenter.system.trigger.ControlCenterAccessibilityService
import com.example.tvcontrolcenter.ui.background.AppWallpaper
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.panel.ControlCenterHost
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.panel.Detail
import com.example.tvcontrolcenter.ui.panel.GlassTile
import com.example.tvcontrolcenter.ui.panel.Label
import com.example.tvcontrolcenter.ui.panel.LivePanel
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.PanelType
import com.example.tvcontrolcenter.ui.theme.ProvideUiScale

/**
 * Home page. Left: what to do, and whether the three permissions the app needs are on (each
 * row opens the right system screen). Right: a live picture of your control center, so every
 * change made in the editor shows here too. The panel itself opens over this page.
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
    val openRequester = remember { FocusRequester() }
    val setup = rememberSetupState()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        AppWallpaper(state.settings.wallpaper, Modifier.fillMaxSize())

        // Fits the screen but ignores the user's panel size: the home page is not the panel.
        ProvideUiScale(1f) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicText(
                        stringResource(R.string.home_title),
                        style = PanelType.title.copy(color = GlassTokens.textPrimary),
                    )
                    BasicText(
                        stringResource(R.string.home_hint),
                        style = PanelType.hint.copy(color = GlassTokens.textSecondary),
                    )
                    // Everything here is inert while the panel is open over it.
                    if (!panelVisible) {
                        HomeButton(stringResource(R.string.home_open), openRequester) { onPanelVisibleChange(true) }
                        HomeButton(stringResource(R.string.demo_edit), null, onEdit)

                        BasicText(
                            stringResource(R.string.home_setup),
                            Modifier.padding(top = 12.dp),
                            style = PanelType.label.copy(color = GlassTokens.textSecondary),
                        )
                        SetupRow(stringResource(R.string.setup_accessibility), setup.accessibility) {
                            context.openCapabilitySettings(Capability.ACCESSIBILITY_SERVICE)
                        }
                        SetupRow(stringResource(R.string.setup_overlay), setup.overlay) {
                            if (setup.overlay) OverlayService.toggle(context)
                            else context.openCapabilitySettings(Capability.OVERLAY)
                        }
                        SetupRow(stringResource(R.string.setup_media), setup.media) {
                            context.openCapabilitySettings(Capability.NOTIFICATION_LISTENER)
                        }
                        LaunchedEffect(Unit) { runCatching { openRequester.requestFocus() } }
                    }
                }

                // Live preview: the real panel drawing, not clickable.
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    LivePreview(viewModel)
                }
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

private class SetupState(val accessibility: Boolean, val overlay: Boolean, val media: Boolean)

/** Re-reads the permissions every time the page comes back, so a row turns green after the user fixes it. */
@Composable
private fun rememberSetupState(): SetupState {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    return remember(tick) {
        SetupState(
            accessibility = ControlCenterAccessibilityService.instance != null,
            overlay = Settings.canDrawOverlays(context),
            media = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
        )
    }
}

@Composable
private fun HomeButton(label: String, requester: FocusRequester?, onClick: () -> Unit) {
    val r = requester ?: remember { FocusRequester() }
    GlassTile(
        label = label,
        cornerRadius = 22.dp,
        focusRequester = r,
        focusProps = {},
        onActivate = onClick,
        modifier = Modifier.size(width = 300.dp, height = 44.dp),
    ) {
        Label(label, Modifier.align(Alignment.Center), maxLines = 1)
    }
}

/** One permission: a coloured dot, its name, and whether it is on. Press OK to open its screen. */
@Composable
private fun SetupRow(label: String, ok: Boolean, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    val status = stringResource(if (ok) R.string.setup_on else R.string.setup_off)
    GlassTile(
        label = "$label, $status",
        cornerRadius = 22.dp,
        focusRequester = requester,
        focusProps = {},
        onActivate = onClick,
        modifier = Modifier.size(width = 300.dp, height = 44.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(10.dp).background(if (ok) Color(0xFF4CD964) else Color(0xFFFFB340), CircleShape))
            Label(label, Modifier.weight(1f), maxLines = 1)
            Detail(status)
        }
    }
}

@Composable
private fun LivePreview(viewModel: ControlCenterViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val layout = state.layout
    val placed = remember(layout) { GridPacker.pack(layout.visibleItems()) }
    val pinned = remember(layout) { GridPacker.pinned(layout.visibleItems()) }
    val requesters = remember { TileId.entries.associateWith { FocusRequester() } }
    // The real panel over a blurred screen, so the blur settings show here too.
    LivePanel(
        state = state,
        grid = placed,
        pinned = pinned,
        requesters = requesters,
        selected = null,
        interactive = false,
        onActivate = {},
    )
}
