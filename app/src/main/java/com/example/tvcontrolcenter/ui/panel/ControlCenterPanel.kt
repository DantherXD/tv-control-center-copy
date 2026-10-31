package com.example.tvcontrolcenter.ui.panel

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.domain.model.NetworkKind
import com.example.tvcontrolcenter.domain.model.PanelEvent
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.ui.glass.LocalGlassBackdrop
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.Motion
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import com.example.tvcontrolcenter.ui.theme.PanelType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val VOLUME_STEP = 5

/**
 * The Control Center panel plus its open/close choreography.
 *
 * On open the whole glass cluster grows out of the top-right corner (where the toggle
 * lives in tvOS) on a spring, and tiles settle one after another. On close the same
 * springs run backwards, so the tiles merge into one blob and shrink into the corner.
 *
 * Nothing is composed while closed, so hidden tiles can never take focus.
 *
 * @param visible desired state. Animations run to completion, then [onClosed] fires.
 */
@Composable
fun ControlCenterHost(
    viewModel: ControlCenterViewModel,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onClosed: () -> Unit,
    onCapability: (Capability) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backdrop = LocalGlassBackdrop.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val progress = remember { Animatable(0f) }
    val tileProgress = remember { TileId.entries.associateWith { Animatable(0f) } }
    val requesters = remember { TileId.entries.associateWith { FocusRequester() } }

    val currentVisible by rememberUpdatedState(visible)
    val shown by remember { derivedStateOf { currentVisible || progress.value > 0.001f } }

    LaunchedEffect(visible) {
        if (visible) {
            viewModel.onOpened()
            launch {
                TileId.entries.forEachIndexed { index, id ->
                    launch {
                        delay(index * Motion.staggerMillis)
                        tileProgress.getValue(id).animateTo(1f, Motion.tileSpring())
                    }
                }
            }
            progress.animateTo(1f, Motion.panelSpring())
        } else {
            coroutineScope {
                tileProgress.values.forEach { launch { it.animateTo(0f, Motion.panelSpring()) } }
                launch { backdrop?.parallax?.animateTo(Offset.Zero, Motion.parallaxSpring()) }
                progress.animateTo(0f, Motion.panelSpring())
            }
            onClosed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PanelEvent.Dismiss -> onDismissRequest()
                is PanelEvent.NeedsCapability -> {
                    onCapability(event.capability)
                    onDismissRequest()
                }
                is PanelEvent.Unavailable ->
                    Toast.makeText(context, R.string.state_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (!shown) return

    // Wait for the first status emission, then give the remembered tile focus.
    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.uiState.value.tiles.isNotEmpty() }.first { it }
        withFrameNanos { }
        runCatching { requesters.getValue(viewModel.restoredFocus()).requestFocus() }
    }

    val parallaxMax = with(density) { PanelDimens.parallaxMax.toPx() }

    Box(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) onDismissRequest()
                    true
                } else false
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    top = PanelDimens.screenInsetV + PanelDimens.statusBarHeight,
                    end = PanelDimens.screenInsetH,
                )
                .graphicsLayer {
                    val p = progress.value
                    val s = Motion.openFromScale + (1f - Motion.openFromScale) * p
                    scaleX = s
                    scaleY = s
                    alpha = (p * 1.6f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(1f, 0f)
                },
            verticalArrangement = Arrangement.spacedBy(PanelDimens.gap),
        ) {
            PanelContent(
                state = state,
                requesters = requesters,
                appear = { id -> { tileProgress.getValue(id).value } },
                onFocused = { id ->
                    viewModel.onFocused(id)
                    val v = parallaxVector(id)
                    scope.launch {
                        backdrop?.parallax?.animateTo(
                            Offset(-v.x * parallaxMax, -v.y * parallaxMax),
                            Motion.parallaxSpring(),
                        )
                    }
                },
                onActivate = viewModel::onTileActivated,
                onVolumeStep = viewModel::onVolumeStep,
                onSkipNext = viewModel::onSkipNext,
                onSkipPrevious = viewModel::onSkipPrevious,
            )
        }
    }
}

@Composable
private fun PanelContent(
    state: ControlCenterUiState,
    requesters: Map<TileId, FocusRequester>,
    appear: (TileId) -> () -> Float,
    onFocused: (TileId) -> Unit,
    onActivate: (TileId) -> Unit,
    onVolumeStep: (Int) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
) {
    val hasNowPlaying = state.hasNowPlaying
    val gap = PanelDimens.gap

    val env = TileEnv(requesters, hasNowPlaying, appear, onFocused, onActivate)

    val on = stringResource(R.string.state_on)
    val off = stringResource(R.string.state_off)
    val capsule = Modifier.size(PanelDimens.capsuleWidth, PanelDimens.capsuleHeight)

    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        env.Tile(
            TileId.POWER, stringResource(R.string.tile_power), PanelDimens.powerRadius,
            Modifier.size(PanelDimens.capsuleWidth, PanelDimens.tallTile),
        ) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                TileIcon(Icons.Filled.PowerSettingsNew, PanelDimens.powerIconSize, tint = Color.White)
                Label(stringResource(R.string.tile_power), Modifier.padding(top = 8.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            val net = state.status.network
            val wifiDetail = when (net) {
                NetworkKind.WIFI -> stringResource(R.string.state_on)
                NetworkKind.ETHERNET -> "Ethernet"
                NetworkKind.NONE -> stringResource(R.string.state_off)
            }
            env.Tile(TileId.WIFI, stringResource(R.string.tile_wifi), PanelDimens.capsuleRadius, capsule, wifiDetail) {
                CapsuleContent(
                    icon = if (net == NetworkKind.NONE) Icons.Filled.WifiOff else Icons.Filled.Wifi,
                    active = net != NetworkKind.NONE,
                    title = stringResource(R.string.tile_wifi),
                    detail = wifiDetail,
                )
            }
            val dnd = state.status.dndEnabled
            env.Tile(TileId.DND, stringResource(R.string.tile_dnd), PanelDimens.capsuleRadius, capsule, if (dnd) on else off) {
                CapsuleContent(Icons.Filled.DarkMode, dnd, stringResource(R.string.tile_dnd), null)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        val bt = state.tiles[TileId.BLUETOOTH]
        val btDetail = bt?.detail ?: if (bt?.active == true) on else off
        env.Tile(TileId.BLUETOOTH, stringResource(R.string.tile_bluetooth), PanelDimens.capsuleRadius, capsule, btDetail) {
            CapsuleContent(
                Icons.Filled.Headphones, bt?.active == true,
                bt?.detail ?: stringResource(R.string.tile_bluetooth),
                if (bt?.detail == null) btDetail else null,
            )
        }
        val sleep = state.tiles[TileId.SLEEP_TIMER]
        val sleepDetail = sleep?.detail ?: off
        env.Tile(TileId.SLEEP_TIMER, stringResource(R.string.tile_sleep_timer), PanelDimens.capsuleRadius, capsule, sleepDetail) {
            CapsuleContent(Icons.Filled.Timer, sleep?.active == true, stringResource(R.string.tile_sleep_timer), sleepDetail)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        val circle = Modifier.size(PanelDimens.circle)
        env.CircleTile(TileId.GAMEPAD, Icons.Filled.SportsEsports, R.string.tile_gamepad, circle)
        env.CircleTile(TileId.ACCESSIBILITY, Icons.Filled.Accessibility, R.string.tile_accessibility, circle)
        env.CircleTile(TileId.LOCK, Icons.Filled.Lock, R.string.tile_lock, circle)
        env.CircleTile(TileId.SEARCH, Icons.Filled.Search, R.string.tile_search, circle)
    }

    val wide = Modifier.size(PanelDimens.panelWidth, PanelDimens.wideHeight)

    state.nowPlaying?.let { np ->
        env.Tile(
            TileId.NOW_PLAYING, stringResource(R.string.now_playing) + ", " + np.title,
            PanelDimens.capsuleRadius, wide,
            onKey = { e ->
                if (e.type == KeyEventType.KeyDown) when (e.key) {
                    Key.DirectionRight -> { onSkipNext(); true }
                    Key.DirectionLeft -> { onSkipPrevious(); true }
                    else -> false
                } else e.key == Key.DirectionRight || e.key == Key.DirectionLeft
            },
        ) {
            Row(
                Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TileIcon(Icons.Filled.MusicNote, PanelDimens.iconSize)
                Column(Modifier.weight(1f)) {
                    Label(np.title)
                    np.artist?.let { Detail(it) }
                }
                TileIcon(if (np.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, PanelDimens.iconSize)
            }
        }
    }

    val volume = state.volumePercent
    env.Tile(
        TileId.VOLUME, stringResource(R.string.tile_volume), PanelDimens.capsuleRadius, wide, "$volume%",
        onKey = { e ->
            when (e.key) {
                Key.DirectionRight -> { if (e.type == KeyEventType.KeyDown) onVolumeStep(VOLUME_STEP); true }
                Key.DirectionLeft -> { if (e.type == KeyEventType.KeyDown) onVolumeStep(-VOLUME_STEP); true }
                else -> false
            }
        },
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TileIcon(Icons.Filled.VolumeUp, PanelDimens.iconSize)
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
                    .drawBehind {
                        drawRoundRect(
                            color = Color.White,
                            size = Size(size.width * volume / 100f, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    },
            )
        }
    }
}

/** Everything a tile needs from the panel, bundled so tile call sites stay short. */
private class TileEnv(
    val requesters: Map<TileId, FocusRequester>,
    val hasNowPlaying: Boolean,
    val appear: (TileId) -> () -> Float,
    val onFocused: (TileId) -> Unit,
    val onActivate: (TileId) -> Unit,
)

@Composable
private fun TileEnv.Tile(
    id: TileId,
    label: String,
    radius: Dp,
    modifier: Modifier,
    stateText: String? = null,
    onKey: (KeyEvent) -> Boolean = { false },
    content: @Composable BoxScope.() -> Unit,
) = GlassTile(
    label = label,
    cornerRadius = radius,
    focusRequester = requesters.getValue(id),
    focusProps = focusPropsFor(id, hasNowPlaying, requesters),
    onActivate = { onActivate(id) },
    modifier = modifier,
    stateText = stateText,
    appear = appear(id),
    onFocused = { onFocused(id) },
    onKey = onKey,
    content = content,
)

@Composable
private fun TileEnv.CircleTile(id: TileId, icon: ImageVector, labelRes: Int, modifier: Modifier) {
    Tile(id, stringResource(labelRes), PanelDimens.circle / 2, modifier) {
        TileIcon(icon, PanelDimens.iconSize, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun BoxScope.CapsuleContent(icon: ImageVector, active: Boolean, title: String, detail: String?) {
    Row(
        Modifier.align(Alignment.CenterStart).padding(horizontal = PanelDimens.contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TileIcon(icon, PanelDimens.iconSize, tint = if (active) GlassTokens.activeIcon else Color.White)
        Column {
            Label(title)
            detail?.let { Detail(it) }
        }
    }
}

@Composable
private fun TileIcon(icon: ImageVector, size: Dp, modifier: Modifier = Modifier, tint: Color = Color.White) {
    // Decorative: the tile itself carries the spoken label.
    Icon(icon, contentDescription = null, modifier = modifier.size(size), tint = tint)
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text, modifier.clearAndSetSemantics { },
        style = PanelType.label.copy(color = GlassTokens.textPrimary),
        maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Detail(text: String) {
    BasicText(
        text, Modifier.clearAndSetSemantics { },
        style = PanelType.detail.copy(color = GlassTokens.textSecondary),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Explicit D-pad edges from [FocusGraph]. A missing neighbour traps focus inside the panel. */
private fun focusPropsFor(
    id: TileId,
    hasNowPlaying: Boolean,
    requesters: Map<TileId, FocusRequester>,
): FocusProperties.() -> Unit = {
    fun target(dir: Dir): FocusRequester =
        FocusGraph.neighbour(id, dir, hasNowPlaying)?.let(requesters::get) ?: FocusRequester.Cancel
    up = target(Dir.UP)
    down = target(Dir.DOWN)
    left = target(Dir.LEFT)
    right = target(Dir.RIGHT)
}

/** Normalised tile position, -1..1 on both axes. The wallpaper drifts the opposite way. */
internal fun parallaxVector(id: TileId): Offset = when (id) {
    TileId.POWER -> Offset(-0.5f, -0.8f)
    TileId.WIFI -> Offset(0.5f, -0.9f)
    TileId.DND -> Offset(0.5f, -0.5f)
    TileId.BLUETOOTH -> Offset(-0.5f, -0.1f)
    TileId.SLEEP_TIMER -> Offset(0.5f, -0.1f)
    TileId.GAMEPAD -> Offset(-0.75f, 0.3f)
    TileId.ACCESSIBILITY -> Offset(-0.25f, 0.3f)
    TileId.LOCK -> Offset(0.25f, 0.3f)
    TileId.SEARCH -> Offset(0.75f, 0.3f)
    TileId.NOW_PLAYING -> Offset(0f, 0.7f)
    TileId.VOLUME -> Offset(0f, 1f)
}
