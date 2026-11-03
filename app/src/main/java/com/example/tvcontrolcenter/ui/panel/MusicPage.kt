package com.example.tvcontrolcenter.ui.panel

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import com.example.tvcontrolcenter.ui.glass.LocalEdgeWidth
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.NowPlaying
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.glass.LocalFrostStyle
import com.example.tvcontrolcenter.ui.glass.LocalInvertFocus
import com.example.tvcontrolcenter.ui.glass.LocalLiteGlass
import com.example.tvcontrolcenter.ui.glass.LocalOverBlur
import com.example.tvcontrolcenter.ui.glass.LocalTileInk
import com.example.tvcontrolcenter.ui.glass.frostedSheet
import com.example.tvcontrolcenter.ui.glass.frostedTile
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import com.example.tvcontrolcenter.ui.theme.PanelType
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.compose.runtime.CompositionLocalProvider

/**
 * What the panel shows under the top bar. MUSIC is only used when Now Playing sits in the top
 * bar: its button then opens this page instead of acting as a grid control.
 */
internal enum class PanelPage {
    GRID, MUSIC, WIFI, BLUETOOTH, INPUT;

    /** Wi-Fi, Bluetooth and Input open from a tile and take the grid's place. */
    val isSub: Boolean get() = this == WIFI || this == BLUETOOTH || this == INPUT
}

/** Focus handles for the three buttons on the music page. */
internal class MusicFocus {
    val previous = FocusRequester()
    val play = FocusRequester()
    val next = FocusRequester()
}

/** Frosted glass behind a card that replaces the grid: a sheet when nothing blurred shows through, else a big tile. */
@Composable
internal fun cardGlass(backdrop: Backdrop?, origin: Offset, sheet: Boolean): Modifier {
    val style = LocalFrostStyle.current
    val radius = PanelDimens.sheetRadius
    return if (sheet && backdrop == null) {
        Modifier.frostedSheet(radius, style, lite = LocalLiteGlass.current)
    } else {
        Modifier.frostedTile(
            cornerRadius = radius,
            active = false,
            focus = { 0f },
            backdrop = backdrop,
            origin = origin,
            style = style,
            overBlur = LocalOverBlur.current || backdrop != null,
            lite = LocalLiteGlass.current,
            edge = LocalEdgeWidth.current,
        )
    }
}

/**
 * The now-playing card. Compact: album picture, title and artist, a progress bar with elapsed and
 * remaining time, and rewind / play-pause / forward. Big: the album picture fills the card like
 * the iOS widget, with the same controls over it. Takes the place of the grid.
 *
 * @param up focus target above the card (the top-bar button that opened it).
 * @param sheet draw the frosted card when there is no blurred picture to show through.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun MusicCard(
    np: NowPlaying?,
    actions: TileActions,
    focus: MusicFocus,
    up: FocusRequester,
    backdrop: Backdrop?,
    origin: Offset,
    sheet: Boolean,
    big: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val glass = cardGlass(backdrop, origin, sheet)
    if (big) {
        // Like the iOS card: square cover on top, then title and artist, progress, and controls.
        Column(
            modifier
                .popIn()
                .size(PanelDimens.gridWidth, PanelDimens.musicCardBigHeight)
                .then(glass)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Artwork(np?.artwork, PanelDimens.gridWidth - 32.dp, radius = 14.dp, iconSize = 56.dp)
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Label(np?.title ?: stringResource(R.string.nothing_playing), maxLines = 1)
                np?.artist?.let { Detail(it) }
            }
            Progress(np, thick = true)
            MusicControls(np, actions, focus, up, large = true)
        }
        return
    }
    Column(
        modifier
            .popIn()
            .size(PanelDimens.gridWidth, PanelDimens.musicCardHeight)
            .then(glass)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Artwork(np?.artwork, 36.dp)
            Column(Modifier.weight(1f)) {
                Label(np?.title ?: stringResource(R.string.nothing_playing), maxLines = 1)
                np?.artist?.let { Detail(it) }
            }
        }
        Progress(np)
        MusicControls(np, actions, focus, up)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MusicControls(np: NowPlaying?, actions: TileActions, focus: MusicFocus, up: FocusRequester, large: Boolean = false) {
    val side = if (large) 52.dp else 44.dp
    val sideIcon = if (large) 30.dp else 24.dp
    val mainBox = if (large) 60.dp else 44.dp
    val mainIcon = if (large) 40.dp else 24.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        TransportButton(
            icon = Icons.Filled.FastRewind,
            label = stringResource(R.string.music_previous),
            requester = focus.previous,
            upTarget = up, leftTarget = FocusRequester.Cancel, rightTarget = focus.play,
            activate = actions.onSkipPrevious,
            box = side, iconSize = sideIcon,
        )
        TransportButton(
            icon = if (np?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = stringResource(if (np?.isPlaying == true) R.string.music_pause else R.string.music_play),
            requester = focus.play,
            upTarget = up, leftTarget = focus.previous, rightTarget = focus.next,
            activate = actions.onPlayPause,
            box = mainBox, iconSize = mainIcon,
        )
        TransportButton(
            icon = Icons.Filled.FastForward,
            label = stringResource(R.string.music_next),
            requester = focus.next,
            upTarget = up, leftTarget = focus.play, rightTarget = FocusRequester.Cancel,
            activate = actions.onSkipNext,
            box = side, iconSize = sideIcon,
        )
    }
}

/**
 * The music button's icon, like tvOS: a row of thin bars of different heights. While something
 * plays they sway gently; when it stops they rest. The motion is read while drawing, so it never
 * recomposes.
 */
@Composable
internal fun EqualizerIcon(playing: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val ink = LocalTileInk.current
    val phase: State<Float>? = if (playing) {
        rememberInfiniteTransition(label = "equalizer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
            label = "phase",
        )
    } else null
    Canvas(modifier.size(size * 0.9f, size * 0.7f)) {
        val bars = EQ_BASE.size
        val barWidth = this.size.width / (bars * 2f - 1f)
        val t = phase?.value ?: 0f
        for (i in 0 until bars) {
            // Heights stay close to their resting shape; only a fifth of each bar moves.
            val sway = if (phase == null) 0.9f
            else 0.78f + 0.22f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (t * EQ_SPEED[i] + EQ_SHIFT[i])))
            val h = (this.size.height * EQ_BASE[i] * sway).coerceAtLeast(barWidth)
            drawRoundRect(
                ink,
                topLeft = Offset(i * 2f * barWidth, (this.size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

private val EQ_BASE = floatArrayOf(0.30f, 0.55f, 0.40f, 0.80f, 1.00f, 0.65f, 0.85f, 0.45f, 0.30f)
private val EQ_SPEED = intArrayOf(2, 1, 3, 2, 1, 3, 2, 1, 3)
private val EQ_SHIFT = floatArrayOf(0f, 0.2f, 0.4f, 0.1f, 0.6f, 0.3f, 0.8f, 0.5f, 0.7f)

/** Pops a card in when it appears: fades in, grows from 90% and settles down from just above. */
@Composable
internal fun Modifier.popIn(): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 380f))
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val scale = 0.9f + 0.1f * p
        scaleX = scale
        scaleY = scale
        translationY = (1f - p) * -14.dp.toPx()
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}

@Composable
private fun Artwork(bitmap: Bitmap?, size: Dp, radius: Dp = 8.dp, iconSize: Dp = 18.dp) {
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            TileIcon(Icons.Filled.MusicNote, iconSize, Color.White)
        }
    }
}

/** Bar plus elapsed and remaining time. Ticks twice a second, and only while something plays. */
@Composable
private fun Progress(np: NowPlaying?, thick: Boolean = false) {
    val playing = np?.isPlaying == true
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(playing, np?.positionUpdatedAt, np?.positionMs) {
        now = SystemClock.elapsedRealtime()
        while (playing) {
            delay(500L)
            now = SystemClock.elapsedRealtime()
        }
    }
    val duration = np?.durationMs ?: 0L
    val position = if (np == null) 0L else {
        val since = if (playing && np.positionUpdatedAt > 0L) ((now - np.positionUpdatedAt) * np.speed).toLong() else 0L
        val raw = (np.positionMs + since).coerceAtLeast(0L)
        if (duration > 0L) raw.coerceAtMost(duration) else raw
    }
    val fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (thick) 7.dp else 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (thick) 0.34f else 0.28f))
                .drawBehind { drawRect(Color.White, size = Size(size.width * fraction, size.height)) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Time(clock(position))
            Time(if (duration > 0L) "-" + clock(duration - position) else "")
        }
    }
}

@Composable
private fun Time(text: String) {
    BasicText(text, style = PanelType.labelSmall.copy(color = Color.White.copy(alpha = 0.8f)), maxLines = 1)
}

private fun clock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60)
}

/** Round icon button. Plain (no glass) until focused, then white with a dark icon like the other controls. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    requester: FocusRequester,
    upTarget: FocusRequester,
    leftTarget: FocusRequester,
    rightTarget: FocusRequester,
    activate: () -> Unit,
    box: Dp = 44.dp,
    iconSize: Dp = 24.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val invert = LocalInvertFocus.current
    val dark = focused && invert
    Box(
        Modifier
            .size(box)
            .focusRequester(requester)
            .focusProperties {
                this.up = upTarget
                this.left = leftTarget
                this.right = rightTarget
                this.down = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                when {
                    e.type == KeyEventType.KeyUp && e.key.isSelectKey() -> { activate(); true }
                    e.key.isSelectKey() -> true
                    else -> false
                }
            }
            .focusable()
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                onClick(label = label) { activate(); true }
            }
            .clip(CircleShape)
            .background(
                when {
                    !focused -> Color.Transparent
                    invert -> Color.White
                    else -> Color.White.copy(alpha = 0.28f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalTileInk provides if (dark) GlassTokens.inkDark else Color.White) {
            FadingIcon(icon, iconSize)
        }
    }
}

private fun Key.isSelectKey() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter
