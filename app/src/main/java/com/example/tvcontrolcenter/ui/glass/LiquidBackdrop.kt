package com.example.tvcontrolcenter.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop as KyantBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop

/**
 * A backdrop for one control, cropped from the sharp screenshot.
 *
 * The Backdrop library's canvas backdrop is coordinates-independent, so it draws in the
 * control's own layer. Translating the whole screen by the control's origin makes the layer
 * show exactly the piece under that control, while the library's blur padding can reach past
 * its edges. [Backdrop.sharp] is the 960 px screenshot; [Backdrop.width] and [Backdrop.height]
 * are the window pixels it covers.
 */
@Composable
internal fun rememberLiquidBackdrop(backdrop: Backdrop?, origin: Offset): KyantBackdrop? {
    val sharp = backdrop?.sharp ?: return null
    val width = backdrop.width
    val height = backdrop.height
    if (width <= 0 || height <= 0) return null
    val onDraw: DrawScope.() -> Unit = remember(sharp, width, height, origin) {
        {
            withTransform({ translate(-origin.x, -origin.y) }) {
                drawImage(
                    image = sharp,
                    dstSize = IntSize(width, height),
                    filterQuality = FilterQuality.Low,
                )
            }
        }
    }
    return rememberCanvasBackdrop(onDraw)
}
