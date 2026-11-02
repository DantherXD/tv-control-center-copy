package com.example.tvcontrolcenter.ui.background

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlin.math.ceil
import kotlin.random.Random

/** Flat, saturated colours in the spirit of the KitKat egg's block wall. */
private val Palette = listOf(
    0xFF33B5E5, 0xFFAA66CC, 0xFF99CC00, 0xFFFFBB33, 0xFFFF4444, 0xFF0099CC,
    0xFF9933CC, 0xFF669900, 0xFFFF8800, 0xFFE91E63, 0xFF009688, 0xFF3F51B5,
    0xFFFFEB3B, 0xFF00BCD4,
).map { Color(it) }

private class Block(val x: Float, val y: Float, val side: Float, val color: Color)

/**
 * Static wallpaper of colourful boxes behind the demo and the editor.
 *
 * Nothing moves, so it costs nothing after the first frame: the blocks are worked out once per
 * screen size and drawn as plain rounded squares into their own layer.
 *
 * To use a real photo instead, drop a picture named `wallpaper_boxes.jpg` (or .png) into
 * `app/src/main/res/drawable/`. It is picked up automatically, cropped to fill the screen.
 */
@Suppress("DiscouragedApi")
@Composable
fun BoxesBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val photo = remember(context) { context.resources.getIdentifier("wallpaper_boxes", "drawable", context.packageName) }
    if (photo != 0) {
        Image(painterResource(photo), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
        return
    }
    Box(
        modifier.drawWithCache {
            // Painted once into a small bitmap; every frame after that is one image draw.
            val scale = minOf(1f, MAX_WIDTH / size.width.coerceAtLeast(1f))
            val bw = (size.width * scale).toInt().coerceAtLeast(1)
            val bh = (size.height * scale).toInt().coerceAtLeast(1)
            val image = paint(bw, bh).asImageBitmap()
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

private const val MAX_WIDTH = 1280f

private fun paint(width: Int, height: Int): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(0xFF10141A.toInt())
    val corner = height / 8f * 0.07f
    for (b in layoutBlocks(width.toFloat(), height.toFloat())) {
        fill.color = b.color.toArgb()
        canvas.drawRoundRect(RectF(b.x, b.y, b.x + b.side, b.y + b.side), corner, corner, fill)
    }
    // Calms the colours so white text stays readable on top.
    canvas.drawColor(0x4D000000)
    return bmp
}

/** Fills the screen with squares of 1, 2 or 3 cells, in a fixed random order so it never changes. */
private fun layoutBlocks(width: Float, height: Float): List<Block> {
    val cell = height / 8f
    val cols = ceil(width / cell).toInt() + 1
    val rows = 9
    val gap = cell * 0.05f
    val taken = Array(rows) { BooleanArray(cols) }
    val rnd = Random(4)

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
            color = Palette[rnd.nextInt(Palette.size)],
        )
    }
    return blocks
}
