package com.example.tvcontrolcenter.fakes

import com.example.tvcontrolcenter.domain.model.ActionResult
import com.example.tvcontrolcenter.domain.model.AppNotification
import com.example.tvcontrolcenter.domain.model.NowPlaying
import com.example.tvcontrolcenter.domain.model.PanelLayout
import com.example.tvcontrolcenter.domain.model.PanelSettings
import com.example.tvcontrolcenter.domain.model.SystemStatus
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.repository.FocusMemory
import com.example.tvcontrolcenter.domain.repository.LayoutRepository
import com.example.tvcontrolcenter.domain.repository.NotificationSource
import com.example.tvcontrolcenter.domain.repository.NowPlayingSource
import com.example.tvcontrolcenter.domain.repository.SleepTimerRepository
import com.example.tvcontrolcenter.domain.repository.SystemActions
import com.example.tvcontrolcenter.domain.repository.SystemStatusSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeStatusSource(initial: SystemStatus = SystemStatus()) : SystemStatusSource {
    val state = MutableStateFlow(initial)
    override val status: Flow<SystemStatus> = state
    override suspend fun read(): SystemStatus = state.value
}

/** Records calls. Every action returns [result] unless a test overrides [dndResult]. */
class FakeActions : SystemActions {
    var result: ActionResult = ActionResult.Done
    var dndResult: ActionResult? = null
    val calls = mutableListOf<String>()
    var lastVolume: Int? = null

    override fun setVolumePercent(percent: Int) { lastVolume = percent; calls += "volume:$percent" }
    override fun openWifiSettings() = record("wifi")
    override fun openBluetoothSettings() = record("bluetooth")
    override fun openGameControllerSettings() = record("gamepad")
    override fun openAccessibilitySettings() = record("accessibility")
    override fun openSearch() = record("search")
    override fun toggleDoNotDisturb(currentlyOn: Boolean): ActionResult {
        calls += "dnd:$currentlyOn"
        return dndResult ?: result
    }
    override fun powerOff() = record("power")
    override fun sleepScreen() = record("sleep")
    override fun toggleMute() = record("mute")
    override fun openSettings() = record("settings")
    override fun openDisplaySettings() = record("display")
    override fun openSoundSettings() = record("sound")
    override fun openCaptionSettings() = record("captions")
    override fun openScreensaverSettings() = record("screensaver")
    override fun openAppsSettings() = record("apps")
    override fun goHome() = record("home")
    override fun showRecents() = record("recents")
    override fun takeScreenshot() = record("screenshot")
    override fun showNotifications() = record("notifications")

    private fun record(name: String): ActionResult { calls += name; return result }
}

class FakeNowPlaying(var access: Boolean = true) : NowPlayingSource {
    val state = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = state
    val calls = mutableListOf<String>()
    override fun hasAccess() = access
    override fun refresh() { calls += "refresh" }
    override fun togglePlayPause() { calls += "toggle" }
    override fun skipNext() { calls += "next" }
    override fun skipPrevious() { calls += "previous" }
}

class FakeNotifications(var access: Boolean = true) : NotificationSource {
    val state = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = state
    val calls = mutableListOf<String>()
    override fun hasAccess() = access
    override fun refresh() { calls += "refresh" }
    override fun open(key: String): Boolean { calls += "open:$key"; return true }
    override fun dismiss(key: String) { calls += "dismiss:$key" }
    override fun clearAll() { calls += "clear" }
}

class FakeSleepTimer : SleepTimerRepository {
    val state = MutableStateFlow<Int?>(null)
    var cycles = 0
    override val remainingMinutes: StateFlow<Int?> = state
    override fun cycle() { cycles++ }
    override fun cancel() { state.value = null }
}

class FakeFocusMemory(var last: TileId = TileId.POWER) : FocusMemory {
    override fun get() = last
    override fun set(id: TileId) { last = id }
}

class FakeLayoutRepository(initial: PanelLayout = PanelLayout.Default) : LayoutRepository {
    val layoutState = MutableStateFlow(initial)
    val settingsState = MutableStateFlow(PanelSettings())
    override val layout: StateFlow<PanelLayout> = layoutState
    override val settings: StateFlow<PanelSettings> = settingsState
    override fun update(transform: (PanelLayout) -> PanelLayout) { layoutState.value = transform(layoutState.value) }
    override fun resetLayout() { layoutState.value = PanelLayout.Default }
    override fun setUiScale(scale: Float) { settingsState.value = settingsState.value.copy(uiScale = scale) }
    override fun updateSettings(transform: (com.example.tvcontrolcenter.domain.model.PanelSettings) -> com.example.tvcontrolcenter.domain.model.PanelSettings) { settingsState.value = transform(settingsState.value) }
    override fun setBlurBehind(enabled: Boolean) { settingsState.value = settingsState.value.copy(blurBehind = enabled) }
}
