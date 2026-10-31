package com.example.tvcontrolcenter.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvcontrolcenter.domain.model.ActionResult
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.domain.model.NetworkKind
import com.example.tvcontrolcenter.domain.model.NowPlaying
import com.example.tvcontrolcenter.domain.model.PanelEvent
import com.example.tvcontrolcenter.domain.model.SystemStatus
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileState
import com.example.tvcontrolcenter.domain.repository.FocusMemory
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
    val timerMinutes: Int? = null,
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
) : ViewModel() {

    private val localVolume = MutableStateFlow<Int?>(null)
    private var volumeJob: Job? = null

    private val _events = MutableSharedFlow<PanelEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PanelEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ControlCenterUiState> = combine(
        statusSource.status,
        nowPlayingSource.nowPlaying,
        sleepTimer.remainingMinutes,
        localVolume,
    ) { status, playing, timer, overrideVolume ->
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
            timerMinutes = timer,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ControlCenterUiState())

    /** Called each time the panel appears. */
    fun onOpened() {
        nowPlayingSource.refresh()
    }

    /** Tile that should hold focus when the panel appears. Reads live memory, never a cached value. */
    fun restoredFocus(): TileId = FocusGraph.restore(focusMemory.get(), uiState.value.hasNowPlaying)

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
            TileId.VOLUME -> Unit // adjusted with left/right, see onVolumeStep
        }
    }

    /** Positive steps raise the volume. Shows the new value at once, then lets polling take over. */
    fun onVolumeStep(deltaPercent: Int) {
        val next = (uiState.value.volumePercent + deltaPercent).coerceIn(0, 100)
        localVolume.value = next
        actions.setVolumePercent(next)
        volumeJob?.cancel()
        volumeJob = viewModelScope.launch {
            delay(VOLUME_OVERRIDE_MS)
            localVolume.value = null
        }
    }

    fun onSkipNext() = nowPlayingSource.skipNext()

    fun onSkipPrevious() = nowPlayingSource.skipPrevious()

    private fun handle(result: ActionResult, dismissOnDone: Boolean = false, tile: TileId = TileId.POWER) {
        when (result) {
            ActionResult.Done -> if (dismissOnDone) _events.tryEmit(PanelEvent.Dismiss)
            is ActionResult.NeedsCapability -> _events.tryEmit(PanelEvent.NeedsCapability(result.capability))
            ActionResult.Unavailable -> _events.tryEmit(PanelEvent.Unavailable(tile))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val VOLUME_OVERRIDE_MS = 2_500L
    }
}
