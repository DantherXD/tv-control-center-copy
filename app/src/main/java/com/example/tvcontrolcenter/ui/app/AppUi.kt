package com.example.tvcontrolcenter.ui.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.ui.theme.AppDimens
import com.example.tvcontrolcenter.ui.theme.AppMotion
import com.example.tvcontrolcenter.ui.theme.AppPalette
import com.example.tvcontrolcenter.ui.theme.AppType

/**
 * App-page design system.
 *
 * TV first: a flat backdrop, a left navigation rail, one settings column and one passive
 * preview card. Focus is the only moving state; it uses a white surface and a small scale
 * change, never decoration. The Control Center keeps its own glass and is not routed here.
 */

/** The six places a viewer can be. Home is always the start destination. */
enum class AppDestination(val label: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Rounded.Home),
    Layout(R.string.tab_controls, Icons.Rounded.GridView),
    Style(R.string.tab_look, Icons.Rounded.Palette),
    TopBar(R.string.tab_topbar, Icons.Rounded.ViewCarousel),
    Blur(R.string.tab_blur, Icons.Rounded.BlurOn),
    Apps(R.string.tab_apps, Icons.Rounded.Apps),
    ;

    val isHome: Boolean get() = this == Home
}

/** A flat, full-screen backdrop. Solid colour avoids banding on low-end TV panels. */
@Composable
fun AppScreenBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(AppPalette.background))
}

@Composable
fun AppNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppPalette.textSecondary,
) {
    BasicText(
        text,
        modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        style = AppType.note.copy(color = color),
    )
}

/**
 * The one focusable surface. Default is quiet; focus becomes white with dark content and a
 * small scale lift, following the TV focus system. Rows stay still; cards and rail items lift.
 */
@Composable
internal fun AppInteractive(
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier,
    radius: Dp,
    requester: FocusRequester? = null,
    semanticRole: Role = Role.Button,
    scaleOnFocus: Boolean = false,
    focusRing: Boolean = false,
    baseFill: Brush = SolidColor(Color.Transparent),
    rimBase: Float = 0f,
    focusOverlay: Boolean = true,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val localRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.985f
            focused && scaleOnFocus -> 1.04f
            else -> 1f
        },
        animationSpec = AppMotion.focusSpring(),
        label = "tvScale",
    )
    val shape = RoundedCornerShape(radius)
    val scaled = if (scaleOnFocus) {
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    } else modifier

    Box(
        scaled
            .focusRequester(requester ?: localRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) pressed = false
            }
            .onKeyEvent { event ->
                when {
                    isAppSelectKey(event) -> {
                        if (event.type == KeyEventType.KeyDown) {
                            pressed = true
                        } else if (event.type == KeyEventType.KeyUp) {
                            pressed = false
                            onActivate()
                        }
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
                    if (focusRing) {
                        drawRoundRect(
                            color = AppPalette.accent,
                            cornerRadius = corner,
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                } else if (rimBase > 0f) {
                    drawRoundRect(
                        color = AppPalette.outline.copy(alpha = rimBase),
                        cornerRadius = corner,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            },
    ) {
        content(focused)
    }
}

@Composable
private fun TvIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}

/** The home hero action: a wide TV button with an icon, title and one short subtitle. */
@Composable
fun AppWideButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
) {
    AppInteractive(
        label = title,
        onActivate = onClick,
        modifier = modifier.height(84.dp),
        radius = AppDimens.cardRadius,
        requester = requester,
        scaleOnFocus = true,
        focusRing = true,
        baseFill = SolidColor(AppPalette.focusFill),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIcon(icon, AppPalette.focusInk, Modifier.size(30.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                BasicText(
                    title,
                    style = AppType.title.copy(color = AppPalette.focusInk),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    subtitle,
                    style = AppType.note.copy(color = AppPalette.focusInk.copy(alpha = 0.68f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Persistent TV navigation. Six destinations, vertical, always visible. */
@Composable
fun AppNavigationRail(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(AppDimens.railWidth).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIcon(Icons.Rounded.Tune, AppPalette.accent, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            BasicText(
                stringResource(R.string.app_name),
                style = AppType.subtitle.copy(color = AppPalette.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppDestination.entries.forEach { destination ->
            AppNavItem(
                destination = destination,
                selected = destination == selected,
                onClick = { onSelect(destination) },
            )
        }
    }
}

@Composable
private fun AppNavItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AppInteractive(
        label = stringResource(destination.label),
        onActivate = onClick,
        modifier = Modifier.fillMaxWidth().height(AppDimens.navItemHeight),
        radius = AppDimens.rowRadius,
        semanticRole = Role.Tab,
        scaleOnFocus = true,
        baseFill = SolidColor(if (selected) AppPalette.surfaceHigh else Color.Transparent),
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(22.dp)
                        .background(if (focused) AppPalette.focusInk else AppPalette.accent, CircleShape),
                )
            }
            Spacer(Modifier.width(if (selected) 10.dp else 15.dp))
            TvIcon(
                destination.icon,
                when {
                    focused -> AppPalette.focusInk
                    selected -> AppPalette.textPrimary
                    else -> AppPalette.textSecondary
                },
                Modifier.size(23.dp),
            )
            Spacer(Modifier.width(11.dp))
            BasicText(
                stringResource(destination.label),
                style = AppType.label.copy(
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

/** A short secondary chip. */
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
        baseFill = SolidColor(if (highlighted) AppPalette.surfaceHigh else AppPalette.surface),
        rimBase = 0.4f,
        scaleOnFocus = true,
    ) { focused ->
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            BasicText(
                label,
                style = AppType.label.copy(
                    color = if (focused) AppPalette.focusInk else AppPalette.textPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A settings row. The row itself is the focus target; there are no row cards. */
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
        modifier = modifier.fillMaxWidth().height(AppDimens.rowHeight),
        radius = AppDimens.rowRadius,
        requester = requester,
        baseFill = SolidColor(Color.Transparent),
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (swatch != null) {
                Box(Modifier.size(12.dp).background(swatch, CircleShape))
                Spacer(Modifier.width(10.dp))
            }
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
            Chevron(
                pointsLeft = true,
                color = if (focused) AppPalette.focusInk.copy(alpha = 0.7f) else AppPalette.textTertiary,
                modifier = Modifier.size(6.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                value,
                Modifier.widthIn(min = 54.dp, max = 150.dp),
                style = AppType.value.copy(
                    color = if (focused) AppPalette.focusInk.copy(alpha = 0.7f) else AppPalette.textSecondary,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A direct action row. */
@Composable
fun AppActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    showChevron: Boolean = false,
    requester: FocusRequester? = null,
) {
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier.fillMaxWidth().height(AppDimens.rowHeight),
        radius = AppDimens.rowRadius,
        requester = requester,
        baseFill = SolidColor(Color.Transparent),
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
                        color = if (focused) AppPalette.focusInk.copy(alpha = 0.65f) else AppPalette.textSecondary,
                    ),
                    maxLines = 1,
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(5.dp)
                        .background(if (focused) AppPalette.focusInk else AppPalette.accent, CircleShape),
                )
            }
            if (showChevron) {
                Spacer(Modifier.width(8.dp))
                Chevron(
                    pointsLeft = false,
                    color = if (focused) AppPalette.focusInk.copy(alpha = 0.6f) else AppPalette.textTertiary,
                    modifier = Modifier.size(7.dp),
                )
            }
        }
    }
}

/** A setup/readiness row. */
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
        modifier = modifier.fillMaxWidth().height(AppDimens.rowHeight),
        radius = AppDimens.rowRadius,
        baseFill = SolidColor(Color.Transparent),
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(tone, CircleShape))
            Spacer(Modifier.width(12.dp))
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
                color = if (focused) AppPalette.focusInk.copy(alpha = 0.6f) else AppPalette.textTertiary,
                modifier = Modifier.size(7.dp),
            )
        }
    }
}

/** A section label and its continuous list. No sheet around the rows. */
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
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    title,
                    Modifier.weight(1f),
                    style = AppType.section.copy(color = AppPalette.textSecondary),
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(6.dp))
        }
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/** A compact card for the add-control grid. */
@Composable
fun AppControlCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    leading: @Composable () -> Unit,
) {
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier.height(72.dp),
        radius = AppDimens.cardRadius,
        requester = requester,
        scaleOnFocus = true,
        baseFill = SolidColor(AppPalette.surface),
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(
                        if (focused) AppPalette.focusInk.copy(alpha = 0.10f) else AppPalette.surfaceHigh,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                leading()
            }
            Spacer(Modifier.width(10.dp))
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

/** One app slot. */
@Composable
fun AppSlotCard(
    index: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
) {
    AppInteractive(
        label = "$index, $label",
        onActivate = onClick,
        modifier = modifier.height(64.dp),
        radius = AppDimens.cardRadius,
        requester = requester,
        scaleOnFocus = true,
        baseFill = SolidColor(if (selected) AppPalette.surfaceHigh else AppPalette.surface),
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .background(
                        if (focused) AppPalette.focusInk.copy(alpha = 0.10f) else AppPalette.surfaceHigh,
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
            Spacer(Modifier.width(9.dp))
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
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(5.dp)
                        .background(if (focused) AppPalette.focusInk else AppPalette.accent, CircleShape),
                )
            }
        }
    }
}

/** The panel preview. It is deliberately not focusable; the settings list owns navigation. */
@Composable
fun AppPreview(
    label: String,
    note: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(AppDimens.cardRadius)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            label,
            Modifier.fillMaxWidth(),
            style = AppType.section.copy(color = AppPalette.textSecondary, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(AppPalette.surface)
                .border(1.dp, AppPalette.outline, shape)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        if (note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                note,
                Modifier.fillMaxWidth(),
                style = AppType.note.copy(color = AppPalette.textTertiary, textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A two-stroke chevron. */
@Composable
private fun Chevron(
    pointsLeft: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.4.dp.toPx()
        if (pointsLeft) {
            drawLine(color, Offset(w * 0.72f, h * 0.16f), Offset(w * 0.28f, h * 0.5f), stroke, StrokeCap.Round)
            drawLine(color, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.72f, h * 0.84f), stroke, StrokeCap.Round)
        } else {
            drawLine(color, Offset(w * 0.28f, h * 0.16f), Offset(w * 0.72f, h * 0.5f), stroke, StrokeCap.Round)
            drawLine(color, Offset(w * 0.72f, h * 0.5f), Offset(w * 0.28f, h * 0.84f), stroke, StrokeCap.Round)
        }
    }
}

private fun isAppSelectKey(event: KeyEvent): Boolean =
    event.key == Key.DirectionCenter ||
        event.key == Key.Enter ||
        event.key == Key.NumPadEnter
