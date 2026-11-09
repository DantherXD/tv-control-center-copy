package com.example.tvcontrolcenter.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width in dp of the screen the design tokens were drawn for. */
private const val REFERENCE_WIDTH_DP = 960f
private const val REFERENCE_HEIGHT_DP = 540f

/**
 * Scales every dp and sp inside [content] so the panel takes the same share of the screen on
 * any TV. A 4K set that reports 1920 dp (density 2) gets a factor of 2; one that already
 * reports 960 dp (density 4) gets 1. [userScale] is the S/M/L choice from the editor.
 *
 * [neededHeight] is how tall the content is at 100 % (in reference dp). When the panel would
 * not fit the screen, the scale shrinks until it does, so adding controls never pushes
 * the bottom row off screen.
 *
 * Done by replacing the density, so no layout code needs to know about it.
 */
@Composable
fun ProvideUiScale(userScale: Float, neededHeight: Dp = 0.dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val fit = if (config.screenWidthDp > 0) (config.screenWidthDp / REFERENCE_WIDTH_DP).coerceIn(0.75f, 3f) else 1f
    val availableHeight = if (config.screenHeightDp > 0) config.screenHeightDp / fit else REFERENCE_HEIGHT_DP
    val limit = if (neededHeight.value > 0f) availableHeight / neededHeight.value else Float.MAX_VALUE
    val user = userScale.coerceIn(0.5f, 3f).coerceAtMost(limit).coerceAtLeast(0.4f)
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * fit * user, density.fontScale),
        content = content,
    )
}

/**
 * Text scale for the notifications list only (see [ProvideTextScale]). Provided by the panel and
 * the editor's preview, so the list can read smaller than the controls around it.
 */
val LocalNoticeScale = compositionLocalOf { 0.8f }

/**
 * Makes text [scale] times bigger or smaller inside [content] without moving any dp sizes.
 * Done through the font scale, so every sp in the panel follows.
 */
@Composable
fun ProvideTextScale(scale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * scale),
        content = content,
    )
}
