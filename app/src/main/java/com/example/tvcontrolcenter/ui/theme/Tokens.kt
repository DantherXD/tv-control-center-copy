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

/**
 * Colour of the app's own glass over the atmospheric background. Text is white at several
 * opacities so translucency reads as light rather than as a grey fill.
 */
object AppPalette {
    // Restrained lavender for selection ticks, the eyebrow dot and focus light only.
    val accent = Color(0xFF9E93D9)
    val accentBright = Color(0xFFDDD8F0)
    val violet = Color(0xFF7A6FA8)
    val indigo = Color(0xFF3C4559)
    val blue = Color(0xFF4E6E9E)

    val textPrimary = Color(0xF2FFFFFF)
    val textSecondary = Color(0xC6FFFFFF)
    val textTertiary = Color(0xA0FFFFFF)
    val textMuted = Color(0x7AFFFFFF)

    val success = Color(0xFF78DFB8)
    val warning = Color(0xFFF0C27A)
    val danger = Color(0xFFE7A0A0)

    val midnight = Color(0xFF08090E)
    val midnightSoft = Color(0xFF10131A)
    val shadow = Color(0xFF020306)
}

/**
 * The four liquid-glass materials. Each is a translucent white gradient plus a light source and
 * a directional rim; nothing is opaque and nothing relies on a flat outline.
 */
object AppMaterial {
    // Cool silver-white over the charcoal backdrop reads as smoky graphite, not flat grey.
    val primaryTop = Color(0x29EDF0F6)
    val primaryBottom = Color(0x0AE7EBF2)
    val secondaryTop = Color(0x16E3E8F0)
    val secondaryBottom = Color(0x05E6EAF2)
    val floatingTop = Color(0x33ECEFF5)
    val floatingBottom = Color(0x10E8ECF3)
    val interactiveTop = Color(0x3DEDF0F7)
    val interactiveBottom = Color(0x17E9EDF4)

    val rimStart = Color(0x5CFFFFFF)
    val rimEnd = Color(0x08FFFFFF)
    val specular = Color(0x2EFFFFFF)
    val focusGlow = Color(0x1FA89BD8)

    val accentTop = Color(0xC49E93D9)
    val accentBottom = Color(0xA8756CAF)
    val accentFocusTop = Color(0xE0B2A8E3)
    val accentFocusBottom = Color(0xC48178B8)
    val accentPressedTop = Color(0xB07D73AD)
    val accentPressedBottom = Color(0x9962598A)

    val selectedTint = Color(0x2690A4C0)
    val divider = Color(0x0AFFFFFF)
}

/** Sizes for the app's own pages, in dp on the same 960 x 540 reference screen. */
object AppDimens {
    val screenHorizontal = 44.dp
    val screenVertical = 30.dp
    val columnGap = 30.dp

    val groupRadius = 24.dp
    val floatingRadius = 20.dp
    val rowRadius = 14.dp
    val chipRadius = 14.dp
    val buttonRadius = 18.dp
    val tabRailRadius = 24.dp
    val previewRadius = 30.dp

    val groupPadding = 6.dp
    val groupGap = 16.dp
    val rowHeight = 48.dp
    val tabHeight = 36.dp
    val tabRailHeight = 46.dp
    val chipHeight = 42.dp
    val primaryHeight = 60.dp
    val secondaryHeight = 48.dp

    val previewWidth = 304.dp
    val previewGlowPadding = 10.dp

    val focusRim = 1.2.dp
    val focusBarHeight = 16.dp
}

/** Type scale for the app's own pages. Inter, with one confident display size and quieter detail. */
object AppType {
    val eyebrow = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 2.2.sp,
    )
    val display = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp,
    )
    val heading = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.4).sp,
    )
    val headingSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp,
    )
    val subtitle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    val label = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp)
    val labelStrong = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val value = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp)
    val tab = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp)
    val section = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.7.sp,
    )
    val note = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 16.sp)
    val badge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 13.sp)
}

/** Motion for app focus, press and tab selection. Short and quiet, so a weak TV stays responsive. */
object AppMotion {
    fun <T> focusSpring() = spring<T>(dampingRatio = 0.9f, stiffness = 1200f)
    fun <T> tabSpring() = spring<T>(dampingRatio = 0.92f, stiffness = 900f)
    const val focusScale = 1.02f
    const val pressScale = 0.985f
}

@Composable
fun ControlCenterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
