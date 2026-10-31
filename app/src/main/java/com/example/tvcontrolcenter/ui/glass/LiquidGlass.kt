package com.example.tvcontrolcenter.ui.glass

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import kotlin.math.PI

/** What a tile asks of its glass painter on every draw. */
internal class GlassFrame(
    val size: Size,
    val radiusPx: Float,
    val originInRoot: Offset,
    val focus: Float,
    val phase: Float,
    val tint: Color,
    val tintAmount: Float,
    val density: Float,
)

internal interface GlassPainter {
    fun draw(scope: DrawScope, frame: GlassFrame)
}

/** API 33+: refraction, chromatic fringing and specular via AGSL. One instance per tile. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AgslGlassPainter(private val backdrop: GlassBackdrop) : GlassPainter {
    private val shader = RuntimeShader(GLASS_AGSL).also {
        it.setInputShader(
            "wall",
            BitmapShader(backdrop.blurred.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
        )
    }
    private val brush = ShaderBrush(shader)

    override fun draw(scope: DrawScope, frame: GlassFrame) {
        val wall = backdrop.wallpaperRect()
        shader.setFloatUniform("size", frame.size.width, frame.size.height)
        shader.setFloatUniform("origin", frame.originInRoot.x - wall.left, frame.originInRoot.y - wall.top)
        shader.setFloatUniform("k", backdrop.bitmapPerScreenPx())
        shader.setFloatUniform("radius", frame.radiusPx)
        shader.setFloatUniform("focus", frame.focus)
        shader.setFloatUniform("phase", frame.phase)
        shader.setFloatUniform("lens", GlassTokens.lensStrengthPx * frame.density / 2f)
        shader.setFloatUniform("chroma", GlassTokens.chromaPx * frame.density / 2f)
        shader.setFloatUniform("tint", frame.tint.red, frame.tint.green, frame.tint.blue)
        shader.setFloatUniform("tintAmount", frame.tintAmount)
        scope.drawRect(brush = brush, size = frame.size)
    }
}

/**
 * API 31-32 (blurred wallpaper crop plus gradient highlight) and below 31 (tinted
 * translucent gradient). Neither needs RuntimeShader.
 */
internal class FallbackGlassPainter(private val backdrop: GlassBackdrop?) : GlassPainter {
    override fun draw(scope: DrawScope, frame: GlassFrame) {
        val shape = Path().apply {
            addRoundRect(RoundRect(0f, 0f, frame.size.width, frame.size.height, CornerRadius(frame.radiusPx)))
        }
        scope.clipPath(shape) {
            if (backdrop != null) {
                val wall = backdrop.wallpaperRect()
                val k = backdrop.bitmapPerScreenPx()
                drawImage(
                    image = backdrop.blurred,
                    srcOffset = IntOffset(
                        ((frame.originInRoot.x - wall.left) * k).toInt().coerceAtLeast(0),
                        ((frame.originInRoot.y - wall.top) * k).toInt().coerceAtLeast(0),
                    ),
                    srcSize = IntSize(
                        (frame.size.width * k).toInt().coerceAtLeast(1),
                        (frame.size.height * k).toInt().coerceAtLeast(1),
                    ),
                    dstSize = IntSize(frame.size.width.toInt(), frame.size.height.toInt()),
                )
                drawRect(frame.tint.copy(alpha = frame.tintAmount))
            } else {
                drawRect(frame.tint.copy(alpha = 0.30f + 0.5f * frame.tintAmount))
            }
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.14f + 0.10f * frame.focus),
                    0.5f to Color.White.copy(alpha = 0.02f),
                    1f to Color.White.copy(alpha = 0.06f),
                ),
            )
        }
        val rim = GlassTokens.rimBase + (GlassTokens.rimFocused - GlassTokens.rimBase) * frame.focus
        scope.drawRoundRect(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = rim),
                0.5f to Color.White.copy(alpha = rim * 0.35f),
                1f to Color.White.copy(alpha = rim * 0.7f),
            ),
            cornerRadius = CornerRadius(frame.radiusPx),
            style = Stroke(width = 1.5f * frame.density / 2f),
        )
    }
}

internal fun createGlassPainter(backdrop: GlassBackdrop?): GlassPainter = when {
    backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> AgslGlassPainter(backdrop)
    else -> FallbackGlassPainter(
        // API 31-32 gets the blurred crop. Below 31 the spec asks for a plain tinted gradient.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop else null,
    )
}

/**
 * Liquid Glass surface. [focus] and [phase] are lambdas so animated values are
 * read in the draw phase and never trigger recomposition.
 *
 * The tile reads its position in the root, so the root of the composition must
 * be the same size as the wallpaper's screen (true for the activity and the overlay).
 */
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp,
    focus: () -> Float = { 0f },
    phase: () -> Float = { 0f },
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val painter = remember(backdrop) { createGlassPainter(backdrop) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    return this
        .shadow(
            elevation = GlassTokens.shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = GlassTokens.shadowAmbient,
            spotColor = GlassTokens.shadowSpot,
        )
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            val f = focus().coerceIn(0f, 1f)
            val center = origin + Offset(size.width / 2f, size.height / 2f)
            val bright = backdrop?.luminanceAt(center) ?: 0f
            val lightBehind = bright > GlassTokens.luminanceSwitch
            painter.draw(
                this,
                GlassFrame(
                    size = size,
                    radiusPx = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f),
                    originInRoot = origin,
                    focus = f,
                    phase = phase(),
                    tint = if (lightBehind) GlassTokens.tintOnLight else GlassTokens.tintOnDark,
                    tintAmount = GlassTokens.tintAmountRest +
                        (GlassTokens.tintAmountFocused - GlassTokens.tintAmountRest) * f,
                    density = density * 2f,
                ),
            )
        }
}

internal const val TWO_PI = (2.0 * PI).toFloat()
