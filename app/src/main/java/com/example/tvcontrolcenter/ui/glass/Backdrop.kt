package com.example.tvcontrolcenter.ui.glass

import androidx.compose.ui.graphics.ImageBitmap
import com.kyant.backdrop.Backdrop as KyantBackdrop

/**
 * A blurred copy of what is on screen, used as the "glass" inside each control.
 *
 * [image] is the blurred copy used for fallback drawing and for the brightness map. [sharp]
 * is the sharper copy the real liquid-glass shader samples. [width] and [height] are the size
 * of the area they cover, in the pixels of the window that draws it, so a control at (x, y)
 * in that window finds its pixels at x * image.width / width.
 */
data class Backdrop(
    val image: ImageBitmap,
    val width: Int,
    val height: Int,
    /** Coarse brightness of [image]; null when it was not built, and the ink stays white. */
    val luma: LumaMap? = null,
    /** Sharp screenshot for the real glass. Falls back to [image] when no extra copy exists. */
    val sharp: ImageBitmap? = null,
    /** The screenshot recorded as a layer the Backdrop library can refract. */
    val layer: KyantBackdrop? = null,
) {

    /**
     * Average brightness (0 black .. 1 white) of a rectangle given in this window's pixels.
     * Null when there is no map, so the caller keeps the plain white ink.
     */
    fun brightness(left: Float, top: Float, widthPx: Float, heightPx: Float): Float? {
        val map = luma ?: return null
        if (width <= 0 || height <= 0) return null
        return map.average(
            u0 = left / width,
            v0 = top / height,
            u1 = (left + widthPx) / width,
            v1 = (top + heightPx) / height,
        )
    }
}

/**
 * A backdrop's brightness in [cols] x [rows] cells, row by row. Built once from the blurred
 * pixels on the thread that blurs them, so asking about a control costs a few adds.
 */
class LumaMap(private val cols: Int, private val rows: Int, private val cells: FloatArray) {

    /** Average brightness of the area from (u0, v0) to (u1, v1), all in 0..1. */
    fun average(u0: Float, v0: Float, u1: Float, v1: Float): Float {
        val x0 = (u0.coerceIn(0f, 1f) * cols).toInt().coerceIn(0, cols - 1)
        val x1 = (u1.coerceIn(0f, 1f) * cols).toInt().coerceIn(0, cols - 1)
        val y0 = (v0.coerceIn(0f, 1f) * rows).toInt().coerceIn(0, rows - 1)
        val y1 = (v1.coerceIn(0f, 1f) * rows).toInt().coerceIn(0, rows - 1)
        var sum = 0f
        var n = 0
        for (y in minOf(y0, y1)..maxOf(y0, y1)) {
            for (x in minOf(x0, x1)..maxOf(x0, x1)) {
                sum += cells[y * cols + x]
                n++
            }
        }
        return if (n == 0) 0f else sum / n
    }
}

/**
 * Builds the [LumaMap] of an ARGB picture, averaging every pixel into its cell. Cheap enough
 * to run on the blur thread: the picture is only about 480 px wide.
 */
internal fun lumaMapOf(pixels: IntArray, width: Int, height: Int, cols: Int = 32, rows: Int = 18): LumaMap {
    val cells = FloatArray(cols * rows)
    val counts = IntArray(cols * rows)
    for (y in 0 until height) {
        val row = (y * rows / height).coerceIn(0, rows - 1)
        for (x in 0 until width) {
            val px = pixels[y * width + x]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val i = row * cols + (x * cols / width).coerceIn(0, cols - 1)
            cells[i] += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            counts[i]++
        }
    }
    for (i in cells.indices) if (counts[i] > 0) cells[i] /= counts[i]
    return LumaMap(cols, rows, cells)
}
