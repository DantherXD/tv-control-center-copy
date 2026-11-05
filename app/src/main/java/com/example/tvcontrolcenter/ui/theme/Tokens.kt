package com.example.tvcontrolcenter.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    val sheetPadding = 8.dp
    val sheetRadius = 30.dp
    val largeRadius = 24.dp

    val iconSmall = 22.dp
    val iconLarge = 34.dp

    /** Safe margin from the right edge. The gap above the panel is a setting (topOffset). */
    val edgeInset = 32.dp

    /** Clock on the left, round shortcut buttons on the right, above the grid. */
    val headerHeight = 44.dp
    /** Height of the now-playing card that replaces the grid on the music page. */
    val musicCardHeight = 150.dp

    /** The big now-playing card (album picture fills it), about square like the grid is wide. */
    val musicCardBigHeight = 388.dp

    /** Wi-Fi, Bluetooth and Input pages: the shortest they get. */
    val subPageHeight = 214.dp
    /** A window grows past [subPageHeight] to fit its rows, up to this; longer lists scroll. */
    val subPageMaxHeight = 320.dp

    fun musicHeightFor(big: Boolean): Dp = if (big) musicCardBigHeight else musicCardHeight
    val topButton = 40.dp

    /** Header row: tall enough for the clock and for the top-bar buttons of [topSize] dp. */
    fun headerFor(topSize: Int): Dp = maxOf(headerHeight, topSize.dp)
    val topButtonGap = 12.dp

    /** Width of the 4-column grid, without the sheet padding. */
    val gridWidth = cell * 4 + gap * 3
}

object GlassTokens {
    const val focusScale = 1.06f
    /** Short system sound on focus move and select. Set false to silence. */
    const val soundCues = true

    val textPrimary = Color.White
    val textSecondary = Color(0xFFDDE3EC)
    /** Icon and text colour on a white (focused) control. */
    val inkDark = Color(0xFF14181D)

    // Frosted glass layers. All plain fills and gradients, no shader and no blur pass.
    const val sheetBaseAlpha = 0.40f
    const val sheetVeilTop = 0.20f
    const val sheetVeilBottom = 0.06f
    const val tileBaseAlpha = 0.22f
    /** White over the real blurred backdrop. Low, so the picture shows through. */
    const val glassVeil = 0.13f
    const val tileVeilTop = 0.22f
    const val tileVeilBottom = 0.10f
    const val tileActiveBoost = 0.14f
    const val tileFocusBoost = 0.26f
    const val grainAlpha = 0.055f
    const val rimTop = 0.20f
    const val rimBottom = 0.03f
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
    val clock = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 19.sp)
    val hint = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val title = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
}

// -- App-page design system --
// Everything below is for the home page and the editor only. The Control Center keeps using
// PanelDimens, GlassTokens, Motion and PanelType above, so restyling the app can never move a
// tile, change the glass or alter the panel's typography.

/** Colour of the app's own surfaces: deep midnight violet, one accent, calm status colours. */
object AppPalette {
    val accent = Color(0xFF9F8CFF)
    val accentBright = Color(0xFFC9BEFF)
    val accentLine = Color(0x998B6DFF)
    val accentSoft = Color(0x2E9F8CFF)
    val accentFocus = Color(0x3DA78BFA)

    val screenTint = Color(0xFF07050E)
    val scrim = Color(0xCC05040B)

    val cardTop = Color(0xF0161126)
    val cardBottom = Color(0xF20B0815)
    val cardFocusedTop = Color(0xFF2B2245)
    val cardFocusedBottom = Color(0xFF1B1531)
    val controlTop = Color(0xEB211A35)
    val controlBottom = Color(0xEB151024)
    val cardPressedTop = Color(0xF2241C3B)
    val cardPressedBottom = Color(0xF2151026)
    val rowTop = Color(0x14FFFFFF)
    val rowBottom = Color(0x0AFFFFFF)
    val rowFocusedTop = Color(0xF2342952)
    val rowFocusedBottom = Color(0xF2211938)
    val rowPressed = Color(0x1FFFFFFF)

    val textPrimary = Color(0xFFF8F5FF)
    val textSecondary = Color(0xFFC6BEDB)
    val textMuted = Color(0xFF978EAC)
    val hairline = Color(0x1FFFFFFF)

    val success = Color(0xFF5CE1B0)
    val successSoft = Color(0x265CE1B0)
    val warning = Color(0xFFFFC46B)
    val warningSoft = Color(0x26FFC46B)
}

/** Sizes for the app's own pages, in dp on the same 960 x 540 reference screen. */
object AppDimens {
    val screenHorizontal = 40.dp
    val screenVertical = 28.dp
    val columnGap = 28.dp

    val cardRadius = 26.dp
    val cardPadding = 18.dp
    val tabBarRadius = 18.dp
    val rowRadius = 14.dp
    val chipRadius = 12.dp
    val buttonRadius = 16.dp

    val gap = 8.dp
    val tabHeight = 38.dp
    val rowHeight = 46.dp
    val chipHeight = 40.dp

    val previewWidth = 296.dp
    val previewPadding = 14.dp

    val focusBorder = 2.dp
}

/** Type scale for the app's own pages. Inter, like the panel, but with a clearer hierarchy. */
object AppType {
    val eyebrow = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.8.sp,
    )
    val display = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        lineHeight = 43.sp,
        letterSpacing = (-0.5).sp,
    )
    val heading = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.2).sp,
    )
    val subtitle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val label = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.sp)
    val labelStrong = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
    val value = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp)
    val tab = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 16.sp)
    val section = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.2.sp,
    )
    val note = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 16.sp)
    val badge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 13.sp)
}

/** Motion for app focus and press feedback. Short and quiet, so a weak TV stays responsive. */
object AppMotion {
    fun <T> focusSpring() = spring<T>(dampingRatio = 0.9f, stiffness = 1400f)
    const val focusScale = 1.02f
    const val pressScale = 0.98f
}

@Composable
fun ControlCenterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
