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
    /** The Notifications page: two lines a row, and a little more room than the other windows. */
    val notificationRow = 56.dp
    val notificationsMaxHeight = 400.dp
    /** An opened notification may grow past the list height to show its whole message. */
    val notificationDetailMaxHeight = 440.dp

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
 * Colours for the app's own pages: a near-black backdrop, two flat surface levels and white
 * focus. Text is white at several opacities, so hierarchy comes from contrast, not decoration.
 */
object AppPalette {
    /** Near-black, with just enough lift for surfaces and focus to read on a TV. */
    val background = Color(0xFF0B0B0B)
    val surface = Color(0xFF191919)
    val surfaceHigh = Color(0xFF242424)
    val outline = Color(0xFF333333)

    /** Blue is reserved for active state and the primary-action focus ring. */
    val accent = Color(0xFF8AB4F8)
    val accentBright = Color(0xFFD3E3FD)

    val textPrimary = Color(0xFFF2F2F2)
    val textSecondary = Color(0xFFB3B3B3)
    val textTertiary = Color(0xFF8A8A8A)
    val textMuted = Color(0xFF6B6B6B)

    /** Solid focus surface and the dark ink that sits on it. */
    val focusFill = Color(0xFFF5F5F5)
    val focusInk = Color(0xFF0B0B0B)

    val success = Color(0xFF81C995)
    val warning = Color(0xFFFDD663)
    val danger = Color(0xFFF2B8B5)
}

/** Sizes for the app's own pages, in dp on the same 960 x 540 reference screen. */
object AppDimens {
    // 960 x 540 reference: 58dp side margins and a 196dp rail leave a 624dp content column.
    val screenHorizontal = 58.dp
    val screenVertical = 28.dp
    val railWidth = 196.dp
    val columnGap = 24.dp

    val settingsWidth = 336.dp
    val previewWidth = 264.dp
    val paneGap = 20.dp

    val groupGap = 24.dp
    val rowHeight = 52.dp
    val navItemHeight = 52.dp
    val chipHeight = 44.dp
    val primaryHeight = 84.dp
    val secondaryHeight = 48.dp

    val cardRadius = 12.dp
    val rowRadius = 10.dp
    val chipRadius = 22.dp
    val previewRadius = 16.dp
}

/**
 * Type scale for the app's own pages. Roboto, at 10-foot sizes: a 56sp display, 34sp page title,
 * 16sp labels and 13sp notes. Nothing below 12sp, so it still reads from a couch.
 */
object AppType {
    val display = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.8).sp,
    )
    val heading = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.2).sp,
    )
    val title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 27.sp,
    )
    val headingSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    )
    val subtitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 25.sp,
    )
    val label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    )
    val labelStrong = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    )
    val value = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )
    val section = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    )
    val note = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val badge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

/** Motion for app focus, press and rail selection. Short and quiet, so a weak TV stays responsive. */
object AppMotion {
    fun <T> focusSpring() = spring<T>(dampingRatio = 0.9f, stiffness = 1200f)
    fun <T> tabSpring() = spring<T>(dampingRatio = 0.92f, stiffness = 900f)
    const val focusScale = 1.04f
    const val pressScale = 0.985f
}

@Composable
fun ControlCenterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
