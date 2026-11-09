package com.example.tvcontrolcenter.domain.model

/** Every control the panel can show. Which ones appear, and how big, is a [PanelLayout]. */
enum class TileId {
    POWER, WIFI, DND, BLUETOOTH, SLEEP_TIMER,
    GAMEPAD, ACCESSIBILITY, LOCK, SEARCH,
    NOW_PLAYING, VOLUME,
    // Quick actions column, Google TV style.
    SETTINGS, DISPLAY, SOUND, CAPTIONS, SCREENSAVER, APPS,
    HOME, RECENTS, SCREENSHOT, NOTIFICATIONS,
    // Opens a list of the TV's inputs (HDMI and so on).
    INPUT,
    // Keeps the screen on while it is switched on.
    CAFFEINE,
    // Opens the layout editor.
    EDIT,
    // Round button next to the clock; shows the profile picture.
    PROFILE,
    // Four slots that open an app chosen in the editor (see [AppSlots]).
    APP_1, APP_2, APP_3, APP_4,
}

/** The app slots, in order. A slot's package name lives in [PanelSettings.appSlots]. */
val AppSlots: List<TileId> = listOf(TileId.APP_1, TileId.APP_2, TileId.APP_3, TileId.APP_4)

/** 0..3 for an app slot, null for every other control. */
val TileId.appSlot: Int?
    get() = when (this) {
        TileId.APP_1 -> 0
        TileId.APP_2 -> 1
        TileId.APP_3 -> 2
        TileId.APP_4 -> 3
        else -> null
    }

/** An app the editor can pin to a slot. Its icon is read from the system when it is drawn. */
data class LauncherApp(val packageName: String, val label: String)

enum class NetworkKind { WIFI, ETHERNET, NONE }

/** Snapshot of live system state shown on the tiles. Pure data, no Android types. */
data class SystemStatus(
    val network: NetworkKind = NetworkKind.NONE,
    val bluetoothEnabled: Boolean = false,
    val audioDeviceName: String? = null,
    val dndEnabled: Boolean = false,
    val volumePercent: Int = 0,
    val muted: Boolean = false,
)

data class NowPlaying(
    val title: String,
    val artist: String?,
    val packageName: String?,
    val isPlaying: Boolean,
    /** Album picture, already shrunk by the media source. Null when the app gives none. */
    val artwork: android.graphics.Bitmap? = null,
    /** 0 when unknown (live streams). */
    val durationMs: Long = 0L,
    /** Where playback was at [positionUpdatedAt]; the music page adds the time since then. */
    val positionMs: Long = 0L,
    /** SystemClock.elapsedRealtime() when [positionMs] was read. 0 when unknown. */
    val positionUpdatedAt: Long = 0L,
    val speed: Float = 1f,
)

/** One notification in the shade, as the panel needs it. Android types stay in the source. */
data class AppNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String? = null,
    val time: Long = 0L,
    /** Ongoing notifications cannot be dismissed. */
    val clearable: Boolean = true,
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
    /** The Edit tile was activated. The host decides where the editor lives. */
    data object OpenEditor : PanelEvent
}

/** Sleep timer presets in minutes. 0 means off. */
val SleepTimerPresets: List<Int> = listOf(0, 15, 30, 60, 90)

/** One TV input (an HDMI port, say) that the Input page can switch to. */
data class TvInputItem(val id: String, val label: String)
