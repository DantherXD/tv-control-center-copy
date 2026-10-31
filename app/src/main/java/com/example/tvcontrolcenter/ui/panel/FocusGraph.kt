package com.example.tvcontrolcenter.ui.panel

import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileId.ACCESSIBILITY
import com.example.tvcontrolcenter.domain.model.TileId.BLUETOOTH
import com.example.tvcontrolcenter.domain.model.TileId.DND
import com.example.tvcontrolcenter.domain.model.TileId.GAMEPAD
import com.example.tvcontrolcenter.domain.model.TileId.LOCK
import com.example.tvcontrolcenter.domain.model.TileId.NOW_PLAYING
import com.example.tvcontrolcenter.domain.model.TileId.POWER
import com.example.tvcontrolcenter.domain.model.TileId.SEARCH
import com.example.tvcontrolcenter.domain.model.TileId.SLEEP_TIMER
import com.example.tvcontrolcenter.domain.model.TileId.VOLUME
import com.example.tvcontrolcenter.domain.model.TileId.WIFI

enum class Dir { UP, DOWN, LEFT, RIGHT }

/**
 * Explicit D-pad map, so focus order never depends on measurement quirks.
 * Pure Kotlin on purpose: the unit tests walk it without Compose.
 *
 *   [POWER     ][WIFI      ]
 *   [POWER     ][DND       ]
 *   [BLUETOOTH ][SLEEP     ]
 *   [GAMEPAD][A11Y][LOCK][SEARCH]
 *   [NOW PLAYING]   (only while something plays)
 *   [VOLUME]
 *
 * A null neighbour means focus stays put; the panel traps focus.
 */
object FocusGraph {

    fun visibleTiles(hasNowPlaying: Boolean): List<TileId> =
        TileId.entries.filter { it != NOW_PLAYING || hasNowPlaying }

    fun neighbour(from: TileId, dir: Dir, hasNowPlaying: Boolean): TileId? {
        val below = if (hasNowPlaying) NOW_PLAYING else VOLUME
        return when (from) {
            POWER -> when (dir) { Dir.RIGHT -> WIFI; Dir.DOWN -> BLUETOOTH; else -> null }
            WIFI -> when (dir) { Dir.LEFT -> POWER; Dir.DOWN -> DND; else -> null }
            DND -> when (dir) { Dir.LEFT -> POWER; Dir.UP -> WIFI; Dir.DOWN -> SLEEP_TIMER; else -> null }
            BLUETOOTH -> when (dir) { Dir.UP -> POWER; Dir.RIGHT -> SLEEP_TIMER; Dir.DOWN -> GAMEPAD; else -> null }
            SLEEP_TIMER -> when (dir) { Dir.UP -> DND; Dir.LEFT -> BLUETOOTH; Dir.DOWN -> LOCK; else -> null }
            GAMEPAD -> when (dir) { Dir.UP -> BLUETOOTH; Dir.RIGHT -> ACCESSIBILITY; Dir.DOWN -> below; else -> null }
            ACCESSIBILITY -> when (dir) { Dir.UP -> BLUETOOTH; Dir.LEFT -> GAMEPAD; Dir.RIGHT -> LOCK; Dir.DOWN -> below }
            LOCK -> when (dir) { Dir.UP -> SLEEP_TIMER; Dir.LEFT -> ACCESSIBILITY; Dir.RIGHT -> SEARCH; Dir.DOWN -> below }
            SEARCH -> when (dir) { Dir.UP -> SLEEP_TIMER; Dir.LEFT -> LOCK; Dir.DOWN -> below; else -> null }
            NOW_PLAYING -> when (dir) { Dir.UP -> ACCESSIBILITY; Dir.DOWN -> VOLUME; else -> null }
            VOLUME -> when (dir) { Dir.UP -> if (hasNowPlaying) NOW_PLAYING else ACCESSIBILITY; else -> null }
        }
    }

    /** Where focus lands on open: the remembered tile if it is still shown, else POWER. */
    fun restore(remembered: TileId, hasNowPlaying: Boolean): TileId =
        if (remembered in visibleTiles(hasNowPlaying)) remembered else POWER
}
