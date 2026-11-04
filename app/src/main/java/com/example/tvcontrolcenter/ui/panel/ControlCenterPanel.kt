package com.example.tvcontrolcenter.ui.panel

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.domain.model.GridPacker
import com.example.tvcontrolcenter.domain.model.PanelEvent
import com.example.tvcontrolcenter.domain.model.Placed
import com.example.tvcontrolcenter.domain.model.BlurMethod
import com.example.tvcontrolcenter.domain.model.MusicStyle
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileSize
import androidx.compose.ui.graphics.FilterQuality
import kotlin.math.roundToInt
import com.example.tvcontrolcenter.ui.glass.LocalEdgeWidth
import com.example.tvcontrolcenter.ui.glass.LocalInvertFocus
import com.example.tvcontrolcenter.ui.glass.LocalOverBlur
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.glass.LocalFrostStyle
import com.example.tvcontrolcenter.ui.theme.Motion
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import com.example.tvcontrolcenter.ui.theme.ProvideTextScale
import com.example.tvcontrolcenter.ui.theme.ProvideUiScale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Open/close animates per row: slot 0 is the header, then one per grid row. More share the last slot. */
private const val ROW_SLOTS = 24
private const val STAGGER_ROWS = 8

/**
 * The Control Center panel plus its open/close choreography.
 *
 * The frosted sheet grows out of the top-right corner on a short spring and the rows settle
 * one after another. Closing runs the same springs backwards. Nothing is composed while
 * closed, so hidden controls can never take focus.
 *
 * @param visible desired state. Animations run to completion, then [onClosed] fires.
 * @param backdrop blurred copy of the screen to show inside the glass. Without it the panel
 *   draws its own frosted sheet instead.
 * @param scrimAlpha darkens everything behind the panel.
 * @param windowBlur true when the system is blurring what is behind the window for us.
 */
@Composable
fun ControlCenterHost(
    viewModel: ControlCenterViewModel,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onClosed: () -> Unit,
    onCapability: (Capability) -> Unit,
    modifier: Modifier = Modifier,
    onOpenEditor: () -> Unit = {},
    backdrop: Backdrop? = null,
    scrimAlpha: Float = 0f,
    windowBlur: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shown = remember(state.layout, state.hasNowPlaying) {
        // Now Playing (grid tile or top-bar button) appears only while something plays.
        state.layout.visibleItems().filter { it.id != TileId.NOW_PLAYING || state.hasNowPlaying }
    }
    val placed = remember(shown) { GridPacker.pack(shown) }
    val pinned = remember(shown) { GridPacker.pinned(shown) }
    val musicOnTop = pinned.any { it.id == TileId.NOW_PLAYING }
    val body = maxOf(gridHeight(GridPacker.rowCount(placed)), if (musicOnTop) PanelDimens.musicHeightFor(state.settings.musicStyle == MusicStyle.BIG) else 0.dp)
    val needed = state.settings.topOffset.dp + PanelDimens.sheetPadding * 2 +
        PanelDimens.headerFor(state.settings.topSize) + state.settings.headerGap.dp + PanelDimens.sheetPadding +
        body + PanelDimens.edgeInset
    ProvideUiScale(state.settings.uiScale, neededHeight = needed) {
        HostBody(
            state = state,
            placed = placed,
            pinned = pinned,
            backdrop = backdrop,
            viewModel = viewModel,
            visible = visible,
            onDismissRequest = onDismissRequest,
            onClosed = onClosed,
            onCapability = onCapability,
            onOpenEditor = onOpenEditor,
            scrimAlpha = scrimAlpha,
            windowBlur = windowBlur,
            modifier = modifier,
        )
    }
}

@Composable
private fun HostBody(
    state: ControlCenterUiState,
    placed: List<Placed>,
    pinned: List<Placed>,
    backdrop: Backdrop?,
    viewModel: ControlCenterViewModel,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onClosed: () -> Unit,
    onCapability: (Capability) -> Unit,
    onOpenEditor: () -> Unit,
    scrimAlpha: Float,
    windowBlur: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val topOffset = state.settings.topOffset.dp
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember { Animatable(0f) }
    val rowProgress = remember { List(ROW_SLOTS) { Animatable(0f) } }
    val requesters = remember { TileId.entries.associateWith { FocusRequester() } }
    val musicFocus = remember { MusicFocus() }
    val subFocus = remember { FocusRequester() }
    // Tile to give focus back to when a small window closes.
    var returnTo by remember { mutableStateOf<TileId?>(null) }
    var page by remember { mutableStateOf(PanelPage.GRID) }
    // A small window is shrinking back into its control; the page follows when it has landed.
    var closingSub by remember { mutableStateOf(false) }
    // Same for the music page, which goes back into the button that opened it.
    var closingMusic by remember { mutableStateOf(false) }
    val musicOnTop = pinned.any { it.id == TileId.NOW_PLAYING }

    val currentPlaced by rememberUpdatedState(placed)
    val currentPinned by rememberUpdatedState(pinned)
    val currentVisible by rememberUpdatedState(visible)
    val dismiss by rememberUpdatedState(onDismissRequest)
    val capability by rememberUpdatedState(onCapability)
    val editor by rememberUpdatedState(onOpenEditor)
    val shown by remember { derivedStateOf { currentVisible || progress.value > 0.001f } }

    // Closing before the first open (the owner is still preparing a backdrop) is not a close.
    val hasOpened = remember { booleanArrayOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            hasOpened[0] = true
            page = PanelPage.GRID
            closingSub = false
            closingMusic = false
            viewModel.onOpened()
            launch {
                for (row in 0 until ROW_SLOTS) {
                    launch {
                        delay(minOf(row, STAGGER_ROWS) * Motion.staggerMillis)
                        rowProgress[row].animateTo(1f, Motion.tileSpring())
                    }
                }
            }
            progress.animateTo(1f, Motion.panelSpring())
        } else if (hasOpened[0]) {
            coroutineScope {
                rowProgress.forEach { launch { it.animateTo(0f, Motion.panelSpring()) } }
                progress.animateTo(0f, Motion.panelSpring())
            }
            onClosed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PanelEvent.Dismiss -> dismiss()
                is PanelEvent.NeedsCapability -> {
                    capability(event.capability)
                    dismiss()
                }
                is PanelEvent.Unavailable ->
                    Toast.makeText(context, R.string.state_unavailable, Toast.LENGTH_SHORT).show()
                PanelEvent.OpenEditor -> {
                    editor()
                    dismiss()
                }
            }
        }
    }

    if (!shown) return

    // Wait for the first status emission, then give the remembered control focus.
    LaunchedEffect(Unit) {
        viewModel.uiState.first { it.tiles.isNotEmpty() }
        withFrameNanos { }
        val remembered = viewModel.rememberedFocus()
        val all = currentPinned + currentPlaced
        val target = all.firstOrNull { it.id == remembered }?.id ?: all.firstOrNull()?.id
        if (target != null) runCatching { requesters.getValue(target).requestFocus() }
    }

    // Where the panel's top-left corner is, so each tile can find its piece of the backdrop.
    val origin = with(density) {
        Offset(
            hostSize.width - (PanelDimens.edgeInset + PanelDimens.sheetPadding + PanelDimens.gridWidth).toPx(),
            (topOffset + PanelDimens.sheetPadding).toPx(),
        )
    }
    val mapped = remember(backdrop, hostSize) {
        if (backdrop != null && hostSize.width > 0) backdrop.copy(width = hostSize.width, height = hostSize.height) else null
    }
    // Whole screen: the blurred picture covers everything behind the panel and the controls are
    // plain glass on top of it. No frosted box is drawn around them.
    val wholeScreen = state.settings.blurMethod == BlurMethod.WINDOW
    val fullBackdrop = if (wholeScreen) backdrop else null
    val tileBackdrop = if (wholeScreen) null else mapped
    // Nothing can blur this screen: no screenshot, and the system will not blur behind the
    // window. Draw the card then, so the controls never float on a sharp picture.
    val glassCard = mapped == null && (state.settings.card || !windowBlur)

    val menuIsHome = state.settings.menuIsHome
    val profileShown = pinned.any { it.id == TileId.PROFILE }
    // Nothing playing any more: the music page has nothing to show.
    LaunchedEffect(state.hasNowPlaying) {
        if (!state.hasNowPlaying && page == PanelPage.MUSIC) {
            closingMusic = false
            page = PanelPage.GRID
        }
    }
    val openSub: (PanelPage, TileId) -> Unit = { target, from ->
        returnTo = from
        closingSub = false
        closingMusic = false
        page = target
    }
    // The window has shrunk back onto its control: the grid takes over from here.
    val closeSub: () -> Unit = {
        closingSub = false
        page = PanelPage.GRID
    }
    // The music card has shrunk back into its button: focus goes there too.
    val closeMusic: () -> Unit = {
        closingMusic = false
        returnTo = TileId.NOW_PLAYING
        page = PanelPage.GRID
    }
    val subActions = remember(viewModel) {
        SubPageActions(
            wifiEnabled = viewModel::wifiEnabled,
            wifiName = viewModel::wifiName,
            setWifi = viewModel::onSetWifi,
            openWifiSettings = viewModel::onOpenWifiSettings,
            setBluetooth = viewModel::onSetBluetooth,
            openBluetoothSettings = viewModel::onOpenBluetoothSettings,
            pairedBluetooth = viewModel::pairedBluetooth,
            inputs = viewModel::tvInputs,
            switchInput = viewModel::onSwitchInput,
        )
    }
    // A card on its way home hands focus back at once, so it lands on its control in one move
    // instead of wandering over the grid while the card disappears.
    LaunchedEffect(closingSub, closingMusic) {
        val target = when {
            closingSub -> returnTo
            closingMusic -> TileId.NOW_PLAYING
            else -> null
        } ?: return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { requesters.getValue(target).requestFocus() }
    }
    // A small window opened: put focus on its first row. Closed: back to the tile that opened it.
    LaunchedEffect(page) {
        if (page.isSub) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { subFocus.requestFocus() }
        } else if (page == PanelPage.GRID) {
            val target = returnTo
            if (target != null) {
                returnTo = null
                withFrameNanos { }
                withFrameNanos { }
                runCatching { requesters.getValue(target).requestFocus() }
            }
        }
    }
    val onActivate: (TileId) -> Unit = { id ->
        when {
            id == TileId.INPUT -> openSub(PanelPage.INPUT, TileId.INPUT)
            id == TileId.NOW_PLAYING && musicOnTop -> {
                if (page == PanelPage.MUSIC) {
                    // With Menu as the main page, the music button only opens the music page.
                    if (!menuIsHome) closeMusic()
                } else if (viewModel.onMusicPageRequested()) {
                    closingMusic = false
                    page = PanelPage.MUSIC
                }
            }
            id == TileId.EDIT && menuIsHome -> {
                if (page != PanelPage.GRID) {
                    returnTo = null
                    page = PanelPage.GRID
                } else if (!profileShown) {
                    // Long-press on Profile opens the editor; without that button Menu still does.
                    viewModel.onTileActivated(id)
                }
            }
            else -> viewModel.onTileActivated(id)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { hostSize = it }
            .drawBehind {
                val full = fullBackdrop
                if (full != null && progress.value > 0.001f) {
                    drawImage(
                        full.image,
                        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                        alpha = progress.value.coerceIn(0f, 1f),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                val a = scrimAlpha * progress.value
                if (a > 0.001f) drawRect(Color.Black.copy(alpha = a))
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        if (event.type == KeyEventType.KeyUp) {
                            when {
                                // A card is already on its way home: Back closes the panel, as before.
                                closingSub || closingMusic -> dismiss()
                                // First Back shrinks the card back into the control it came from.
                                page.isSub -> closingSub = true
                                page == PanelPage.MUSIC -> closingMusic = true
                                else -> dismiss()
                            }
                        }
                        true
                    }
                    Key.Menu -> {
                        // Menu swaps the main page and the now-playing page, both ways.
                        if (event.type == KeyEventType.KeyUp && !closingSub && !closingMusic) {
                            when {
                                // A window is open: Menu goes back to the grid.
                                page.isSub -> closingSub = true
                                page == PanelPage.MUSIC -> closingMusic = true
                                page == PanelPage.GRID && musicOnTop -> {
                                    if (viewModel.onMusicPageRequested()) page = PanelPage.MUSIC
                                }
                            }
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            modifier = Modifier
                .padding(top = topOffset, end = PanelDimens.edgeInset)
                .graphicsLayer {
                    val p = progress.value
                    val s = Motion.openFromScale + (1f - Motion.openFromScale) * p
                    scaleX = s
                    scaleY = s
                    alpha = (p * 1.5f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .padding(PanelDimens.sheetPadding),
        ) {
            CompositionLocalProvider(
                LocalFrostStyle provides state.settings.frost,
                LocalInvertFocus provides state.settings.invertFocus,
                LocalOverBlur provides (backdrop != null),
                LocalEdgeWidth provides state.settings.edgeWidth.dp,
                LocalIconStyle provides state.settings.iconStyle,
            ) {
                ProvideTextScale(state.settings.textScale) {
                PanelContent(
                    grid = placed,
                    pinned = pinned,
                    state = state,
                    requesters = requesters,
                    interactive = true,
                    trapEdges = true,
                    selected = null,
                    appear = { p -> { rowProgress[(p.row + 1).coerceIn(0, ROW_SLOTS - 1)].value } },
                    onFocused = viewModel::onFocused,
                    onActivate = onActivate,
                    onLongPress = { id ->
                        when (id) {
                            TileId.WIFI -> openSub(PanelPage.WIFI, id)
                            TileId.BLUETOOTH -> openSub(PanelPage.BLUETOOTH, id)
                            else -> viewModel.onTileLongPressed(id)
                        }
                    },
                    subFocus = subFocus,
                    subActions = subActions,
                    actions = TileActions(
                        onVolumeStep = viewModel::onVolumeStep,
                        onSkipNext = viewModel::onSkipNext,
                        onSkipPrevious = viewModel::onSkipPrevious,
                        onPlayPause = viewModel::onPlayPause,
                    ),
                    backdrop = tileBackdrop,
                    origin = origin,
                    sheet = glassCard,
                    page = page,
                    musicFocus = musicFocus,
                    subClosing = closingSub,
                    onSubClosed = closeSub,
                    musicClosing = closingMusic,
                    onMusicClosed = closeMusic,
                )
                }
            }
        }
    }
}
