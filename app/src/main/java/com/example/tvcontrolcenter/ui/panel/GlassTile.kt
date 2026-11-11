package com.example.tvcontrolcenter.ui.panel

import android.view.SoundEffectConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.glass.LocalEdgeWidth
import com.example.tvcontrolcenter.ui.glass.LocalFrostStyle
import com.example.tvcontrolcenter.ui.glass.LocalGlassBlur
import com.example.tvcontrolcenter.ui.glass.LocalInvertFocus
import com.example.tvcontrolcenter.ui.glass.LocalLiteGlass
import com.example.tvcontrolcenter.ui.glass.LocalOverBlur
import com.example.tvcontrolcenter.ui.glass.LocalSoundCues
import com.example.tvcontrolcenter.ui.glass.LocalTileInk
import com.example.tvcontrolcenter.ui.glass.frostedTile
import com.example.tvcontrolcenter.ui.glass.rememberLiquidBackdrop
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.Motion

/**
 * One focusable frosted-glass control.
 *
 * Focus scales the tile (no lift) to [GlassTokens.focusScale], brightens the glass and adds
 * a glint. Everything that changes per frame (focus, open progress) is read in the draw
 * phase, so moving focus redraws two tiles and recomposes nothing.
 *
 * @param appear 0..1 open/close progress, read in the draw phase.
 * @param active the control is "on"; its glass is brighter.
 * @param selected editor only: draws an outline.
 * @param backdrop blurred screen to show through the glass; [backdropOrigin] is this tile's
 *   top-left corner in the backdrop's coordinates.
 */
@Composable
fun GlassTile(
    label: String,
    cornerRadius: Dp,
    focusRequester: FocusRequester,
    focusProps: FocusProperties.() -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    stateText: String? = null,
    active: Boolean = false,
    selected: Boolean = false,
    /** Drawn white with dark content whatever the focus, e.g. the open page's top-bar button. */
    solid: Boolean = false,
    /** Borderless: no glass until focused. */
    flat: Boolean = false,
    /** Icon and text are greyed until focused (top-bar buttons that are not the open page). */
    dimIdle: Boolean = false,
    /** Ink that reads on the glass behind this control (see `glassInk`); null keeps it white. */
    ink: Color? = null,
    /** Held OK. When set, a short press still activates, a long one calls this instead. */
    onLongPress: (() -> Unit)? = null,
    /** False while a page is leaving: a control that is going away must not be able to take focus. */
    focusable: Boolean = true,
    backdrop: Backdrop? = null,
    backdropOrigin: Offset = Offset.Zero,
    appear: () -> Float = { 1f },
    onFocused: () -> Unit = {},
    onKey: (KeyEvent) -> Boolean = { false },
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val invert = LocalInvertFocus.current
    val lite = LocalLiteGlass.current
    val overBlur = LocalOverBlur.current
    val edge = LocalEdgeWidth.current
    val glassBlur = LocalGlassBlur.current
    val soundCues = LocalSoundCues.current
    val longFired = remember { booleanArrayOf(false) }
    // A screen can open while OK is still held (long press); that release must not press anything.
    val pressedHere = remember { booleanArrayOf(false) }
    val focusAmount = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (lite) snap<Float>() else Motion.focusSpring<Float>(),
        label = "tileFocus",
    )
    val view = LocalView.current
    // The sharp screenshot is cropped for this control only when the shader is enabled. Off, the
    // pre-blurred fallback keeps working everywhere, including Android 11 and below.
    val liquidBackdrop = if (glassBlur.value > 0f && !lite && !flat && !solid) {
        rememberLiquidBackdrop(backdrop, backdropOrigin)
    } else null
    // Content colour changes at once on focus; the white fill fades in over it in the draw phase.
    val inkColor = when {
        // A white control takes dark content whatever the glass behind it is doing.
        solid || (invert && focused) -> GlassTokens.inkDark
        ink == null -> if (dimIdle && !focused) Color.White.copy(alpha = 0.6f) else Color.White
        dimIdle && !focused -> ink.copy(alpha = 0.62f)
        else -> ink
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusProperties(focusProps)
            .onFocusChanged { state ->
                if (state.isFocused && !focused) {
                    onFocused()
                    if (soundCues) view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                }
                focused = state.isFocused
            }
            .onKeyEvent { event ->
                when {
                    onKey(event) -> true
                    event.key.isSelect() && event.type == KeyEventType.KeyDown &&
                        event.nativeKeyEvent.repeatCount == 0 -> {
                        pressedHere[0] = true
                        true
                    }
                    onLongPress != null && event.key.isSelect() && event.type == KeyEventType.KeyDown &&
                        event.nativeKeyEvent.repeatCount > 0 -> {
                        if (!longFired[0]) {
                            longFired[0] = true
                            if (soundCues) view.playSoundEffect(SoundEffectConstants.CLICK)
                            onLongPress()
                        }
                        true
                    }
                    event.type == KeyEventType.KeyUp && event.key.isSelect() -> {
                        if (longFired[0]) {
                            longFired[0] = false
                        } else if (pressedHere[0]) {
                            if (soundCues) view.playSoundEffect(SoundEffectConstants.CLICK)
                            onActivate()
                        }
                        pressedHere[0] = false
                        true
                    }
                    event.key.isSelect() -> true // swallow the matching key-down
                    else -> false
                }
            }
            .focusable(focusable)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateText?.let { stateDescription = it }
                role = Role.Button
                onClick(label = label) { onActivate(); true }
                // The held action is part of the control, so a screen reader can reach it too.
                onLongPress?.let { press -> onLongClick(label = label) { press(); true } }
            }
            .then(
                if (lite) Modifier else Modifier.graphicsLayer {
                    val a = appear()
                    val f = focusAmount.value
                    val s = (0.7f + 0.3f * a) * (1f + (GlassTokens.focusScale - 1f) * f)
                    scaleX = s
                    scaleY = s
                    alpha = a.coerceIn(0f, 1f)
                },
            )
            .frostedTile(
                cornerRadius = cornerRadius,
                active = active,
                focus = { focusAmount.value },
                selected = selected,
                backdrop = backdrop,
                origin = backdropOrigin,
                style = LocalFrostStyle.current,
                overBlur = overBlur || backdrop != null,
                invert = invert,
                solid = solid,
                lite = lite,
                edge = edge,
                flat = flat,
                liquidBackdrop = liquidBackdrop,
                blurRadius = glassBlur,
            ),
    ) {
        CompositionLocalProvider(LocalTileInk provides inkColor) { content() }
    }
}

private fun Key.isSelect() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter
