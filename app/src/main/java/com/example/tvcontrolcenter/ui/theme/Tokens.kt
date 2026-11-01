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
 * Design tokens. Sizes were measured from the reference screenshot (1144x644)
 * and scaled by 1.25 for 10-foot reading distance and 48dp minimum targets.
 * Re-tune these after comparing with a screen recording.
 */
object PanelDimens {
    val gap = 12.dp
    val tallTile = 132.dp          // POWER: two capsules plus one gap
    val capsuleWidth = 132.dp
    val extraWidth = 176.dp        // quick-action capsules: wide enough for "Notifications"
    val capsuleHeight = 60.dp      // (132 - 12) / 2
    val circle = 60.dp
    val panelWidth = capsuleWidth * 2 + gap   // 276dp, equals four circles plus three gaps
    val wideHeight = 60.dp
    val capsuleRadius = 30.dp
    val powerRadius = 44.dp
    val contentPadding = 14.dp
    val iconSize = 26.dp
    val powerIconSize = 48.dp
    val statusBarHeight = 48.dp
    val screenInsetH = 48.dp       // TV overscan-safe margins
    val screenInsetV = 27.dp
    val parallaxMax = 24.dp
}

object GlassTokens {
    const val focusScale = 1.08f
    /** Short system sound on focus move and select. Set false to silence. */
    const val soundCues = true
    val rimBase = 0.28f
    val rimFocused = 0.65f
    val shadowElevation = 14.dp
    val shadowAmbient = Color.Black.copy(alpha = 0.14f)
    val shadowSpot = Color.Black.copy(alpha = 0.22f)

    // Adaptive tint: dark glass over bright content, light glass over dark content.
    val tintOnDark = Color(0xFFEFF6F1)
    val tintOnLight = Color(0xFF16201B)
    const val tintAmountRest = 0.20f
    const val tintAmountFocused = 0.09f
    const val luminanceSwitch = 0.55f

    // Focus brightening and refraction strength, read by the shader.
    const val lensStrengthPx = 30f
    const val chromaPx = 5f

    val textPrimary = Color.White
    val textSecondary = Color(0xFFE3EBE6)
    val activeIcon = Color(0xFF5FE3D3)
}

object Motion {
    /** Open/close of the whole panel. */
    fun <T> panelSpring() = spring<T>(dampingRatio = 0.78f, stiffness = 260f)
    /** Per-tile pop-in; staggered by index. */
    fun <T> tileSpring() = spring<T>(dampingRatio = 0.72f, stiffness = 340f)
    /** Focus lift, scale and brightness. */
    fun <T> focusSpring() = spring<T>(dampingRatio = 0.7f, stiffness = 520f)
    /** Background parallax follows focus, slowly. */
    fun <T> parallaxSpring() = spring<T>(dampingRatio = 1f, stiffness = 90f)
    const val staggerMillis = 28L
    const val openFromScale = 0.28f
    const val shimmerPeriodMillis = 5200
}

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

object PanelType {
    val label = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 17.sp)
    val detail = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 17.sp)
    val clock = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val hint = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp)
}

@Composable
fun ControlCenterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
