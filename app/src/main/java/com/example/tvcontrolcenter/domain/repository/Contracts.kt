package com.example.tvcontrolcenter.domain.repository

import com.example.tvcontrolcenter.domain.model.ActionResult
import com.example.tvcontrolcenter.domain.model.NowPlaying
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
