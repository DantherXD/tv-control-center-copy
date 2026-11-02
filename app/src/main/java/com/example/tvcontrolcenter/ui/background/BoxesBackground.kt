package com.example.tvcontrolcenter.ui.background

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
        modifier
            .graphicsLayer()
            .drawWithCache {
                val blocks = layoutBlocks(size.width, size.height)
                val corner = CornerRadius(size.height / 8f * 0.07f)
                onDrawBehind {
                    drawRect(Color(0xFF10141A))
                    for (b in blocks) {
                        drawRoundRect(b.color, Offset(b.x, b.y), Size(b.side, b.side), corner)
                    }
                    // Calms the colours so white text stays readable on top.
                    drawRect(Color.Black.copy(alpha = 0.30f))
                }
            },
    )
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
