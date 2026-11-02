package com.example.tvcontrolcenter.ui.glass

import androidx.compose.ui.graphics.ImageBitmap

/**
 * A blurred copy of what is on screen, used as the "glass" inside each control.
 *
 * [image] is small (about 480 px wide). [width] and [height] are the size of the area it
 * covers, in the pixels of the window that draws it, so a control at (x, y) in that window
 * finds its pixels at x * image.width / width.
 */
data class Backdrop(val image: ImageBitmap, val width: Int, val height: Int)
