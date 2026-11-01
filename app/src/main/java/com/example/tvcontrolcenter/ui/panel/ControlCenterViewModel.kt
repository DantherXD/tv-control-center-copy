package com.example.tvcontrolcenter.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvcontrolcenter.domain.model.ActionResult
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.domain.model.NetworkKind
import com.example.tvcontrolcenter.domain.model.NowPlaying
import com.example.tvcontrolcenter.domain.model.PanelEvent
import com.example.tvcontrolcenter.domain.model.PanelLayout
import com.example.tvcontrolcenter.domain.model.PanelSettings
import com.example.tvcontrolcenter.domain.model.SystemStatus
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileSize
import com.example.tvcontrolcenter.domain.model.TileState
import com.example.tvcontrolcenter.domain.repository.FocusMemory
import com.example.tvcontrolcenter.domain.repository.LayoutRepository
import com.example.tvcontrolcenter.domain.repository.NowPlayingSource
import com.example.tvcontrolcenter.domain.repository.SleepTimerRepository
import com.example.tvcontrolcenter.domain.repository.SystemActions
import com.example.tvcontrolcenter.domain.repository.SystemStatusSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControlCenterUiState(
    val tiles: Map<TileId, TileState> = emptyMap(),
    val status: SystemStatus = SystemStatus(),
    val nowPlaying: NowPlaying? = null,
    val volumePercent: Int = 0,
    val muted: Boolean = false,
    val timerMinutes: Int? = null,
    val layout: PanelLayout = PanelLayout.Default,
    val settings: PanelSettings = PanelSettings(),
) {
    val hasNowPlaying: Boolean get() = nowPlaying != null
}

/**
 * Holds all panel state as one immutable [ControlCenterUiState] and turns tile
 * activations into system actions. The UI never touches Android services.
 */
class ControlCenterViewModel @Inject constructor(
    statusSource: SystemStatusSource,
    private val actions: SystemActions,
    private val nowPlayingSource: NowPlayingSource,
    private val sleepTimer: SleepTimerRepository,
    private val focusMemory: FocusMemory,
    private val layoutRepository: LayoutRepository,
) : ViewModel() {

    // Volume and mute show the user's change at once; polling takes over after a moment.
    private val localVolume = MutableStateFlow<Int?>(null)
    private val localMute = MutableStateFlow<Boolean?>(null)
    private var overrideJob: Job? = null

    private val _events = MutableSharedFlow<PanelEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PanelEvent> = _events.asSharedFlow()

    private val live = combine(
        statusSource.status,
        nowPlayingSource.nowPlaying,
        sleepTimer.remainingMinutes,
        localVolume,
        localMute,
    ) { status, playing, timer, overrideVolume, overrideMute ->
        val tiles = listOf(
            TileState(TileId.POWER),
            TileState(TileId.WIFI, active = status.network != NetworkKind.NONE),
            TileState(TileId.DND, active = status.dndEnabled),
            TileState(TileId.BLUETOOTH, active = status.bluetoothEnabled, detail = status.audioDeviceName),
            TileState(TileId.SLEEP_TIMER, active = timer != null, detail = timer?.let { "$it min" }),
            TileState(TileId.GAMEPAD),
            TileState(TileId.ACCESSIBILITY),
            TileState(TileId.LOCK),
            TileState(TileId.SEARCH),
        ).associateBy { it.id }
        ControlCenterUiState(
            tiles = tiles,
            status = status,
            nowPlaying = playing,
            volumePercent = overrideVolume ?: status.volumePercent,
            muted = overrideMute ?: status.muted,
            timerMinutes = timer,
        )
    }

    val uiState: StateFlow<ControlCenterUiState> = combine(
        live,
        layoutRepository.layout,
        layoutRepository.settings,
    ) { base, layout, settings ->
        base.copy(layout = layout, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ControlCenterUiState())

    /** Called each time the panel appears. */
    fun onOpened() {
        nowPlayingSource.refresh()
    }

    /** Tile that held focus last time. The panel falls back to its first tile if it is not shown. */
    fun rememberedFocus(): TileId = focusMemory.get()

    fun onFocused(id: TileId) {
        focusMemory.set(id)
    }

    fun onTileActivated(id: TileId) {
        val state = uiState.value
        when (id) {
            TileId.POWER -> handle(actions.powerOff(), dismissOnDone = true)
            TileId.WIFI -> handle(actions.openWifiSettings(), dismissOnDone = true, tile = id)
            TileId.DND -> handle(actions.toggleDoNotDisturb(state.status.dndEnabled), tile = id)
            TileId.BLUETOOTH -> handle(actions.openBluetoothSettings(), dismissOnDone = true, tile = id)
            TileId.SLEEP_TIMER -> sleepTimer.cycle()
            TileId.GAMEPAD -> handle(actions.openGameControllerSettings(), dismissOnDone = true, tile = id)
            TileId.ACCESSIBILITY -> handle(actions.openAccessibilitySettings(), dismissOnDone = true, tile = id)
            TileId.LOCK -> handle(actions.sleepScreen(), dismissOnDone = true, tile = id)
            TileId.SEARCH -> handle(actions.openSearch(), dismissOnDone = true, tile = id)
            TileId.NOW_PLAYING -> {
                if (nowPlayingSource.hasAccess()) nowPlayingSource.togglePlayPause()
                else _events.tryEmit(PanelEvent.NeedsCapability(Capability.NOTIFICATION_LISTENER))
            }
            TileId.VOLUME -> toggleMute(state.muted)

            TileId.SETTINGS -> handle(actions.openSettings(), dismissOnDone = true, tile = id)
            TileId.DISPLAY -> handle(actions.openDisplaySettings(), dismissOnDone = true, tile = id)
            TileId.SOUND -> handle(actions.openSoundSettings(), dismissOnDone = true, tile = id)
            TileId.CAPTIONS -> handle(actions.openCaptionSettings(), dismissOnDone = true, tile = id)
            TileId.SCREENSAVER -> handle(actions.openScreensaverSettings(), dismissOnDone = true, tile = id)
            TileId.APPS -> handle(actions.openAppsSettings(), dismissOnDone = true, tile = id)
            TileId.HOME -> handle(actions.goHome(), dismissOnDone = true, tile = id)
            TileId.RECENTS -> handle(actions.showRecents(), dismissOnDone = true, tile = id)
            TileId.SCREENSHOT -> handle(actions.takeScreenshot(), dismissOnDone = true, tile = id)
            TileId.NOTIFICATIONS -> handle(actions.showNotifications(), dismissOnDone = true, tile = id)
            TileId.EDIT -> _events.tryEmit(PanelEvent.OpenEditor)
        }
    }

    // Layout editing. The repository persists and every open panel updates at once.
    fun resizeTileBy(id: TileId, delta: Int) = layoutRepository.update { it.resizeBy(id, delta) }

    fun setTileSize(id: TileId, size: TileSize) = layoutRepository.update { it.resize(id, size) }

    fun moveTile(id: TileId, delta: Int) = layoutRepository.update { it.move(id, delta) }

    fun setTileVisible(id: TileId, visible: Boolean) = layoutRepository.update { it.setVisible(id, visible) }

    fun resetLayout() = layoutRepository.resetLayout()

    fun setUiScale(scale: Float) = layoutRepository.setUiScale(scale)

    fun setBlurBehind(enabled: Boolean) = layoutRepository.setBlurBehind(enabled)

    /** Positive steps raise the volume. Shows the new value at once, then lets polling take over. */
    fun onVolumeStep(deltaPercent: Int) {
        val next = (uiState.value.volumePercent + deltaPercent).coerceIn(0, 100)
        localVolume.value = next
        if (next > 0) localMute.value = false // raising the volume unmutes the stream
        actions.setVolumePercent(next)
        scheduleOverrideReset()
    }

    fun onSkipNext() = nowPlayingSource.skipNext()

    fun onSkipPrevious() = nowPlayingSource.skipPrevious()

    private fun toggleMute(currentlyMuted: Boolean) {
        val result = actions.toggleMute()
        if (result == ActionResult.Done) {
            localMute.value = !currentlyMuted
            scheduleOverrideReset()
        } else {
            handle(result, tile = TileId.VOLUME)
        }
    }

    private fun scheduleOverrideReset() {
        overrideJob?.cancel()
        overrideJob = viewModelScope.launch {
            delay(OVERRIDE_MS)
            localVolume.value = null
            localMute.value = null
        }
    }

    private fun handle(result: ActionResult, dismissOnDone: Boolean = false, tile: TileId = TileId.POWER) {
        when (result) {
            ActionResult.Done -> if (dismissOnDone) _events.tryEmit(PanelEvent.Dismiss)
            is ActionResult.NeedsCapability -> _events.tryEmit(PanelEvent.NeedsCapability(result.capability))
            ActionResult.Unavailable -> _events.tryEmit(PanelEvent.Unavailable(tile))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val OVERRIDE_MS = 2_500L
    }
}
