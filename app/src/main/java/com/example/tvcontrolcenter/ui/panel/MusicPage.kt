package com.example.tvcontrolcenter.ui.panel

import android.graphics.Bitmap
import android.os.SystemClock
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
internal enum class PanelPage { GRID, MUSIC }

/** Focus handles for the three buttons on the music page. */
internal class MusicFocus {
    val previous = FocusRequester()
    val play = FocusRequester()
    val next = FocusRequester()
}

/**
 * The now-playing card: album picture, title and artist, a progress bar with elapsed and
 * remaining time, and rewind / play-pause / forward. Takes the place of the grid.
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
    modifier: Modifier = Modifier,
) {
    val style = LocalFrostStyle.current
    val radius = PanelDimens.sheetRadius
    val glass = if (sheet && backdrop == null) {
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
        )
    }
    Column(
        modifier
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TransportButton(
                icon = Icons.Filled.FastRewind,
                label = stringResource(R.string.music_previous),
                requester = focus.previous,
                upTarget = up, leftTarget = FocusRequester.Cancel, rightTarget = focus.play,
                activate = actions.onSkipPrevious,
            )
            TransportButton(
                icon = if (np?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = stringResource(if (np?.isPlaying == true) R.string.music_pause else R.string.music_play),
                requester = focus.play,
                upTarget = up, leftTarget = focus.previous, rightTarget = focus.next,
                activate = actions.onPlayPause,
            )
            TransportButton(
                icon = Icons.Filled.FastForward,
                label = stringResource(R.string.music_next),
                requester = focus.next,
                upTarget = up, leftTarget = focus.play, rightTarget = FocusRequester.Cancel,
                activate = actions.onSkipNext,
            )
        }
    }
}

@Composable
private fun Artwork(bitmap: Bitmap?, size: Dp) {
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            TileIcon(Icons.Filled.MusicNote, 18.dp, Color.White)
        }
    }
}

/** Bar plus elapsed and remaining time. Ticks twice a second, and only while something plays. */
@Composable
private fun Progress(np: NowPlaying?) {
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
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.28f))
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
) {
    var focused by remember { mutableStateOf(false) }
    val invert = LocalInvertFocus.current
    val dark = focused && invert
    Box(
        Modifier
            .size(44.dp)
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
            FadingIcon(icon, 24.dp)
        }
    }
}

private fun Key.isSelectKey() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter
