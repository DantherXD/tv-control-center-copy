package com.example.tvcontrolcenter.ui.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.domain.model.Wallpaper
import com.example.tvcontrolcenter.ui.background.AppWallpaper
import com.example.tvcontrolcenter.ui.theme.AppDimens
import com.example.tvcontrolcenter.ui.theme.AppMaterial
import com.example.tvcontrolcenter.ui.theme.AppMotion
import com.example.tvcontrolcenter.ui.theme.AppPalette
import com.example.tvcontrolcenter.ui.theme.AppType

/**
 * App-page liquid glass.
 *
 * The app pages are built from four optical layers, none of which needs a runtime blur: a hazy
 * midnight atmosphere, a translucent fill, a specular light source, and a directional rim that
 * catches light at the top-left and disappears into the lower edge. Focus adds light, not outline.
 * The Control Center keeps its own glass and is never routed through these components.
 */

enum class AppGlassLevel { Secondary, Primary, Floating }

/** Full-screen atmosphere: the chosen wallpaper under a midnight grade and three soft light blooms. */
@Composable
fun AppScreenBackground(wallpaper: Wallpaper, modifier: Modifier = Modifier) {
    Box(modifier) {
        AppWallpaper(wallpaper, Modifier.fillMaxSize())
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val grade = Brush.verticalGradient(
                        0f to Color(0xD10A0B1C),
                        0.52f to Color(0x990C0E22),
                        1f to Color(0xD9050612),
                    )
                    val violet = Brush.radialGradient(
                        colors = listOf(Color(0x4DA78BFA), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.08f),
                        radius = size.maxDimension * 0.62f,
                    )
                    val blue = Brush.radialGradient(
                        colors = listOf(Color(0x3D4C6FFF), Color.Transparent),
                        center = Offset(size.width * 0.10f, size.height * 0.92f),
                        radius = size.maxDimension * 0.56f,
                    )
                    val rose = Brush.radialGradient(
                        colors = listOf(Color(0x2EFF7AB6), Color.Transparent),
                        center = Offset(size.width * 0.64f, size.height * 1.02f),
                        radius = size.maxDimension * 0.46f,
                    )
                    // A single wide sheen makes the backdrop feel printed on glass rather than flat.
                    val sheen = Brush.linearGradient(
                        colors = listOf(Color(0x14FFFFFF), Color.Transparent, Color(0x0AFFFFFF)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    val leftShade = Brush.horizontalGradient(
                        colors = listOf(Color(0x59000000), Color.Transparent),
                        startX = 0f,
                        endX = size.width * 0.68f,
                    )
                    val vignette = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Transparent, Color(0x8A02030A)),
                        center = Offset(size.width * 0.50f, size.height * 0.48f),
                        radius = size.maxDimension * 0.82f,
                    )
                    onDrawBehind {
                        drawRect(grade)
                        drawRect(violet)
                        drawRect(blue)
                        drawRect(rose)
                        drawRect(sheen)
                        drawRect(leftShade)
                        drawRect(vignette)
                    }
                },
        )
    }
}

/** One continuous piece of glass: fill, specular, caustic, rim and an inner lower shadow. */
@Composable
fun AppGlassPanel(
    level: AppGlassLevel,
    modifier: Modifier = Modifier,
    radius: Dp = AppDimens.groupRadius,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val elevation = when (level) {
        AppGlassLevel.Floating -> 28.dp
        AppGlassLevel.Primary -> 18.dp
        AppGlassLevel.Secondary -> 0.dp
    }
    val shadow = if (elevation > 0.dp) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.55f),
            spotColor = AppPalette.violet.copy(alpha = 0.30f),
        )
    } else Modifier

    Box(
        modifier
            .then(shadow)
            .clip(shape)
            .drawWithCache {
                val fill = when (level) {
                    AppGlassLevel.Secondary -> Brush.verticalGradient(
                        listOf(AppMaterial.secondaryTop, AppMaterial.secondaryBottom),
                    )
                    AppGlassLevel.Primary -> Brush.verticalGradient(
                        listOf(AppMaterial.primaryTop, AppMaterial.primaryBottom),
                    )
                    AppGlassLevel.Floating -> Brush.verticalGradient(
                        listOf(AppMaterial.floatingTop, AppMaterial.floatingBottom),
                    )
                }
                val specular = Brush.radialGradient(
                    colors = listOf(Color(0x29FFFFFF), Color.Transparent),
                    center = Offset(size.width * 0.24f, -size.height * 0.08f),
                    radius = size.width * 0.82f,
                )
                val sheen = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0x0BFFFFFF), Color.Transparent),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                )
                val caustic = Brush.radialGradient(
                    colors = listOf(AppPalette.blue.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height),
                    radius = size.width * 0.72f,
                )
                val rim = Brush.linearGradient(
                    colors = listOf(Color(0x73FFFFFF), Color(0x0AFFFFFF)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val innerShadow = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)),
                )
                // A group sheet catches much less light than a floating surface; this keeps the
                // edge reading as a reflection instead of an outline around every container.
                val rimAlpha = when (level) {
                    AppGlassLevel.Secondary -> 0.20f
                    AppGlassLevel.Primary -> 0.34f
                    AppGlassLevel.Floating -> 0.44f
                }
                val corner = CornerRadius(radius.toPx())
                onDrawBehind {
                    drawRoundRect(fill, cornerRadius = corner)
                    drawRoundRect(specular, cornerRadius = corner)
                    drawRoundRect(sheen, cornerRadius = corner)
                    drawRoundRect(caustic, cornerRadius = corner)
                    drawRoundRect(
                        brush = rim,
                        cornerRadius = corner,
                        style = Stroke(1.dp.toPx()),
                        alpha = rimAlpha,
                    )
                    drawRoundRect(innerShadow, cornerRadius = corner, style = Stroke(2.dp.toPx()))
                }
            },
    ) {
        content()
    }
}

/** A small lit dot and a quiet tracked label, used above the preview and as an app eyebrow. */
@Composable
fun AppEyebrow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .drawWithCache {
                    onDrawBehind {
                        drawCircle(AppPalette.accent.copy(alpha = 0.20f), radius = size.minDimension * 0.5f)
                        drawCircle(AppPalette.accentBright, radius = size.minDimension * 0.20f)
                    }
                },
        )
        Spacer(Modifier.width(3.dp))
        BasicText(
            text.uppercase(),
            style = AppType.eyebrow.copy(color = AppPalette.textSecondary),
        )
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

/**
 * A focusable glass control. The light lifts over a short spring instead of switching on, and the
 * rim only becomes visible as the control takes focus.
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
    elevation: Dp = 0.dp,
    baseFill: Brush,
    rimBase: Float = 0f,
    focusOverlay: Boolean = true,
    specular: Boolean = false,
    glow: Boolean = false,
    onKeyExtra: ((KeyEvent) -> Boolean)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val localRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val focusProgress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = AppMotion.focusSpring(),
        label = "glassFocus",
    )
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = AppMotion.focusSpring(),
        label = "glassPress",
    )
    val scale = if (scaleOnFocus) {
        1f + (AppMotion.focusScale - 1f) * focusProgress - 0.015f * pressProgress
    } else 1f
    val shape = RoundedCornerShape(radius)
    val shadow = if (elevation > 0.dp) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.50f),
            spotColor = AppPalette.violet.copy(alpha = 0.28f),
        )
    } else Modifier

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(shadow)
            .focusRequester(requester ?: localRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) pressed = false
            }
            .onKeyEvent { event ->
                when {
                    onKeyExtra?.invoke(event) == true -> true
                    isAppSelectKey(event) -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> pressed = true
                            KeyEventType.KeyUp -> {
                                pressed = false
                                onActivate()
                            }
                            else -> Unit
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
            .drawWithCache {
                val focusFill = Brush.verticalGradient(
                    listOf(Color(0x26FFFFFF), Color(0x0AFFFFFF)),
                )
                val specularBrush = Brush.radialGradient(
                    colors = listOf(Color(0x3DFFFFFF), Color.Transparent),
                    center = Offset(size.width * 0.28f, -size.height * 0.18f),
                    radius = size.width * 0.9f,
                )
                val rim = Brush.linearGradient(
                    colors = listOf(Color(0x73FFFFFF), Color(0x0AFFFFFF)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val corner = CornerRadius(radius.toPx())
                onDrawBehind {
                    drawRoundRect(baseFill, cornerRadius = corner)
                    if (specular) drawRoundRect(specularBrush, cornerRadius = corner)
                    if (focusOverlay && focusProgress > 0f) {
                        drawRoundRect(focusFill, cornerRadius = corner, alpha = focusProgress)
                    }
                    if (pressProgress > 0f) {
                        drawRoundRect(
                            color = Color.Black,
                            cornerRadius = corner,
                            alpha = 0.06f * pressProgress,
                        )
                    }
                    if (rimBase > 0f || focusProgress > 0f) {
                        drawRoundRect(
                            brush = rim,
                            cornerRadius = corner,
                            style = Stroke(AppDimens.focusRim.toPx()),
                            alpha = rimBase + (1f - rimBase) * focusProgress,
                        )
                    }
                    if (glow && focusProgress > 0f) {
                        drawRoundRect(
                            color = AppMaterial.focusGlow,
                            cornerRadius = corner,
                            style = Stroke(6.dp.toPx()),
                            alpha = focusProgress,
                        )
                    }
                }
            },
    ) {
        content(focused)
    }
}

enum class AppButtonTone { Primary, Secondary }

/** A milky glass primary action, or a darker glass secondary one; both are capsules. */
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
    val ink = Color(0xFF15121F)
    val baseFill = if (primary) {
        Brush.verticalGradient(listOf(Color(0xE8FFFFFF), Color(0xD9E9E4FF)))
    } else {
        Brush.verticalGradient(listOf(AppMaterial.floatingTop, AppMaterial.floatingBottom))
    }
    AppInteractive(
        label = label,
        onActivate = onClick,
        modifier = modifier.height(height),
        radius = height * 0.5f,
        requester = requester,
        scaleOnFocus = true,
        elevation = if (primary) 22.dp else 10.dp,
        baseFill = baseFill,
        rimBase = if (primary) 0.60f else 0.24f,
        specular = true,
        glow = primary,
    ) { _ ->
        Box(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                label,
                style = AppType.labelStrong.copy(
                    color = if (primary) ink else AppPalette.textPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showArrow) {
                Chevron(
                    pointsLeft = false,
                    color = if (primary) ink.copy(alpha = 0.72f) else AppPalette.textSecondary,
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
        scaleOnFocus = true,
        baseFill = if (highlighted) {
            Brush.verticalGradient(
                listOf(AppMaterial.selectedTint, Color(0x12A78BFA)),
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
                    color = if (focused || highlighted) AppPalette.textPrimary else AppPalette.textSecondary,
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
                    color = if (focused) AppPalette.textPrimary else AppPalette.textSecondary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            Chevron(
                pointsLeft = true,
                color = if (focused) AppPalette.accentBright else AppPalette.textMuted,
                modifier = Modifier.size(6.dp),
            )
            Spacer(Modifier.width(7.dp))
            BasicText(
                value,
                Modifier.widthIn(min = 54.dp, max = 190.dp),
                style = AppType.value.copy(
                    color = if (focused) AppPalette.textPrimary else AppPalette.textTertiary,
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
                        destructive -> AppPalette.danger
                        focused -> AppPalette.textPrimary
                        else -> AppPalette.textSecondary
                    },
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value != null) {
                Spacer(Modifier.width(10.dp))
                BasicText(
                    value,
                    style = AppType.value.copy(color = AppPalette.textTertiary),
                    maxLines = 1,
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(5.dp).background(AppPalette.accentBright, CircleShape))
            }
            if (showChevron) {
                Spacer(Modifier.width(8.dp))
                Chevron(
                    pointsLeft = false,
                    color = if (focused) AppPalette.textSecondary else AppPalette.textMuted,
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
                    color = if (focused) AppPalette.textPrimary else AppPalette.textSecondary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                status,
                style = AppType.value.copy(color = tone),
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Chevron(
                pointsLeft = false,
                color = if (focused) AppPalette.textSecondary else AppPalette.textMuted,
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
                    title.uppercase(),
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

/**
 * Segmented navigation with a pill that glides between equal-width tabs. The selected pill is
 * always present; focus adds a second, brighter layer of light on top of any tab.
 */
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
            val pillOffset by animateDpAsState(
                targetValue = (tabWidth + gap) * selected.toFloat(),
                animationSpec = AppMotion.tabSpring(),
                label = "tabPill",
            )
            Box(
                Modifier
                    .offset(x = pillOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(19.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.40f),
                        spotColor = AppPalette.violet.copy(alpha = 0.25f),
                    )
                    .clip(RoundedCornerShape(19.dp))
                    .drawWithCache {
                        val fill = Brush.verticalGradient(
                            listOf(Color(0x29FFFFFF), Color(0x0DFFFFFF)),
                        )
                        val specular = Brush.radialGradient(
                            colors = listOf(Color(0x33FFFFFF), Color.Transparent),
                            center = Offset(size.width * 0.3f, -size.height * 0.2f),
                            radius = size.width * 0.9f,
                        )
                        val rim = Brush.linearGradient(
                            colors = listOf(Color(0x59FFFFFF), Color(0x08FFFFFF)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        )
                        val corner = CornerRadius(19.dp.toPx())
                        onDrawBehind {
                            drawRoundRect(fill, cornerRadius = corner)
                            drawRoundRect(specular, cornerRadius = corner)
                            drawRoundRect(rim, cornerRadius = corner, style = Stroke(1.dp.toPx()))
                        }
                    },
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
                        selected || focused -> AppPalette.textPrimary
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
        scaleOnFocus = true,
        baseFill = Brush.verticalGradient(
            listOf(AppMaterial.secondaryTop, AppMaterial.secondaryBottom),
        ),
        rimBase = 0f,
        specular = true,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(Color.White.copy(alpha = 0.07f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                leading()
            }
            Spacer(Modifier.width(9.dp))
            BasicText(
                label,
                Modifier.weight(1f),
                style = AppType.label.copy(
                    color = if (focused) AppPalette.textPrimary else AppPalette.textSecondary,
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
        scaleOnFocus = true,
        baseFill = if (selected) {
            Brush.verticalGradient(listOf(AppMaterial.selectedTint, Color(0x0FA78BFA)))
        } else {
            Brush.verticalGradient(listOf(AppMaterial.secondaryTop, AppMaterial.secondaryBottom))
        },
        rimBase = if (selected) 0.12f else 0f,
        specular = true,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "$index",
                    style = AppType.badge.copy(
                        color = if (selected || focused) AppPalette.textPrimary else AppPalette.textTertiary,
                    ),
                )
            }
            Spacer(Modifier.width(7.dp))
            BasicText(
                label,
                Modifier.weight(1f),
                style = AppType.label.copy(
                    color = if (focused || selected) AppPalette.textPrimary else AppPalette.textSecondary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(4.dp).background(AppPalette.accentBright, CircleShape))
            }
        }
    }
}

/**
 * The real panel, floating. There is deliberately no framing card: a soft bloom under it and a
 * broad shadow give the screen its depth, and a hairline of light catches the panel's own edge.
 */
@Composable
fun AppPreview(
    label: String,
    note: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .drawWithCache {
                        onDrawBehind {
                            drawCircle(AppPalette.accent.copy(alpha = 0.20f), radius = size.minDimension * 0.5f)
                            drawCircle(AppPalette.accentBright, radius = size.minDimension * 0.20f)
                        }
                    },
            )
            Spacer(Modifier.width(4.dp))
            BasicText(
                label.uppercase(),
                style = AppType.section.copy(color = AppPalette.textTertiary),
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Spacer(
                Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val bloom = Brush.radialGradient(
                            colors = listOf(
                                AppPalette.violet.copy(alpha = 0.28f),
                                AppPalette.blue.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.48f),
                            radius = size.maxDimension * 0.62f,
                        )
                        val plinth = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.96f),
                            radius = size.width * 0.42f,
                        )
                        onDrawBehind {
                            drawRect(bloom)
                            drawRect(plinth)
                        }
                    },
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .shadow(
                        elevation = 30.dp,
                        shape = RoundedCornerShape(AppDimens.previewRadius),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.72f),
                        spotColor = AppPalette.violet.copy(alpha = 0.38f),
                    ),
            ) {
                content()
                Spacer(
                    Modifier
                        .matchParentSize()
                        .drawWithCache {
                            val rim = Brush.linearGradient(
                                colors = listOf(Color(0x40FFFFFF), Color(0x08FFFFFF)),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height),
                            )
                            onDrawBehind {
                                drawRoundRect(
                                    brush = rim,
                                    cornerRadius = CornerRadius(AppDimens.previewRadius.toPx()),
                                    style = Stroke(0.8.dp.toPx()),
                                )
                            }
                        },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
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
