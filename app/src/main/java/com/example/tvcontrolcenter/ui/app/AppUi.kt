package com.example.tvcontrolcenter.ui.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.ui.theme.AppDimens
import com.example.tvcontrolcenter.ui.theme.AppMaterial
import com.example.tvcontrolcenter.ui.theme.AppMotion
import com.example.tvcontrolcenter.ui.theme.AppPalette
import com.example.tvcontrolcenter.ui.theme.AppType

/**
 * App-page surfaces.
 *
 * One flat backdrop, one translucent fill and one solid white focus state. No blur, shadows or
 * scaling: the Control Center keeps its own glass and never routes through these.
 */

enum class AppGlassLevel { Secondary, Primary, Floating }

/**
 * The app-page backdrop. One flat colour, with no wallpaper or decoration behind the content.
 * The Control Center keeps its own wallpaper and backdrop pipeline.
 */
@Composable
fun AppScreenBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(AppPalette.midnight))
}

/** One quiet surface: a translucent fill and a hairline edge. */
@Composable
fun AppGlassPanel(
    level: AppGlassLevel,
    modifier: Modifier = Modifier,
    radius: Dp = AppDimens.groupRadius,
    content: @Composable BoxScope.() -> Unit,
) {
    val fill = when (level) {
        AppGlassLevel.Secondary -> Color.White.copy(alpha = 0.055f)
        AppGlassLevel.Primary -> Color.White.copy(alpha = 0.075f)
        AppGlassLevel.Floating -> Color.White.copy(alpha = 0.09f)
    }
    val shape = RoundedCornerShape(radius)

    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape),
    ) {
        content()
    }
}

/** A footnote that belongs to the page, not to a card. */
@Composable
fun AppNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppPalette.textMuted,
) {
    BasicText(
        text,
        modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        style = AppType.note.copy(color = color),
    )
}

/** A focusable surface. Focus is a solid white card, like tvOS; there is no motion or shadow. */
@Composable
internal fun AppInteractive(
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier,
    radius: Dp,
    requester: FocusRequester? = null,
    semanticRole: Role = Role.Button,
    baseFill: Brush,
    rimBase: Float = 0f,
    focusOverlay: Boolean = true,
    onKeyExtra: ((KeyEvent) -> Boolean)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val localRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(radius)

    Box(
        modifier
            .focusRequester(requester ?: localRequester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                when {
                    onKeyExtra?.invoke(event) == true -> true
                    isAppSelectKey(event) -> {
                        if (event.type == KeyEventType.KeyUp) onActivate()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .semantics {
                role = semanticRole
                contentDescription = label
                onClick(label = label) {
                    onActivate()
                    true
                }
            }
            .clip(shape)
            .drawBehind {
                val corner = CornerRadius(radius.toPx())
                drawRoundRect(baseFill, cornerRadius = corner)
                if (focused && focusOverlay) {
                    drawRoundRect(AppPalette.focusFill, cornerRadius = corner)
                } else if (rimBase > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = rimBase * 0.55f),
                        cornerRadius = corner,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            },
    ) {
        content(focused)
    }
}

enum class AppButtonTone { Primary, Secondary }

/** A flat capsule action. Focus turns it white; primary is distinguished by its brighter edge. */
@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AppButtonTone = AppButtonTone.Secondary,
    requester: FocusRequester? = null,
    height: Dp = AppDimens.secondaryHeight,
    showArrow: Boolean = false,
) {
    val primary = tone == AppButtonTone.Primary
    val baseFill = if (primary) {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.07f)),
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.035f)),
        )
    }
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier.height(height),
        radius = height * 0.5f,
        requester = requester,
        baseFill = baseFill,
        rimBase = if (primary) 0.38f else 0.12f,
    ) { focused ->
        Box(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                label,
                style = AppType.labelStrong.copy(
                    color = if (focused) AppPalette.focusInk else AppPalette.textPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showArrow) {
                Chevron(
                    pointsLeft = false,
                    color = if (focused) AppPalette.focusInk.copy(alpha = 0.65f) else AppPalette.textSecondary,
                    modifier = Modifier.align(Alignment.CenterEnd).size(8.dp),
                )
            }
        }
    }
}

/** A quiet capsule action, used only where a full settings row would be too heavy. */
@Composable
fun AppActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier.height(AppDimens.chipHeight),
        radius = AppDimens.chipRadius,
        baseFill = if (highlighted) {
            Brush.verticalGradient(
                listOf(AppMaterial.selectedTint, Color(0x0F90A4C0)),
            )
        } else {
            Brush.verticalGradient(listOf(AppMaterial.secondaryTop, AppMaterial.secondaryBottom))
        },
        rimBase = if (highlighted) 0.10f else 0f,
        focusOverlay = true,
    ) { focused ->
        Box(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                label,
                style = AppType.label.copy(
                    color = if (focused) AppPalette.focusInk else AppPalette.textSecondary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A flat settings row inside the group's glass. There is no row card: focus itself is the
 * material change. The little chevron before the value is the only cue that Left adjusts it.
 */
@Composable
fun AppSettingRow(
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
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.rowHeight)
            .padding(horizontal = 6.dp),
        radius = AppDimens.rowRadius,
        requester = requester,
        baseFill = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        rimBase = 0f,
        onKeyExtra = { event ->
            if (event.key == Key.DirectionLeft) {
                if (event.type == KeyEventType.KeyUp) onStep(-1)
                true
            } else false
        },
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (swatch != null) {
                Box(
                    Modifier
                        .size(13.dp)
                        .background(swatch, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
            }
            BasicText(
                label,
                Modifier.weight(1f),
                style = AppType.label.copy(
                    color = if (focused) AppPalette.focusInk else AppPalette.textSecondary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            Chevron(
                pointsLeft = true,
                color = if (focused) AppPalette.focusInk.copy(alpha = 0.7f) else AppPalette.textMuted,
                modifier = Modifier.size(6.dp),
            )
            Spacer(Modifier.width(7.dp))
            BasicText(
                value,
                Modifier.widthIn(min = 54.dp, max = 190.dp),
                style = AppType.value.copy(
                    color = if (focused) AppPalette.focusInk.copy(alpha = 0.65f) else AppPalette.textTertiary,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A direct action row, for moves, pickers and quiet destructive actions. */
@Composable
fun AppActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    showChevron: Boolean = false,
) {
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.rowHeight)
            .padding(horizontal = 6.dp),
        radius = AppDimens.rowRadius,
        baseFill = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        rimBase = 0f,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                label,
                Modifier.weight(1f),
                style = AppType.label.copy(
                    color = when {
                        focused -> AppPalette.focusInk
                        destructive -> AppPalette.danger
                        else -> AppPalette.textPrimary
                    },
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value != null) {
                Spacer(Modifier.width(10.dp))
                BasicText(
                    value,
                    style = AppType.value.copy(
                        color = if (focused) AppPalette.focusInk.copy(alpha = 0.6f) else AppPalette.textTertiary,
                    ),
                    maxLines = 1,
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(5.dp)
                        .background(if (focused) AppPalette.focusInk else AppPalette.accentBright, CircleShape),
                )
            }
            if (showChevron) {
                Spacer(Modifier.width(8.dp))
                Chevron(
                    pointsLeft = false,
                    color = if (focused) AppPalette.focusInk.copy(alpha = 0.6f) else AppPalette.textMuted,
                    modifier = Modifier.size(7.dp),
                )
            }
        }
    }
}

/** A readiness row in the home setup sheet. The dot carries the state, the text confirms it. */
@Composable
fun AppStatusRow(
    label: String,
    status: String,
    tone: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppInteractive(
        label = "$label, $status",
        onActivate = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.rowHeight)
            .padding(horizontal = 6.dp),
        radius = AppDimens.rowRadius,
        baseFill = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        rimBase = 0f,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .drawWithCache {
                        onDrawBehind {
                            drawCircle(tone.copy(alpha = 0.18f), radius = size.minDimension * 0.5f)
                            drawCircle(tone, radius = size.minDimension * 0.24f)
                        }
                    },
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                label,
                Modifier.weight(1f),
                style = AppType.label.copy(
                    color = if (focused) AppPalette.focusInk else AppPalette.textPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                status,
                style = AppType.value.copy(color = if (focused) AppPalette.focusInk else tone),
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Chevron(
                pointsLeft = false,
                color = if (focused) AppPalette.focusInk.copy(alpha = 0.6f) else AppPalette.textMuted,
                modifier = Modifier.size(7.dp),
            )
        }
    }
}

/** A glass group: one sheet, a tracked section label, and an inset list of rows. */
@Composable
fun AppSettingGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    title,
                    Modifier.weight(1f),
                    style = AppType.section.copy(color = AppPalette.textTertiary),
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(9.dp))
        }
        AppGlassPanel(
            level = AppGlassLevel.Secondary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                content = content,
            )
        }
    }
}

/** Segmented navigation. The selected tab has a quiet fill; focus is the white card. */
@Composable
fun AppTabRail(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val count = labels.size
    val selected = selectedIndex.coerceIn(0, count - 1)
    AppGlassPanel(
        level = AppGlassLevel.Floating,
        modifier = modifier.fillMaxWidth().height(AppDimens.tabRailHeight),
        radius = AppDimens.tabRailRadius,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val gap = 3.dp
            val tabWidth = (maxWidth - gap * (count - 1).toFloat()) / count.toFloat()
            Box(
                Modifier
                    .offset(x = (tabWidth + gap) * selected.toFloat())
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
            )
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                labels.forEachIndexed { index, label ->
                    AppTab(
                        label = label,
                        selected = index == selected,
                        onClick = { onSelect(index) },
                        modifier = Modifier.width(tabWidth).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier,
        radius = 19.dp,
        baseFill = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        rimBase = 0f,
        focusOverlay = true,
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(
                label,
                style = AppType.tab.copy(
                    color = when {
                        focused -> AppPalette.focusInk
                        selected -> AppPalette.textPrimary
                        else -> AppPalette.textSecondary
                    },
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A control in the add-controls grid: an icon lit inside its own small glass disc. */
@Composable
fun AppControlCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier.height(60.dp),
        radius = 16.dp,
        baseFill = Brush.verticalGradient(
            listOf(AppMaterial.secondaryTop, AppMaterial.secondaryBottom),
        ),
        rimBase = 0f,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(
                        if (focused) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.07f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                leading()
            }
            Spacer(Modifier.width(9.dp))
            BasicText(
                label,
                Modifier.weight(1f),
                style = AppType.label.copy(
                    color = if (focused) AppPalette.focusInk else AppPalette.textPrimary,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One of the four app slots: a glass tile with a number, a name and a selected light. */
@Composable
fun AppSlotCard(
    index: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppInteractive(
        label = "$index, $label",
        onActivate = onClick,
        modifier = modifier.height(56.dp),
        radius = 16.dp,
        baseFill = if (selected) {
            Brush.verticalGradient(listOf(AppMaterial.selectedTint, Color(0x0F90A4C0)))
        } else {
            Brush.verticalGradient(listOf(AppMaterial.secondaryTop, AppMaterial.secondaryBottom))
        },
        rimBase = if (selected) 0.12f else 0f,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(
                        if (focused) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "$index",
                    style = AppType.badge.copy(
                        color = if (focused) AppPalette.focusInk else AppPalette.textPrimary,
                    ),
                )
            }
            Spacer(Modifier.width(7.dp))
            BasicText(
                label,
                Modifier.weight(1f),
                style = AppType.label.copy(
                    color = if (focused) AppPalette.focusInk else AppPalette.textPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Spacer(Modifier.width(5.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .background(if (focused) AppPalette.focusInk else AppPalette.accentBright, CircleShape),
                )
            }
        }
    }
}

/**
 * The real panel in the editor. No bloom, no broad shadow and no framing card: just the panel and
 * its hairline edge over the page, so the preview does not add another full-size effect layer.
 */
@Composable
fun AppPreview(
    label: String,
    note: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            label,
            Modifier.fillMaxWidth(),
            style = AppType.section.copy(color = AppPalette.textTertiary, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 4.dp)) {
            content()
            Spacer(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.12f),
                            cornerRadius = CornerRadius(AppDimens.previewRadius.toPx()),
                            style = Stroke(0.8.dp.toPx()),
                        )
                    },
            )
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            note,
            Modifier.fillMaxWidth(),
            style = AppType.note.copy(color = AppPalette.textMuted, textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A two-stroke chevron, light enough to sit inside a row. */
@Composable
private fun Chevron(
    pointsLeft: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.3.dp.toPx()
        if (pointsLeft) {
            drawLine(
                color,
                Offset(w * 0.72f, h * 0.16f),
                Offset(w * 0.28f, h * 0.5f),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(w * 0.28f, h * 0.5f),
                Offset(w * 0.72f, h * 0.84f),
                stroke,
                StrokeCap.Round,
            )
        } else {
            drawLine(
                color,
                Offset(w * 0.28f, h * 0.16f),
                Offset(w * 0.72f, h * 0.5f),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(w * 0.72f, h * 0.5f),
                Offset(w * 0.28f, h * 0.84f),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

private fun isAppSelectKey(event: KeyEvent): Boolean =
    event.key == Key.DirectionCenter ||
        event.key == Key.Enter ||
        event.key == Key.NumPadEnter ||
        event.key == Key.Spacebar
