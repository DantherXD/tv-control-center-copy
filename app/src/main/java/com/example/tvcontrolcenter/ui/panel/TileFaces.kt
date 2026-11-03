package com.example.tvcontrolcenter.ui.panel

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.NetworkKind
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileSize
import com.example.tvcontrolcenter.ui.glass.LocalTileInk
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import com.example.tvcontrolcenter.ui.theme.PanelType

/** What a control shows right now. */
internal class Face(
    val icon: ImageVector,
    val title: String,
    val detail: String?,
    val active: Boolean,
)

@Composable
internal fun faceOf(id: TileId, state: ControlCenterUiState): Face {
    val look = lookOf(id)
    val title = stringResource(look.label)
    val on = stringResource(R.string.state_on)
    val off = stringResource(R.string.state_off)
    return when (id) {
        TileId.WIFI -> {
            val net = state.status.network
            val detail = when (net) {
                NetworkKind.WIFI -> on
                NetworkKind.ETHERNET -> "Ethernet"
                NetworkKind.NONE -> off
            }
            Face(if (net == NetworkKind.NONE) Icons.Filled.WifiOff else look.icon, title, detail, net != NetworkKind.NONE)
        }
        TileId.DND -> {
            val dnd = state.status.dndEnabled
            Face(look.icon, title, if (dnd) on else off, dnd)
        }
        TileId.BLUETOOTH -> {
            val bt = state.tiles[TileId.BLUETOOTH]
            val name = bt?.detail
            Face(look.icon, name ?: title, if (name == null) (if (bt?.active == true) on else off) else null, bt?.active == true)
        }
        TileId.CAFFEINE -> {
            val awake = state.tiles[TileId.CAFFEINE]?.active == true
            Face(look.icon, title, if (awake) on else off, awake)
        }
        TileId.SLEEP_TIMER -> {
            val sleep = state.tiles[TileId.SLEEP_TIMER]
            Face(look.icon, title, sleep?.detail ?: off, sleep?.active == true)
        }
        else -> Face(look.icon, title, null, false)
    }
}

/** Icon plus name (and state) laid out for the control's size. */
@Composable
internal fun GenericFace(id: TileId, face: Face, size: TileSize) {
    val tint = LocalTileInk.current.copy(alpha = if (face.active) 1f else 0.9f)
    when (size) {
        TileSize.SMALL, TileSize.TOP -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TileIcon(face.icon, PanelDimens.iconSmall, tint)
        }
        TileSize.WIDE, TileSize.FULL -> Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TileIcon(face.icon, PanelDimens.iconSmall, tint)
            Column(Modifier.weight(1f)) {
                Label(face.title)
                face.detail?.let { Detail(it) }
            }
        }
        TileSize.TALL -> Column(
            Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TileIcon(face.icon, PanelDimens.iconSmall, tint)
            Spacer(Modifier.height(6.dp))
            Label(face.title, style = PanelType.labelSmall, maxLines = 1)
        }
        TileSize.LARGE -> if (id == TileId.POWER) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TileIcon(face.icon, PanelDimens.iconLarge * 1.2f, tint)
                Spacer(Modifier.height(6.dp))
                Label(face.title)
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                TileIcon(face.icon, PanelDimens.iconLarge, tint)
                Column {
                    Label(face.title)
                    face.detail?.let { Detail(it) }
                }
            }
        }
    }
}

@Composable
internal fun VolumeFace(percent: Int, muted: Boolean, size: TileSize) {
    val icon = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp
    val fill = if (muted) 0 else percent
    val ink = LocalTileInk.current
    when (size) {
        TileSize.SMALL, TileSize.TOP -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FadingIcon(icon, PanelDimens.iconSmall)
        }
        TileSize.WIDE, TileSize.FULL -> Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FadingIcon(icon, PanelDimens.iconSmall)
            VolumeBar(fill, Modifier.weight(1f).height(7.dp), ink = ink)
        }
        TileSize.TALL -> Column(
            Modifier.fillMaxSize().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VolumeBar(fill, Modifier.weight(1f).width(7.dp), vertical = true, ink = ink)
            Spacer(Modifier.height(8.dp))
            FadingIcon(icon, PanelDimens.iconSmall)
        }
        TileSize.LARGE -> Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            FadingIcon(icon, PanelDimens.iconLarge)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(if (muted) stringResource(R.string.state_muted) else "$percent%")
                VolumeBar(fill, Modifier.fillMaxWidth().height(7.dp), ink = ink)
            }
        }
    }
}

@Composable
private fun VolumeBar(percent: Int, modifier: Modifier, vertical: Boolean = false, ink: Color = Color.White) {
    // The bar glides to the new level instead of jumping; read while drawing, so it never recomposes.
    val level by animateFloatAsState(percent / 100f, tween(220), label = "volume")
    Box(
        modifier
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.28f))
            .drawBehind {
                val f = level.coerceIn(0f, 1f)
                val s = if (vertical) Size(size.width, size.height * f) else Size(size.width * f, size.height)
                val top = if (vertical) size.height - s.height else 0f
                drawRect(ink, topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = s)
            },
    )
}

@Composable
internal fun NowPlayingFace(
    title: String?,
    artist: String?,
    playing: Boolean,
    size: TileSize,
) {
    val note = lookOf(TileId.NOW_PLAYING).icon
    val toggle = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow
    val name = title ?: stringResource(R.string.nothing_playing)
    when (size) {
        TileSize.SMALL, TileSize.TOP -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FadingIcon(if (title == null) note else toggle, PanelDimens.iconSmall)
        }
        TileSize.WIDE -> Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TileIcon(note, PanelDimens.iconSmall)
            Column(Modifier.weight(1f)) {
                Label(name, maxLines = 1)
                artist?.let { Detail(it) }
            }
        }
        TileSize.FULL -> Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TileIcon(note, PanelDimens.iconSmall)
            Column(Modifier.weight(1f)) {
                Label(name, maxLines = 1)
                artist?.let { Detail(it) }
            }
            FadingIcon(toggle, PanelDimens.iconSmall)
        }
        TileSize.TALL -> Column(
            Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FadingIcon(toggle, PanelDimens.iconSmall)
            Spacer(Modifier.height(6.dp))
            Label(name, style = PanelType.labelSmall, maxLines = 1)
        }
        TileSize.LARGE -> Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TileIcon(note, PanelDimens.iconLarge)
                FadingIcon(toggle, PanelDimens.iconSmall)
            }
            Column {
                Label(name, maxLines = 2)
                artist?.let { Detail(it) }
            }
        }
    }
}

@Composable
internal fun TileIcon(icon: ImageVector, size: Dp, tint: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    // Decorative: the tile itself carries the spoken label. Unspecified follows the tile: white, or dark on a white tile.
    val color = if (tint == Color.Unspecified) LocalTileInk.current else tint
    val styled = IconStyles.resolve(icon, LocalIconStyle.current)
    Icon(styled, contentDescription = null, modifier = modifier.size(size), tint = color)
}

@Composable
internal fun Label(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = PanelType.label,
    maxLines: Int = 2,
) {
    BasicText(
        text, modifier.clearAndSetSemantics { },
        style = style.copy(color = LocalTileInk.current),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun Detail(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text, modifier.clearAndSetSemantics { },
        style = PanelType.detail.copy(color = LocalTileInk.current.copy(alpha = 0.82f)),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** An icon that fades into the next one when it changes (play and pause, volume and mute). */
@Composable
internal fun FadingIcon(icon: ImageVector, size: Dp, modifier: Modifier = Modifier) {
    Crossfade(targetState = icon, animationSpec = tween(180), label = "icon", modifier = modifier) {
        TileIcon(it, size)
    }
}
