package com.example.tvcontrolcenter.ui.edit

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.data.ProfilePhoto
import com.example.tvcontrolcenter.domain.model.AppSlots
import com.example.tvcontrolcenter.domain.model.BluetoothIcon
import com.example.tvcontrolcenter.domain.model.BlurMethod
import com.example.tvcontrolcenter.domain.model.EdgeWidthSteps
import com.example.tvcontrolcenter.domain.model.FrostStyle
import com.example.tvcontrolcenter.domain.model.GridPacker
import com.example.tvcontrolcenter.domain.model.HeaderGapSteps
import com.example.tvcontrolcenter.domain.model.IconPack
import com.example.tvcontrolcenter.domain.model.IconStyle
import com.example.tvcontrolcenter.domain.model.MenuIcon
import com.example.tvcontrolcenter.domain.model.MusicStyle
import com.example.tvcontrolcenter.domain.model.PhosphorWeight
import com.example.tvcontrolcenter.domain.model.TextScaleSteps
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileSize
import com.example.tvcontrolcenter.domain.model.TopOffsetSteps
import com.example.tvcontrolcenter.domain.model.TopSizeSteps
import com.example.tvcontrolcenter.domain.model.UiScaleSteps
import com.example.tvcontrolcenter.domain.model.VolumeStepSteps
import com.example.tvcontrolcenter.domain.model.Wallpaper
import com.example.tvcontrolcenter.domain.model.WindowMotion
import com.example.tvcontrolcenter.system.backdrop.BackdropCapture
import com.example.tvcontrolcenter.system.wholeScreenBlurSupported
import com.example.tvcontrolcenter.ui.app.AppActionRow
import com.example.tvcontrolcenter.ui.app.AppControlCard
import com.example.tvcontrolcenter.ui.app.AppDestination
import com.example.tvcontrolcenter.ui.app.AppNavigationRail
import com.example.tvcontrolcenter.ui.app.AppNote
import com.example.tvcontrolcenter.ui.app.AppPreview
import com.example.tvcontrolcenter.ui.app.AppScreenBackground
import com.example.tvcontrolcenter.ui.app.AppSettingGroup
import com.example.tvcontrolcenter.ui.app.AppSettingRow
import com.example.tvcontrolcenter.ui.app.AppSlotCard
import com.example.tvcontrolcenter.ui.glass.LocalEdgeWidth
import com.example.tvcontrolcenter.ui.glass.LocalInvertFocus
import com.example.tvcontrolcenter.ui.glass.LocalLiteGlass
import com.example.tvcontrolcenter.ui.panel.AvatarColors
import com.example.tvcontrolcenter.ui.panel.ControlCenterUiState
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.panel.LivePanel
import com.example.tvcontrolcenter.ui.panel.LocalBluetoothIcon
import com.example.tvcontrolcenter.ui.panel.LocalIconPack
import com.example.tvcontrolcenter.ui.panel.LocalIconStyle
import com.example.tvcontrolcenter.ui.panel.LocalMenuIcon
import com.example.tvcontrolcenter.ui.panel.LocalPhosphorWeight
import com.example.tvcontrolcenter.ui.panel.TileIcon
import com.example.tvcontrolcenter.ui.panel.appLabelOf
import com.example.tvcontrolcenter.ui.panel.gridHeight
import com.example.tvcontrolcenter.ui.panel.lookIcon
import com.example.tvcontrolcenter.ui.panel.lookOf
import com.example.tvcontrolcenter.ui.theme.AppDimens
import com.example.tvcontrolcenter.ui.theme.AppPalette
import com.example.tvcontrolcenter.ui.theme.AppType
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import com.example.tvcontrolcenter.ui.theme.ProvideUiScale
import kotlin.math.roundToInt

/**
 * Edit Control Center. The right side is the real panel; pick a control with OK, then size,
 * move or hide it on the left. Hidden controls are listed under "Add controls". Everything
 * saves as you go, and the panel everywhere else follows at once.
 *
 * The one-row-per-setting model is intentional: one focus stop per setting and OK cycles its
 * value. D-pad Left is left free, so the navigation rail is always one press away.
 */
@Composable
fun EditScreen(
    viewModel: ControlCenterViewModel,
    initialDestination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Fits the screen but ignores the user's panel size, so the controls always have room.
    val rows = remember(state.layout) { GridPacker.rowCount(GridPacker.pack(state.layout.visibleItems())) }
    val needed = gridHeight(rows) + PanelDimens.sheetPadding * 2 +
        PanelDimens.headerFor(state.settings.topSize) + state.settings.headerGap.dp + PanelDimens.sheetPadding + 56.dp
    ProvideUiScale(1f, neededHeight = needed) {
        // Lite glass: flat fills and no animation, so this page with its many controls stays quick.
        CompositionLocalProvider(
            LocalLiteGlass provides true,
            LocalInvertFocus provides state.settings.invertFocus,
            LocalEdgeWidth provides state.settings.edgeWidth.dp,
            LocalIconStyle provides state.settings.iconStyle,
            LocalIconPack provides state.settings.iconPack,
            LocalPhosphorWeight provides state.settings.phosphorWeight,
            LocalBluetoothIcon provides state.settings.bluetoothIcon,
            LocalMenuIcon provides state.settings.menuIcon,
        ) {
            EditBody(state, viewModel, initialDestination, onNavigate, onDone)
        }
    }
}

@Composable
private fun EditBody(
    state: ControlCenterUiState,
    vm: ControlCenterViewModel,
    initialDestination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    onDone: () -> Unit,
) {
    val layout = state.layout
    val settings = state.settings
    val placed = remember(layout) { GridPacker.pack(layout.visibleItems()) }
    val pinned = remember(layout) { GridPacker.pinned(layout.visibleItems()) }
    val currentPlaced by rememberUpdatedState(pinned + placed)
    val tileRequesters = remember { TileId.entries.associateWith { FocusRequester() } }
    val firstAction = remember { FocusRequester() }
    var selected by remember { mutableStateOf<TileId?>(null) }
    var returnTo by remember { mutableStateOf<TileId?>(null) }
    // The preview skips wallpaper blur: the box blur is CPU work the editor should not pay for
    // on every setting change. The real panel still applies the saved blur level.
    val previewState = remember(state) {
        if (state.settings.effectiveBlur == 0) state
        else state.copy(settings = state.settings.copy(blurBehind = false, blurLevel = 0))
    }

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
        AppScreenBackground(Modifier.fillMaxSize())
        Row(
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = AppDimens.screenHorizontal,
                    vertical = AppDimens.screenVertical,
                ),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.columnGap),
        ) {
            AppNavigationRail(
                selected = initialDestination,
                onSelect = onNavigate,
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                BasicText(
                    stringResource(initialDestination.label),
                    style = AppType.heading.copy(color = AppPalette.textPrimary),
                    maxLines = 1,
                )
                if (initialDestination == AppDestination.Layout) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        stringResource(R.string.edit_controls_hint),
                        style = AppType.note.copy(color = AppPalette.textSecondary),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Photo pickers live here so they are not re-created when the tab changes.
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

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (initialDestination) {
                        AppDestination.Layout -> {
                            val item = selected?.let { layout.item(it) }
                            AppSettingGroup(title = stringResource(R.string.group_layout)) {
                                if (currentPlaced.isEmpty()) {
                                    AppNote(stringResource(R.string.edit_no_controls))
                                } else {
                                    currentPlaced.forEach { placedTile ->
                                        val control = layout.item(placedTile.id) ?: return@forEach
                                        AppActionRow(
                                            label = stringResource(lookOf(control.id).label),
                                            value = stringResource(sizeLabel(control.size)),
                                            selected = selected == control.id,
                                            onClick = { selected = control.id },
                                            requester = tileRequesters.getValue(control.id),
                                        )
                                    }
                                }
                            }
                            if (item != null) {
                                Spacer(Modifier.height(AppDimens.groupGap))
                                AppSettingGroup(title = stringResource(R.string.group_selected)) {
                                    val sizes = TileSize.entries.filter {
                                        it != TileSize.TOP || item.id == TileId.EDIT || item.id == TileId.PROFILE || item.id == TileId.NOW_PLAYING
                                    }
                                    AppSettingRow(
                                        label = stringResource(R.string.edit_size),
                                        value = stringResource(sizeLabel(item.size)),
                                        requester = firstAction,
                                    ) { d -> vm.setTileSize(item.id, sizes.step(item.size, d)) }
                                    AppActionRow(
                                        label = stringResource(R.string.edit_move_earlier),
                                        onClick = { vm.moveTile(item.id, -1) },
                                    )
                                    AppActionRow(
                                        label = stringResource(R.string.edit_move_later),
                                        onClick = { vm.moveTile(item.id, 1) },
                                    )
                                    AppActionRow(
                                        label = stringResource(R.string.edit_hide),
                                        onClick = {
                                            vm.setTileVisible(item.id, false)
                                            returnTo = currentPlaced.firstOrNull { it.id != item.id }?.id
                                            selected = null
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup(title = stringResource(R.string.edit_add)) {
                                val hidden = layout.hiddenItems()
                                if (hidden.isEmpty()) {
                                    AppNote(stringResource(R.string.edit_nothing_hidden))
                                } else {
                                    hidden.chunked(3).forEach { row ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            row.forEach { hiddenItem ->
                                                AddControlCard(
                                                    id = hiddenItem.id,
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    vm.setTileVisible(hiddenItem.id, true)
                                                    selected = hiddenItem.id
                                                }
                                            }
                                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup {
                                AppActionRow(
                                    label = stringResource(R.string.edit_reset),
                                    onClick = {
                                        vm.resetLayout()
                                        selected = null
                                    },
                                    destructive = true,
                                )
                            }
                        }

                        AppDestination.Style -> {
                            AppSettingGroup(title = stringResource(R.string.group_appearance)) {
                                AppSettingRow(
                                    label = stringResource(R.string.edit_wallpaper),
                                    value = stringResource(wallpaperLabel(settings.wallpaper)),
                                ) { d ->
                                    vm.updateSettings { it.copy(wallpaper = Wallpaper.entries.step(it.wallpaper, d)) }
                                }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_frost),
                                    value = stringResource(frostLabel(settings.frost)),
                                ) { d ->
                                    vm.updateSettings { it.copy(frost = FrostStyle.entries.step(it.frost, d)) }
                                }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_panel_size),
                                    value = "${(settings.uiScale * 100).roundToInt()}%",
                                ) { d -> vm.setUiScale(UiScaleSteps.step(settings.uiScale, d)) }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_text_size),
                                    value = "${(settings.textScale * 100).roundToInt()}%",
                                ) { d ->
                                    vm.updateSettings { it.copy(textScale = TextScaleSteps.step(it.textScale, d)) }
                                }
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup(title = stringResource(R.string.group_layout)) {
                                AppSettingRow(
                                    label = stringResource(R.string.edit_position),
                                    value = TopOffsetSteps.indexOf(settings.topOffset).let {
                                        if (it <= 0) stringResource(R.string.edit_pos_top) else "$it"
                                    },
                                ) { d ->
                                    vm.updateSettings { it.copy(topOffset = TopOffsetSteps.step(it.topOffset, d)) }
                                }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_header_gap),
                                    value = "${settings.headerGap}",
                                ) { d ->
                                    vm.updateSettings { it.copy(headerGap = HeaderGapSteps.step(it.headerGap, d)) }
                                }
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup(title = stringResource(R.string.group_windows)) {
                                val motions = listOf(
                                    WindowMotion.FROM_CONTROL to R.string.motion_from_control,
                                    WindowMotion.MID_AIR to R.string.motion_mid_air,
                                    WindowMotion.OFF to R.string.motion_none,
                                )
                                AppSettingRow(
                                    label = stringResource(R.string.edit_window_motion),
                                    value = stringResource(motions.first { it.first == settings.windowMotion }.second),
                                ) { d ->
                                    val at = motions.indexOfFirst { it.first == settings.windowMotion }
                                    vm.updateSettings {
                                        it.copy(windowMotion = motions[Math.floorMod(at + d, motions.size)].first)
                                    }
                                }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_card),
                                    value = onOff(settings.card),
                                ) { vm.updateSettings { it.copy(card = !it.card) } }
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup(title = stringResource(R.string.group_glass)) {
                                val edge = settings.edgeWidth
                                AppSettingRow(
                                    label = stringResource(R.string.edit_edge),
                                    value = when {
                                        edge <= 0f -> stringResource(R.string.edge_none)
                                        // Whole dp reads as "2", not "2.0"; the finer steps keep their decimals.
                                        edge % 1f == 0f -> "${edge.toInt()}"
                                        else -> "$edge"
                                    },
                                ) { d ->
                                    vm.updateSettings { it.copy(edgeWidth = EdgeWidthSteps.step(it.edgeWidth, d)) }
                                }
                                val iconNames = listOf(
                                    IconStyle.FILLED to R.string.icons_filled,
                                    IconStyle.OUTLINED to R.string.icons_outlined,
                                    IconStyle.ROUNDED to R.string.icons_rounded,
                                )
                                AppSettingRow(
                                    label = stringResource(R.string.edit_icons),
                                    value = stringResource(iconNames.first { it.first == settings.iconStyle }.second),
                                ) { d ->
                                    val at = iconNames.indexOfFirst { it.first == settings.iconStyle }
                                    vm.updateSettings {
                                        it.copy(iconStyle = iconNames[Math.floorMod(at + d, iconNames.size)].first)
                                    }
                                }
                                val iconPacks = listOf(
                                    IconPack.MATERIAL to R.string.pack_system,
                                    IconPack.PHOSPHOR to R.string.pack_phosphor,
                                )
                                AppSettingRow(
                                    label = stringResource(R.string.edit_icon_pack),
                                    value = stringResource(iconPacks.first { it.first == settings.iconPack }.second),
                                ) { d ->
                                    val at = iconPacks.indexOfFirst { it.first == settings.iconPack }
                                    vm.updateSettings {
                                        it.copy(iconPack = iconPacks[Math.floorMod(at + d, iconPacks.size)].first)
                                    }
                                }
                                if (settings.iconPack == IconPack.PHOSPHOR) {
                                    val weightNames = listOf(
                                        PhosphorWeight.THIN to R.string.weight_thin,
                                        PhosphorWeight.LIGHT to R.string.weight_light,
                                        PhosphorWeight.REGULAR to R.string.weight_regular,
                                        PhosphorWeight.BOLD to R.string.weight_bold,
                                        PhosphorWeight.FILL to R.string.weight_fill,
                                        PhosphorWeight.DUOTONE to R.string.weight_duotone,
                                    )
                                    AppSettingRow(
                                        label = stringResource(R.string.edit_phosphor_weight),
                                        value = stringResource(weightNames.first { it.first == settings.phosphorWeight }.second),
                                    ) { d ->
                                        val at = weightNames.indexOfFirst { it.first == settings.phosphorWeight }
                                        vm.updateSettings {
                                            it.copy(phosphorWeight = weightNames[Math.floorMod(at + d, weightNames.size)].first)
                                        }
                                    }
                                }
                                val bluetoothIcons = listOf(
                                    BluetoothIcon.STANDARD to R.string.bt_icon_standard,
                                    BluetoothIcon.HEADPHONES to R.string.bt_icon_headphones,
                                )
                                AppSettingRow(
                                    label = stringResource(R.string.edit_bluetooth_icon),
                                    value = stringResource(bluetoothIcons.first { it.first == settings.bluetoothIcon }.second),
                                ) { d ->
                                    val at = bluetoothIcons.indexOfFirst { it.first == settings.bluetoothIcon }
                                    vm.updateSettings {
                                        it.copy(bluetoothIcon = bluetoothIcons[Math.floorMod(at + d, bluetoothIcons.size)].first)
                                    }
                                }
                                val menuIcons = listOf(
                                    MenuIcon.BARS to R.string.menu_icon_bars,
                                    MenuIcon.TOGGLES to R.string.menu_icon_toggles,
                                )
                                AppSettingRow(
                                    label = stringResource(R.string.edit_menu_icon),
                                    value = stringResource(menuIcons.first { it.first == settings.menuIcon }.second),
                                ) { d ->
                                    val at = menuIcons.indexOfFirst { it.first == settings.menuIcon }
                                    vm.updateSettings {
                                        it.copy(menuIcon = menuIcons[Math.floorMod(at + d, menuIcons.size)].first)
                                    }
                                }
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup(title = stringResource(R.string.group_behaviour)) {
                                AppSettingRow(
                                    label = stringResource(R.string.edit_invert),
                                    value = stringResource(
                                        if (settings.invertFocus) R.string.invert_yes else R.string.invert_no,
                                    ),
                                ) { vm.updateSettings { it.copy(invertFocus = !it.invertFocus) } }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_auto_ink),
                                    value = onOff(settings.autoInk),
                                ) { vm.updateSettings { it.copy(autoInk = !it.autoInk) } }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_active_highlight),
                                    value = onOff(settings.activeHighlight),
                                ) { vm.updateSettings { it.copy(activeHighlight = !it.activeHighlight) } }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_sound_cues),
                                    value = onOff(settings.soundCues),
                                ) { vm.updateSettings { it.copy(soundCues = !it.soundCues) } }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_volume_step),
                                    value = "${settings.volumeStep}%",
                                ) { d ->
                                    vm.updateSettings { it.copy(volumeStep = VolumeStepSteps.step(it.volumeStep, d)) }
                                }
                            }
                        }

                        AppDestination.TopBar -> {
                            AppSettingGroup(title = stringResource(R.string.group_topbar)) {
                                listOf(
                                    TileId.EDIT to R.string.edit_btn_menu,
                                    TileId.PROFILE to R.string.edit_btn_profile,
                                ).forEach { (id, name) ->
                                    val on = layout.item(id)?.visible == true
                                    AppSettingRow(
                                        label = stringResource(name),
                                        value = onOff(on),
                                    ) { vm.setTileVisible(id, !on) }
                                }
                                val music = layout.item(TileId.NOW_PLAYING)
                                val musicOn = music?.visible == true && music.size == TileSize.TOP
                                AppSettingRow(
                                    label = stringResource(R.string.edit_btn_music),
                                    value = onOff(musicOn),
                                ) {
                                    if (musicOn) vm.setTileSize(TileId.NOW_PLAYING, TileSize.FULL)
                                    else {
                                        vm.setTileVisible(TileId.NOW_PLAYING, true)
                                        vm.setTileSize(TileId.NOW_PLAYING, TileSize.TOP)
                                    }
                                }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_top_size),
                                    value = "${settings.topSize}",
                                ) { d ->
                                    vm.updateSettings { it.copy(topSize = TopSizeSteps.step(it.topSize, d)) }
                                }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_top_flat),
                                    value = onOff(settings.topBorderless),
                                ) { vm.updateSettings { it.copy(topBorderless = !it.topBorderless) } }
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup(title = stringResource(R.string.group_behaviour)) {
                                AppSettingRow(
                                    label = stringResource(R.string.edit_menu_home),
                                    value = onOff(settings.menuIsHome),
                                ) { vm.updateSettings { it.copy(menuIsHome = !it.menuIsHome) } }
                                val musicStyles = listOf(
                                    MusicStyle.COMPACT to R.string.music_compact,
                                    MusicStyle.BIG to R.string.music_big,
                                )
                                AppSettingRow(
                                    label = stringResource(R.string.edit_music_style),
                                    value = stringResource(musicStyles.first { it.first == settings.musicStyle }.second),
                                ) { d ->
                                    val at = musicStyles.indexOfFirst { it.first == settings.musicStyle }
                                    vm.updateSettings {
                                        it.copy(musicStyle = musicStyles[Math.floorMod(at + d, musicStyles.size)].first)
                                    }
                                }
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup(title = stringResource(R.string.group_profile)) {
                                AppActionRow(
                                    label = stringResource(R.string.edit_photo_choose),
                                    onClick = {
                                        try {
                                            openDoc.launch(arrayOf("image/*"))
                                        } catch (_: ActivityNotFoundException) {
                                            try {
                                                getContent.launch("image/*")
                                            } catch (_: ActivityNotFoundException) {
                                                Toast.makeText(context, R.string.edit_photo_no_picker, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    showChevron = true,
                                )
                                AppActionRow(
                                    label = stringResource(R.string.edit_photo_remove),
                                    onClick = {
                                        ProfilePhoto.clear(context)
                                        vm.updateSettings { it.copy(profilePhotoVersion = 0) }
                                    },
                                )
                                AppSettingRow(
                                    label = stringResource(R.string.edit_photo_color),
                                    value = "${settings.profileColor.mod(AvatarColors.size) + 1}",
                                    swatch = AvatarColors[settings.profileColor.mod(AvatarColors.size)],
                                ) { d ->
                                    vm.updateSettings {
                                        it.copy(profileColor = Math.floorMod(it.profileColor + d, AvatarColors.size))
                                    }
                                }
                            }
                        }

                        AppDestination.Apps -> {
                            // The slot the next app press fills; 0 is the first.
                            var slot by remember { mutableStateOf(0) }
                            AppSettingGroup(title = stringResource(R.string.group_slots)) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    AppSlots.forEachIndexed { index, _ ->
                                        val name = appLabelOf(settings.appSlots.getOrNull(index))
                                            ?: stringResource(R.string.edit_app_empty)
                                        AppSlotCard(
                                            index = index + 1,
                                            label = name,
                                            selected = slot == index,
                                            onClick = { slot = index },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                AppActionRow(
                                    label = stringResource(R.string.edit_app_clear),
                                    onClick = {
                                        vm.updateSettings { it.copy(appSlots = it.appSlots.withSlot(slot, null)) }
                                        vm.setTileVisible(AppSlots[slot], false)
                                    },
                                )
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup(title = stringResource(R.string.group_apps)) {
                                val apps = remember { vm.installedApps() }
                                apps.forEach { app ->
                                    AppActionRow(
                                        label = app.label,
                                        onClick = {
                                            vm.updateSettings {
                                                it.copy(appSlots = it.appSlots.withSlot(slot, app.packageName))
                                            }
                                            vm.setTileVisible(AppSlots[slot], true)
                                        },
                                        selected = settings.appSlots.getOrNull(slot) == app.packageName,
                                    )
                                }
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup(title = stringResource(R.string.group_diagnostics)) {
                                AppSettingRow(
                                    label = stringResource(R.string.edit_capture_log),
                                    value = onOff(settings.activityLog),
                                ) { vm.updateSettings { it.copy(activityLog = !it.activityLog) } }
                            }
                        }

                        AppDestination.Blur -> {
                            AppSettingGroup(title = stringResource(R.string.group_blur)) {
                                val blurNames = listOf(
                                    R.string.blur_off,
                                    R.string.blur_light,
                                    R.string.blur_medium,
                                    R.string.blur_strong,
                                    R.string.blur_max,
                                ).map { stringResource(it) }
                                AppSettingRow(
                                    label = stringResource(R.string.edit_blur_level),
                                    value = blurNames[settings.effectiveBlur],
                                ) { d ->
                                    val level = Math.floorMod(settings.effectiveBlur + d, blurNames.size)
                                    vm.updateSettings { it.copy(blurBehind = level > 0, blurLevel = level) }
                                }
                                val methods = listOf(
                                    BlurMethod.AUTO to R.string.method_auto,
                                    BlurMethod.SCREENSHOT to R.string.method_screenshot,
                                    BlurMethod.WINDOW to R.string.method_window,
                                )
                                AppSettingRow(
                                    label = stringResource(R.string.edit_blur_method),
                                    value = stringResource(methods.first { it.first == settings.blurMethod }.second),
                                ) { d ->
                                    val at = methods.indexOfFirst { it.first == settings.blurMethod }
                                    vm.updateSettings {
                                        it.copy(blurMethod = methods[Math.floorMod(at + d, methods.size)].first)
                                    }
                                }
                            }
                            Spacer(Modifier.height(AppDimens.groupGap))
                            AppSettingGroup(title = stringResource(R.string.group_capture)) {
                                AppSettingRow(
                                    label = stringResource(R.string.edit_patient),
                                    value = onOff(settings.patientCapture),
                                ) { vm.updateSettings { it.copy(patientCapture = !it.patientCapture) } }
                                val status = BackdropCapture.status()
                                AppNote(
                                    stringResource(
                                        when (status) {
                                            BackdropCapture.Status.READY -> R.string.blur_status_ready
                                            BackdropCapture.Status.NEEDS_SERVICE -> R.string.blur_status_service
                                            BackdropCapture.Status.NEEDS_ANDROID_11 -> R.string.blur_status_old
                                        },
                                    ),
                                    color = if (status == BackdropCapture.Status.READY) {
                                        AppPalette.success
                                    } else {
                                        AppPalette.warning
                                    },
                                )
                            }
                        }

                        AppDestination.Home -> Unit
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            // Passive preview over a sharp wallpaper. It never takes focus; the list owns the D-pad.
            // The default panel has six grid rows, taller than this pane, so the preview is scaled
            // down as a whole to fit rather than clipped.
            BoxWithConstraints(
                modifier = Modifier.width(AppDimens.previewWidth).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val previewRows = GridPacker.rowCount(placed)
                val naturalHeight = gridHeight(previewRows) +
                    PanelDimens.headerFor(settings.topSize) +
                    settings.headerGap.dp +
                    PanelDimens.sheetPadding * 2 + 26.dp
                val fit = (maxHeight.value / naturalHeight.value.coerceAtLeast(1f))
                    .coerceIn(0.70f, 1f)
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density * fit, density.fontScale),
                ) {
                    AppPreview(
                        label = stringResource(R.string.home_preview),
                        note = "",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LivePanel(
                            state = previewState,
                            grid = placed,
                            pinned = pinned,
                            requesters = tileRequesters,
                            selected = selected,
                            interactive = false,
                            onActivate = { selected = it },
                            showSheet = settings.card,
                        )
                    }
                }
            }
        }
    }
}

private fun wallpaperLabel(style: Wallpaper): Int = when (style) {
    Wallpaper.BOXES -> R.string.wall_boxes
    Wallpaper.AURORA -> R.string.wall_aurora
    Wallpaper.SUNSET -> R.string.wall_sunset
    Wallpaper.FOREST -> R.string.wall_forest
    Wallpaper.WAVES -> R.string.wall_waves
    Wallpaper.NEBULA -> R.string.wall_nebula
    Wallpaper.GRID -> R.string.wall_grid
    Wallpaper.PLAIN -> R.string.wall_plain
}

private fun frostLabel(style: FrostStyle): Int = when (style) {
    FrostStyle.SMOKE -> R.string.frost_smoke
    FrostStyle.MILKY -> R.string.frost_milky
    FrostStyle.CLEAR -> R.string.frost_clear
    FrostStyle.LENS -> R.string.frost_lens
    FrostStyle.OCEAN -> R.string.frost_ocean
    FrostStyle.AMETHYST -> R.string.frost_amethyst
    FrostStyle.EMBER -> R.string.frost_ember
    FrostStyle.FOREST -> R.string.frost_forest
    FrostStyle.ROSE -> R.string.frost_rose
    FrostStyle.MIDNIGHT -> R.string.frost_midnight
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
private fun onOff(on: Boolean): String = stringResource(if (on) R.string.state_on else R.string.state_off)

private fun <T> List<T>.step(current: T, delta: Int): T {
    val i = indexOf(current).let { if (it < 0) 0 else it }
    return this[Math.floorMod(i + delta, size)]
}

/** A copy of the slot list with [index] set to [value]. */
private fun List<String?>.withSlot(index: Int, value: String?): List<String?> =
    toMutableList().also { if (index in it.indices) it[index] = value }

@Composable
private fun AddControlCard(id: TileId, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = lookOf(id)
    val label = stringResource(look.label)
    AppControlCard(
        label = label,
        onClick = onClick,
        modifier = modifier,
        leading = { TileIcon(lookIcon(id), 18.dp) },
    )
}
