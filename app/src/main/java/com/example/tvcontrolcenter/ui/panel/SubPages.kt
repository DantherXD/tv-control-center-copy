package com.example.tvcontrolcenter.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.NetworkKind
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TvInputItem
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.glass.LocalInvertFocus
import com.example.tvcontrolcenter.ui.glass.LocalTileInk
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the Wi-Fi, Bluetooth and Input pages can ask of the system. */
internal class SubPageActions(
    val wifiEnabled: () -> Boolean,
    val wifiName: () -> String?,
    val setWifi: (Boolean) -> Unit,
    val openWifiSettings: () -> Unit,
    val setBluetooth: (Boolean) -> Unit,
    val openBluetoothSettings: () -> Unit,
    val pairedBluetooth: () -> List<String>,
    val inputs: () -> List<TvInputItem>,
    val switchInput: (String) -> Unit,
)

/**
 * The small window a long press on Wi-Fi or Bluetooth (or a press on Input) opens in place of the
 * grid: a title, then rows. The first row takes focus; Back closes the window.
 */
@Composable
internal fun SubPageCard(
    page: PanelPage,
    state: ControlCenterUiState,
    actions: SubPageActions,
    first: FocusRequester,
    backdrop: Backdrop?,
    origin: Offset,
    sheet: Boolean,
    modifier: Modifier = Modifier,
) {
    val glass = cardGlass(backdrop, origin, sheet)
    val tile = when (page) {
        PanelPage.BLUETOOTH -> TileId.BLUETOOTH
        PanelPage.INPUT -> TileId.INPUT
        else -> TileId.WIFI
    }
    Column(
        modifier
            .popIn()
            .size(PanelDimens.gridWidth, PanelDimens.subPageHeight)
            .then(glass)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TileIcon(lookOf(tile).icon, 20.dp, Color.White)
            Label(stringResource(lookOf(tile).label), maxLines = 1)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (page) {
                PanelPage.WIFI -> WifiRows(state, actions, first)
                PanelPage.BLUETOOTH -> BluetoothRows(state, actions, first)
                else -> InputRows(actions, first)
            }
        }
    }
}

@Composable
private fun onOffText(on: Boolean) = stringResource(if (on) R.string.state_on else R.string.state_off)

@Composable
private fun WifiRows(state: ControlCenterUiState, actions: SubPageActions, first: FocusRequester) {
    val network = when (state.status.network) {
        NetworkKind.WIFI -> actions.wifiName() ?: stringResource(R.string.sub_connected)
        NetworkKind.ETHERNET -> "Ethernet"
        NetworkKind.NONE -> stringResource(R.string.sub_not_connected)
    }
    SheetInfo(stringResource(R.string.sub_status), onOffText(actions.wifiEnabled() || state.status.network == NetworkKind.WIFI))
    SheetInfo(stringResource(R.string.sub_network), network)
    SheetRow(stringResource(R.string.sub_open_wifi), null, first, actions.openWifiSettings)
}

@Composable
private fun BluetoothRows(state: ControlCenterUiState, actions: SubPageActions, first: FocusRequester) {
    val paired = remember { actions.pairedBluetooth() }
    SheetInfo(stringResource(R.string.sub_status), onOffText(state.status.bluetoothEnabled))
    SheetInfo(stringResource(R.string.sub_bt_audio), state.status.audioDeviceName ?: stringResource(R.string.sub_bt_none))
    paired.forEach { SheetInfo(stringResource(R.string.sub_paired), it) }
    SheetRow(stringResource(R.string.sub_open_remotes), null, first, actions.openBluetoothSettings)
}

@Composable
private fun InputRows(actions: SubPageActions, first: FocusRequester) {
    val inputs = remember { actions.inputs() }
    if (inputs.isEmpty()) {
        SheetInfo(stringResource(R.string.sub_input_none), "")
    } else {
        inputs.forEachIndexed { i, input ->
            SheetRow(input.label, null, if (i == 0) first else null) { actions.switchInput(input.id) }
        }
    }
}

/** A focusable row: name on the left, value on the right. White when focused, like the other controls. */
@Composable
private fun SheetRow(label: String, value: String?, requester: FocusRequester?, activate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val invert = LocalInvertFocus.current
    val dark = focused && invert
    val r = requester ?: remember { FocusRequester() }
    // The window opens while OK is still held (long press), so the release lands on this row.
    // Only a press that started here counts, or opening Wi-Fi would also toggle it.
    val pressed = remember { booleanArrayOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .focusRequester(r)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                when {
                    e.key.isOk() && e.type == KeyEventType.KeyDown -> {
                        // Repeats come from a key still held since before this row existed: not a press.
                        if (e.nativeKeyEvent.repeatCount == 0) pressed[0] = true
                        true
                    }
                    e.key.isOk() && e.type == KeyEventType.KeyUp -> {
                        if (pressed[0]) activate()
                        pressed[0] = false
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .semantics(mergeDescendants = true) {
                contentDescription = if (value == null) label else "$label, $value"
                role = Role.Button
                onClick(label = label) { activate(); true }
            }
            .clip(CircleShape)
            .background(
                when {
                    !focused -> Color.Black.copy(alpha = 0.30f)
                    invert -> Color.White
                    else -> Color.White.copy(alpha = 0.30f)
                },
            ),
    ) {
        CompositionLocalProvider(LocalTileInk provides if (dark) GlassTokens.inkDark else Color.White) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Label(label, Modifier.weight(1f), maxLines = 1)
                if (value != null) Detail(value)
            }
        }
    }
}

/** A row that only shows something. */
@Composable
private fun SheetInfo(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Detail(label, Modifier.weight(1f))
        if (value.isNotEmpty()) Label(value, maxLines = 1)
    }
}

private fun Key.isOk() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter
