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
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileSize
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import com.example.tvcontrolcenter.ui.glass.LocalInvertFocus
import com.example.tvcontrolcenter.ui.glass.LocalOverBlur
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.glass.LocalFrostStyle
import com.example.tvcontrolcenter.ui.theme.Motion
import com.example.tvcontrolcenter.ui.theme.PanelDimens
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shown = remember(state.layout, state.hasNowPlaying) {
        // The grid tile appears only while something plays; the top-bar button is always there.
        state.layout.visibleItems().filter {
            it.id != TileId.NOW_PLAYING || it.size == TileSize.TOP || state.hasNowPlaying
        }
    }
    val placed = remember(shown) { GridPacker.pack(shown) }
    val pinned = remember(shown) { GridPacker.pinned(shown) }
    val musicOnTop = pinned.any { it.id == TileId.NOW_PLAYING }
    val body = maxOf(gridHeight(GridPacker.rowCount(placed)), if (musicOnTop) PanelDimens.musicCardHeight else 0.dp)
    val needed = state.settings.topOffset.dp + PanelDimens.sheetPadding * 2 +
        PanelDimens.headerHeight + state.settings.headerGap.dp + PanelDimens.sheetPadding +
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
    var page by remember { mutableStateOf(PanelPage.GRID) }
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

    val onActivate: (TileId) -> Unit = { id ->
        if (id == TileId.NOW_PLAYING && musicOnTop) {
            if (page == PanelPage.MUSIC) {
                page = PanelPage.GRID
            } else if (viewModel.onMusicPageRequested()) {
                page = PanelPage.MUSIC
            }
        } else {
            viewModel.onTileActivated(id)
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
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) {
                        if (page == PanelPage.MUSIC) {
                            // First Back leaves the music page, the next one closes the panel.
                            page = PanelPage.GRID
                            runCatching { requesters.getValue(TileId.NOW_PLAYING).requestFocus() }
                        } else dismiss()
                    }
                    true
                } else false
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
            ) {
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
                    actions = TileActions(
                        onVolumeStep = viewModel::onVolumeStep,
                        onSkipNext = viewModel::onSkipNext,
                        onSkipPrevious = viewModel::onSkipPrevious,
                        onPlayPause = viewModel::onPlayPause,
                    ),
                    backdrop = tileBackdrop,
                    origin = origin,
                    sheet = !wholeScreen && mapped == null,
                    page = page,
                    musicFocus = musicFocus,
                )
            }
        }
    }
}
