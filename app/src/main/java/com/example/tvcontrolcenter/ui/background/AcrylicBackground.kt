package com.example.tvcontrolcenter.ui.background

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** The scene is drawn at 1/6 size and stretched, so a 4K screen costs the same as 640 x 360. */
private const val DOWNSCALE = 6

/** One full cycle. Every blob moves by whole multiples of this, so the loop has no seam. */
private const val PERIOD_MS = 90_000L

/** 25 updates a second is plenty for something that drifts this slowly. */
private const val STEP_MS = 40L

private const val TWO_PI = (2.0 * Math.PI).toFloat()

private class Blob(
    val color: Color,
    val cx: Float,
    val cy: Float,
    val ax: Float,
    val ay: Float,
    val kx: Int,
    val ky: Int,
    val phase: Float,
    val radius: Float,
)

private val Blobs = listOf(
    Blob(Color(0xFF3B6CFF), 0.22f, 0.30f, 0.16f, 0.12f, 1, 2, 0.0f, 0.55f),
    Blob(Color(0xFF9B4DFF), 0.78f, 0.25f, 0.14f, 0.16f, 2, 1, 1.7f, 0.50f),
    Blob(Color(0xFF16B8C8), 0.70f, 0.80f, 0.18f, 0.10f, 1, 1, 3.1f, 0.55f),
    Blob(Color(0xFFFF5C8A), 0.18f, 0.85f, 0.12f, 0.14f, 3, 2, 4.4f, 0.42f),
    Blob(Color(0xFFFFA24D), 0.50f, 0.50f, 0.20f, 0.18f, 2, 3, 5.6f, 0.30f),
)

/**
 * Slowly drifting acrylic colour fields behind the demo and the editor.
 *
 * Cheap on purpose: five soft gradients drawn into an offscreen layer one sixth the size of
 * the screen, then scaled up by the GPU (the scaling is the blur). The position is the only
 * thing that changes, 25 times a second, and it is read in the draw phase, so nothing
 * recomposes. Set [animate] to false for a still frame.
 */
@Composable
fun AcrylicBackground(modifier: Modifier = Modifier, animate: Boolean = true) {
    val phase = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            phase.floatValue = (SystemClock.uptimeMillis() % PERIOD_MS).toFloat() / PERIOD_MS * TWO_PI
            delay(STEP_MS)
        }
    }

    Layout(
        content = { Canvas(Modifier.fillMaxSize()) { drawAcrylic(phase.floatValue) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val lowW = (w / DOWNSCALE).coerceAtLeast(1)
        val lowH = (h / DOWNSCALE).coerceAtLeast(1)
        val placeable = measurables.first().measure(Constraints.fixed(lowW, lowH))
        layout(w, h) {
            placeable.placeWithLayer(0, 0) {
                scaleX = w.toFloat() / lowW
                scaleY = h.toFloat() / lowH
                transformOrigin = TransformOrigin(0f, 0f)
                compositingStrategy = CompositingStrategy.Offscreen
            }
        }
    }
}

private fun DrawScope.drawAcrylic(phase: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF170B30))))
    for (b in Blobs) {
        val x = (b.cx + b.ax * sin(phase * b.kx + b.phase)) * size.width
        val y = (b.cy + b.ay * cos(phase * b.ky + b.phase)) * size.height
        val r = b.radius * size.width
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(b.color.copy(alpha = 0.62f), b.color.copy(alpha = 0f)),
                center = Offset(x, y),
                radius = r,
            ),
            radius = r,
            center = Offset(x, y),
        )
    }
    // Soft vignette keeps the corners calm so white text stays readable.
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
            center = center,
            radius = size.maxDimension * 0.75f,
        ),
    )
}
