package com.example.tvcontrolcenter.ui.glass

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.ui.theme.GlassTokens
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
 */
fun Modifier.frostedTile(
    cornerRadius: Dp,
    active: Boolean,
    focus: () -> Float,
    selected: Boolean = false,
): Modifier = drawBehind {
    val f = focus()
    val r = CornerRadius(cornerRadius.toPx())

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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRim(
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
