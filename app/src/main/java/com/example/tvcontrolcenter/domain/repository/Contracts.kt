package com.example.tvcontrolcenter.domain.repository

import com.example.tvcontrolcenter.domain.model.ActionResult
import com.example.tvcontrolcenter.domain.model.NowPlaying
import com.example.tvcontrolcenter.domain.model.PanelLayout
import com.example.tvcontrolcenter.domain.model.PanelSettings
import com.example.tvcontrolcenter.domain.model.SystemStatus
import com.example.tvcontrolcenter.domain.model.TileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Live system state. Implementations may poll; collectors get distinct values only. */
interface SystemStatusSource {
    val status: Flow<SystemStatus>
    suspend fun read(): SystemStatus
}

/** Everything the panel can do to the device. Each call degrades gracefully. */
interface SystemActions {
    fun setVolumePercent(percent: Int)
    fun openWifiSettings(): ActionResult
    fun openBluetoothSettings(): ActionResult
    fun openGameControllerSettings(): ActionResult
    fun openAccessibilitySettings(): ActionResult
    fun openSearch(): ActionResult
    fun toggleDoNotDisturb(currentlyOn: Boolean): ActionResult
    /** Android cannot cut power. Uses the accessibility power dialog where possible. */
    fun powerOff(): ActionResult
    /** Turns the screen off / locks via the accessibility service. */
    fun sleepScreen(): ActionResult

    fun toggleMute(): ActionResult
    fun openSettings(): ActionResult
    fun openDisplaySettings(): ActionResult
    fun openSoundSettings(): ActionResult
    fun openCaptionSettings(): ActionResult
    fun openScreensaverSettings(): ActionResult
    fun openAppsSettings(): ActionResult

    /**
     * Home, recents, screenshot and notifications use the accessibility service and run a moment
     * after the panel closes, so the panel is not in the screenshot or on top of recents.
     * Without the service they return [ActionResult.NeedsCapability] (Home has an intent fallback).
     */
    fun goHome(): ActionResult
    fun showRecents(): ActionResult
    fun takeScreenshot(): ActionResult
    fun showNotifications(): ActionResult
}

interface NowPlayingSource {
    val nowPlaying: StateFlow<NowPlaying?>
    fun hasAccess(): Boolean
    fun refresh()
    fun togglePlayPause()
    fun skipNext()
    fun skipPrevious()
}

interface SleepTimerRepository {
    /** Minutes remaining, or null when the timer is off. */
    val remainingMinutes: StateFlow<Int?>
    /** Cycles Off, 15, 30, 60, 90, Off. */
    fun cycle()
    fun cancel()
}

/** Remembers the last focused tile so reopening the panel restores focus. */
interface FocusMemory {
    fun get(): TileId
    fun set(id: TileId)
}

/** The user's arrangement of controls plus look-and-feel settings. Survives restarts. */
interface LayoutRepository {
    val layout: StateFlow<PanelLayout>
    val settings: StateFlow<PanelSettings>
    fun update(transform: (PanelLayout) -> PanelLayout)
    fun resetLayout()
    fun setUiScale(scale: Float)
    fun setBlurBehind(enabled: Boolean)
}
