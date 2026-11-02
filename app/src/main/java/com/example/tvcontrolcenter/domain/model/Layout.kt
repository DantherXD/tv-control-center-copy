package com.example.tvcontrolcenter.domain.model

import kotlin.math.abs
import com.example.tvcontrolcenter.domain.model.TileId.ACCESSIBILITY
import com.example.tvcontrolcenter.domain.model.TileId.APPS
import com.example.tvcontrolcenter.domain.model.TileId.BLUETOOTH
import com.example.tvcontrolcenter.domain.model.TileId.CAPTIONS
import com.example.tvcontrolcenter.domain.model.TileId.DISPLAY
import com.example.tvcontrolcenter.domain.model.TileId.DND
import com.example.tvcontrolcenter.domain.model.TileId.EDIT
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
import kotlin.math.abs

/**
 * Footprint of a control on the 4-column grid. Every control can take every size.
 * [TOP] takes no grid space: the control becomes a round button in the bar above the grid.
 */
enum class TileSize(val cols: Int, val rows: Int) {
    SMALL(1, 1),
    WIDE(2, 1),
    TALL(1, 2),
    LARGE(2, 2),
    FULL(4, 1),
    TOP(0, 0),
}

enum class Dir { UP, DOWN, LEFT, RIGHT }

data class ControlItem(
    val id: TileId,
    val size: TileSize,
    val visible: Boolean = true,
)

/**
 * The user's arrangement: order, size and visibility of every control.
 * Order matters; the grid is filled first-fit in this order. Hidden items keep their slot
 * in the list so they can be offered again in the editor.
 */
data class PanelLayout(val items: List<ControlItem>) {

    fun item(id: TileId): ControlItem? = items.firstOrNull { it.id == id }

    fun visibleItems(): List<ControlItem> = items.filter { it.visible }

    /** Shown controls that live in the top bar, in list order. */
    fun pinnedItems(): List<ControlItem> = items.filter { it.visible && it.size == TileSize.TOP }

    fun hiddenItems(): List<ControlItem> = items.filter { !it.visible }

    /** The top bar has room for [MAX_PINNED] buttons; asking for more changes nothing. */
    fun resize(id: TileId, size: TileSize): PanelLayout {
        val current = item(id) ?: return this
        if (size == TileSize.TOP && current.size != TileSize.TOP && pinnedItems().size >= MAX_PINNED) return this
        return copy(items = items.map { if (it.id == id) it.copy(size = size) else it })
    }

    /** Steps through the sizes in enum order, wrapping round. */
    fun resizeBy(id: TileId, delta: Int): PanelLayout {
        val current = item(id) ?: return this
        val all = TileSize.entries
        val next = all[Math.floorMod(all.indexOf(current.size) + delta, all.size)]
        return resize(id, next)
    }

    /** Hiding keeps the slot. Showing puts the control at the end, where it is easy to find. */
    fun setVisible(id: TileId, visible: Boolean): PanelLayout {
        val current = item(id) ?: return this
        if (current.visible == visible) return this
        return if (visible) {
            PanelLayout(items.filter { it.id != id } + current.copy(visible = true))
        } else {
            copy(items = items.map { if (it.id == id) it.copy(visible = false) else it })
        }
    }

    /** Swaps with the next visible control before (negative) or after (positive). */
    fun move(id: TileId, delta: Int): PanelLayout {
        val from = items.indexOfFirst { it.id == id }
        if (from < 0 || !items[from].visible || delta == 0) return this
        val step = if (delta < 0) -1 else 1
        var to = from + step
        while (to in items.indices && !items[to].visible) to += step
        if (to !in items.indices) return this
        val list = items.toMutableList()
        val moved = list[from]
        list[from] = list[to]
        list[to] = moved
        return PanelLayout(list)
    }

    fun encode(): String = items.joinToString(",") {
        "${it.id.name}:${it.size.name}:${if (it.visible) 1 else 0}"
    }

    companion object {
        const val MAX_PINNED = 4

        val Default = PanelLayout(
            listOf(
                ControlItem(POWER, TileSize.LARGE),
                ControlItem(WIFI, TileSize.WIDE),
                ControlItem(DND, TileSize.WIDE),
                ControlItem(BLUETOOTH, TileSize.WIDE),
                ControlItem(SLEEP_TIMER, TileSize.WIDE),
                ControlItem(GAMEPAD, TileSize.SMALL),
                ControlItem(ACCESSIBILITY, TileSize.SMALL),
                ControlItem(LOCK, TileSize.SMALL),
                ControlItem(SEARCH, TileSize.SMALL),
                ControlItem(NOW_PLAYING, TileSize.FULL),
                ControlItem(VOLUME, TileSize.FULL),
                // Round buttons above the grid, on the right.
                ControlItem(SETTINGS, TileSize.TOP),
                ControlItem(HOME, TileSize.TOP),
                ControlItem(RECENTS, TileSize.TOP),
                ControlItem(EDIT, TileSize.TOP),
                // Available from the editor.
                ControlItem(NOTIFICATIONS, TileSize.SMALL, visible = false),
                ControlItem(SCREENSHOT, TileSize.SMALL, visible = false),
                ControlItem(APPS, TileSize.SMALL, visible = false),
                ControlItem(DISPLAY, TileSize.SMALL, visible = false),
                ControlItem(SOUND, TileSize.SMALL, visible = false),
                ControlItem(CAPTIONS, TileSize.SMALL, visible = false),
                ControlItem(SCREENSAVER, TileSize.SMALL, visible = false),
            ),
        )

        /**
         * Reads what [encode] wrote. Unknown names and repeats are dropped; controls added by
         * a later app version appear hidden at the end. Anything unusable gives [Default].
         */
        fun decode(text: String?): PanelLayout {
            if (text.isNullOrBlank()) return Default
            val seen = LinkedHashMap<TileId, ControlItem>()
            for (part in text.split(',')) {
                val f = part.split(':')
                if (f.size != 3) continue
                val id = TileId.entries.firstOrNull { it.name == f[0] } ?: continue
                val size = TileSize.entries.firstOrNull { it.name == f[1] } ?: continue
                if (id !in seen) seen[id] = ControlItem(id, size, f[2] == "1")
            }
            if (seen.values.none { it.visible }) return Default
            val missing = TileId.entries
                .filter { it !in seen }
                .map { id -> ControlItem(id, Default.item(id)?.size ?: TileSize.SMALL, visible = false) }
            return PanelLayout(seen.values.toList() + missing)
        }
    }
}

/** Look-and-feel choices that are not about which controls exist. */
data class PanelSettings(
    /** Multiplies the automatic screen-size scaling. 1.0 fits the panel to the screen. */
    val uiScale: Float = 1f,
    /** Ask the system to blur the app behind the panel (Android 12+, device permitting). */
    val blurBehind: Boolean = true,
)

val UiScaleSteps: List<Float> = listOf(0.8f, 1f, 1.25f, 1.5f, 1.75f)

/** A control placed on the grid. Coordinates are cells, not pixels. */
data class Placed(val id: TileId, val size: TileSize, val col: Int, val row: Int) {
    val cols: Int get() = size.cols
    val rows: Int get() = size.rows
}

/** First-fit packing: each control takes the first free spot in reading order. */
object GridPacker {
    const val COLUMNS = 4

    /** Row used for the top bar, so D-pad moves between it and the grid work like any other row. */
    const val TOP_ROW = -1

    /**
     * Top-bar buttons, right-aligned over the last columns on [TOP_ROW]. They count as 1x1 cells
     * for navigation only; the buttons are drawn smaller than a grid cell.
     */
    fun pinned(items: List<ControlItem>, columns: Int = COLUMNS): List<Placed> {
        val bar = items.filter { it.size == TileSize.TOP }.take(PanelLayout.MAX_PINNED)
        return bar.mapIndexed { i, item ->
            Placed(item.id, TileSize.SMALL, columns - bar.size + i, TOP_ROW)
        }
    }

    /** Packs everything except top-bar buttons. */
    fun pack(items: List<ControlItem>, columns: Int = COLUMNS): List<Placed> {
        val taken = HashSet<Long>()
        fun key(col: Int, row: Int) = row.toLong() * 1000L + col

        fun fits(col: Int, row: Int, w: Int, h: Int): Boolean {
            for (r in row until row + h) for (c in col until col + w) if (key(c, r) in taken) return false
            return true
        }

        val placed = ArrayList<Placed>(items.size)
        for (item in items) {
            if (item.size == TileSize.TOP) continue
            val w = minOf(item.size.cols, columns)
            val h = item.size.rows
            var row = 0
            search@ while (true) {
                for (col in 0..(columns - w)) {
                    if (fits(col, row, w, h)) {
                        for (r in row until row + h) for (c in col until col + w) taken.add(key(c, r))
                        placed.add(Placed(item.id, item.size, col, row))
                        break@search
                    }
                }
                row++
            }
        }
        return placed
    }

    fun rowCount(placed: List<Placed>): Int = placed.maxOfOrNull { it.row + it.rows } ?: 0
}

/** D-pad movement over a packed grid. Pure geometry, so it works for any arrangement. */
object GridNav {

    fun neighbour(placed: List<Placed>, from: TileId, dir: Dir): TileId? {
        val a = placed.firstOrNull { it.id == from } ?: return null
        val horizontal = dir == Dir.LEFT || dir == Dir.RIGHT

        data class Candidate(val id: TileId, val gap: Int, val cross: Int, val overlaps: Boolean)

        val candidates = placed.mapNotNull { b ->
            if (b.id == from) return@mapNotNull null
            val gap = when (dir) {
                Dir.RIGHT -> b.col - (a.col + a.cols)
                Dir.LEFT -> a.col - (b.col + b.cols)
                Dir.DOWN -> b.row - (a.row + a.rows)
                Dir.UP -> a.row - (b.row + b.rows)
            }
            if (gap < 0) return@mapNotNull null
            // Centres in half-cells, so everything stays integer.
            val cross = if (horizontal) {
                abs((a.row * 2 + a.rows) - (b.row * 2 + b.rows))
            } else {
                abs((a.col * 2 + a.cols) - (b.col * 2 + b.cols))
            }
            val overlaps = if (horizontal) {
                a.row < b.row + b.rows && b.row < a.row + a.rows
            } else {
                a.col < b.col + b.cols && b.col < a.col + a.cols
            }
            Candidate(b.id, gap, cross, overlaps)
        }

        val aligned = candidates.filter { it.overlaps }
        if (aligned.isNotEmpty()) {
            return aligned.minWithOrNull(compareBy<Candidate>({ it.gap }, { it.cross }))?.id
        }
        return candidates.minWithOrNull(compareBy<Candidate>({ it.gap * 4 + it.cross }))?.id
    }
}
