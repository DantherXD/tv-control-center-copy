package com.example.tvcontrolcenter.domain.model

/** Every focusable control in the panel. Order here is the default focus order. */
enum class TileId {
    POWER, WIFI, DND, BLUETOOTH, SLEEP_TIMER,
    GAMEPAD, ACCESSIBILITY, LOCK, SEARCH,
    NOW_PLAYING, VOLUME,
}

enum class NetworkKind { WIFI, ETHERNET, NONE }

/** Snapshot of live system state shown on the tiles. Pure data, no Android types. */
data class SystemStatus(
    val network: NetworkKind = NetworkKind.NONE,
    val bluetoothEnabled: Boolean = false,
    val audioDeviceName: String? = null,
    val dndEnabled: Boolean = false,
    val volumePercent: Int = 0,
)

data class NowPlaying(
    val title: String,
    val artist: String?,
    val packageName: String?,
    val isPlaying: Boolean,
)

/** Permissions or services that unlock a feature. The UI maps each to a settings screen. */
enum class Capability { ACCESSIBILITY_SERVICE, NOTIFICATION_POLICY, NOTIFICATION_LISTENER, OVERLAY }

sealed interface ActionResult {
    data object Done : ActionResult
    data class NeedsCapability(val capability: Capability) : ActionResult
    data object Unavailable : ActionResult
}

data class TileState(
    val id: TileId,
    val active: Boolean = false,
    val detail: String? = null,
)

/** One-shot effects the UI reacts to. */
sealed interface PanelEvent {
    data object Dismiss : PanelEvent
    data class NeedsCapability(val capability: Capability) : PanelEvent
    data class Unavailable(val tile: TileId) : PanelEvent
}

/** Sleep timer presets in minutes. 0 means off. */
val SleepTimerPresets: List<Int> = listOf(0, 15, 30, 60, 90)
