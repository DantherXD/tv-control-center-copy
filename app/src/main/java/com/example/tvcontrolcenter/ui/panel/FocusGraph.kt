package com.example.tvcontrolcenter.ui.panel

import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileId.ACCESSIBILITY
import com.example.tvcontrolcenter.domain.model.TileId.APPS
import com.example.tvcontrolcenter.domain.model.TileId.BLUETOOTH
import com.example.tvcontrolcenter.domain.model.TileId.CAPTIONS
import com.example.tvcontrolcenter.domain.model.TileId.DISPLAY
import com.example.tvcontrolcenter.domain.model.TileId.DND
import com.example.tvcontrolcenter.domain.model.TileId.GAMEPAD
import com.example.tvcontrolcenter.domain.model.TileId.HOME
import com.example.tvcontrolcenter.domain.model.TileId.LOCK
import com.example.tvcontrolcenter.domain.model.TileId.NOTIFICATIONS
import com.example.tvcontrolcenter.domain.model.TileId.NOW_PLAYING
import com.example.tvcontrolcenter.domain.model.TileId.POWER
import com.example.tvcontrolcenter.domain.model.TileId.RECENTS
import com.example.tvcontrolcenter.domain.model.TileId.SCREENSAVER
import com.example.tvcontrolcenter.domain.model.TileId.SCREENSHOT
import com.example.tvcontrolcenter.domain.model.TileId.SEARCH
import com.example.tvcontrolcenter.domain.model.TileId.SETTINGS
import com.example.tvcontrolcenter.domain.model.TileId.SLEEP_TIMER
import com.example.tvcontrolcenter.domain.model.TileId.SOUND
import com.example.tvcontrolcenter.domain.model.TileId.VOLUME
import com.example.tvcontrolcenter.domain.model.TileId.WIFI

enum class Dir { UP, DOWN, LEFT, RIGHT }

/**
 * Explicit D-pad map, so focus order never depends on measurement quirks.
 * Pure Kotlin on purpose: the unit tests walk it without Compose.
 *
 * ```
 *  QUICK ACTIONS (176dp)            MAIN CLUSTER (276dp)
 *  [SETTINGS   ][DISPLAY    ]       [POWER     ][WIFI      ]
 *  [SOUND      ][CAPTIONS   ]       [POWER     ][DND       ]
 *  [SCREENSAVER][APPS       ]       [BLUETOOTH ][SLEEP     ]
 *  [HOME       ][RECENTS    ]       [GAMEPAD][A11Y][LOCK][SEARCH]
 *  [SCREENSHOT ][NOTIFICATIONS]     [NOW PLAYING]   (only while something plays)
 *                                   [VOLUME]
 * ```
 *
 * A missing link means focus stays put; the panel traps focus. Links to NOW_PLAYING are
 * resolved in [neighbour]: when nothing plays, focus steps over it.
 * Left/Right on NOW_PLAYING and VOLUME are consumed by the tiles (skip, volume), so the
 * quick actions are entered from the tiles above them.
 */
object FocusGraph {

    private val links: Map<TileId, Map<Dir, TileId>> = buildMap<TileId, Map<Dir, TileId>> {
        fun link(
            id: TileId,
            up: TileId? = null,
            down: TileId? = null,
            left: TileId? = null,
            right: TileId? = null,
        ) {
            val edges = buildMap<Dir, TileId> {
                up?.let { put(Dir.UP, it) }
                down?.let { put(Dir.DOWN, it) }
                left?.let { put(Dir.LEFT, it) }
                right?.let { put(Dir.RIGHT, it) }
            }
            put(id, edges)
        }

        // Main cluster.
        link(POWER, down = BLUETOOTH, left = DISPLAY, right = WIFI)
        link(WIFI, down = DND, left = POWER)
        link(DND, up = WIFI, down = SLEEP_TIMER, left = POWER)
        link(BLUETOOTH, up = POWER, down = GAMEPAD, left = APPS, right = SLEEP_TIMER)
        link(SLEEP_TIMER, up = DND, down = LOCK, left = BLUETOOTH)
        link(GAMEPAD, up = BLUETOOTH, down = NOW_PLAYING, left = RECENTS, right = ACCESSIBILITY)
        link(ACCESSIBILITY, up = BLUETOOTH, down = NOW_PLAYING, left = GAMEPAD, right = LOCK)
        link(LOCK, up = SLEEP_TIMER, down = NOW_PLAYING, left = ACCESSIBILITY, right = SEARCH)
        link(SEARCH, up = SLEEP_TIMER, down = NOW_PLAYING, left = LOCK)
        link(NOW_PLAYING, up = ACCESSIBILITY, down = VOLUME, left = NOTIFICATIONS)
        link(VOLUME, up = NOW_PLAYING, left = NOTIFICATIONS)

        // Quick actions: two columns, rows aligned with the main cluster.
        link(SETTINGS, down = SOUND, right = DISPLAY)
        link(DISPLAY, down = CAPTIONS, left = SETTINGS, right = POWER)
        link(SOUND, up = SETTINGS, down = SCREENSAVER, right = CAPTIONS)
        link(CAPTIONS, up = DISPLAY, down = APPS, left = SOUND, right = POWER)
        link(SCREENSAVER, up = SOUND, down = HOME, right = APPS)
        link(APPS, up = CAPTIONS, down = RECENTS, left = SCREENSAVER, right = BLUETOOTH)
        link(HOME, up = SCREENSAVER, down = SCREENSHOT, right = RECENTS)
        link(RECENTS, up = APPS, down = NOTIFICATIONS, left = HOME, right = GAMEPAD)
        link(SCREENSHOT, up = HOME, right = NOTIFICATIONS)
        link(NOTIFICATIONS, up = RECENTS, left = SCREENSHOT, right = NOW_PLAYING)
    }

    fun visibleTiles(hasNowPlaying: Boolean): List<TileId> =
        TileId.entries.filter { it != NOW_PLAYING || hasNowPlaying }

    fun neighbour(from: TileId, dir: Dir, hasNowPlaying: Boolean): TileId? {
        val target = links[from]?.get(dir) ?: return null
        if (target != NOW_PLAYING || hasNowPlaying) return target
        // Now playing is hidden: step over it.
        return if (dir == Dir.UP) links.getValue(NOW_PLAYING)[Dir.UP] else VOLUME
    }

    /** Where focus lands on open: the remembered tile if it is still shown, else POWER. */
    fun restore(remembered: TileId, hasNowPlaying: Boolean): TileId =
        if (remembered in visibleTiles(hasNowPlaying)) remembered else POWER
}
