package com.example.tvcontrolcenter.ui.panel

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.domain.model.TileId

internal class TileLook(val icon: ImageVector, @StringRes val label: Int)

/** Resting icon and name of every control. The `when` is exhaustive on purpose. */
internal fun lookOf(id: TileId): TileLook = when (id) {
    TileId.POWER -> TileLook(Icons.Filled.PowerSettingsNew, R.string.tile_power)
    TileId.WIFI -> TileLook(Icons.Filled.Wifi, R.string.tile_wifi)
    TileId.DND -> TileLook(Icons.Filled.DarkMode, R.string.tile_dnd)
    TileId.BLUETOOTH -> TileLook(Icons.Filled.Headphones, R.string.tile_bluetooth)
    TileId.SLEEP_TIMER -> TileLook(Icons.Filled.Timer, R.string.tile_sleep_timer)
    TileId.GAMEPAD -> TileLook(Icons.Filled.SportsEsports, R.string.tile_gamepad)
    TileId.ACCESSIBILITY -> TileLook(Icons.Filled.Accessibility, R.string.tile_accessibility)
    TileId.LOCK -> TileLook(Icons.Filled.Lock, R.string.tile_lock)
    TileId.SEARCH -> TileLook(Icons.Filled.Search, R.string.tile_search)
    TileId.NOW_PLAYING -> TileLook(Icons.Filled.MusicNote, R.string.now_playing)
    TileId.VOLUME -> TileLook(Icons.Filled.VolumeUp, R.string.tile_volume)
    TileId.SETTINGS -> TileLook(Icons.Filled.Settings, R.string.tile_settings)
    TileId.DISPLAY -> TileLook(Icons.Filled.Tv, R.string.tile_display)
    TileId.SOUND -> TileLook(Icons.Filled.Speaker, R.string.tile_sound)
    TileId.CAPTIONS -> TileLook(Icons.Filled.ClosedCaption, R.string.tile_captions)
    TileId.SCREENSAVER -> TileLook(Icons.Filled.PhotoLibrary, R.string.tile_screensaver)
    TileId.APPS -> TileLook(Icons.Filled.Apps, R.string.tile_apps)
    TileId.HOME -> TileLook(Icons.Filled.Home, R.string.tile_home)
    TileId.RECENTS -> TileLook(Icons.Filled.Layers, R.string.tile_recents)
    TileId.SCREENSHOT -> TileLook(Icons.Filled.CameraAlt, R.string.tile_screenshot)
    TileId.NOTIFICATIONS -> TileLook(Icons.Filled.Notifications, R.string.tile_notifications)
    TileId.EDIT -> TileLook(Icons.Filled.Edit, R.string.tile_edit)
}
