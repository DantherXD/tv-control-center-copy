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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.domain.model.BlurMethod
import com.example.tvcontrolcenter.domain.model.Placed
import com.example.tvcontrolcenter.domain.model.TileId
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
 * It is the same [PanelContent] the real panel draws, with the same glass: the wallpaper is
 * blurred as strongly as the blur setting says and put behind the controls exactly like the
 * screenshot is on the TV. Because the blur is recomputed whenever the wallpaper, the blur
 * strength or the size changes, every setting shows up here at once.
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
    var size by remember { mutableStateOf(IntSize.Zero) }
    val level = state.settings.effectiveBlur
    // Blurring is real work: keep it off the frame that draws the controls.
    val screen by produceState<ImageBitmap?>(null, state.settings.wallpaper, level, size) {
        val w = size.width
        val h = size.height
        value = if (w <= 0 || h <= 0) null
        else withContext(Dispatchers.Default) { previewWallpaper(state.settings.wallpaper, w, h, level) }
    }
    val wholeScreen = state.settings.blurMethod == BlurMethod.WINDOW
    // Blur off: the picture is still drawn behind, but no glass samples it (as on the TV).
    val mapped = remember(screen, size, level) {
        if (level <= 0) null else screen?.let { Backdrop(it, size.width, size.height) }
    }
    // Same rule as the panel: a sheet only when the glass has no blurred screen to show, or
    // when the card was asked for.
    val sheet = mapped == null || state.settings.card
    val density = LocalDensity.current
    Box(
        modifier
            .clip(RoundedCornerShape(PanelDimens.sheetRadius))
            .onSizeChanged { size = it }
            .drawBehind {
                val image = screen ?: return@drawBehind
                drawImage(
                    image,
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Medium,
                )
            }
            .padding(PanelDimens.sheetPadding),
    ) {
        CompositionLocalProvider(
            LocalLiteGlass provides true,
            LocalFrostStyle provides state.settings.frost,
            LocalInvertFocus provides state.settings.invertFocus,
            LocalOverBlur provides (mapped != null),
            LocalEdgeWidth provides state.settings.edgeWidth.dp,
            LocalIconStyle provides state.settings.iconStyle,
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
                    backdrop = if (wholeScreen) null else mapped,
                    origin = with(density) {
                        Offset(PanelDimens.sheetPadding.toPx(), PanelDimens.sheetPadding.toPx())
                    },
                    sheet = sheet,
                )
            }
        }
    }
}
