package com.example.tvcontrolcenter.ui.panel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.NetworkKind
import com.example.tvcontrolcenter.domain.model.Placed
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TvInputItem
import com.example.tvcontrolcenter.domain.model.WindowMotion
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.glass.LocalInvertFocus
import com.example.tvcontrolcenter.ui.glass.LocalTileInk
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.LocalNoticeScale
import com.example.tvcontrolcenter.ui.theme.ProvideTextScale
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import kotlinx.coroutines.delay

/** What the Wi-Fi, Bluetooth and Input pages can ask of the system. */
internal class SubPageActions(
    val wifiEnabled: () -> Boolean,
    val bluetoothEnabled: () -> Boolean,
    val wifiName: () -> String?,
    val setWifi: (Boolean) -> Unit,
    val openWifiSettings: () -> Unit,
    val setBluetooth: (Boolean) -> Unit,
    val openBluetoothSettings: () -> Unit,
    val pairedBluetooth: () -> List<String>,
    val inputs: () -> List<TvInputItem>,
    val switchInput: (String) -> Unit,
    val openNotification: (String) -> Unit,
    val clearNotifications: () -> Unit,
    val openNotificationAccess: () -> Unit,
)

/** One line of a small window: a value, or something OK can press. */
private sealed interface SubRow {
    val label: String

    /** Height this line needs; the window is sized from the sum of these. */
    val height: Dp

    data class Info(override val label: String, val value: String) : SubRow {
        override val height: Dp get() = INFO_ROW
    }

    /** Something OK can press; [value] is drawn on the right, for the rows that switch a radio. */
    data class Action(
        override val label: String,
        val value: String? = null,
        val activate: () -> Unit,
    ) : SubRow {
        override val height: Dp get() = ACTION_ROW
    }

    /** A notification: the app above, then the notification's own title and a line of its text. */
    data class Notice(
        val appLabel: String,
        val title: String,
        val text: String?,
        val activate: () -> Unit,
    ) : SubRow {
        override val label: String get() = title
        override val height: Dp get() = NOTICE_ROW
    }
}

private val INFO_ROW = 30.dp
private val ACTION_ROW = 38.dp
private val NOTICE_ROW = PanelDimens.notificationRow
private val TITLE_ROW = 30.dp
private val ROW_GAP = 6.dp
private val WINDOW_PADDING = 10.dp
/** Everything that is not a row: the title line, the gap under it and the window's padding. */
private val CHROME = TITLE_ROW + ROW_GAP + WINDOW_PADDING * 2

/** The control a small window belongs to; the window grows out of it. */
internal fun subTileOf(page: PanelPage): TileId = when (page) {
    PanelPage.BLUETOOTH -> TileId.BLUETOOTH
    PanelPage.INPUT -> TileId.INPUT
    PanelPage.NOTIFICATIONS -> TileId.NOTIFICATIONS
    else -> TileId.WIFI
}

/**
 * The small window a long press on Wi-Fi or Bluetooth (or a press on Input) opens in place of the
 * grid: a title, then one line per row. [motion] decides how it arrives and leaves; it is as tall
 * as its rows so none of them hides below the fold. The first pressable row takes focus; Back
 * closes the window.
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
    from: Placed? = null,
    motion: WindowMotion = WindowMotion.MID_AIR,
    closing: Boolean = false,
    onClosed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glass = cardGlass(backdrop, origin, sheet)
    val tile = subTileOf(page)
    // Read once per window: both lists come from the system, and the height depends on them.
    val paired = remember(page) { if (page == PanelPage.BLUETOOTH) actions.pairedBluetooth() else emptyList() }
    val inputs = remember(page) { if (page == PanelPage.INPUT) actions.inputs() else emptyList() }
    // Wi-Fi and Bluetooth are switched from their own window. A press has to show at once, and the
    // system is asked again a moment later, since a TV that refuses the change shows its own screen
    // instead and the row must not keep claiming it worked.
    var wifiOn by remember(page) { mutableStateOf(actions.wifiEnabled()) }
    var bluetoothOn by remember(page) { mutableStateOf(state.status.bluetoothEnabled) }
    var settles by remember(page) { mutableStateOf(0) }
    LaunchedEffect(page, state.status.network, state.status.bluetoothEnabled) {
        wifiOn = actions.wifiEnabled()
        bluetoothOn = state.status.bluetoothEnabled
    }
    LaunchedEffect(settles) {
        if (settles == 0) return@LaunchedEffect
        delay(RADIO_SETTLE_MILLIS)
        wifiOn = actions.wifiEnabled()
        bluetoothOn = actions.bluetoothEnabled()
    }
    val rows = when (page) {
        PanelPage.WIFI -> WifiRows(state, wifiOn, actions) { next ->
            wifiOn = next
            actions.setWifi(next)
            settles++
        }
        PanelPage.BLUETOOTH -> BluetoothRows(state, paired, bluetoothOn, actions) { next ->
            bluetoothOn = next
            actions.setBluetooth(next)
            settles++
        }
        PanelPage.NOTIFICATIONS -> NotificationRows(state, actions)
        else -> InputRows(inputs, actions)
    }
    // A window with nothing to list says so in its middle instead of with a row.
    val emptyNotice = page == PanelPage.NOTIFICATIONS && state.notificationAccess &&
        state.notifications.isEmpty()
    val rowsHeight = rows.fold(0.dp) { sum, row -> sum + row.height } +
        ROW_GAP * (rows.size - 1).coerceAtLeast(0)
    // Tall enough for every row at once, up to a cap; past that the rows scroll. The notifications
    // window is allowed to grow further than the others, since there is more of them than inputs.
    val tallest = if (page == PanelPage.NOTIFICATIONS) PanelDimens.notificationsMaxHeight
        else PanelDimens.subPageMaxHeight
    val height = (CHROME + rowsHeight).coerceIn(PanelDimens.subPageHeight, tallest)
    val firstRow = rows.indexOfFirst { it is SubRow.Action || it is SubRow.Notice }
    Column(
        modifier
            .windowMotion(motion, from, PanelDimens.gridWidth, height, closing, onClosed)
            .size(PanelDimens.gridWidth, height)
            // Nothing to press (no inputs found): the window itself takes focus, so Back works.
            .then(if (firstRow < 0 && !closing) Modifier.focusRequester(first).focusable() else Modifier)
            .then(glass)
            .padding(WINDOW_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        Row(
            Modifier.fillMaxWidth().height(TITLE_ROW).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TileIcon(lookOf(tile).icon, 20.dp, Color.White)
            Label(stringResource(lookOf(tile).label), maxLines = 1)
        }
        if (emptyNotice) {
            EmptyNotice(Modifier.weight(1f))
        } else Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        ) {
            rows.forEachIndexed { index, row ->
                when (row) {
                    is SubRow.Info -> SheetInfo(row.label, row.value)
                    // While the window is on its way home, a press does nothing: it is leaving.
                    is SubRow.Action -> SheetRow(
                        label = row.label,
                        value = row.value,
                        requester = if (index == firstRow) first else null,
                        focusable = !closing,
                        activate = if (closing) NoActivate else row.activate,
                    )
                    is SubRow.Notice -> SheetNoticeRow(
                        appLabel = row.appLabel,
                        title = row.title,
                        text = row.text,
                        requester = if (index == firstRow) first else null,
                        focusable = !closing,
                        activate = if (closing) NoActivate else row.activate,
                    )
                }
            }
        }
    }
}

/**
 * How the window arrives and leaves, chosen by [motion] in the editor.
 *
 * [WindowMotion.FROM_CONTROL] starts as exactly the control that opened it and springs to its full
 * size, the way iOS opens a control's detail. [WindowMotion.MID_AIR] starts a little smaller in the
 * window's own place and grows, so the picture inside it is never stretched into a tile's shape.
 * [WindowMotion.OFF] draws it at once. [closing] runs whichever motion backwards, so the window
 * leaves the way it arrived; [onClosed] fires when it has landed, and only then does the host drop
 * the window.
 */
@Composable
private fun Modifier.windowMotion(
    motion: WindowMotion,
    from: Placed?,
    width: Dp,
    height: Dp,
    closing: Boolean,
    onClosed: () -> Unit,
): Modifier {
    // From the control needs a control to come from; with none, or with no motion at all, there is
    // nothing to animate, but the close must still finish or Back would leave the window stuck.
    val tile = if (motion == WindowMotion.FROM_CONTROL) from else null
    if (motion == WindowMotion.OFF || (motion == WindowMotion.FROM_CONTROL && tile == null)) {
        LaunchedEffect(closing) { if (closing) onClosed() }
        return this
    }
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = 520f))
    }
    // Closing reverses the open, from wherever it got to; the running open is cancelled.
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 700f))
        onClosed()
    }
    val start = if (tile != null) {
        with(density) {
            Start(
                x = (PanelDimens.cell + PanelDimens.gap).toPx() * tile.col,
                y = (PanelDimens.cell + PanelDimens.gap).toPx() * tile.row,
                scaleX = span(tile.cols).toPx() / width.toPx(),
                scaleY = span(tile.rows).toPx() / height.toPx(),
            )
        }
    } else {
        Start(x = 0f, y = 0f, scaleX = MID_AIR_SCALE, scaleY = MID_AIR_SCALE)
    }
    return this.graphicsLayer {
        val p = progress.value
        val s = p.coerceIn(0f, 1f)
        // A tile's shape has to be anchored at its top-left corner to land on the control; a
        // mid-air window grows out of its own middle.
        transformOrigin = if (tile != null) TransformOrigin(0f, 0f) else TransformOrigin.Center
        scaleX = start.scaleX + (1f - start.scaleX) * s
        scaleY = start.scaleY + (1f - start.scaleY) * s
        // Maps the window's own rect onto the tile's rect at the first frame: same place, small.
        translationX = start.x * (1f - s)
        translationY = start.y * (1f - s)
        // Opening from a control fades in over the first frames; anything else stays solid until
        // it is nearly home on the way out.
        val fade = when {
            closing -> 1.4f
            tile != null -> 4f
            else -> 2.2f
        }
        alpha = (p * fade).coerceIn(0f, 1f)
    }
}

/** How much smaller a mid-air window is at its first frame. */
private const val MID_AIR_SCALE = 0.94f

/** How long a radio switch shows its press before asking the system what really happened. */
private const val RADIO_SETTLE_MILLIS = 1_200L

/** Where the window starts: the opened control's top-left corner and the scale that fits it. */
private class Start(val x: Float, val y: Float, val scaleX: Float, val scaleY: Float)

@Composable
private fun onOffText(on: Boolean) = stringResource(if (on) R.string.state_on else R.string.state_off)

@Composable
private fun WifiRows(
    state: ControlCenterUiState,
    on: Boolean,
    actions: SubPageActions,
    toggle: (Boolean) -> Unit,
): List<SubRow> {
    val network = when (state.status.network) {
        NetworkKind.WIFI -> actions.wifiName() ?: stringResource(R.string.sub_connected)
        NetworkKind.ETHERNET -> "Ethernet"
        NetworkKind.NONE -> stringResource(R.string.sub_not_connected)
    }
    return listOf(
        SubRow.Action(stringResource(R.string.tile_wifi), onOffText(on)) { toggle(!on) },
        SubRow.Info(stringResource(R.string.sub_network), network),
        SubRow.Action(stringResource(R.string.sub_open_wifi)) { actions.openWifiSettings() },
    )
}

@Composable
private fun BluetoothRows(
    state: ControlCenterUiState,
    paired: List<String>,
    on: Boolean,
    actions: SubPageActions,
    toggle: (Boolean) -> Unit,
): List<SubRow> = buildList {
    add(SubRow.Action(stringResource(R.string.tile_bluetooth), onOffText(on)) { toggle(!on) })
    add(
        SubRow.Info(
            stringResource(R.string.sub_bt_audio),
            state.status.audioDeviceName ?: stringResource(R.string.sub_bt_none),
        ),
    )
    paired.forEach { add(SubRow.Info(stringResource(R.string.sub_paired), it)) }
    add(SubRow.Action(stringResource(R.string.sub_pair_accessory)) { actions.openBluetoothSettings() })
}

/** Every input is its own row, so they all show at once. */
@Composable
private fun InputRows(inputs: List<TvInputItem>, actions: SubPageActions): List<SubRow> =
    if (inputs.isEmpty()) {
        listOf(SubRow.Info(stringResource(R.string.sub_input_none), ""))
    } else {
        inputs.map { input -> SubRow.Action(input.label) { actions.switchInput(input.id) } }
    }

/**
 * One row per notification, newest first, then Clear all once something can actually be cleared.
 * Without the listener grant the window still opens, with the one row that leads to the grant.
 */
@Composable
private fun NotificationRows(state: ControlCenterUiState, actions: SubPageActions): List<SubRow> {
    if (!state.notificationAccess) {
        return listOf(
            SubRow.Action(stringResource(R.string.sub_notif_access)) { actions.openNotificationAccess() },
        )
    }
    // Nothing to list: the window shows its own empty state in the middle (see SubPageCard).
    if (state.notifications.isEmpty()) return emptyList()
    return buildList {
        state.notifications.forEach { notice ->
            add(
                SubRow.Notice(
                    appLabel = notice.appLabel,
                    title = notice.title,
                    text = notice.text,
                    activate = { actions.openNotification(notice.key) },
                ),
            )
        }
        if (state.notifications.any { it.clearable }) {
            add(SubRow.Action(stringResource(R.string.sub_notif_clear_all)) { actions.clearNotifications() })
        }
    }
}

/** A focusable row: name on the left, value on the right. White when focused, like the other controls. */
@Composable
private fun SheetRow(
    label: String,
    value: String?,
    requester: FocusRequester?,
    focusable: Boolean = true,
    activate: () -> Unit,
) {
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
            .height(ACTION_ROW)
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
            .focusable(focusable)
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

/**
 * A notification row: the app on the left of the title, then a line of the notification's text.
 * Two lines inside the usual 38 dp would not fit at TV distances, so the row is taller.
 */
@Composable
private fun SheetNoticeRow(
    appLabel: String,
    title: String,
    text: String?,
    requester: FocusRequester?,
    focusable: Boolean = true,
    activate: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val invert = LocalInvertFocus.current
    val dark = focused && invert
    val r = requester ?: remember { FocusRequester() }
    // Same press rule as the other rows: only an OK that started on this row counts.
    val pressed = remember { booleanArrayOf(false) }
    // A notification titled with the app's own name would otherwise say it twice on one line.
    val showApp = appLabel != title
    Box(
        Modifier
            .fillMaxWidth()
            .height(NOTICE_ROW)
            .focusRequester(r)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                when {
                    e.key.isOk() && e.type == KeyEventType.KeyDown -> {
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
            .focusable(focusable)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(appLabel.takeIf { showApp }, title, text).joinToString(", ")
                role = Role.Button
                onClick(label = title) { activate(); true }
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
            // The list has its own text size: two lines at the panel's normal size read large, and
            // the row keeps its height, so only the text follows this and not the window.
            ProvideTextScale(LocalNoticeScale.current) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (showApp) Detail(appLabel, Modifier.widthIn(max = 108.dp))
                        Label(title, Modifier.weight(1f), maxLines = 1)
                    }
                    if (!text.isNullOrBlank()) {
                        Detail(text, Modifier.fillMaxWidth().padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** Nothing to show: a trophy and a word about it, in the middle of the window. */
@Composable
private fun EmptyNotice(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Drawn straight from the filled Material icon: the trophy is filled by design, so the
        // icon style and the Phosphor pack must not turn it into its outline.
        Icon(
            Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
        Label(stringResource(R.string.sub_notif_caught_up), Modifier.padding(top = 10.dp))
    }
}

/** A row that only shows something. */
@Composable
private fun SheetInfo(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().height(INFO_ROW).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Detail(label, Modifier.weight(1f))
        if (value.isNotEmpty()) Label(value, maxLines = 1)
    }
}

private fun Key.isOk() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

/** A row that has stopped listening: the window it belongs to is closing. */
private val NoActivate: () -> Unit = {}
