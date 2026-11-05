package com.example.tvcontrolcenter.ui.background

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.example.tvcontrolcenter.domain.model.BlurLevels
import com.example.tvcontrolcenter.domain.model.Wallpaper
import com.example.tvcontrolcenter.system.backdrop.boxBlur
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/** Painted once into a bitmap this wide; bigger screens scale it up. Cheap after the first frame. */
private const val MAX_WIDTH = 1280f

/** The preview is small, and the blur runs on a small copy just like the real screenshot blur. */
private const val PREVIEW_WIDTH = 480f

/**
 * Background of the app's own pages, in the style picked in the editor.
 *
 * Every style is painted with plain gradients and shapes into one bitmap, so a frame costs a
 * single image draw and nothing moves. Nothing here needs a picture file or the network.
 */
@Composable
fun AppWallpaper(style: Wallpaper, modifier: Modifier = Modifier) {
    Box(
        modifier.drawWithCache {
            val (w, h) = targetSize(size.width, size.height, MAX_WIDTH)
            val image = paintWallpaper(style, w, h).asImageBitmap()
            onDrawBehind {
                drawImage(
                    image,
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Low,
                )
            }
        },
    )
}

/**
 * The wallpaper for the live preview: the screen as it is, and the blurred copy the glass
 * samples. The panel normally keeps the screen sharp around the controls and blurs only inside
 * the glass, so the preview needs both to look like the real thing.
 */
internal class PreviewWallpaper(val sharp: ImageBitmap, val blurred: ImageBitmap?)

/** Built from one painting; [level] 0 gives no blurred copy, as on a device that cannot blur. */
internal fun previewWallpaper(style: Wallpaper, width: Int, height: Int, level: Int): PreviewWallpaper {
    val (w, h) = targetSize(width.toFloat(), height.toFloat(), PREVIEW_WIDTH)
    val painted = paintWallpaper(style, w, h)
    // Not recycled: [sharp] wraps these pixels.
    val sharp = painted.asImageBitmap()
    if (level <= 0) return PreviewWallpaper(sharp, null)
    val pixels = IntArray(w * h)
    painted.getPixels(pixels, 0, w, 0, 0, w, h)
    boxBlur(pixels, w, h, BlurLevels.boxRadius(level))
    return PreviewWallpaper(sharp, Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap())
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
        Wallpaper.BOXES -> paintBoxes(canvas, w, h)
        Wallpaper.AURORA -> paintAurora(canvas, w, h)
        Wallpaper.SUNSET -> paintSunset(canvas, w, h)
        Wallpaper.FOREST -> paintForest(canvas, w, h)
        Wallpaper.WAVES -> paintWaves(canvas, w, h)
        Wallpaper.NEBULA -> paintNebula(canvas, w, h)
        Wallpaper.GRID -> paintGrid(canvas, w, h)
        Wallpaper.PLAIN -> paintPlain(canvas, w, h)
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

// -- Boxes: the original look. Flat saturated squares, in a fixed order so it never changes. --

private val BoxPalette = intArrayOf(
    0xFF33B5E5.toInt(), 0xFFAA66CC.toInt(), 0xFF99CC00.toInt(), 0xFFFFBB33.toInt(),
    0xFFFF4444.toInt(), 0xFF0099CC.toInt(), 0xFF9933CC.toInt(), 0xFF669900.toInt(),
    0xFFFF8800.toInt(), 0xFFE91E63.toInt(), 0xFF009688.toInt(), 0xFF3F51B5.toInt(),
    0xFFFFEB3B.toInt(), 0xFF00BCD4.toInt(),
)

private class Block(val x: Float, val y: Float, val side: Float, val color: Int, val corner: Float)

private fun paintBoxes(canvas: Canvas, w: Float, h: Float) {
    canvas.drawColor(0xFF10141A.toInt())
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    for (b in layoutBlocks(w, h)) {
        fill.color = b.color
        canvas.drawRoundRect(RectF(b.x, b.y, b.x + b.side, b.y + b.side), b.corner, b.corner, fill)
    }
    canvas.drawColor(0x4D000000)
}

/** Fills the screen with squares of 1, 2 or 3 cells, in a fixed random order. */
private fun layoutBlocks(width: Float, height: Float): List<Block> {
    val cell = height / 8f
    val cols = ceil(width / cell).toInt() + 1
    val rows = 9
    val gap = cell * 0.05f
    val taken = Array(rows) { BooleanArray(cols) }
    val rnd = Random(4)
    val corner = cell * 0.07f

    fun fits(r: Int, c: Int, span: Int): Boolean {
        if (r + span > rows || c + span > cols) return false
        for (rr in r until r + span) for (cc in c until c + span) if (taken[rr][cc]) return false
        return true
    }

    val blocks = ArrayList<Block>()
    for (r in 0 until rows) for (c in 0 until cols) {
        if (taken[r][c]) continue
        var span = when (rnd.nextInt(10)) { 0 -> 3; 1, 2, 3 -> 2; else -> 1 }
        while (span > 1 && !fits(r, c, span)) span--
        for (rr in r until r + span) for (cc in c until c + span) taken[rr][cc] = true
        blocks += Block(
            x = c * cell + gap,
            y = r * cell + gap,
            side = span * cell - 2 * gap,
            color = BoxPalette[rnd.nextInt(BoxPalette.size)],
            corner = corner,
        )
    }
    return blocks
}

// -- Aurora: deep night sky with teal and violet curtains. --

private fun paintAurora(canvas: Canvas, w: Float, h: Float) {
    canvas.verticalGradient(intArrayOf(0xFF05080F.toInt(), 0xFF0B1424.toInt(), 0xFF101B33.toInt()), w, h)
    canvas.blob(w * 0.12f, h * 0.18f, h * 0.85f, 0xFF14B8A6.toInt(), 150, w, h)
    canvas.blob(w * 0.72f, h * 0.10f, h * 0.95f, 0xFF6366F1.toInt(), 155, w, h)
    canvas.blob(w * 0.55f, h * 1.05f, h * 0.85f, 0xFFA855F7.toInt(), 130, w, h)
    canvas.blob(w * 0.95f, h * 0.70f, h * 0.60f, 0xFF0EA5E9.toInt(), 90, w, h)
    canvas.vignette(0x3D000000)
}

// -- Sunset: warm horizon under a purple evening. --

private fun paintSunset(canvas: Canvas, w: Float, h: Float) {
    canvas.verticalGradient(
        intArrayOf(0xFF1A1026.toInt(), 0xFF4C1C43.toInt(), 0xFF9E3B4A.toInt(), 0xFFE4703C.toInt()),
        w,
        h,
    )
    canvas.blob(w * 0.28f, h * 1.02f, h * 0.75f, 0xFFFFD166.toInt(), 200, w, h)
    canvas.blob(w * 0.85f, h * 0.30f, h * 0.65f, 0xFFF472B6.toInt(), 110, w, h)
    canvas.vignette(0x33000000)
}

// -- Forest: dark green canopy with cool light between the trees. --

private fun paintForest(canvas: Canvas, w: Float, h: Float) {
    canvas.verticalGradient(intArrayOf(0xFF05130D.toInt(), 0xFF0B2419.toInt(), 0xFF123525.toInt()), w, h)
    canvas.blob(w * 0.20f, h * 0.25f, h * 0.80f, 0xFF10B981.toInt(), 150, w, h)
    canvas.blob(w * 0.80f, h * 0.20f, h * 0.70f, 0xFF34D399.toInt(), 110, w, h)
    canvas.blob(w * 0.60f, h * 1.05f, h * 0.75f, 0xFF0E7490.toInt(), 130, w, h)
    canvas.vignette(0x3D000000)
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

// -- Grid: quiet technical backdrop with a few accent squares. --

private fun paintGrid(canvas: Canvas, w: Float, h: Float) {
    canvas.verticalGradient(intArrayOf(0xFF0A0D13.toInt(), 0xFF121821.toInt()), w, h)
    val cell = h / 8f
    val line = Paint().apply {
        color = (0x16 shl 24) or 0xFFFFFF
        strokeWidth = (h * 0.0022f).coerceAtLeast(1f)
    }
    var x = 0f
    while (x <= w) {
        canvas.drawLine(x, 0f, x, h, line)
        x += cell
    }
    var y = 0f
    while (y <= h) {
        canvas.drawLine(0f, y, w, y, line)
        y += cell
    }
    val accents = intArrayOf(0xFF3B82F6.toInt(), 0xFFEC4899.toInt(), 0xFF22D3EE.toInt())
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    accents.forEachIndexed { i, color ->
        fill.color = (0x2E shl 24) or (color and 0x00FFFFFF)
        val side = cell * (1f + i)
        val left = w * 0.08f + i * w * 0.26f
        val top = h * 0.16f + i * h * 0.24f
        canvas.drawRoundRect(RectF(left, top, left + side, top + side), cell * 0.12f, cell * 0.12f, fill)
    }
    canvas.vignette(0x26000000)
}

// -- Plain: one dark tone, the quietest choice. --

private fun paintPlain(canvas: Canvas, w: Float, h: Float) {
    canvas.verticalGradient(intArrayOf(0xFF0D1117.toInt(), 0xFF151B23.toInt()), w, h)
}
