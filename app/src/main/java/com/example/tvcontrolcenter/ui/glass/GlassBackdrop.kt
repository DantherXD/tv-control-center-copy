package com.example.tvcontrolcenter.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picture that sits behind the glass. Glass tiles sample [blurred] directly,
 * so the refraction needs no screen capture and costs one shader pass per tile.
 *
 * The wallpaper is drawn slightly larger than the screen ([OVERSCAN]) so it can
 * shift a few dp with focus (parallax) without exposing an edge.
 */
@Stable
class GlassBackdrop(
    val sharp: ImageBitmap,
    val blurred: ImageBitmap,
    private val luminance: FloatArray,
) {
    var screenSize by mutableStateOf(IntSize.Zero)

    /** Wallpaper shift in screen pixels. Read in draw phase only, so it never recomposes. */
    val parallax = Animatable(Offset.Zero, Offset.VectorConverter)

    /** Where the wallpaper is drawn, in screen pixels. */
    fun wallpaperRect(): Rect {
        val w = screenSize.width * OVERSCAN
        val h = screenSize.height * OVERSCAN
        val p = parallax.value
        return Rect(
            left = (screenSize.width - w) / 2f + p.x,
            top = (screenSize.height - h) / 2f + p.y,
            right = (screenSize.width + w) / 2f + p.x,
            bottom = (screenSize.height + h) / 2f + p.y,
        )
    }

    /** Blurred-bitmap pixels per screen pixel. */
    fun bitmapPerScreenPx(): Float {
        val rect = wallpaperRect()
        return if (rect.width <= 0f) 1f else blurred.width / rect.width
    }

    /** Average brightness (0..1) of the content under [screenPoint]. Drives the adaptive tint. */
    fun luminanceAt(screenPoint: Offset): Float {
        val rect = wallpaperRect()
        if (rect.width <= 0f) return 0f
        val k = blurred.width / rect.width
        val x = ((screenPoint.x - rect.left) * k).toInt().coerceIn(0, blurred.width - 1)
        val y = ((screenPoint.y - rect.top) * k).toInt().coerceIn(0, blurred.height - 1)
        return luminance[y * blurred.width + x]
    }

    companion object {
        const val OVERSCAN = 1.08f
        const val BLUR_WIDTH = 480
        const val BLUR_HEIGHT = 270

        fun create(context: Context, @DrawableRes res: Int): GlassBackdrop {
            val sharpBitmap = BitmapFactory.decodeResource(context.resources, res)
            val small = Bitmap.createScaledBitmap(sharpBitmap, BLUR_WIDTH, BLUR_HEIGHT, true)
            val pixels = IntArray(BLUR_WIDTH * BLUR_HEIGHT)
            small.getPixels(pixels, 0, BLUR_WIDTH, 0, 0, BLUR_WIDTH, BLUR_HEIGHT)
            repeat(2) { boxBlur(pixels, BLUR_WIDTH, BLUR_HEIGHT, radius = 2) }
            val blurredBitmap = Bitmap.createBitmap(pixels, BLUR_WIDTH, BLUR_HEIGHT, Bitmap.Config.ARGB_8888)
            val lum = FloatArray(pixels.size) { i ->
                val c = pixels[i]
                (0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)) / 255f
            }
            return GlassBackdrop(sharpBitmap.asImageBitmap(), blurredBitmap.asImageBitmap(), lum)
        }

        /** Separable box blur on ARGB pixels. Runs once at load on a 480x270 copy. */
        internal fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int) {
            val tmp = IntArray(px.size)
            val span = radius * 2 + 1
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var r = 0; var g = 0; var b = 0
                    for (k in -radius..radius) {
                        val c = px[y * w + (x + k).coerceIn(0, w - 1)]
                        r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                    }
                    tmp[y * w + x] = (0xFF shl 24) or ((r / span) shl 16) or ((g / span) shl 8) or (b / span)
                }
            }
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var r = 0; var g = 0; var b = 0
                    for (k in -radius..radius) {
                        val c = tmp[(y + k).coerceIn(0, h - 1) * w + x]
                        r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                    }
                    px[y * w + x] = (0xFF shl 24) or ((r / span) shl 16) or ((g / span) shl 8) or (b / span)
                }
            }
        }
    }
}

/** Null means there is no picture behind the panel (overlay mode); glass then uses its tinted fallback. */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

@Composable
fun rememberGlassBackdrop(@DrawableRes res: Int): GlassBackdrop? {
    val context = LocalContext.current
    val state = produceState<GlassBackdrop?>(initialValue = null, res) {
        value = withContext(Dispatchers.Default) { GlassBackdrop.create(context, res) }
    }
    return state.value
}

@Suppress("unused")
private fun Size.isEmpty() = width <= 0f || height <= 0f
