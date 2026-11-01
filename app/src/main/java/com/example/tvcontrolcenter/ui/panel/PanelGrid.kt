package com.example.tvcontrolcenter.ui.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.Dir
import com.example.tvcontrolcenter.domain.model.GridNav
import com.example.tvcontrolcenter.domain.model.GridPacker
import com.example.tvcontrolcenter.domain.model.Placed
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileSize
import com.example.tvcontrolcenter.ui.theme.PanelDimens

private const val VOLUME_STEP = 5

/** Callbacks for the controls that react to Left/Right themselves. */
internal class TileActions(
    val onVolumeStep: (Int) -> Unit = {},
    val onSkipNext: () -> Unit = {},
    val onSkipPrevious: () -> Unit = {},
)

/** Edge length of a block of [cells] cells, gaps included. */
internal fun span(cells: Int): Dp = PanelDimens.cell * cells + PanelDimens.gap * (cells - 1)

internal fun gridHeight(rows: Int): Dp = if (rows <= 0) 0.dp else span(rows)

internal fun radiusFor(size: TileSize): Dp =
    if (size == TileSize.LARGE) PanelDimens.largeRadius else PanelDimens.cell / 2

/**
 * The packed controls, absolutely positioned on the 4-column grid.
 *
 * @param interactive false in the editor: nothing reacts to Left/Right and OK only selects.
 * @param trapEdges true in the panel, where focus must not leave the glass.
 * @param appear per-tile open progress, read in the draw phase.
 */
@Composable
internal fun TileGrid(
    placed: List<Placed>,
    state: ControlCenterUiState,
    requesters: Map<TileId, FocusRequester>,
    interactive: Boolean,
    trapEdges: Boolean,
    selected: TileId?,
    appear: (Placed) -> () -> Float,
    onFocused: (TileId) -> Unit,
    onActivate: (TileId) -> Unit,
    actions: TileActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(PanelDimens.gridWidth, gridHeight(GridPacker.rowCount(placed)))) {
        placed.forEach { p ->
            key(p.id) {
                PanelTile(
                    p = p,
                    all = placed,
                    state = state,
                    requesters = requesters,
                    interactive = interactive,
                    trapEdges = trapEdges,
                    selected = selected == p.id,
                    appear = appear(p),
                    onFocused = { onFocused(p.id) },
                    onActivate = { onActivate(p.id) },
                    actions = actions,
                    modifier = Modifier
                        .offset(
                            x = (PanelDimens.cell + PanelDimens.gap) * p.col,
                            y = (PanelDimens.cell + PanelDimens.gap) * p.row,
                        )
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
    modifier: Modifier,
) {
    val face = faceOf(p.id, state)
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
        p.id == TileId.NOW_PLAYING -> nowPlayingKeys(actions)
        p.id == TileId.VOLUME -> volumeKeys(actions)
        else -> NoKeys
    }
    val focusProps = remember(p.id, all, trapEdges, requesters) {
        focusPropsFor(p.id, all, requesters, trapEdges)
    }

    GlassTile(
        label = label,
        cornerRadius = radiusFor(p.size),
        focusRequester = requesters.getValue(p.id),
        focusProps = focusProps,
        onActivate = onActivate,
        modifier = modifier,
        stateText = stateText,
        active = face.active,
        selected = selected,
        appear = appear,
        onFocused = onFocused,
        onKey = onKey,
    ) {
        when (p.id) {
            TileId.VOLUME -> VolumeFace(volume, muted, p.size)
            TileId.NOW_PLAYING -> NowPlayingFace(np?.title, np?.artist, np?.isPlaying == true, p.size)
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
): FocusProperties.() -> Unit = {
    fun target(dir: Dir): FocusRequester =
        GridNav.neighbour(placed, id, dir)?.let(requesters::get)
            ?: if (trapEdges) FocusRequester.Cancel else FocusRequester.Default
    up = target(Dir.UP)
    down = target(Dir.DOWN)
    left = target(Dir.LEFT)
    right = target(Dir.RIGHT)
}
