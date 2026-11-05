package com.example.tvcontrolcenter.ui.demo

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.example.tvcontrolcenter.ui.app.AppButton
import com.example.tvcontrolcenter.ui.app.AppButtonTone
import com.example.tvcontrolcenter.ui.app.AppCard
import com.example.tvcontrolcenter.ui.app.AppEyebrow
import com.example.tvcontrolcenter.ui.app.AppPreview
import com.example.tvcontrolcenter.ui.app.AppScreenBackground
import com.example.tvcontrolcenter.ui.app.AppSectionLabel
import com.example.tvcontrolcenter.ui.app.AppSetupRow
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.panel.ControlCenterHost
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.panel.LivePanel
import com.example.tvcontrolcenter.ui.theme.AppDimens
import com.example.tvcontrolcenter.ui.theme.AppPalette
import com.example.tvcontrolcenter.ui.theme.AppType
import com.example.tvcontrolcenter.ui.theme.ProvideUiScale

/**
 * Home page. The left side explains the app in one line, offers one unmistakable action and a
 * compact permission checklist (each row opens the right system screen). The right side frames
 * the real panel drawing, so every editor change shows here too. The panel itself opens over this
 * page.
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
        AppScreenBackground(state.settings.wallpaper, Modifier.fillMaxSize())

        // Fits the screen but ignores the user's panel size: the home page is not the panel.
        ProvideUiScale(1f) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = AppDimens.screenHorizontal,
                        vertical = AppDimens.screenVertical,
                    ),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.columnGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppEyebrow(stringResource(R.string.home_eyebrow))
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        stringResource(R.string.home_title),
                        style = AppType.display.copy(color = AppPalette.textPrimary),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        stringResource(R.string.home_hint),
                        Modifier.fillMaxWidth(0.9f),
                        style = AppType.subtitle.copy(color = AppPalette.textSecondary),
                    )

                    Spacer(Modifier.height(20.dp))
                    // Everything here is inert while the panel is open over it.
                    if (!panelVisible) {
                        AppButton(
                            label = stringResource(R.string.home_open),
                            onClick = { onPanelVisibleChange(true) },
                            modifier = Modifier.width(320.dp),
                            tone = AppButtonTone.Primary,
                            requester = openRequester,
                            height = 54.dp,
                            showArrow = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        AppButton(
                            label = stringResource(R.string.demo_edit),
                            onClick = onEdit,
                            modifier = Modifier.width(320.dp),
                            tone = AppButtonTone.Secondary,
                            height = 44.dp,
                        )

                        Spacer(Modifier.height(20.dp))
                        AppSectionLabel(stringResource(R.string.home_setup))
                        Spacer(Modifier.height(8.dp))
                        AppCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AppSetupRow(
                                    label = stringResource(R.string.setup_accessibility),
                                    ok = setup.accessibility,
                                    status = stringResource(
                                        if (setup.accessibility) R.string.setup_on else R.string.setup_off,
                                    ),
                                    onClick = {
                                        context.openCapabilitySettings(Capability.ACCESSIBILITY_SERVICE)
                                    },
                                )
                                AppSetupRow(
                                    label = stringResource(R.string.setup_overlay),
                                    ok = setup.overlay,
                                    status = stringResource(
                                        if (setup.overlay) R.string.setup_on else R.string.setup_off,
                                    ),
                                    onClick = {
                                        if (setup.overlay) OverlayService.toggle(context)
                                        else context.openCapabilitySettings(Capability.OVERLAY)
                                    },
                                )
                                AppSetupRow(
                                    label = stringResource(R.string.setup_media),
                                    ok = setup.media,
                                    status = stringResource(
                                        if (setup.media) R.string.setup_on else R.string.setup_off,
                                    ),
                                    onClick = {
                                        context.openCapabilitySettings(Capability.NOTIFICATION_LISTENER)
                                    },
                                )
                            }
                        }
                        LaunchedEffect(Unit) { runCatching { openRequester.requestFocus() } }
                    }
                }

                Column(
                    Modifier.width(AppDimens.previewWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Live preview: the real panel drawing, not clickable.
                    AppPreview(
                        label = stringResource(R.string.home_preview),
                        note = stringResource(R.string.home_preview_note),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LivePreview(viewModel)
                    }
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
