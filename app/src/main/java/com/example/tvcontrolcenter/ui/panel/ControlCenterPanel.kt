package com.example.tvcontrolcenter.ui.panel

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.domain.model.GridPacker
import com.example.tvcontrolcenter.domain.model.PanelEvent
import com.example.tvcontrolcenter.domain.model.Placed
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.ui.glass.frostedSheet
import com.example.tvcontrolcenter.ui.theme.Motion
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import com.example.tvcontrolcenter.ui.theme.ProvideUiScale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Open/close animates per grid row. More rows than this share the last slot's progress. */
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
 * @param scrimAlpha darkens everything behind the panel; windows use their own dim instead.
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
    scrimAlpha: Float = 0f,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val placed = remember(state.layout, state.hasNowPlaying) {
        GridPacker.pack(
            state.layout.visibleItems().filter { it.id != TileId.NOW_PLAYING || state.hasNowPlaying },
        )
    }
    val needed = PanelDimens.topInset + PanelDimens.sheetPadding * 2 +
        gridHeight(GridPacker.rowCount(placed)) + PanelDimens.edgeInset
    ProvideUiScale(state.settings.uiScale, neededHeight = needed) {
        HostBody(
            state = state,
            placed = placed,
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
    val progress = remember { Animatable(0f) }
    val rowProgress = remember { List(ROW_SLOTS) { Animatable(0f) } }
    val requesters = remember { TileId.entries.associateWith { FocusRequester() } }

    val currentPlaced by rememberUpdatedState(placed)
    val currentVisible by rememberUpdatedState(visible)
    val dismiss by rememberUpdatedState(onDismissRequest)
    val capability by rememberUpdatedState(onCapability)
    val editor by rememberUpdatedState(onOpenEditor)
    val shown by remember { derivedStateOf { currentVisible || progress.value > 0.001f } }

    LaunchedEffect(visible) {
        if (visible) {
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
        } else {
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
        val target = currentPlaced.firstOrNull { it.id == remembered }?.id ?: currentPlaced.firstOrNull()?.id
        if (target != null) runCatching { requesters.getValue(target).requestFocus() }
    }

    Box(
        modifier = modifier
            .drawBehind {
                val a = scrimAlpha * progress.value
                if (a > 0.001f) drawRect(Color.Black.copy(alpha = a))
            }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) dismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            modifier = Modifier
                .padding(top = PanelDimens.topInset, end = PanelDimens.edgeInset)
                .graphicsLayer {
                    val p = progress.value
                    val s = Motion.openFromScale + (1f - Motion.openFromScale) * p
                    scaleX = s
                    scaleY = s
                    alpha = (p * 1.5f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .frostedSheet(PanelDimens.sheetRadius)
                .padding(PanelDimens.sheetPadding),
        ) {
            TileGrid(
                placed = placed,
                state = state,
                requesters = requesters,
                interactive = true,
                trapEdges = true,
                selected = null,
                appear = { p -> { rowProgress[minOf(p.row, ROW_SLOTS - 1)].value } },
                onFocused = viewModel::onFocused,
                onActivate = viewModel::onTileActivated,
                actions = TileActions(
                    onVolumeStep = viewModel::onVolumeStep,
                    onSkipNext = viewModel::onSkipNext,
                    onSkipPrevious = viewModel::onSkipPrevious,
                ),
            )
        }
    }
}
