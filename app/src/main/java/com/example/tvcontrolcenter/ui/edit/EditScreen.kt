package com.example.tvcontrolcenter.ui.edit

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.example.tvcontrolcenter.ui.background.AcrylicBackground
import com.example.tvcontrolcenter.ui.glass.LocalFrostStyle
import com.example.tvcontrolcenter.ui.panel.ControlCenterUiState
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.panel.GlassTile
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
        PanelDimens.headerHeight + PanelDimens.headerGap + 56.dp
    ProvideUiScale(1f, neededHeight = needed) {
        EditBody(state, viewModel, onDone)
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
        AcrylicBackground(Modifier.fillMaxSize())
        Row(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText(stringResource(R.string.edit_title), style = PanelType.title.copy(color = GlassTokens.textPrimary))

                val item = selected?.let { layout.item(it) }
                if (item == null) {
                    Hint(stringResource(R.string.edit_hint_pick))
                } else {
                    Section(stringResource(lookOf(item.id).label))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TileSize.entries.filter { it != TileSize.TOP || item.id == TileId.EDIT || item.id == TileId.PROFILE }.forEachIndexed { index, size ->
                            EditChip(
                                label = stringResource(sizeLabel(size)),
                                highlighted = item.size == size,
                                width = 76.dp,
                                requester = if (index == 0) firstAction else null,
                            ) { vm.setTileSize(item.id, size) }
                        }
                    }
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

                Section(stringResource(R.string.edit_add))
                val hidden = layout.hiddenItems()
                if (hidden.isEmpty()) {
                    Hint(stringResource(R.string.edit_nothing_hidden))
                } else {
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

                Section(stringResource(R.string.edit_panel_size))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiScaleSteps.forEach { step ->
                        EditChip("${(step * 100).roundToInt()}%", highlighted = state.settings.uiScale == step) {
                            vm.setUiScale(step)
                        }
                    }
                }

                val settings = state.settings

                Section(stringResource(R.string.edit_position))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopOffsetSteps.forEachIndexed { index, step ->
                        EditChip(
                            if (index == 0) stringResource(R.string.edit_pos_top) else "$index",
                            highlighted = settings.topOffset == step,
                        ) { vm.updateSettings { it.copy(topOffset = step) } }
                    }
                }

                Section(stringResource(R.string.edit_header_buttons))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TileId.EDIT to R.string.edit_btn_menu, TileId.PROFILE to R.string.edit_btn_profile).forEach { (id, name) ->
                        val on = layout.item(id)?.visible == true
                        EditChip(
                            stringResource(name) + ": " + stringResource(if (on) R.string.state_on else R.string.state_off),
                            highlighted = on,
                            width = 150.dp,
                        ) { vm.setTileVisible(id, !on) }
                    }
                }
                Hint(stringResource(R.string.edit_menu_note))

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
                    AvatarColors.forEachIndexed { index, color ->
                        EditChip(stringResource(R.string.edit_photo_color), highlighted = settings.profileColor == index && settings.profilePhotoVersion == 0, width = 40.dp, tint = color) {
                            vm.updateSettings { it.copy(profileColor = index) }
                        }
                    }
                }

                Section(stringResource(R.string.edit_blur_level))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val names = listOf(R.string.blur_off, R.string.blur_light, R.string.blur_medium, R.string.blur_strong, R.string.blur_max)
                    names.forEachIndexed { level, name ->
                        EditChip(stringResource(name), highlighted = settings.effectiveBlur == level) {
                            vm.updateSettings { it.copy(blurBehind = level > 0, blurLevel = level) }
                        }
                    }
                }

                Section(stringResource(R.string.edit_blur_method))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        BlurMethod.AUTO to R.string.method_auto,
                        BlurMethod.SCREENSHOT to R.string.method_screenshot,
                        BlurMethod.WINDOW to R.string.method_window,
                    ).forEach { (method, name) ->
                        EditChip(stringResource(name), highlighted = settings.blurMethod == method, width = 112.dp) {
                            vm.updateSettings { it.copy(blurMethod = method) }
                        }
                    }
                }
                Hint(
                    stringResource(
                        when (BackdropCapture.status()) {
                            BackdropCapture.Status.READY -> R.string.blur_status_ready
                            BackdropCapture.Status.NEEDS_SERVICE -> R.string.blur_status_service
                            BackdropCapture.Status.NEEDS_ANDROID_11 -> R.string.blur_status_old
                        },
                    ),
                )

                Section(stringResource(R.string.edit_frost))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FrostStyle.SMOKE to R.string.frost_smoke,
                        FrostStyle.MILKY to R.string.frost_milky,
                        FrostStyle.CLEAR to R.string.frost_clear,
                    ).forEach { (style, name) ->
                        EditChip(stringResource(name), highlighted = settings.frost == style) {
                            vm.updateSettings { it.copy(frost = style) }
                        }
                    }
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

/** A small glass button. [highlighted] marks the current choice in a group. */
@Composable
private fun EditChip(
    label: String,
    highlighted: Boolean = false,
    width: Dp = 84.dp,
    requester: FocusRequester? = null,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val r = requester ?: remember { FocusRequester() }
    GlassTile(
        label = label,
        cornerRadius = 20.dp,
        focusRequester = r,
        focusProps = {},
        onActivate = onClick,
        modifier = Modifier.size(width, 40.dp),
        active = highlighted,
    ) {
        if (tint != null) Box(Modifier.align(Alignment.Center).size(18.dp).background(tint, CircleShape))
        if (tint == null) BasicText(
            label,
            Modifier.align(Alignment.Center).padding(horizontal = 6.dp),
            style = PanelType.label.copy(color = GlassTokens.textPrimary),
            maxLines = 1,
        )
    }
}

@Composable
private fun AddChip(id: TileId, onClick: () -> Unit) {
    val look = lookOf(id)
    val label = stringResource(look.label)
    GlassTile(
        label = label,
        cornerRadius = 20.dp,
        focusRequester = remember { FocusRequester() },
        focusProps = {},
        onActivate = onClick,
        modifier = Modifier.size(168.dp, 40.dp),
    ) {
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
