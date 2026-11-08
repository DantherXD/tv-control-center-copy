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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
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
import com.example.tvcontrolcenter.system.openCapabilitySettings
import com.example.tvcontrolcenter.system.overlay.OverlayService
import com.example.tvcontrolcenter.system.trigger.ControlCenterAccessibilityService
import com.example.tvcontrolcenter.ui.app.AppDestination
import com.example.tvcontrolcenter.ui.app.AppNavigationRail
import com.example.tvcontrolcenter.ui.app.AppScreenBackground
import com.example.tvcontrolcenter.ui.app.AppSettingGroup
import com.example.tvcontrolcenter.ui.app.AppStatusRow
import com.example.tvcontrolcenter.ui.app.AppWideButton
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.panel.ControlCenterHost
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.theme.AppDimens
import com.example.tvcontrolcenter.ui.theme.AppPalette
import com.example.tvcontrolcenter.ui.theme.AppType
import com.example.tvcontrolcenter.ui.theme.ProvideUiScale

/**
 * Home page: a persistent navigation rail, one large title, one primary action and the setup
 * list. The real Control Center is composed over this page only while it is open, so Home does
 * not pay for the panel's wallpaper or blur work.
 */
@Composable
fun DemoScreen(
    viewModel: ControlCenterViewModel,
    panelVisible: Boolean,
    backdrop: Backdrop?,
    onPanelVisibleChange: (Boolean) -> Unit,
    onNavigate: (AppDestination) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openRequester = remember { FocusRequester() }
    val setup = rememberSetupState()
    val ready = listOf(setup.accessibility, setup.overlay, setup.media).count { it }
    val summary = if (ready == 3) {
        stringResource(R.string.home_setup_all)
    } else {
        stringResource(R.string.home_setup_summary, ready, 3)
    }

    Box(Modifier.fillMaxSize()) {
        AppScreenBackground(
            wallpaper = state.settings.wallpaper,
            modifier = Modifier.fillMaxSize(),
            animated = !panelVisible,
        )

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
            ) {
                AppNavigationRail(
                    selected = AppDestination.Home,
                    onSelect = onNavigate,
                )

                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    BasicText(
                        stringResource(R.string.home_title),
                        style = AppType.display.copy(color = AppPalette.textPrimary),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        stringResource(R.string.home_hint),
                        style = AppType.subtitle.copy(color = AppPalette.textSecondary),
                        maxLines = 1,
                    )

                    Spacer(Modifier.height(24.dp))
                    // Everything here is inert while the panel is open over it.
                    if (!panelVisible) {
                        AppWideButton(
                            title = stringResource(R.string.home_open),
                            subtitle = stringResource(R.string.home_open_subtitle),
                            icon = Icons.Rounded.PowerSettingsNew,
                            onClick = { onPanelVisibleChange(true) },
                            modifier = Modifier.fillMaxWidth(),
                            requester = openRequester,
                        )

                        Spacer(Modifier.height(28.dp))
                        AppSettingGroup(
                            title = stringResource(R.string.home_setup),
                            trailing = {
                                BasicText(
                                    summary,
                                    style = AppType.badge.copy(
                                        color = if (ready == 3) AppPalette.success else AppPalette.textSecondary,
                                    ),
                                )
                            },
                        ) {
                            AppStatusRow(
                                label = stringResource(R.string.setup_accessibility),
                                status = stringResource(
                                    if (setup.accessibility) R.string.setup_on else R.string.setup_off,
                                ),
                                tone = if (setup.accessibility) AppPalette.success else AppPalette.warning,
                                onClick = {
                                    context.openCapabilitySettings(Capability.ACCESSIBILITY_SERVICE)
                                },
                            )
                            AppStatusRow(
                                label = stringResource(R.string.setup_overlay),
                                status = stringResource(
                                    if (setup.overlay) R.string.setup_on else R.string.setup_off,
                                ),
                                tone = if (setup.overlay) AppPalette.success else AppPalette.warning,
                                onClick = {
                                    if (setup.overlay) OverlayService.toggle(context)
                                    else context.openCapabilitySettings(Capability.OVERLAY)
                                },
                            )
                            AppStatusRow(
                                label = stringResource(R.string.setup_media),
                                status = stringResource(
                                    if (setup.media) R.string.setup_on else R.string.setup_off,
                                ),
                                tone = if (setup.media) AppPalette.success else AppPalette.warning,
                                onClick = {
                                    context.openCapabilitySettings(Capability.NOTIFICATION_LISTENER)
                                },
                            )
                        }
                        LaunchedEffect(Unit) { runCatching { openRequester.requestFocus() } }
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
            onOpenEditor = { onNavigate(AppDestination.Layout) },
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
