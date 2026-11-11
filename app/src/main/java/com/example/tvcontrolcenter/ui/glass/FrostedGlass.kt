package com.example.tvcontrolcenter.ui.glass

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.example.tvcontrolcenter.domain.model.FrostStyle
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.kyant.backdrop.Backdrop as KyantBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Fine white noise, one 64 px tile repeated. Faint grain is what makes a flat translucent
 * fill read as frosted glass instead of plastic. Built once, shared by every tile.
 */
private val Grain: ShaderBrush by lazy {
    val size = 64
    val rnd = Random(7)
    val px = IntArray(size * size) { (rnd.nextInt(256) shl 24) or 0x00FFFFFF }
    val bmp = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * The real shader's tuning. [blur] is its own radius, separate from the screenshot's blur
 * strength. [lensHeight] is a fraction of the corner radius and [lensBend] is a fraction of the
 * control's smallest side, so the lens scales with every tile size.
 */
@Immutable
data class LiquidGlassParams(
    val blur: Dp = 9.dp,
    val lensHeight: Float = 0.45f,
    val lensBend: Float = 0.12f,
    val vibrancy: Boolean = true,
    val lensDepth: Boolean = true,
    val chromatic: Boolean = true,
)

/** How dark and how white a glass layer is. Plain numbers, so a style costs nothing to draw. */
private class Tint(
    val dark: Float,
    val veilTop: Float,
    val veilBottom: Float,
    /** Colour of the dark base layer (black for the plain styles). */
    val base: Color = Color.Black,
    /** Colour of the light veil (white for the plain styles). */
    val veil: Color = Color.White,
    /** How much black is laid over the blurred picture inside the glass, dimming it. */
    val cropDim: Float = 0f,
    /** Strength of the glass edge line, relative to the setting's thickness. */
    val rim: Float = 1f,
)

/** A coloured glass: [base] tints the dark layer and [veil] the light one. */
private fun colored(base: Long, veil: Long, dark: Float, top: Float, bottom: Float) =
    Tint(dark, top, bottom, Color(base), Color(veil))

private fun FrostStyle.sheetTint() = when (this) {
    FrostStyle.SMOKE -> Tint(GlassTokens.sheetBaseAlpha, GlassTokens.sheetVeilTop, GlassTokens.sheetVeilBottom)
    FrostStyle.MILKY -> Tint(0.14f, 0.34f, 0.18f)
    FrostStyle.CLEAR -> Tint(0.22f, 0.10f, 0.04f)
    FrostStyle.LENS -> Tint(0.30f, 0.10f, 0.03f, rim = 1.9f)
    FrostStyle.OCEAN -> colored(0xFF0B2E66, 0xFFA8D0FF, 0.46f, 0.20f, 0.10f)
    FrostStyle.AMETHYST -> colored(0xFF2A0F55, 0xFFC8A8FF, 0.46f, 0.20f, 0.10f)
    FrostStyle.EMBER -> colored(0xFF4A1A08, 0xFFFFB27A, 0.46f, 0.20f, 0.10f)
    FrostStyle.FOREST -> colored(0xFF0B3A24, 0xFFA8F0C8, 0.46f, 0.20f, 0.10f)
    FrostStyle.ROSE -> colored(0xFF4A0F2A, 0xFFFFB0D0, 0.46f, 0.20f, 0.10f)
    FrostStyle.MIDNIGHT -> Tint(0.80f, 0.03f, 0.01f)
}

private fun FrostStyle.tileTint(overBlur: Boolean) = if (overBlur) {
    when (this) {
        FrostStyle.SMOKE -> Tint(0.20f, 0.10f, 0.06f)
        FrostStyle.MILKY -> Tint(0.04f, 0.34f, 0.26f)
        FrostStyle.CLEAR -> Tint(0.06f, 0.06f, 0.03f)
        // Dims the blurred picture and drops the white veil, so the edge and sheen carry the look.
        FrostStyle.LENS -> Tint(0.14f, 0.08f, 0.03f, cropDim = 0.30f, rim = 1.9f)
        FrostStyle.OCEAN -> colored(0xFF0B2E66, 0xFFA8D0FF, 0.34f, 0.18f, 0.10f)
        FrostStyle.AMETHYST -> colored(0xFF2A0F55, 0xFFC8A8FF, 0.34f, 0.18f, 0.10f)
        FrostStyle.EMBER -> colored(0xFF4A1A08, 0xFFFFB27A, 0.34f, 0.18f, 0.10f)
        FrostStyle.FOREST -> colored(0xFF0B3A24, 0xFFA8F0C8, 0.34f, 0.18f, 0.10f)
        FrostStyle.ROSE -> colored(0xFF4A0F2A, 0xFFFFB0D0, 0.34f, 0.18f, 0.10f)
        FrostStyle.MIDNIGHT -> Tint(0.62f, 0.04f, 0.02f)
    }
} else {
    when (this) {
        FrostStyle.SMOKE -> Tint(GlassTokens.tileBaseAlpha, GlassTokens.tileVeilTop, GlassTokens.tileVeilBottom)
        FrostStyle.MILKY -> Tint(0.08f, 0.40f, 0.28f)
        FrostStyle.CLEAR -> Tint(0.12f, 0.10f, 0.05f)
        FrostStyle.LENS -> Tint(0.34f, 0.16f, 0.06f, rim = 1.9f)
        FrostStyle.OCEAN -> colored(0xFF0B2E66, 0xFFA8D0FF, 0.40f, 0.22f, 0.12f)
        FrostStyle.AMETHYST -> colored(0xFF2A0F55, 0xFFC8A8FF, 0.40f, 0.22f, 0.12f)
        FrostStyle.EMBER -> colored(0xFF4A1A08, 0xFFFFB27A, 0.40f, 0.22f, 0.12f)
        FrostStyle.FOREST -> colored(0xFF0B3A24, 0xFFA8F0C8, 0.40f, 0.22f, 0.12f)
        FrostStyle.ROSE -> colored(0xFF4A0F2A, 0xFFFFB0D0, 0.40f, 0.22f, 0.12f)
        FrostStyle.MIDNIGHT -> Tint(0.70f, 0.05f, 0.02f)
    }
}

/**
 * The card behind the grid, used only when there is no blurred screen to show through the
 * tiles. Dark base, a white veil that fades downwards and grain. No border.
 * [inflate] grows it past the modifier's bounds on every side, so it can wrap content that
 * sits inside padding.
 */
fun Modifier.frostedSheet(
    cornerRadius: Dp,
    style: FrostStyle = FrostStyle.SMOKE,
    inflate: Dp = 0.dp,
    lite: Boolean = false,
): Modifier = drawBehind {
    val t = style.sheetTint()
    val pad = inflate.toPx()
    val topLeft = Offset(-pad, -pad)
    val box = Size(size.width + 2 * pad, size.height + 2 * pad)
    val r = CornerRadius(cornerRadius.toPx())
    if (lite) {
        // Editor: one flat fill, no gradient and no grain.
        drawRoundRect(Color.Black.copy(alpha = 0.38f), topLeft, box, r)
        return@drawBehind
    }
    drawRoundRect(t.base.copy(alpha = t.dark), topLeft, box, r)
    drawRoundRect(
        Brush.verticalGradient(
            listOf(t.veil.copy(alpha = t.veilTop), t.veil.copy(alpha = t.veilBottom)),
            startY = topLeft.y,
            endY = topLeft.y + box.height,
        ),
        topLeft, box, r,
    )
    drawRoundRect(Grain, topLeft, box, r, alpha = GlassTokens.grainAlpha)
}

/**
 * One control. [focus] runs in the draw phase (0..1), so a focus change redraws this tile
 * only and never recomposes anything. [active] brightens the glass for "on" controls, in
 * place of a coloured icon.
 *
 * With a [liquidBackdrop], the tile samples the real screenshot through the Backdrop
 * library: real blur, vibrancy and a refracting lens around the rim. [backdrop] and [origin]
 * remain the pre-blurred fallback for older Android versions and for the no-screenshot case.
 */
fun Modifier.frostedTile(
    cornerRadius: Dp,
    active: Boolean,
    focus: () -> Float,
    selected: Boolean = false,
    backdrop: Backdrop? = null,
    origin: Offset = Offset.Zero,
    style: FrostStyle = FrostStyle.SMOKE,
    overBlur: Boolean = backdrop != null,
    /** The focused control turns white (its content turns dark, see GlassTile). */
    invert: Boolean = false,
    /** Always white, e.g. the top-bar button whose page is open. */
    solid: Boolean = false,
    /** Flat fills only: no gradient, grain or glint. Used by the editor, which has many controls. */
    lite: Boolean = false,
    /** Thickness of the glass edge line. */
    edge: Dp = 0.8.dp,
    /** Borderless: no glass at rest, only the white focus or page fill. */
    flat: Boolean = false,
    liquidBackdrop: KyantBackdrop? = null,
    liquidGlass: LiquidGlassParams? = null,
): Modifier {
    // RenderEffect needs Android 12; the lens shader needs Android 13 and simply no-ops below
    // it. Older devices, the editor's lite path and borderless/solid tiles keep the fallback.
    if (liquidBackdrop != null && liquidGlass != null && !lite && !flat && !solid &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        val tint = style.tileTint(overBlur = true)
        val shape = RoundedCornerShape(cornerRadius)
        return this.drawBackdrop(
            backdrop = liquidBackdrop,
            shape = { shape },
            effects = {
                if (liquidGlass.vibrancy) vibrancy()
                if (liquidGlass.blur > 0.dp) blur(liquidGlass.blur.toPx(), TileMode.Clamp)
                val radius = cornerRadius.toPx()
                lens(
                    refractionHeight = radius * liquidGlass.lensHeight,
                    refractionAmount = (size.minDimension * liquidGlass.lensBend)
                        .coerceIn(0f, size.minDimension),
                    depthEffect = liquidGlass.lensDepth,
                    chromaticAberration = liquidGlass.chromatic,
                )
            },
            // The app owns the rim and tint, so the library only supplies the refracted image.
            highlight = null,
            shadow = null,
            innerShadow = null,
            onDrawSurface = {
                drawLiquidGlass(tint, cornerRadius, focus, active, invert, solid, selected, edge)
            },
        )
    }
    return frostedTileFallback(
        cornerRadius = cornerRadius,
        active = active,
        focus = focus,
        selected = selected,
        backdrop = backdrop,
        origin = origin,
        style = style,
        overBlur = overBlur,
        invert = invert,
        solid = solid,
        lite = lite,
        edge = edge,
        flat = flat,
    )
}

private fun Modifier.frostedTileFallback(
    cornerRadius: Dp,
    active: Boolean,
    focus: () -> Float,
    selected: Boolean = false,
    backdrop: Backdrop? = null,
    origin: Offset = Offset.Zero,
    style: FrostStyle = FrostStyle.SMOKE,
    overBlur: Boolean = backdrop != null,
    invert: Boolean = false,
    solid: Boolean = false,
    lite: Boolean = false,
    edge: Dp = 0.8.dp,
    flat: Boolean = false,
): Modifier = drawWithCache {
    val tint = style.tileTint(overBlur = overBlur)
    val r = CornerRadius(cornerRadius.toPx())
    if (flat) {
        return@drawWithCache onDrawBehind {
            val f = focus()
            // The open page keeps a soft highlight even without glass.
            if (active) drawRoundRect(Color.White.copy(alpha = 0.22f), cornerRadius = r)
            when {
                solid -> drawRoundRect(Color.White.copy(alpha = 0.97f), cornerRadius = r)
                f > 0.01f && invert -> drawRoundRect(Color.White.copy(alpha = 0.97f * f), cornerRadius = r)
                f > 0.01f -> drawRoundRect(Color.White.copy(alpha = 0.30f * f), cornerRadius = r)
            }
        }
    }
    if (lite) {
        return@drawWithCache onDrawBehind {
            val f = focus()
            drawRoundRect(Color.Black.copy(alpha = 0.38f), cornerRadius = r)
            drawRoundRect(Color.White.copy(alpha = if (active) 0.24f else 0.10f), cornerRadius = r)
            when {
                solid -> drawRoundRect(Color.White.copy(alpha = 0.97f), cornerRadius = r)
                f > 0.01f && invert -> drawRoundRect(Color.White.copy(alpha = 0.97f * f), cornerRadius = r)
                f > 0.01f -> drawRoundRect(Color.White.copy(alpha = 0.30f * f), cornerRadius = r)
            }
            if (edge > 0.dp) drawRim(r, edge.toPx(), tint.rim)
            if (selected) {
                val w = 2.5.dp.toPx()
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius((r.x - w / 2f).coerceAtLeast(0f), (r.y - w / 2f).coerceAtLeast(0f)),
                    style = Stroke(width = w),
                )
            }
        }
    }
    val shape = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, r))
    }
    onDrawBehind {
        val f = focus()

        val boost = (if (active) GlassTokens.tileActiveBoost else 0f) + GlassTokens.tileFocusBoost * f
        if (backdrop != null) {
            clipPath(shape) {
                drawBackdropCrop(backdrop, origin)
                // Styles that dim the blur keep the glass, not the screen, as the bright thing.
                if (tint.cropDim > 0f) drawRect(tint.base.copy(alpha = tint.cropDim))
            }
        }
        drawRoundRect(tint.base.copy(alpha = tint.dark * (1f - 0.5f * f)), cornerRadius = r)
        drawRoundRect(
            Brush.verticalGradient(
                listOf(
                    tint.veil.copy(alpha = (tint.veilTop + boost).coerceAtMost(0.7f)),
                    tint.veil.copy(alpha = (tint.veilBottom + boost * 0.6f).coerceAtMost(0.7f)),
                ),
            ),
            cornerRadius = r,
        )
        drawRoundRect(Grain, cornerRadius = r, alpha = GlassTokens.grainAlpha)

        // Inverted focus: the whole tile turns white.
        val white = if (solid) 1f else if (invert) f else 0f
        if (white > 0.01f) drawRoundRect(Color.White.copy(alpha = 0.97f * white), cornerRadius = r)

        // Top-left specular glint, only while focused (not on a white tile).
        if (f > 0.01f && !invert && !solid) {
            val radius = size.minDimension * 0.9f
            drawRoundRect(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.34f * f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.12f),
                    radius = radius,
                ),
                cornerRadius = r,
            )
        }
        // Glass edge; thickness is a setting, its brightness is the style's.
        if (edge > 0.dp) drawRim(r, edge.toPx(), tint.rim)

        // Editor: the control being edited gets a solid outline.
        if (selected) {
            val w = 2.5.dp.toPx()
            drawRoundRect(
                Color.White,
                topLeft = Offset(w / 2f, w / 2f),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius((r.x - w / 2f).coerceAtLeast(0f), (r.y - w / 2f).coerceAtLeast(0f)),
                style = Stroke(width = w),
            )
        }
    }
}

/**
 * The tint, grain, glint and rim drawn over the library's refracted image. Keeping these in
 * the app is what preserves the existing glass styles while the lens does the optical work.
 */
private fun DrawScope.drawLiquidGlass(
    tint: Tint,
    cornerRadius: Dp,
    focus: () -> Float,
    active: Boolean,
    invert: Boolean,
    solid: Boolean,
    selected: Boolean,
    edge: Dp,
) {
    val r = CornerRadius(cornerRadius.toPx())
    val f = focus()
    val boost = (if (active) GlassTokens.tileActiveBoost else 0f) + GlassTokens.tileFocusBoost * f
    // Lens is the style that dims the picture so its rim reads instead of the screen.
    if (tint.cropDim > 0f) drawRect(tint.base.copy(alpha = tint.cropDim))
    drawRoundRect(tint.base.copy(alpha = tint.dark * (1f - 0.5f * f)), cornerRadius = r)
    drawRoundRect(
        Brush.verticalGradient(
            listOf(
                tint.veil.copy(alpha = (tint.veilTop + boost).coerceAtMost(0.7f)),
                tint.veil.copy(alpha = (tint.veilBottom + boost * 0.6f).coerceAtMost(0.7f)),
            ),
        ),
        cornerRadius = r,
    )
    drawRoundRect(Grain, cornerRadius = r, alpha = GlassTokens.grainAlpha)

    val white = if (solid) 1f else if (invert) f else 0f
    if (white > 0.01f) drawRoundRect(Color.White.copy(alpha = 0.97f * white), cornerRadius = r)

    if (f > 0.01f && !invert && !solid) {
        drawRoundRect(
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.34f * f), Color.Transparent),
                center = Offset(size.width * 0.22f, size.height * 0.12f),
                radius = size.minDimension * 0.9f,
            ),
            cornerRadius = r,
        )
    }
    if (edge > 0.dp) drawRim(r, edge.toPx(), tint.rim)

    if (selected) {
        val w = 2.5.dp.toPx()
        drawRoundRect(
            Color.White,
            topLeft = Offset(w / 2f, w / 2f),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius((r.x - w / 2f).coerceAtLeast(0f), (r.y - w / 2f).coerceAtLeast(0f)),
            style = Stroke(width = w),
        )
    }
}

/** Draws the part of the blurred screen that lies under this tile, stretched to the tile. */
private fun DrawScope.drawBackdropCrop(backdrop: Backdrop, origin: Offset) {
    val image = backdrop.image
    if (backdrop.width <= 0 || backdrop.height <= 0) return
    val sx = image.width / backdrop.width.toFloat()
    val sy = image.height / backdrop.height.toFloat()
    val left = (origin.x * sx).roundToInt().coerceIn(0, image.width - 1)
    val top = (origin.y * sy).roundToInt().coerceIn(0, image.height - 1)
    val w = (size.width * sx).roundToInt().coerceIn(1, image.width - left)
    val h = (size.height * sy).roundToInt().coerceIn(1, image.height - top)
    drawImage(
        image = image,
        srcOffset = IntOffset(left, top),
        srcSize = IntSize(w, h),
        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Low,
    )
}

private fun DrawScope.drawRim(
    radius: CornerRadius,
    widthPx: Float,
    strength: Float,
) {
    val inset = widthPx / 2f
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = (GlassTokens.rimTop * strength).coerceAtMost(1f)),
                Color.White.copy(alpha = GlassTokens.rimBottom * strength),
            ),
        ),
        topLeft = Offset(inset, inset),
        size = Size(size.width - widthPx, size.height - widthPx),
        cornerRadius = CornerRadius(
            (radius.x - inset).coerceAtLeast(0f),
            (radius.y - inset).coerceAtLeast(0f),
        ),
        style = Stroke(width = widthPx),
    )
}

/** White ink over the glass needs this much contrast; below it, dark ink reads better. */
private const val INK_CONTRAST_FLOOR = 3f

/**
 * Icon and text colour for a control whose backdrop is [brightness] (0 black .. 1 white) in
 * [style]. Glass over a white app is light, so white ink would vanish into it; there the ink turns
 * dark, just as it does on a focused white control. Over normal TV content it stays white.
 *
 * The layers are the ones [frostedTile] draws: the picture, the dark base over it, then the light
 * veil. Coloured styles bring their own base and veil, so each style is judged on its own.
 */
fun glassInk(style: FrostStyle, brightness: Float, overBlur: Boolean = true): Color {
    val t = style.tileTint(overBlur)
    val veil = (t.veilTop + t.veilBottom) / 2f
    val under = linear(brightness.coerceIn(0f, 1f)) * (1f - t.dark) + linear(t.base) * t.dark
    val surface = under * (1f - veil) + linear(t.veil) * veil
    val onWhite = contrast(surface, 1f)
    val onDark = contrast(surface, linear(GlassTokens.inkDark))
    return if (onWhite >= INK_CONTRAST_FLOOR || onWhite >= onDark) Color.White else GlassTokens.inkDark
}

/** Relative luminance of a grey in 0..1, as the contrast ratio formula wants it. */
private fun linear(grey: Float): Float =
    if (grey <= 0.03928f) grey / 12.92f else ((grey + 0.055f) / 1.055f).pow(2.4f)

private fun linear(c: Color): Float =
    0.2126f * linear(c.red) + 0.7152f * linear(c.green) + 0.0722f * linear(c.blue)

private fun contrast(a: Float, b: Float): Float =
    (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)

/** The frost style every tile below draws with. Provided by the panel and the editor. */
val LocalFrostStyle = compositionLocalOf { FrostStyle.SMOKE }

/** Setting: thickness of the glass edge line. */
val LocalEdgeWidth = compositionLocalOf { 0.8.dp }

/** Setting: top-bar buttons have no glass at rest. */
val LocalTopBorderless = compositionLocalOf { false }

/** Setting: focused controls turn white with dark content. */
val LocalInvertFocus = compositionLocalOf { true }

/** Setting: short system sounds on focus and on OK. */
val LocalSoundCues = compositionLocalOf { true }

/** True in the editor: controls draw flat fills and skip animation, so the page stays fast. */
val LocalLiteGlass = compositionLocalOf { false }

/** True when a blurred picture is behind the panel, so glass can be lighter. */
val LocalOverBlur = compositionLocalOf { false }

/** Colour of icons and text inside a control: white, or dark while the control is white. */
val LocalTileInk = compositionLocalOf { Color.White }

/** Real shader tuning; null turns the library path off and keeps the fallback. */
val LocalLiquidGlass = compositionLocalOf<LiquidGlassParams?> { null }
