package com.example.tvcontrolcenter.ui.panel

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.Dir
import com.example.tvcontrolcenter.domain.model.GridNav
import com.example.tvcontrolcenter.domain.model.GridPacker
import com.example.tvcontrolcenter.domain.model.Placed
import com.example.tvcontrolcenter.domain.model.MusicStyle
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileSize
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.glass.LocalFrostStyle
import com.example.tvcontrolcenter.ui.glass.LocalLiteGlass
import com.example.tvcontrolcenter.ui.glass.frostedSheet
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import com.example.tvcontrolcenter.ui.theme.PanelType
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val VOLUME_STEP = 3

/** Callbacks for the controls that react to Left/Right themselves. */
internal class TileActions(
    val onVolumeStep: (Int) -> Unit = {},
    val onSkipNext: () -> Unit = {},
    val onSkipPrevious: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
)

/** Edge length of a block of [cells] cells, gaps included. */
internal fun span(cells: Int): Dp = PanelDimens.cell * cells + PanelDimens.gap * (cells - 1)

internal fun gridHeight(rows: Int): Dp = if (rows <= 0) 0.dp else span(rows)

internal fun radiusFor(size: TileSize): Dp =
    if (size == TileSize.LARGE) PanelDimens.largeRadius else PanelDimens.cell / 2

/**
 * Clock and the two round buttons above the grid, then the grid itself: everything the panel shows.
 * The host and the editor both draw this, so the editor preview is the real panel.
 *
 * @param origin top-left corner of this block in the backdrop's coordinates, in pixels.
 * @param sheet draw a frosted card behind the grid. Used when there is no blurred screen to show.
 */
@Composable
internal fun PanelContent(
    grid: List<Placed>,
    pinned: List<Placed>,
    state: ControlCenterUiState,
    requesters: Map<TileId, FocusRequester>,
    interactive: Boolean,
    trapEdges: Boolean,
    selected: TileId?,
    appear: (Placed) -> () -> Float,
    onFocused: (TileId) -> Unit,
    onActivate: (TileId) -> Unit,
    actions: TileActions,
    backdrop: Backdrop?,
    origin: Offset,
    sheet: Boolean = false,
    page: PanelPage = PanelPage.GRID,
    musicFocus: MusicFocus? = null,
    subFocus: FocusRequester? = null,
    subActions: SubPageActions? = null,
    onLongPress: (TileId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Focus moves over the top bar and the grid as one set of rows.
    val navigation = remember(grid, pinned) { pinned + grid }
    val density = LocalDensity.current
    // The sheet reaches sheetPadding above the grid, so it adds that to keep the visible gap equal.
    val gap = state.settings.headerGap.dp + if (sheet) PanelDimens.sheetPadding else 0.dp
    val music = page == PanelPage.MUSIC && musicFocus != null
    val sub = page.isSub && subFocus != null && subActions != null
    val musicButton = pinned.any { it.id == TileId.NOW_PLAYING }
    val top = state.settings.topSize.dp
    val header = PanelDimens.headerFor(state.settings.topSize)
    val gridOrigin = origin + with(density) {
        Offset(0f, (header + gap).toPx())
    }
    Column(modifier.width(PanelDimens.gridWidth)) {
        Row(
            Modifier.fillMaxWidth().height(header),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClockText(Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(PanelDimens.topButtonGap)) {
                pinned.forEachIndexed { index, p ->
                    key(p.id) {
                        val left = PanelDimens.gridWidth -
                            (top + PanelDimens.topButtonGap) * (pinned.size - index) +
                            PanelDimens.topButtonGap
                        PanelTile(
                            p = p,
                            all = navigation,
                            state = state,
                            requesters = requesters,
                            interactive = interactive,
                            trapEdges = trapEdges,
                            selected = selected == p.id,
                            appear = appear(p),
                            onFocused = { onFocused(p.id) },
                            onActivate = { onActivate(p.id) },
                            actions = actions,
                            backdrop = backdrop,
                            backdropOrigin = origin + with(density) { Offset(left.toPx(), 0f) },
                            downTarget = when {
                                music -> musicFocus?.play
                                sub -> subFocus
                                else -> null
                            },
                            // The open page's button is white and the other one stays grey.
                            pageOpen = when (p.id) {
                                TileId.NOW_PLAYING -> music
                                TileId.EDIT -> state.settings.menuIsHome && musicButton && page == PanelPage.GRID
                                else -> false
                            },
                            radius = top / 2,
                            flat = state.settings.topBorderless,
                            longPress = if (interactive && p.id == TileId.PROFILE) ({ onLongPress(p.id) }) else null,
                            modifier = Modifier.size(top),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(gap))
        if (music && musicFocus != null) {
            MusicCard(
                np = state.nowPlaying,
                actions = actions,
                focus = musicFocus,
                up = requesters.getValue(TileId.NOW_PLAYING),
                backdrop = backdrop,
                origin = gridOrigin,
                sheet = sheet,
                big = state.settings.musicStyle == MusicStyle.BIG,
            )
        } else if (sub && subFocus != null && subActions != null) {
            SubPageCard(
                page = page,
                state = state,
                actions = subActions,
                first = subFocus,
                backdrop = backdrop,
                origin = gridOrigin,
                sheet = sheet,
            )
        } else TileGrid(
            onLongPress = onLongPress,
            placed = grid,
            navigation = navigation,
            state = state,
            requesters = requesters,
            interactive = interactive,
            trapEdges = trapEdges,
            selected = selected,
            appear = appear,
            onFocused = onFocused,
            onActivate = onActivate,
            actions = actions,
            backdrop = backdrop,
            origin = gridOrigin,
            modifier = if (sheet) {
                Modifier.frostedSheet(PanelDimens.sheetRadius, LocalFrostStyle.current, PanelDimens.sheetPadding, LocalLiteGlass.current)
            } else Modifier,
        )
    }
}

/** Time of day in the user's 12 or 24 hour style, refreshed on the minute. */
@Composable
internal fun ClockText(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val formatter = remember(context) {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm")
    }
    var text by remember { mutableStateOf(LocalTime.now().format(formatter)) }
    LaunchedEffect(formatter) {
        while (true) {
            val now = LocalTime.now()
            text = now.format(formatter)
            delay((60 - now.second) * 1000L - now.nano / 1_000_000L + 50L)
        }
    }
    BasicText(
        text,
        modifier,
        style = PanelType.clock.copy(color = Color.White.copy(alpha = 0.8f)),
        maxLines = 1,
    )
}

/**
 * The packed controls, absolutely positioned on the 4-column grid.
 *
 * @param navigation every control focus can move to (grid plus top bar), for D-pad edges.
 * @param interactive false in the editor: nothing reacts to Left/Right and OK only selects.
 * @param trapEdges true in the panel, where focus must not leave the glass.
 * @param appear per-tile open progress, read in the draw phase.
 * @param origin top-left of the grid in the backdrop's coordinates, in pixels.
 */
@Composable
internal fun TileGrid(
    placed: List<Placed>,
    navigation: List<Placed>,
    state: ControlCenterUiState,
    requesters: Map<TileId, FocusRequester>,
    interactive: Boolean,
    trapEdges: Boolean,
    selected: TileId?,
    appear: (Placed) -> () -> Float,
    onFocused: (TileId) -> Unit,
    onActivate: (TileId) -> Unit,
    actions: TileActions,
    backdrop: Backdrop?,
    origin: Offset,
    modifier: Modifier = Modifier,
    onLongPress: (TileId) -> Unit = {},
) {
    val density = LocalDensity.current
    Box(modifier.size(PanelDimens.gridWidth, gridHeight(GridPacker.rowCount(placed)))) {
        placed.forEach { p ->
            key(p.id) {
                val x = (PanelDimens.cell + PanelDimens.gap) * p.col
                val y = (PanelDimens.cell + PanelDimens.gap) * p.row
                PanelTile(
                    p = p,
                    all = navigation,
                    state = state,
                    requesters = requesters,
                    interactive = interactive,
                    trapEdges = trapEdges,
                    selected = selected == p.id,
                    appear = appear(p),
                    onFocused = { onFocused(p.id) },
                    onActivate = { onActivate(p.id) },
                    actions = actions,
                    backdrop = backdrop,
                    backdropOrigin = origin + with(density) { Offset(x.toPx(), y.toPx()) },
                    downTarget = null,
                    pageOpen = false,
                    flat = false,
                    // Hold OK on Wi-Fi or Bluetooth for their small window.
                    longPress = if (interactive && (p.id == TileId.WIFI || p.id == TileId.BLUETOOTH)) ({ onLongPress(p.id) }) else null,
                    radius = radiusFor(p.size),
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .size(span(p.cols), span(p.rows)),
                )
            }
        }
    }
}

@Composable
private fun PanelTile(
    p: Placed,
    all: List<Placed>,
    state: ControlCenterUiState,
    requesters: Map<TileId, FocusRequester>,
    interactive: Boolean,
    trapEdges: Boolean,
    selected: Boolean,
    appear: () -> Float,
    onFocused: () -> Unit,
    onActivate: () -> Unit,
    actions: TileActions,
    backdrop: Backdrop?,
    backdropOrigin: Offset,
    downTarget: FocusRequester?,
    pageOpen: Boolean,
    flat: Boolean,
    longPress: (() -> Unit)?,
    radius: Dp,
    modifier: Modifier,
) {
    val face = faceOf(p.id, state)
    // Now Playing in the top bar is a page button, not a grid control.
    val musicButton = p.id == TileId.NOW_PLAYING && p.row == GridPacker.TOP_ROW
    val np = state.nowPlaying
    val volume = state.volumePercent
    val muted = state.muted

    val label = when (p.id) {
        TileId.NOW_PLAYING -> stringResource(R.string.now_playing) + (np?.title?.let { ", $it" } ?: "")
        else -> face.title
    }
    val stateText = when (p.id) {
        TileId.VOLUME -> if (muted) stringResource(R.string.state_muted) else "$volume%"
        TileId.NOW_PLAYING -> null
        else -> face.detail
    }
    val onKey: (KeyEvent) -> Boolean = when {
        !interactive -> NoKeys
        musicButton -> NoKeys
        p.id == TileId.NOW_PLAYING -> nowPlayingKeys(actions)
        p.id == TileId.VOLUME -> volumeKeys(actions)
        else -> NoKeys
    }
    val focusProps = remember(p.id, all, trapEdges, requesters, downTarget) {
        focusPropsFor(p.id, all, requesters, trapEdges, downTarget)
    }

    GlassTile(
        label = label,
        cornerRadius = radius,
        focusRequester = requesters.getValue(p.id),
        focusProps = focusProps,
        onActivate = onActivate,
        modifier = modifier,
        stateText = stateText,
        // Top-bar buttons: the open page is highlighted (not white), the rest are grey until focused.
        active = if (p.row == GridPacker.TOP_ROW) pageOpen else face.active,
        dimIdle = p.row == GridPacker.TOP_ROW && !pageOpen,
        flat = flat,
        onLongPress = longPress,
        selected = selected,
        backdrop = backdrop,
        backdropOrigin = backdropOrigin,
        appear = appear,
        onFocused = onFocused,
        onKey = onKey,
    ) {
        when (p.id) {
            TileId.VOLUME -> VolumeFace(volume, muted, p.size)
            TileId.NOW_PLAYING ->
                if (musicButton) EqualizerIcon(np?.isPlaying == true, PanelDimens.iconSmall, Modifier.align(Alignment.Center))
                else NowPlayingFace(np?.title, np?.artist, np?.isPlaying == true, p.size)
            TileId.PROFILE -> ProfileFace(state.settings)
            else -> GenericFace(p.id, face, p.size)
        }
    }
}

private val NoKeys: (KeyEvent) -> Boolean = { false }

private fun nowPlayingKeys(a: TileActions): (KeyEvent) -> Boolean = { e ->
    when (e.key) {
        Key.DirectionRight -> { if (e.type == KeyEventType.KeyDown) a.onSkipNext(); true }
        Key.DirectionLeft -> { if (e.type == KeyEventType.KeyDown) a.onSkipPrevious(); true }
        else -> false
    }
}

private fun volumeKeys(a: TileActions): (KeyEvent) -> Boolean = { e ->
    when (e.key) {
        Key.DirectionRight -> { if (e.type == KeyEventType.KeyDown) a.onVolumeStep(VOLUME_STEP); true }
        Key.DirectionLeft -> { if (e.type == KeyEventType.KeyDown) a.onVolumeStep(-VOLUME_STEP); true }
        else -> false
    }
}

/**
 * Explicit D-pad edges from [GridNav]. In the panel a missing neighbour traps focus on the
 * glass; in the editor it falls back to the default search, which can leave for the side pane.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun focusPropsFor(
    id: TileId,
    placed: List<Placed>,
    requesters: Map<TileId, FocusRequester>,
    trapEdges: Boolean,
    downOverride: FocusRequester? = null,
): FocusProperties.() -> Unit = {
    fun target(dir: Dir): FocusRequester =
        GridNav.neighbour(placed, id, dir)?.let(requesters::get)
            ?: if (trapEdges) FocusRequester.Cancel else FocusRequester.Default
    up = target(Dir.UP)
    down = downOverride ?: target(Dir.DOWN)
    left = target(Dir.LEFT)
    right = target(Dir.RIGHT)
}
