package com.example.tvcontrolcenter.ui.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.domain.model.Wallpaper
import com.example.tvcontrolcenter.ui.background.AppWallpaper
import com.example.tvcontrolcenter.ui.theme.AppDimens
import com.example.tvcontrolcenter.ui.theme.AppMotion
import com.example.tvcontrolcenter.ui.theme.AppPalette
import com.example.tvcontrolcenter.ui.theme.AppType

/**
 * Wallpaper plus the two quiet veils that keep the app's own pages readable. Only the home page
 * and the editor use this; the panel keeps its own backdrop and scrim.
 */
@Composable
internal fun AppScreenBackground(wallpaper: Wallpaper, modifier: Modifier = Modifier) {
    Box(modifier) {
        AppWallpaper(wallpaper, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    // Built once per size, not once per frame: focus animations redraw often.
                    val depth = Brush.verticalGradient(
                        0f to Color(0xE6070510),
                        0.55f to Color(0xB3080514),
                        1f to Color(0xF207050E),
                    )
                    // A little more shade on the reading side, a little more picture on the right.
                    val readingSide = Brush.horizontalGradient(
                        0f to Color(0xD905040B),
                        0.5f to Color(0x8C05040B),
                        1f to Color(0x3305040B),
                    )
                    val floor = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1f to Color(0x9905040B),
                    )
                    onDrawBehind {
                        drawRect(depth)
                        drawRect(readingSide)
                        drawRect(floor)
                    }
                },
        )
    }
}

/**
 * A restrained glass card for app content: one gradient fill, one hairline and a faint top sheen.
 * No blur pass, so a card costs a single draw and behaves on weak hardware.
 */
@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    radius: Dp = AppDimens.cardRadius,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(AppPalette.cardTop, AppPalette.cardBottom)))
            .border(1.dp, AppPalette.hairline, shape),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .drawWithCache {
                    val sheen = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.055f),
                        0.45f to Color.Transparent,
                    )
                    onDrawBehind { drawRect(sheen) }
                },
        )
        content()
    }
}

/** Small label above a heading: accent dot, then the words in a wide, quiet caps style. */
@Composable
internal fun AppEyebrow(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(AppPalette.accentBright, CircleShape))
        Spacer(Modifier.width(7.dp))
        BasicText(
            text,
            style = AppType.eyebrow.copy(color = AppPalette.accentBright),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Section heading inside the editor: a short accent rule, then the label. */
@Composable
internal fun AppSectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(12.dp)
                .background(AppPalette.accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text,
            style = AppType.section.copy(color = AppPalette.accentBright),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A contextual note. The short rule ties it to the setting above without shouting. */
@Composable
internal fun AppNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(2.dp)
                .height(14.dp)
                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(1.dp)),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text,
            Modifier.weight(1f),
            style = AppType.note.copy(color = AppPalette.textMuted),
        )
    }
}

/**
 * The shared focus/press behaviour for every app-page control. It follows the editor's existing
 * remote rules: OK activates on key up (so a long press that opened the page is not a press),
 * extra keys such as Left are handled by the caller, and focus is always visible as a border,
 * a brighter surface and, for buttons, a small lift.
 */
@Composable
internal fun AppInteractive(
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppDimens.rowRadius),
    requester: FocusRequester? = null,
    semanticRole: Role = Role.Button,
    scaleOnFocus: Boolean = false,
    fill: (focused: Boolean, pressed: Boolean) -> Brush,
    borderColor: (focused: Boolean) -> Color = { focused ->
        if (focused) AppPalette.accentBright else AppPalette.hairline
    },
    borderWidth: (focused: Boolean) -> Dp = { focused ->
        if (focused) AppDimens.focusBorder else 1.dp
    },
    onKeyExtra: (KeyEvent) -> Boolean = { false },
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val focusRequester = requester ?: remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .appFocusScale(scaleOnFocus, focused, pressed)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) pressed = false
            }
            .onKeyEvent { event ->
                when {
                    onKeyExtra(event) -> true
                    event.key.isAppSelectKey() && event.type == KeyEventType.KeyDown -> {
                        if (event.nativeKeyEvent.repeatCount == 0) pressed = true
                        true
                    }
                    event.key.isAppSelectKey() && event.type == KeyEventType.KeyUp -> {
                        if (pressed) onActivate()
                        pressed = false
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = semanticRole
                onClick(label = label) {
                    onActivate()
                    true
                }
            }
            .clip(shape)
            .background(fill(focused, pressed))
            .border(borderWidth(focused), borderColor(focused), shape),
    ) {
        content(focused)
    }
}

@Composable
private fun Modifier.appFocusScale(enabled: Boolean, focused: Boolean, pressed: Boolean): Modifier {
    if (!enabled) return this
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> AppMotion.pressScale
            focused -> AppMotion.focusScale
            else -> 1f
        },
        animationSpec = AppMotion.focusSpring(),
        label = "appFocusScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private fun Key.isAppSelectKey(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

internal enum class AppButtonTone { Primary, Secondary }

/** Primary or secondary action. The primary one is the only filled violet surface in the UI. */
@Composable
internal fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AppButtonTone = AppButtonTone.Secondary,
    requester: FocusRequester? = null,
    height: Dp = AppDimens.chipHeight,
    showArrow: Boolean = false,
) {
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(AppDimens.buttonRadius),
        requester = requester,
        scaleOnFocus = true,
        fill = { focused, pressed ->
            when (tone) {
                AppButtonTone.Primary -> when {
                    pressed -> Brush.linearGradient(listOf(Color(0xFF5334BC), Color(0xFF6B4CE0)))
                    focused -> Brush.linearGradient(listOf(Color(0xFF7B5CF0), Color(0xFFAC96FF)))
                    else -> Brush.linearGradient(listOf(Color(0xFF6344E0), Color(0xFF8B6DFF)))
                }
                AppButtonTone.Secondary -> when {
                    pressed -> Brush.verticalGradient(listOf(AppPalette.cardPressedTop, AppPalette.cardPressedBottom))
                    focused -> Brush.verticalGradient(listOf(AppPalette.cardFocusedTop, AppPalette.cardFocusedBottom))
                    else -> Brush.verticalGradient(listOf(AppPalette.controlTop, AppPalette.controlBottom))
                }
            }
        },
        borderColor = { focused ->
            when {
                focused && tone == AppButtonTone.Primary -> Color.White.copy(alpha = 0.92f)
                focused -> AppPalette.accentBright
                tone == AppButtonTone.Primary -> Color.White.copy(alpha = 0.16f)
                else -> AppPalette.hairline
            }
        },
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BasicText(
                label,
                style = AppType.labelStrong.copy(color = AppPalette.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showArrow) {
                Spacer(Modifier.width(10.dp))
                Chevron(
                    color = if (focused) AppPalette.textPrimary else AppPalette.accentBright,
                    size = 10.dp,
                )
            }
        }
    }
}

/**
 * A flat action chip: move, hide, reset, tab-like choices and app rows. [highlighted] marks the
 * current choice with an accent edge; focus is an accent border plus a lifted surface.
 */
@Composable
internal fun AppActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    requester: FocusRequester? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier.height(AppDimens.chipHeight),
        shape = RoundedCornerShape(AppDimens.chipRadius),
        requester = requester,
        fill = { focused, pressed ->
            when {
                pressed -> Brush.verticalGradient(listOf(AppPalette.cardPressedTop, AppPalette.cardPressedBottom))
                focused -> Brush.verticalGradient(listOf(AppPalette.cardFocusedTop, AppPalette.cardFocusedBottom))
                highlighted -> Brush.verticalGradient(listOf(AppPalette.accentFocus, AppPalette.accentSoft))
                else -> Brush.verticalGradient(listOf(AppPalette.controlTop, AppPalette.controlBottom))
            }
        },
        borderColor = { focused ->
            when {
                focused -> AppPalette.accentBright
                highlighted -> AppPalette.accentLine
                else -> AppPalette.hairline
            }
        },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            BasicText(
                label,
                style = AppType.label.copy(color = AppPalette.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One editor tab. Selected and focused are different states: a fill plus underline, then a border. */
@Composable
internal fun AppTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier.height(AppDimens.tabHeight),
        shape = RoundedCornerShape(AppDimens.chipRadius),
        semanticRole = Role.Tab,
        fill = { focused, pressed ->
            when {
                pressed -> Brush.verticalGradient(listOf(AppPalette.cardPressedTop, AppPalette.cardPressedBottom))
                focused -> Brush.verticalGradient(listOf(AppPalette.cardFocusedTop, AppPalette.cardFocusedBottom))
                selected -> Brush.verticalGradient(listOf(AppPalette.accentFocus, AppPalette.accentSoft))
                else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
            }
        },
        borderColor = { focused ->
            when {
                focused -> AppPalette.accentBright
                selected -> AppPalette.accentLine
                else -> Color.Transparent
            }
        },
    ) { focused ->
        Box(Modifier.fillMaxSize()) {
            BasicText(
                label,
                Modifier.align(Alignment.Center),
                style = AppType.tab.copy(
                    color = if (selected || focused) AppPalette.textPrimary else AppPalette.textSecondary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .width(18.dp)
                        .height(2.dp)
                        .background(AppPalette.accentBright, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

/** A segmented tab bar on one glass card. Focus order is simply left to right. */
@Composable
internal fun AppTabBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier, radius = AppDimens.tabBarRadius) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            labels.forEachIndexed { index, label ->
                AppTab(
                    label = label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One permission on the home page: live status, the name, and a clear action state. Pressing it
 * performs exactly what the row did before (open settings, or toggle the overlay service).
 */
@Composable
internal fun AppSetupRow(
    label: String,
    ok: Boolean,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppInteractive(
        label = "$label, $status",
        onActivate = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(AppDimens.rowRadius),
        fill = { focused, pressed ->
            when {
                pressed -> Brush.verticalGradient(listOf(AppPalette.rowPressed, AppPalette.rowBottom))
                focused -> Brush.verticalGradient(listOf(AppPalette.rowFocusedTop, AppPalette.rowFocusedBottom))
                else -> Brush.verticalGradient(listOf(AppPalette.rowTop, AppPalette.rowBottom))
            }
        },
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (ok) AppPalette.success else AppPalette.warning, CircleShape),
            )
            BasicText(
                label,
                Modifier.weight(1f),
                style = AppType.label.copy(color = AppPalette.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (ok) AppPalette.successSoft else AppPalette.warningSoft)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                BasicText(
                    status,
                    style = AppType.badge.copy(
                        color = if (ok) AppPalette.success else AppPalette.warning,
                    ),
                    maxLines = 1,
                )
            }
            Chevron(
                color = if (focused) AppPalette.accentBright else AppPalette.textMuted,
                size = 9.dp,
            )
        }
    }
}

/**
 * One setting on one row, keeping the product's one-row model: OK goes to the next value, Left
 * goes back one, Right leaves the row to the preview. The value sits in its own quiet pill so long
 * labels and long values never clip each other.
 */
@Composable
internal fun AppSettingRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    swatch: Color? = null,
    onStep: (Int) -> Unit,
) {
    AppInteractive(
        label = "$label, $value",
        onActivate = { onStep(1) },
        modifier = modifier.fillMaxWidth().height(AppDimens.rowHeight),
        shape = RoundedCornerShape(AppDimens.rowRadius),
        requester = requester,
        fill = { focused, pressed ->
            when {
                pressed -> Brush.verticalGradient(listOf(AppPalette.rowPressed, AppPalette.rowBottom))
                focused -> Brush.verticalGradient(listOf(AppPalette.rowFocusedTop, AppPalette.rowFocusedBottom))
                else -> Brush.verticalGradient(listOf(AppPalette.rowTop, AppPalette.rowBottom))
            }
        },
        onKeyExtra = { event ->
            if (event.key == Key.DirectionLeft) {
                if (event.type == KeyEventType.KeyDown) onStep(-1)
                true
            } else {
                false
            }
        },
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                label,
                Modifier.weight(1f),
                style = AppType.label.copy(color = AppPalette.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (swatch != null) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(14.dp).background(swatch, CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .widthIn(max = 190.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (focused) AppPalette.accentSoft else Color.White.copy(alpha = 0.06f),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The left mark is the one honest stepper affordance: Left goes back a value.
                    Chevron(
                        color = if (focused) AppPalette.accentBright else AppPalette.textMuted,
                        size = 7.dp,
                        pointsLeft = true,
                    )
                    Spacer(Modifier.width(5.dp))
                    BasicText(
                        value,
                        style = AppType.value.copy(
                            color = if (focused) AppPalette.accentBright else AppPalette.textSecondary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Framing for the real Control Center preview: a label, a glass card that only frames the panel,
 * and an optional caption. The previewed panel itself is unchanged.
 */
@Composable
internal fun AppPreview(
    label: String,
    note: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(AppPalette.accentBright, CircleShape))
            Spacer(Modifier.width(8.dp))
            BasicText(
                label,
                style = AppType.section.copy(color = AppPalette.accentBright),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(8.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().padding(AppDimens.previewPadding),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                note,
                style = AppType.note.copy(color = AppPalette.textMuted),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A small drawn chevron: no bitmap, no icon font, the same weight at every size. */
@Composable
private fun Chevron(color: Color, size: Dp, pointsLeft: Boolean = false) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = (size.toPx() * 0.13f).coerceAtLeast(1f)
        val outer = if (pointsLeft) w * 0.64f else w * 0.36f
        val tip = if (pointsLeft) w * 0.34f else w * 0.66f
        drawLine(
            color = color,
            start = Offset(outer, h * 0.2f),
            end = Offset(tip, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(tip, h * 0.5f),
            end = Offset(outer, h * 0.8f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
