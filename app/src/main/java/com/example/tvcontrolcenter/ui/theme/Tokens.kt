package com.example.tvcontrolcenter.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.example.tvcontrolcenter.R

/**
 * Design tokens, in dp at a 960 x 540 dp reference screen. [ProvideUiScale] stretches
 * everything to the real screen, so these stay the same on 1080p and 4K.
 */
object PanelDimens {
    /** Side of a 1x1 control. Bigger controls are whole multiples plus the gaps between. */
    val cell = 52.dp
    val gap = 8.dp
    val sheetPadding = 12.dp
    val sheetRadius = 34.dp
    val largeRadius = 34.dp

    val iconSmall = 22.dp
    val iconLarge = 34.dp

    /** Room above the panel for the clock, and the safe margin from the screen edges. */
    val topInset = 56.dp
    val edgeInset = 32.dp

    /** Width of the 4-column grid, without the sheet padding. */
    val gridWidth = cell * 4 + gap * 3
}

object GlassTokens {
    const val focusScale = 1.07f
    /** Short system sound on focus move and select. Set false to silence. */
    const val soundCues = true

    val textPrimary = Color.White
    val textSecondary = Color(0xFFDDE3EC)

    // Frosted glass layers. All plain fills and gradients, no shader and no blur pass.
    const val sheetBaseAlpha = 0.40f
    const val sheetVeilTop = 0.20f
    const val sheetVeilBottom = 0.06f
    const val tileBaseAlpha = 0.22f
    const val tileVeilTop = 0.22f
    const val tileVeilBottom = 0.10f
    const val tileActiveBoost = 0.14f
    const val tileFocusBoost = 0.26f
    const val grainAlpha = 0.055f
    const val rimTop = 0.62f
    const val rimBottom = 0.12f
}

object Motion {
    /** Open and close of the whole sheet. Settles in about 250 ms. */
    fun <T> panelSpring() = spring<T>(dampingRatio = 0.86f, stiffness = 720f)
    /** Per-row pop-in. */
    fun <T> tileSpring() = spring<T>(dampingRatio = 0.82f, stiffness = 900f)
    /** Focus lift, scale and brightness. */
    fun <T> focusSpring() = spring<T>(dampingRatio = 0.8f, stiffness = 1000f)
    const val staggerMillis = 22L
    const val openFromScale = 0.82f
}

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

object PanelType {
    val label = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 15.sp)
    val labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 12.sp)
    val detail = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 14.sp)
    val clock = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val hint = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val title = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
}

@Composable
fun ControlCenterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
