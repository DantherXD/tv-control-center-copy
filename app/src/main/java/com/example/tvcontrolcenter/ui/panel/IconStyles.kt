package com.example.tvcontrolcenter.ui.panel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.tvcontrolcenter.domain.model.IconStyle
import java.util.IdentityHashMap

/** Icon family chosen in the editor. Provided by the panel and the editor. */
internal val LocalIconStyle = compositionLocalOf { IconStyle.FILLED }

/**
 * Swaps a filled icon for its outlined or rounded twin. Every icon the panel uses is listed here;
 * one that is not listed just stays filled.
 */
internal object IconStyles {
    private class Twins(val outlined: ImageVector, val rounded: ImageVector)

    private val twins = IdentityHashMap<ImageVector, Twins>().apply {
        fun add(filled: ImageVector, outlined: ImageVector, rounded: ImageVector) {
            put(filled, Twins(outlined, rounded))
        }
        add(Icons.Filled.Accessibility, Icons.Outlined.Accessibility, Icons.Rounded.Accessibility)
        add(Icons.Filled.Apps, Icons.Outlined.Apps, Icons.Rounded.Apps)
        add(Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt, Icons.Rounded.CameraAlt)
        add(Icons.Filled.ClosedCaption, Icons.Outlined.ClosedCaption, Icons.Rounded.ClosedCaption)
        add(Icons.Filled.DarkMode, Icons.Outlined.DarkMode, Icons.Rounded.DarkMode)
        add(Icons.Filled.FastForward, Icons.Outlined.FastForward, Icons.Rounded.FastForward)
        add(Icons.Filled.FastRewind, Icons.Outlined.FastRewind, Icons.Rounded.FastRewind)
        add(Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, Icons.Rounded.GraphicEq)
        add(Icons.Filled.Headphones, Icons.Outlined.Headphones, Icons.Rounded.Headphones)
        add(Icons.Filled.Home, Icons.Outlined.Home, Icons.Rounded.Home)
        add(Icons.Filled.Input, Icons.Outlined.Input, Icons.Rounded.Input)
        add(Icons.Filled.Layers, Icons.Outlined.Layers, Icons.Rounded.Layers)
        add(Icons.Filled.Lock, Icons.Outlined.Lock, Icons.Rounded.Lock)
        add(Icons.Filled.Menu, Icons.Outlined.Menu, Icons.Rounded.Menu)
        add(Icons.Filled.MusicNote, Icons.Outlined.MusicNote, Icons.Rounded.MusicNote)
        add(Icons.Filled.Notifications, Icons.Outlined.Notifications, Icons.Rounded.Notifications)
        add(Icons.Filled.Pause, Icons.Outlined.Pause, Icons.Rounded.Pause)
        add(Icons.Filled.Person, Icons.Outlined.Person, Icons.Rounded.Person)
        add(Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, Icons.Rounded.PhotoLibrary)
        add(Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow, Icons.Rounded.PlayArrow)
        add(Icons.Filled.PowerSettingsNew, Icons.Outlined.PowerSettingsNew, Icons.Rounded.PowerSettingsNew)
        add(Icons.Filled.Search, Icons.Outlined.Search, Icons.Rounded.Search)
        add(Icons.Filled.Settings, Icons.Outlined.Settings, Icons.Rounded.Settings)
        add(Icons.Filled.Speaker, Icons.Outlined.Speaker, Icons.Rounded.Speaker)
        add(Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports, Icons.Rounded.SportsEsports)
        add(Icons.Filled.Timer, Icons.Outlined.Timer, Icons.Rounded.Timer)
        add(Icons.Filled.Tv, Icons.Outlined.Tv, Icons.Rounded.Tv)
        add(Icons.Filled.VolumeOff, Icons.Outlined.VolumeOff, Icons.Rounded.VolumeOff)
        add(Icons.Filled.VolumeUp, Icons.Outlined.VolumeUp, Icons.Rounded.VolumeUp)
        add(Icons.Filled.Wifi, Icons.Outlined.Wifi, Icons.Rounded.Wifi)
        add(Icons.Filled.WifiOff, Icons.Outlined.WifiOff, Icons.Rounded.WifiOff)
    }

    fun resolve(icon: ImageVector, style: IconStyle): ImageVector = when (style) {
        IconStyle.FILLED -> icon
        IconStyle.OUTLINED -> twins[icon]?.outlined ?: icon
        IconStyle.ROUNDED -> twins[icon]?.rounded ?: icon
    }
}
