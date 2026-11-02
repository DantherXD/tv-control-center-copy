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

/**
 * The big frosted card behind all controls. Dark base for contrast on bright apps, a white
 * veil that fades downwards, grain, and a rim that is bright on top and faint below.
 * Over a window that the system blurs, this reads as real frosted glass; without blur it
 * still reads as acrylic.
 */
fun Modifier.frostedSheet(cornerRadius: Dp): Modifier = drawBehind {
    val r = CornerRadius(cornerRadius.toPx())
    drawRoundRect(Color.Black.copy(alpha = GlassTokens.sheetBaseAlpha), cornerRadius = r)
    drawRoundRect(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = GlassTokens.sheetVeilTop),
                Color.White.copy(alpha = GlassTokens.sheetVeilBottom),
            ),
        ),
        cornerRadius = r,
    )
    drawRoundRect(Grain, cornerRadius = r, alpha = GlassTokens.grainAlpha)
    drawRim(r, 1.2.dp.toPx(), 1f)
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
): Modifier = drawWithCache {
    val r = CornerRadius(cornerRadius.toPx())
    val shape = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, r))
    }
    onDrawBehind {
        val f = focus()

        // Soft contact shadow under a lifted tile: one offset fill, no blur.
        if (f > 0.01f) {
            drawRoundRect(
                Color.Black.copy(alpha = 0.28f * f),
                topLeft = Offset(0f, 5.dp.toPx() * f),
                size = size,
                cornerRadius = r,
            )
        }

        val boost = (if (active) GlassTokens.tileActiveBoost else 0f) + GlassTokens.tileFocusBoost * f
        if (backdrop != null) {
            clipPath(shape) { drawBackdropCrop(backdrop, origin) }
            // A thin veil keeps white icons readable on bright pictures.
            drawRoundRect(
                Color.White.copy(alpha = (GlassTokens.glassVeil + boost * 0.7f).coerceAtMost(0.6f)),
                cornerRadius = r,
            )
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    endY = size.height * 0.6f,
                ),
                cornerRadius = r,
            )
        } else {
            drawRoundRect(Color.Black.copy(alpha = GlassTokens.tileBaseAlpha * (1f - 0.5f * f)), cornerRadius = r)
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = (GlassTokens.tileVeilTop + boost).coerceAtMost(0.7f)),
                        Color.White.copy(alpha = (GlassTokens.tileVeilBottom + boost * 0.6f).coerceAtMost(0.7f)),
                    ),
                ),
                cornerRadius = r,
            )
        }
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
        drawRim(r, 1.dp.toPx() + 0.6.dp.toPx() * f, 0.8f + 0.2f * f)

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
