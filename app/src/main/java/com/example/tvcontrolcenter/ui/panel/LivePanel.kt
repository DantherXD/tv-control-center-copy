package com.example.tvcontrolcenter.ui.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.domain.model.BlurMethod
import com.example.tvcontrolcenter.domain.model.Placed
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.ui.background.PreviewWallpaper
import com.example.tvcontrolcenter.ui.background.previewWallpaper
import com.example.tvcontrolcenter.ui.glass.Backdrop
import com.example.tvcontrolcenter.ui.glass.LocalEdgeWidth
import com.example.tvcontrolcenter.ui.glass.LocalFrostStyle
import com.example.tvcontrolcenter.ui.glass.LocalInvertFocus
import com.example.tvcontrolcenter.ui.glass.LocalLiteGlass
import com.example.tvcontrolcenter.ui.glass.LocalOverBlur
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import com.example.tvcontrolcenter.ui.theme.ProvideTextScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * A small picture of the panel, over a blurred screen, for the home page and the editor.
 *
 * It is the same [PanelContent] the real panel draws, with the same glass: the wallpaper stands
 * in for the screen, its blurred copy for the screenshot, and the scrim, the sheet and the
 * per-tile crops follow the same rules as the host. Because all of it is rebuilt whenever the
 * wallpaper, the blur strength, the method or the size changes, every setting shows up here at
 * once — including what "Whole screen" changes.
 */
@Composable
internal fun LivePanel(
    state: ControlCenterUiState,
    grid: List<Placed>,
    pinned: List<Placed>,
    requesters: Map<TileId, FocusRequester>,
    selected: TileId?,
    interactive: Boolean,
    onActivate: (TileId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val level = state.settings.effectiveBlur
    // Blurring is real work: keep it off the frame that draws the controls.
    val wallpaper by produceState<PreviewWallpaper?>(null, state.settings.wallpaper, level, previewSize) {
        val w = previewSize.width
        val h = previewSize.height
        value = if (w <= 0 || h <= 0) null
        else withContext(Dispatchers.Default) { previewWallpaper(state.settings.wallpaper, w, h, level) }
    }
    val wholeScreen = state.settings.blurMethod == BlurMethod.WINDOW
    // The blurred copy the glass samples. None when blur is off, so the glass falls back to the
    // plain frosted look, exactly like a device without a screenshot.
    val mapped = remember(wallpaper, previewSize, level) {
        val blurred = wallpaper?.blurred
        if (level <= 0 || blurred == null) null else Backdrop(blurred, previewSize.width, previewSize.height)
    }
    // Same rule as the panel: a sheet only when the glass has no blurred screen to show, or
    // when the card was asked for.
    val sheet = mapped == null || state.settings.card
    // Whole screen blurs everything behind the panel. Every other method keeps the screen sharp
    // and blurs only inside the glass, which is what the panel really looks like.
    val screen = if (wholeScreen && level > 0) {
        wallpaper?.blurred ?: wallpaper?.sharp
    } else {
        wallpaper?.sharp
    }
    val density = LocalDensity.current
    Box(
        modifier
            .clip(RoundedCornerShape(PanelDimens.sheetRadius))
            .onSizeChanged { previewSize = it }
            .drawBehind {
                val image = screen ?: return@drawBehind
                drawImage(
                    image,
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Medium,
                )
                // The panel dims the screen behind it; without the same scrim the preview reads
                // brighter and flatter than the real thing.
                drawRect(Color.Black.copy(alpha = if (level > 0) 0.22f else 0.32f))
            }
            .padding(PanelDimens.sheetPadding),
    ) {
        CompositionLocalProvider(
            // The preview must draw the real glass. The editor page around it is lite, and that
            // local would otherwise reach these controls, whose lite path skips the backdrop.
            LocalLiteGlass provides false,
            LocalFrostStyle provides state.settings.frost,
            LocalInvertFocus provides state.settings.invertFocus,
            LocalOverBlur provides (mapped != null),
            LocalEdgeWidth provides state.settings.edgeWidth.dp,
            LocalIconStyle provides state.settings.iconStyle,
            LocalIconPack provides state.settings.iconPack,
            LocalPhosphorWeight provides state.settings.phosphorWeight,
            LocalBluetoothIcon provides state.settings.bluetoothIcon,
        ) {
            ProvideTextScale(state.settings.textScale) {
                PanelContent(
                    grid = grid,
                    pinned = pinned,
                    state = state,
                    requesters = requesters,
                    interactive = interactive,
                    trapEdges = false,
                    selected = selected,
                    appear = { _ -> { 1f } },
                    onFocused = {},
                    onActivate = onActivate,
                    actions = TileActions(),
                    // Whole screen: the blurred picture is the background and the controls are
                    // plain glass on it. Otherwise each control shows its own piece of it.
                    backdrop = if (wholeScreen || level <= 0) null else mapped,
                    origin = with(density) {
                        Offset(PanelDimens.sheetPadding.toPx(), PanelDimens.sheetPadding.toPx())
                    },
                    sheet = sheet,
                )
            }
        }
    }
}
