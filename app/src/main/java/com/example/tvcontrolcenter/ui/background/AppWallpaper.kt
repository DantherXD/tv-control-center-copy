package com.example.tvcontrolcenter.ui.background

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.tvcontrolcenter.domain.model.BlurLevels
import com.example.tvcontrolcenter.domain.model.Wallpaper
import com.example.tvcontrolcenter.system.backdrop.boxBlur
import kotlin.random.Random

/** The preview is small, and the blur runs on a small copy just like the real screenshot blur. */
private const val PREVIEW_WIDTH = 480f

/**
 * The wallpaper behind the panel in the editor preview. Each style is painted with plain gradients
 * and shapes into one bitmap, so a frame costs a single image draw. Acrylic is painted once too;
 * the preview moves that image instead of repainting it. Nothing here needs a picture file or the
 * network.
 */
internal class PreviewWallpaper(val sharp: ImageBitmap, val blurred: ImageBitmap?)

/** Built from one painting; [level] 0 gives no blurred copy, as on a device that cannot blur. */
internal fun previewWallpaper(style: Wallpaper, width: Int, height: Int, level: Int): PreviewWallpaper {
    val (w, h) = targetSize(width.toFloat(), height.toFloat(), PREVIEW_WIDTH)
    val painted = paintWallpaper(style, w, h)
    // Not recycled: [sharp] wraps these pixels.
    return PreviewWallpaper(painted.asImageBitmap(), blurredCopy(painted, w, h, level))
}

private fun blurredCopy(painted: Bitmap, w: Int, h: Int, level: Int): ImageBitmap? {
    if (level <= 0) return null
    val pixels = IntArray(w * h)
    painted.getPixels(pixels, 0, w, 0, 0, w, h)
    boxBlur(pixels, w, h, BlurLevels.boxRadius(level))
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun targetSize(width: Float, height: Float, max: Float): Pair<Int, Int> {
    val scale = minOf(1f, max / width.coerceAtLeast(1f))
    return Pair(
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
    )
}

internal fun paintWallpaper(style: Wallpaper, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val w = width.toFloat()
    val h = height.toFloat()
    when (style) {
        Wallpaper.WAVES -> paintWaves(canvas, w, h)
        Wallpaper.NEBULA -> paintNebula(canvas, w, h)
        Wallpaper.ACRYLIC -> paintAcrylic(canvas, w, h)
    }
    return bitmap
}

/** Fills the whole canvas with a vertical gradient. Colours are top to bottom, ARGB. */
private fun Canvas.verticalGradient(colors: IntArray, w: Float, h: Float) {
    val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, h, colors, null, Shader.TileMode.CLAMP)
    }
    drawRect(0f, 0f, w, h, paint)
}

/** A soft round light: bright [color] in the middle, gone by [radius]. */
private fun Canvas.blob(cx: Float, cy: Float, radius: Float, color: Int, alpha: Int, w: Float, h: Float) {
    val center = (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx, cy, radius.coerceAtLeast(1f), center, 0x00000000, Shader.TileMode.CLAMP)
    }
    drawRect(0f, 0f, w, h, paint)
}

/** Darkens the corners so white text stays readable on top. */
private fun Canvas.vignette(color: Int) {
    drawColor(color)
}

// -- Waves: rings rolling out of the bottom-right corner. --

private fun paintWaves(canvas: Canvas, w: Float, h: Float) {
    canvas.verticalGradient(intArrayOf(0xFF04101C.toInt(), 0xFF0A2136.toInt(), 0xFF0D2C47.toInt()), w, h)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    for (i in 0 until 8) {
        val alpha = (0x38 - i * 6).coerceAtLeast(4)
        paint.color = (alpha shl 24) or 0x7DD3FC
        paint.strokeWidth = h * (0.014f + i * 0.002f)
        canvas.drawCircle(w * 0.78f, h * 1.18f, h * (0.30f + i * 0.21f), paint)
    }
    canvas.blob(w * 0.15f, h * 0.15f, h * 0.70f, 0xFF22D3EE.toInt(), 90, w, h)
    canvas.vignette(0x33000000)
}

// -- Nebula: a dark sky with soft colour clouds and a few stars. --

private fun paintNebula(canvas: Canvas, w: Float, h: Float) {
    canvas.verticalGradient(intArrayOf(0xFF04030A.toInt(), 0xFF0A0718.toInt(), 0xFF120A26.toInt()), w, h)
    canvas.blob(w * 0.30f, h * 0.35f, h * 0.85f, 0xFF7C3AED.toInt(), 165, w, h)
    canvas.blob(w * 0.78f, h * 0.25f, h * 0.65f, 0xFFEC4899.toInt(), 120, w, h)
    canvas.blob(w * 0.60f, h * 0.85f, h * 0.80f, 0xFF2563EB.toInt(), 140, w, h)
    // Few and faint on purpose: the stars are atmosphere, not texture. They must never compete
    // with a heading, an icon or a value on the app's own pages.
    val stars = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3FFFFFF.toInt() }
    val rnd = Random(11)
    repeat(52) {
        val x = rnd.nextFloat() * w
        val y = rnd.nextFloat() * h
        val r = h * (0.0009f + rnd.nextFloat() * 0.0014f)
        stars.alpha = 26 + rnd.nextInt(64)
        canvas.drawCircle(x, y, r, stars)
    }
    canvas.vignette(0x2E000000)
}

// -- Acrylic gradient 2: poured colour ribbons under a broad, soft sheen. --

private fun paintAcrylic(canvas: Canvas, w: Float, h: Float) {
    canvas.verticalGradient(intArrayOf(0xFF070B16.toInt(), 0xFF0B1628.toInt(), 0xFF070A13.toInt()), w, h)

    // Broad overlapping fields, deliberately asymmetric so the drift reads as a slow pour.
    canvas.blob(w * -0.02f, h * 0.10f, h * 1.05f, 0xFF2F6BFF.toInt(), 190, w, h)
    canvas.blob(w * 0.92f, h * 0.16f, h * 0.92f, 0xFF0ED3C4.toInt(), 150, w, h)
    canvas.blob(w * 0.74f, h * 0.98f, h * 1.08f, 0xFF7C4DFF.toInt(), 180, w, h)
    canvas.blob(w * 0.32f, h * 0.92f, h * 0.82f, 0xFFF43F7E.toInt(), 78, w, h)
    canvas.blob(w * 0.56f, h * 0.48f, h * 0.62f, 0xFF20C7B0.toInt(), 60, w, h)

    // Two translucent ribbons, softened at the edges like poured resin.
    val ribbon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(h * 0.08f, BlurMaskFilter.Blur.NORMAL)
    }
    ribbon.color = 0x66A5F3FC
    ribbon.strokeWidth = h * 0.15f
    canvas.drawPath(
        Path().apply {
            moveTo(-w * 0.12f, h * 0.74f)
            cubicTo(w * 0.20f, h * 0.35f, w * 0.58f, h * 1.02f, w * 1.10f, h * 0.40f)
        },
        ribbon,
    )
    ribbon.color = 0x40FFFFFF
    ribbon.strokeWidth = h * 0.055f
    canvas.drawPath(
        Path().apply {
            moveTo(-w * 0.08f, h * 0.30f)
            cubicTo(w * 0.30f, h * 0.10f, w * 0.62f, h * 0.55f, w * 1.08f, h * 0.18f)
        },
        ribbon,
    )

    // A broad diagonal sheen; the preview's drift moves it with the painting.
    val sheen = Paint().apply {
        shader = LinearGradient(
            -w * 0.30f,
            0f,
            w * 1.20f,
            h,
            intArrayOf(0x00FFFFFF, 0x18FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, w, h, sheen)
    canvas.vignette(0x26000000)
}
