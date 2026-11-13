package com.example.tvcontrolcenter.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvcontrolcenter.domain.model.ActionResult
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.domain.model.NetworkKind
import com.example.tvcontrolcenter.domain.model.AppNotification
import com.example.tvcontrolcenter.domain.model.NowPlaying
import com.example.tvcontrolcenter.domain.model.PanelEvent
import com.example.tvcontrolcenter.domain.model.PanelLayout
import com.example.tvcontrolcenter.domain.model.PanelSettings
import com.example.tvcontrolcenter.domain.model.SystemStatus
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileSize
import com.example.tvcontrolcenter.domain.model.LauncherApp
import com.example.tvcontrolcenter.domain.model.TileState
import com.example.tvcontrolcenter.domain.model.appSlot
import com.example.tvcontrolcenter.domain.repository.FocusMemory
import com.example.tvcontrolcenter.domain.repository.LayoutRepository
import com.example.tvcontrolcenter.domain.repository.NotificationSource
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
    /** What is in the shade right now, newest first. */
    val notifications: List<AppNotification> = emptyList(),
    /** Whether the notification listener is granted; without it the list cannot be read. */
    val notificationAccess: Boolean = false,
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
    private val notificationSource: NotificationSource,
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

    private val caffeine = MutableStateFlow(false)

    val uiState: StateFlow<ControlCenterUiState> = combine(
        live,
        layoutRepository.layout,
        layoutRepository.settings,
        caffeine,
        notificationSource.notifications,
    ) { base, layout, settings, awake, notifications ->
        base.copy(
            tiles = base.tiles + (TileId.CAFFEINE to TileState(TileId.CAFFEINE, active = awake)),
            layout = layout,
            settings = settings,
            notifications = notifications,
            notificationAccess = notificationSource.hasAccess(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ControlCenterUiState())

    /** Called each time the panel appears. */
    fun onOpened() {
        nowPlayingSource.refresh()
        notificationSource.refresh()
        caffeine.value = actions.caffeineOn()
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
            TileId.SCREENSAVER -> handle(actions.startScreensaver(), dismissOnDone = true, tile = id)
            TileId.APPS -> handle(actions.openAppsSettings(), dismissOnDone = true, tile = id)
            TileId.HOME -> handle(actions.goHome(), dismissOnDone = true, tile = id)
            TileId.RECENTS -> handle(actions.showRecents(), dismissOnDone = true, tile = id)
            TileId.SCREENSHOT -> handle(actions.takeScreenshot(), dismissOnDone = true, tile = id)
            TileId.NOTIFICATIONS -> handle(actions.showNotifications(), dismissOnDone = true, tile = id)
            // The panel host opens the Input page itself.
            TileId.INPUT -> Unit
            TileId.CAFFEINE -> {
                handle(actions.toggleCaffeine(), tile = id)
                caffeine.value = actions.caffeineOn()
            }
            TileId.EDIT -> _events.tryEmit(PanelEvent.OpenEditor)
            TileId.PROFILE -> handle(actions.openSettings(), dismissOnDone = true, tile = id)
            TileId.APP_1, TileId.APP_2, TileId.APP_3, TileId.APP_4 -> {
                val packageName = state.settings.appSlots.getOrNull(id.appSlot ?: 0)
                if (packageName == null) _events.tryEmit(PanelEvent.Unavailable(id))
                else handle(actions.openApp(packageName), dismissOnDone = true, tile = id)
            }
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

    fun updateSettings(transform: (PanelSettings) -> PanelSettings) = layoutRepository.updateSettings(transform)

    /** Positive steps raise the volume. Shows the new value at once, then lets polling take over. */
    fun onVolumeStep(deltaPercent: Int) {
        val next = (uiState.value.volumePercent + deltaPercent).coerceIn(0, 100)
        localVolume.value = next
        if (next > 0) localMute.value = false // raising the volume unmutes the stream
        actions.setVolumePercent(next)
        scheduleOverrideReset()
    }

    /**
     * The Now Playing button in the top bar was pressed. True when the music page may open.
     * Without media access it asks for it instead, and the page stays closed.
     */
    fun onMusicPageRequested(): Boolean {
        if (nowPlayingSource.hasAccess()) return true
        _events.tryEmit(PanelEvent.NeedsCapability(Capability.NOTIFICATION_LISTENER))
        return false
    }

    /**
     * The Notifications control was pressed. True when the panel may open its list; without the
     * listener grant it asks for it instead and the list stays closed.
     */
    fun onNotificationsPageRequested(): Boolean {
        if (!notificationSource.hasAccess()) {
            _events.tryEmit(PanelEvent.NeedsCapability(Capability.NOTIFICATION_LISTENER))
            return false
        }
        // Read the shade as the list opens, so it starts from what is really there.
        notificationSource.refresh()
        return true
    }

    /**
     * A notification was pressed: fire its own intent while the panel's window is still up, so the
     * target app may come forward, then close the panel over it.
     */
    fun onNotificationOpened(key: String) {
        if (notificationSource.open(key)) _events.tryEmit(PanelEvent.Dismiss)
        else _events.tryEmit(PanelEvent.Unavailable(TileId.NOTIFICATIONS))
    }

    fun onClearNotifications() = notificationSource.clearAll()

    /** The list's access row: the same grant the tile asks for. */
    fun onOpenNotificationAccess() {
        _events.tryEmit(PanelEvent.NeedsCapability(Capability.NOTIFICATION_LISTENER))
    }

    /**
     * The music button pressed on the open music page: hand over to whatever is playing. Nothing to
     * hand over to says so, rather than doing nothing at all.
     */
    fun onOpenNowPlayingApp() {
        val packageName = uiState.value.nowPlaying?.packageName
        if (packageName == null) _events.tryEmit(PanelEvent.Unavailable(TileId.NOW_PLAYING))
        else handle(actions.openApp(packageName), dismissOnDone = true, tile = TileId.NOW_PLAYING)
    }

    fun wifiEnabled() = actions.wifiEnabled()
    fun bluetoothEnabled() = actions.bluetoothEnabled()
    fun wifiName() = actions.wifiNetworkName()
    fun wifiIpAddress() = actions.wifiIpAddress()
    fun pairedBluetooth() = actions.pairedBluetoothDevices()
    fun tvInputs() = actions.tvInputs()

    /** Every app with a launcher entry, for the editor's Apps tab. Read once per visit. */
    fun installedApps(): List<LauncherApp> = actions.installedApps()
    fun onSetWifi(on: Boolean) = handle(actions.setWifiEnabled(on), tile = TileId.WIFI)
    fun onSetBluetooth(on: Boolean) = handle(actions.setBluetoothEnabled(on), tile = TileId.BLUETOOTH)
    fun onDismissNotification(key: String) {
        notificationSource.dismiss(key)
        notificationSource.refresh()
    }
    fun onOpenWifiSettings() = handle(actions.openWifiSettings(), dismissOnDone = true, tile = TileId.WIFI)
    fun onOpenBluetoothSettings() = handle(actions.openBluetoothSettings(), dismissOnDone = true, tile = TileId.BLUETOOTH)
    fun onSwitchInput(id: String) = handle(actions.switchInput(id), dismissOnDone = true, tile = TileId.INPUT)

    /** Holding OK on a control. Profile opens the editor. */
    fun onTileLongPressed(id: TileId) {
        if (id == TileId.PROFILE) _events.tryEmit(PanelEvent.OpenEditor)
    }

    fun onPlayPause() {
        if (nowPlayingSource.hasAccess()) nowPlayingSource.togglePlayPause()
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
