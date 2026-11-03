package com.example.tvcontrolcenter.ui.edit

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import com.example.tvcontrolcenter.domain.model.TextScaleSteps
import com.example.tvcontrolcenter.ui.theme.ProvideTextScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.BoxScope
import com.example.tvcontrolcenter.domain.model.HeaderGapSteps
import com.example.tvcontrolcenter.ui.glass.LocalEdgeWidth
import com.example.tvcontrolcenter.ui.glass.LocalInvertFocus
import com.example.tvcontrolcenter.ui.panel.LocalIconStyle
import com.example.tvcontrolcenter.domain.model.EdgeWidthSteps
import com.example.tvcontrolcenter.domain.model.IconStyle
import com.example.tvcontrolcenter.domain.model.TopSizeSteps
import com.example.tvcontrolcenter.ui.glass.LocalLiteGlass
import com.example.tvcontrolcenter.ui.glass.LocalTileInk
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.tvcontrolcenter.data.ProfilePhoto
import com.example.tvcontrolcenter.domain.model.BlurMethod
import com.example.tvcontrolcenter.domain.model.FrostStyle
import com.example.tvcontrolcenter.domain.model.TopOffsetSteps
import com.example.tvcontrolcenter.system.backdrop.BackdropCapture
import com.example.tvcontrolcenter.ui.panel.AvatarColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.GridPacker
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileSize
import com.example.tvcontrolcenter.domain.model.UiScaleSteps
import com.example.tvcontrolcenter.ui.background.BoxesBackground
import com.example.tvcontrolcenter.ui.glass.LocalFrostStyle
import com.example.tvcontrolcenter.ui.panel.ControlCenterUiState
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.panel.Label
import com.example.tvcontrolcenter.ui.panel.TileActions
import com.example.tvcontrolcenter.ui.panel.PanelContent
import com.example.tvcontrolcenter.ui.panel.TileIcon
import com.example.tvcontrolcenter.ui.panel.gridHeight
import com.example.tvcontrolcenter.ui.panel.lookOf
import com.example.tvcontrolcenter.ui.theme.GlassTokens
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import com.example.tvcontrolcenter.ui.theme.PanelType
import com.example.tvcontrolcenter.ui.theme.ProvideUiScale
import kotlin.math.roundToInt

/**
 * Edit Control Center. The right side is the real panel; pick a control with OK, then size,
 * move or hide it with the buttons on the left. Hidden controls are listed under "Add
 * controls". Everything saves as you go, and the panel everywhere else follows at once.
 */
@Composable
fun EditScreen(viewModel: ControlCenterViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Fits the screen but ignores the user's panel size, so the controls always have room.
    val rows = remember(state.layout) { GridPacker.rowCount(GridPacker.pack(state.layout.visibleItems())) }
    val needed = gridHeight(rows) + PanelDimens.sheetPadding * 2 +
        PanelDimens.headerFor(state.settings.topSize) + state.settings.headerGap.dp + PanelDimens.sheetPadding + 56.dp
    ProvideUiScale(1f, neededHeight = needed) {
        // Lite glass: flat fills and no animation, so this page with its many buttons stays quick.
        CompositionLocalProvider(
            LocalLiteGlass provides true,
            LocalInvertFocus provides state.settings.invertFocus,
            LocalEdgeWidth provides state.settings.edgeWidth.dp,
            LocalIconStyle provides state.settings.iconStyle,
        ) {
            EditBody(state, viewModel, onDone)
        }
    }
}

@Composable
private fun EditBody(state: ControlCenterUiState, vm: ControlCenterViewModel, onDone: () -> Unit) {
    val layout = state.layout
    val placed = remember(layout) { GridPacker.pack(layout.visibleItems()) }
    val pinned = remember(layout) { GridPacker.pinned(layout.visibleItems()) }
    val currentPlaced by rememberUpdatedState(pinned + placed)
    val tileRequesters = remember { TileId.entries.associateWith { FocusRequester() } }
    val firstAction = remember { FocusRequester() }
    var selected by remember { mutableStateOf<TileId?>(null) }
    var returnTo by remember { mutableStateOf<TileId?>(null) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        currentPlaced.firstOrNull()?.let { runCatching { tileRequesters.getValue(it.id).requestFocus() } }
    }
    LaunchedEffect(selected) {
        if (selected != null) {
            withFrameNanos { }
            runCatching { firstAction.requestFocus() }
        }
    }
    LaunchedEffect(returnTo) {
        val id = returnTo ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { tileRequesters.getValue(id).requestFocus() }
        returnTo = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) {
                        val current = selected
                        if (current != null) {
                            returnTo = current
                            selected = null
                        } else onDone()
                    }
                    true
                } else false
            },
    ) {
        BoxesBackground(Modifier.fillMaxSize())
        Row(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText(stringResource(R.string.edit_title), style = PanelType.title.copy(color = GlassTokens.textPrimary))
                val settings = state.settings

                val item = selected?.let { layout.item(it) }
                if (item == null) {
                    Hint(stringResource(R.string.edit_hint_pick))
                    Hint(stringResource(R.string.edit_howto))
                } else {
                    Section(stringResource(lookOf(item.id).label))
                    val sizes = TileSize.entries.filter {
                        it != TileSize.TOP || item.id == TileId.EDIT || item.id == TileId.PROFILE || item.id == TileId.NOW_PLAYING
                    }
                    StepRow(
                        stringResource(R.string.edit_size),
                        stringResource(sizeLabel(item.size)),
                        requester = firstAction,
                    ) { d -> vm.setTileSize(item.id, sizes.step(item.size, d)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditChip(stringResource(R.string.edit_move_earlier), width = 132.dp) { vm.moveTile(item.id, -1) }
                        EditChip(stringResource(R.string.edit_move_later), width = 132.dp) { vm.moveTile(item.id, 1) }
                        EditChip(stringResource(R.string.edit_hide), width = 100.dp) {
                            vm.setTileVisible(item.id, false)
                            returnTo = currentPlaced.firstOrNull { it.id != item.id }?.id
                            selected = null
                        }
                    }
                }

                val hidden = layout.hiddenItems()
                if (hidden.isNotEmpty()) {
                    Section(stringResource(R.string.edit_add))
                    hidden.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { hiddenItem ->
                                AddChip(hiddenItem.id) {
                                    vm.setTileVisible(hiddenItem.id, true)
                                    selected = hiddenItem.id
                                }
                            }
                        }
                    }
                }

                Section(stringResource(R.string.edit_look))
                StepRow(stringResource(R.string.edit_panel_size), "${(settings.uiScale * 100).roundToInt()}%") { d ->
                    vm.setUiScale(UiScaleSteps.step(settings.uiScale, d))
                }
                StepRow(stringResource(R.string.edit_text_size), "${(settings.textScale * 100).roundToInt()}%") { d ->
                    vm.updateSettings { it.copy(textScale = TextScaleSteps.step(it.textScale, d)) }
                }
                StepRow(
                    stringResource(R.string.edit_position),
                    TopOffsetSteps.indexOf(settings.topOffset).let { if (it <= 0) stringResource(R.string.edit_pos_top) else "$it" },
                ) { d -> vm.updateSettings { it.copy(topOffset = TopOffsetSteps.step(it.topOffset, d)) } }
                StepRow(stringResource(R.string.edit_header_gap), "${settings.headerGap}") { d ->
                    vm.updateSettings { it.copy(headerGap = HeaderGapSteps.step(it.headerGap, d)) }
                }
                StepRow(
                    stringResource(R.string.edit_invert),
                    stringResource(if (settings.invertFocus) R.string.invert_yes else R.string.invert_no),
                ) { vm.updateSettings { it.copy(invertFocus = !it.invertFocus) } }
                StepRow(
                    stringResource(R.string.edit_edge),
                    if (settings.edgeWidth <= 0f) stringResource(R.string.edge_none) else "${settings.edgeWidth}",
                ) { d -> vm.updateSettings { it.copy(edgeWidth = EdgeWidthSteps.step(it.edgeWidth, d)) } }
                val iconNames = listOf(
                    IconStyle.FILLED to R.string.icons_filled,
                    IconStyle.OUTLINED to R.string.icons_outlined,
                    IconStyle.ROUNDED to R.string.icons_rounded,
                )
                StepRow(
                    stringResource(R.string.edit_icons),
                    stringResource(iconNames.first { it.first == settings.iconStyle }.second),
                ) { d ->
                    val next = iconNames[Math.floorMod(iconNames.indexOfFirst { it.first == settings.iconStyle } + d, iconNames.size)].first
                    vm.updateSettings { it.copy(iconStyle = next) }
                }

                Section(stringResource(R.string.edit_header_buttons))
                listOf(TileId.EDIT to R.string.edit_btn_menu, TileId.PROFILE to R.string.edit_btn_profile).forEach { (id, name) ->
                    val on = layout.item(id)?.visible == true
                    StepRow(stringResource(name), onOff(on)) { vm.setTileVisible(id, !on) }
                }
                val music = layout.item(TileId.NOW_PLAYING)
                val musicOn = music?.visible == true && music.size == TileSize.TOP
                StepRow(stringResource(R.string.edit_btn_music), onOff(musicOn)) {
                    if (musicOn) vm.setTileSize(TileId.NOW_PLAYING, TileSize.FULL)
                    else {
                        vm.setTileVisible(TileId.NOW_PLAYING, true)
                        vm.setTileSize(TileId.NOW_PLAYING, TileSize.TOP)
                    }
                }
                StepRow(stringResource(R.string.edit_top_size), "${settings.topSize}") { d ->
                    vm.updateSettings { it.copy(topSize = TopSizeSteps.step(it.topSize, d)) }
                }
                StepRow(stringResource(R.string.edit_top_flat), onOff(settings.topBorderless)) {
                    vm.updateSettings { it.copy(topBorderless = !it.topBorderless) }
                }
                StepRow(stringResource(R.string.edit_menu_home), onOff(settings.menuIsHome)) {
                    vm.updateSettings { it.copy(menuIsHome = !it.menuIsHome) }
                }
                Hint(stringResource(R.string.edit_menu_note))
                Hint(stringResource(R.string.edit_music_note))
                Hint(stringResource(R.string.edit_longpress_note))

                Section(stringResource(R.string.edit_photo))
                val context = LocalContext.current
                val picked = { uri: Uri? ->
                    if (uri != null) {
                        if (ProfilePhoto.save(context, uri)) {
                            vm.updateSettings { it.copy(profilePhotoVersion = it.profilePhotoVersion + 1) }
                        } else Toast.makeText(context, R.string.edit_photo_failed, Toast.LENGTH_LONG).show()
                    }
                }
                val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), picked)
                val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), picked)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditChip(stringResource(R.string.edit_photo_choose), width = 124.dp) {
                        try {
                            openDoc.launch(arrayOf("image/*"))
                        } catch (_: ActivityNotFoundException) {
                            try {
                                getContent.launch("image/*")
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.edit_photo_no_picker, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    EditChip(stringResource(R.string.edit_photo_remove), width = 104.dp) {
                        ProfilePhoto.clear(context)
                        vm.updateSettings { it.copy(profilePhotoVersion = 0) }
                    }
                }
                StepRow(
                    stringResource(R.string.edit_photo_color),
                    "${settings.profileColor.mod(AvatarColors.size) + 1}",
                    swatch = AvatarColors[settings.profileColor.mod(AvatarColors.size)],
                ) { d -> vm.updateSettings { it.copy(profileColor = Math.floorMod(it.profileColor + d, AvatarColors.size)) } }

                Section(stringResource(R.string.edit_blur_title))
                val blurNames = listOf(R.string.blur_off, R.string.blur_light, R.string.blur_medium, R.string.blur_strong, R.string.blur_max)
                    .map { stringResource(it) }
                StepRow(stringResource(R.string.edit_blur_level), blurNames[settings.effectiveBlur]) { d ->
                    val level = Math.floorMod(settings.effectiveBlur + d, blurNames.size)
                    vm.updateSettings { it.copy(blurBehind = level > 0, blurLevel = level) }
                }
                val methods = listOf(
                    BlurMethod.AUTO to R.string.method_auto,
                    BlurMethod.SCREENSHOT to R.string.method_screenshot,
                    BlurMethod.WINDOW to R.string.method_window,
                )
                StepRow(
                    stringResource(R.string.edit_blur_method),
                    stringResource(methods.first { it.first == settings.blurMethod }.second),
                ) { d ->
                    val next = methods[Math.floorMod(methods.indexOfFirst { it.first == settings.blurMethod } + d, methods.size)].first
                    vm.updateSettings { it.copy(blurMethod = next) }
                }
                Hint(stringResource(R.string.blur_whole_note))
                Hint(
                    stringResource(
                        when (BackdropCapture.status()) {
                            BackdropCapture.Status.READY -> R.string.blur_status_ready
                            BackdropCapture.Status.NEEDS_SERVICE -> R.string.blur_status_service
                            BackdropCapture.Status.NEEDS_ANDROID_11 -> R.string.blur_status_old
                        },
                    ),
                )
                val frosts = listOf(
                    FrostStyle.SMOKE to R.string.frost_smoke,
                    FrostStyle.MILKY to R.string.frost_milky,
                    FrostStyle.CLEAR to R.string.frost_clear,
                )
                StepRow(
                    stringResource(R.string.edit_frost),
                    stringResource(frosts.first { it.first == settings.frost }.second),
                ) { d ->
                    val next = frosts[Math.floorMod(frosts.indexOfFirst { it.first == settings.frost } + d, frosts.size)].first
                    vm.updateSettings { it.copy(frost = next) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    EditChip(stringResource(R.string.edit_reset), width = 132.dp) {
                        vm.resetLayout()
                        selected = null
                    }
                    EditChip(stringResource(R.string.edit_done), width = 88.dp, onClick = onDone)
                }
            }

            Box(
                Modifier
                    .align(Alignment.Top)
                    .padding(PanelDimens.sheetPadding),
            ) {
                CompositionLocalProvider(LocalFrostStyle provides state.settings.frost) {
                ProvideTextScale(state.settings.textScale) {
                PanelContent(
                    grid = placed,
                    pinned = pinned,
                    state = state,
                    requesters = tileRequesters,
                    interactive = false,
                    trapEdges = false,
                    selected = selected,
                    appear = { _ -> { 1f } },
                    onFocused = {},
                    onActivate = { selected = it },
                    actions = TileActions(),
                    backdrop = null,
                    origin = Offset.Zero,
                    sheet = true,
                )
                }
                }
            }
        }
    }
}

private fun sizeLabel(size: TileSize): Int = when (size) {
    TileSize.SMALL -> R.string.size_small
    TileSize.WIDE -> R.string.size_wide
    TileSize.TALL -> R.string.size_tall
    TileSize.LARGE -> R.string.size_large
    TileSize.FULL -> R.string.size_full
    TileSize.TOP -> R.string.size_top
}

@Composable
private fun Section(text: String) {
    BasicText(
        text,
        Modifier.padding(top = 8.dp),
        style = PanelType.label.copy(color = GlassTokens.textSecondary),
    )
}

@Composable
private fun Hint(text: String) {
    BasicText(text, style = PanelType.hint.copy(color = GlassTokens.textSecondary))
}

/**
 * A small flat button. [highlighted] marks the current choice in a group (outlined), focus turns
 * it white. One fill and no animation: this page has dozens of them, so they must stay cheap.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FlatButton(
    label: String,
    width: Dp,
    highlighted: Boolean,
    requester: FocusRequester?,
    activate: () -> Unit,
    onKeyExtra: (KeyEvent) -> Boolean = { false },
    content: @Composable BoxScope.() -> Unit,
) {
    val r = requester ?: remember { FocusRequester() }
    val invert = LocalInvertFocus.current
    var focused by remember { mutableStateOf(false) }
    val ink = if (focused && invert) GlassTokens.inkDark else Color.White
    Box(
        Modifier
            .size(width, 40.dp)
            .focusRequester(r)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                when {
                    onKeyExtra(e) -> true
                    e.type == KeyEventType.KeyUp && e.key.isSelectKey() -> { activate(); true }
                    e.key.isSelectKey() -> true
                    else -> false
                }
            }
            .focusable()
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                onClick(label = label) { activate(); true }
            }
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                val fill = when {
                    focused && invert -> Color.White
                    focused -> Color.White.copy(alpha = 0.32f)
                    highlighted -> Color.White.copy(alpha = 0.24f)
                    else -> Color.Black.copy(alpha = 0.42f)
                }
                drawRoundRect(fill, cornerRadius = radius)
                if (highlighted && !focused) {
                    val w = 2.dp.toPx()
                    drawRoundRect(
                        Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(w / 2f, w / 2f),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(radius.x - w / 2f),
                        style = Stroke(w),
                    )
                }
            },
    ) {
        CompositionLocalProvider(LocalTileInk provides ink) { content() }
    }
}

private fun Key.isSelectKey() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

@Composable
private fun EditChip(
    label: String,
    highlighted: Boolean = false,
    width: Dp = 84.dp,
    requester: FocusRequester? = null,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    FlatButton(label, width, highlighted, requester, onClick) {
        if (tint != null) Box(Modifier.align(Alignment.Center).size(18.dp).background(tint, CircleShape))
        else Label(label, Modifier.align(Alignment.Center).padding(horizontal = 6.dp), maxLines = 1)
    }
}

/**
 * One setting on one row: name on the left, current value on the right. OK moves to the next
 * value (wrapping), Left to the previous one. A single focusable per setting keeps this page light.
 */
@Composable
private fun StepRow(
    label: String,
    value: String,
    requester: FocusRequester? = null,
    swatch: Color? = null,
    onStep: (Int) -> Unit,
) {
    FlatButton(
        label = "$label, $value",
        width = 340.dp,
        highlighted = false,
        requester = requester,
        activate = { onStep(1) },
        onKeyExtra = { e ->
            if (e.key == Key.DirectionLeft) {
                if (e.type == KeyEventType.KeyDown) onStep(-1)
                true
            } else false
        },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label(label, Modifier.weight(1f), maxLines = 1)
            if (swatch != null) Box(Modifier.size(16.dp).background(swatch, CircleShape))
            Label(value, maxLines = 1)
        }
    }
}

@Composable
private fun onOff(on: Boolean): String = stringResource(if (on) R.string.state_on else R.string.state_off)

private fun <T> List<T>.step(current: T, delta: Int): T {
    val i = indexOf(current).let { if (it < 0) 0 else it }
    return this[Math.floorMod(i + delta, size)]
}

@Composable
private fun AddChip(id: TileId, onClick: () -> Unit) {
    val look = lookOf(id)
    val label = stringResource(look.label)
    FlatButton(label, 168.dp, false, null, onClick) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TileIcon(look.icon, 18.dp)
            Label(label, style = PanelType.label, maxLines = 1)
        }
    }
}
