package com.example.tvcontrolcenter.ui.panel

import android.view.SoundEffectConstants
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.ui.glass.frostedTile
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.Motion

private val LiftDistance = 4.dp

/**
 * One focusable frosted-glass control.
 *
 * Focus lifts the tile, scales it to [GlassTokens.focusScale], brightens the glass and adds
 * a glint. Everything that changes per frame (focus, open progress) is read in the draw
 * phase, so moving focus redraws two tiles and recomposes nothing.
 *
 * @param appear 0..1 open/close progress, read in the draw phase.
 * @param active the control is "on"; its glass is brighter.
 * @param selected editor only: draws an outline.
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
    appear: () -> Float = { 1f },
    onFocused: () -> Unit = {},
    onKey: (KeyEvent) -> Boolean = { false },
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusAmount = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = Motion.focusSpring(),
        label = "tileFocus",
    )
    val view = LocalView.current

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusProperties(focusProps)
            .onFocusChanged { state ->
                if (state.isFocused && !focused) {
                    onFocused()
                    if (GlassTokens.soundCues) view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                }
                focused = state.isFocused
            }
            .onKeyEvent { event ->
                when {
                    onKey(event) -> true
                    event.type == KeyEventType.KeyUp && event.key.isSelect() -> {
                        if (GlassTokens.soundCues) view.playSoundEffect(SoundEffectConstants.CLICK)
                        onActivate()
                        true
                    }
                    event.key.isSelect() -> true // swallow the matching key-down
                    else -> false
                }
            }
            .focusable()
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateText?.let { stateDescription = it }
                role = Role.Button
                onClick(label = label) { onActivate(); true }
            }
            .graphicsLayer {
                val a = appear()
                val f = focusAmount.value
                val s = (0.7f + 0.3f * a) * (1f + (GlassTokens.focusScale - 1f) * f)
                scaleX = s
                scaleY = s
                alpha = a.coerceIn(0f, 1f)
                translationY = -LiftDistance.toPx() * f
            }
            .frostedTile(
                cornerRadius = cornerRadius,
                active = active,
                focus = { focusAmount.value },
                selected = selected,
            ),
        content = content,
    )
}

private fun Key.isSelect() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter
