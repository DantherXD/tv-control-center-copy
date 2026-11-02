package com.example.tvcontrolcenter.ui.glass

import android.graphics.Bitmap
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
import androidx.compose.runtime.compositionLocalOf
import com.example.tvcontrolcenter.domain.model.FrostStyle
import com.example.tvcontrolcenter.ui.theme.GlassTokens
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

/** How dark and how white a glass layer is. Plain numbers, so a style costs nothing to draw. */
private class Tint(val dark: Float, val veilTop: Float, val veilBottom: Float)

private fun FrostStyle.sheetTint() = when (this) {
    FrostStyle.SMOKE -> Tint(GlassTokens.sheetBaseAlpha, GlassTokens.sheetVeilTop, GlassTokens.sheetVeilBottom)
    FrostStyle.MILKY -> Tint(0.14f, 0.34f, 0.18f)
    FrostStyle.CLEAR -> Tint(0.22f, 0.10f, 0.04f)
}

private fun FrostStyle.tileTint(overBlur: Boolean) = if (overBlur) {
    when (this) {
        FrostStyle.SMOKE -> Tint(0.20f, 0.10f, 0.06f)
        FrostStyle.MILKY -> Tint(0.04f, 0.34f, 0.26f)
        FrostStyle.CLEAR -> Tint(0.06f, 0.06f, 0.03f)
    }
} else {
    when (this) {
        FrostStyle.SMOKE -> Tint(GlassTokens.tileBaseAlpha, GlassTokens.tileVeilTop, GlassTokens.tileVeilBottom)
        FrostStyle.MILKY -> Tint(0.08f, 0.40f, 0.28f)
        FrostStyle.CLEAR -> Tint(0.12f, 0.10f, 0.05f)
    }
}

/**
 * The card behind the grid, used only when there is no blurred screen to show through the
 * tiles. Dark base, a white veil that fades downwards and grain. No border.
 * [inflate] grows it past the modifier's bounds on every side, so it can wrap content that
 * sits inside padding.
 */
fun Modifier.frostedSheet(cornerRadius: Dp, style: FrostStyle = FrostStyle.SMOKE, inflate: Dp = 0.dp): Modifier = drawBehind {
    val t = style.sheetTint()
    val pad = inflate.toPx()
    val topLeft = Offset(-pad, -pad)
    val box = Size(size.width + 2 * pad, size.height + 2 * pad)
    val r = CornerRadius(cornerRadius.toPx())
    drawRoundRect(Color.Black.copy(alpha = t.dark), topLeft, box, r)
    drawRoundRect(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = t.veilTop), Color.White.copy(alpha = t.veilBottom)),
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
 * With a [backdrop], the glass is a real blurred copy of the screen behind it: [origin] is
 * the tile's top-left corner in the backdrop's coordinates. Without one, a dark base and a
 * white veil stand in, which still reads as frosted but not as transparent.
 */
fun Modifier.frostedTile(
    cornerRadius: Dp,
    active: Boolean,
    focus: () -> Float,
    selected: Boolean = false,
    backdrop: Backdrop? = null,
    origin: Offset = Offset.Zero,
    style: FrostStyle = FrostStyle.SMOKE,
): Modifier = drawWithCache {
    val tint = style.tileTint(overBlur = backdrop != null)
    val r = CornerRadius(cornerRadius.toPx())
    val shape = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, r))
    }
    onDrawBehind {
        val f = focus()

        val boost = (if (active) GlassTokens.tileActiveBoost else 0f) + GlassTokens.tileFocusBoost * f
        if (backdrop != null) {
            clipPath(shape) { drawBackdropCrop(backdrop, origin) }
        }
        drawRoundRect(Color.Black.copy(alpha = tint.dark * (1f - 0.5f * f)), cornerRadius = r)
        drawRoundRect(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (tint.veilTop + boost).coerceAtMost(0.7f)),
                    Color.White.copy(alpha = (tint.veilBottom + boost * 0.6f).coerceAtMost(0.7f)),
                ),
            ),
            cornerRadius = r,
        )
        drawRoundRect(Grain, cornerRadius = r, alpha = GlassTokens.grainAlpha)

        // Top-left specular glint, only while focused.
        if (f > 0.01f) {
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
        // Hairline only: the glass edge is felt, not drawn as a frame.
        drawRim(r, 0.8.dp.toPx(), 1f)

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

/** The frost style every tile below draws with. Provided by the panel and the editor. */
val LocalFrostStyle = compositionLocalOf { FrostStyle.SMOKE }
