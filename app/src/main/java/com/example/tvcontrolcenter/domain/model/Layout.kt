package com.example.tvcontrolcenter.domain.model

import kotlin.math.abs
import com.example.tvcontrolcenter.domain.model.TileId.ACCESSIBILITY
import com.example.tvcontrolcenter.domain.model.TileId.APP_1
import com.example.tvcontrolcenter.domain.model.TileId.APP_2
import com.example.tvcontrolcenter.domain.model.TileId.APP_3
import com.example.tvcontrolcenter.domain.model.TileId.APP_4
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
import com.example.tvcontrolcenter.domain.model.TileId.PROFILE
import com.example.tvcontrolcenter.domain.model.TileId.RECENTS
import com.example.tvcontrolcenter.domain.model.TileId.SCREENSAVER
import com.example.tvcontrolcenter.domain.model.TileId.CAFFEINE
import com.example.tvcontrolcenter.domain.model.TileId.INPUT
import com.example.tvcontrolcenter.domain.model.TileId.SCREENSHOT
import com.example.tvcontrolcenter.domain.model.TileId.SEARCH
import com.example.tvcontrolcenter.domain.model.TileId.SETTINGS
import com.example.tvcontrolcenter.domain.model.TileId.SLEEP_TIMER
import com.example.tvcontrolcenter.domain.model.TileId.SOUND
import com.example.tvcontrolcenter.domain.model.TileId.VOLUME
import com.example.tvcontrolcenter.domain.model.TileId.WIFI

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
        const val MAX_PINNED = 3

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
                // The two round buttons beside the clock: menu (opens the editor) and profile.
                ControlItem(EDIT, TileSize.TOP),
                ControlItem(PROFILE, TileSize.TOP),
                // Available from the editor.
                ControlItem(SETTINGS, TileSize.SMALL, visible = false),
                ControlItem(HOME, TileSize.SMALL, visible = false),
                ControlItem(RECENTS, TileSize.SMALL, visible = false),
                ControlItem(NOTIFICATIONS, TileSize.SMALL, visible = false),
                ControlItem(SCREENSHOT, TileSize.SMALL, visible = false),
                ControlItem(APPS, TileSize.SMALL, visible = false),
                ControlItem(DISPLAY, TileSize.SMALL, visible = false),
                ControlItem(SOUND, TileSize.SMALL, visible = false),
                ControlItem(CAPTIONS, TileSize.SMALL, visible = false),
                ControlItem(SCREENSAVER, TileSize.SMALL, visible = false),
                ControlItem(INPUT, TileSize.SMALL, visible = false),
                ControlItem(CAFFEINE, TileSize.SMALL, visible = false),
                // App slots: shown once the editor pins an app to them.
                ControlItem(APP_1, TileSize.SMALL, visible = false),
                ControlItem(APP_2, TileSize.SMALL, visible = false),
                ControlItem(APP_3, TileSize.SMALL, visible = false),
                ControlItem(APP_4, TileSize.SMALL, visible = false),
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
                if (id in seen) continue
                // Older versions pinned four buttons. Only menu, profile and now playing may stay on top.
                val bar = size == TileSize.TOP
                val keep = !bar || id == EDIT || id == PROFILE || id == NOW_PLAYING
                seen[id] = ControlItem(id, if (keep) size else TileSize.SMALL, f[2] == "1" && keep)
            }
            if (seen.values.none { it.visible }) return Default
            val missing = TileId.entries
                .filter { it !in seen }
                .map { id ->
                    val d = Default.item(id)
                    // A new top-bar button (profile) shows up at once; anything else waits in the editor.
                    ControlItem(id, d?.size ?: TileSize.SMALL, visible = d?.size == TileSize.TOP)
                }
            return PanelLayout(seen.values.toList() + missing)
        }
    }
}

/** Where the blurred picture inside the glass comes from. */
enum class BlurMethod {
    /** Screenshot first, whole-screen window blur if there is none. */
    AUTO,
    /** Blur only inside the glass, from a screenshot (Android 11+, accessibility service). */
    SCREENSHOT,
    /**
     * Blur the whole screen behind the panel. Uses the screenshot, drawn blurred over the full
     * screen (works on TVs whose system cannot blur windows); falls back to the system window
     * blur (Android 12+, device permitting). No frosted box is drawn around the controls.
     */
    WINDOW,
}

/** How the glass itself is tinted. All three are plain fills; none costs extra drawing. */
enum class FrostStyle {
    /** Dark smoked glass: best contrast on bright apps. */
    SMOKE,
    /** Light milky glass, like frosted white acrylic. */
    MILKY,
    /** Almost clear: only a faint tint and the blur show. */
    CLEAR,
    /**
     * Dark lens: the blurred picture behind the control is dimmed, so the glass edge and the
     * sheen stand out instead of the brightness of the screen. The glassiest of the styles.
     */
    LENS,
    /** Tinted glass in a colour. */
    OCEAN, AMETHYST, EMBER, FOREST, ROSE,
    /** Nearly solid dark glass. */
    MIDNIGHT,
}

/**
 * Background of the app's own pages: the home page and the editor. All of them are painted on
 * the device into one image, so they cost a single draw per frame and nothing else. The panel
 * over other apps blurs the real screen instead and ignores this.
 */
enum class Wallpaper {
    /** Flat saturated squares, the look the app started with. */
    BOXES,
    /** Night sky with teal and violet curtains. */
    AURORA,
    /** Warm horizon under a purple evening. */
    SUNSET,
    /** Dark green canopy. */
    FOREST,
    /** Rings rolling out of a corner. */
    WAVES,
    /** Dark sky with colour clouds and stars. */
    NEBULA,
    /** Quiet technical grid. */
    GRID,
    /** One dark tone, the quietest choice. */
    PLAIN,
}

/** Blur strength 0 (none) to [MAX]. Radii are for the 480 px screenshot and for the window blur. */
object BlurLevels {
    const val MAX = 4
    fun clamp(level: Int) = level.coerceIn(0, MAX)
    /** Box-blur radius on the ~480 px copy of the screen. Three passes, so the real spread is wider. */
    fun boxRadius(level: Int): Int = when (clamp(level)) { 0 -> 0; 1 -> 3; 2 -> 6; 3 -> 10; else -> 16 }
    /** Radius in pixels for `blurBehindRadius`. */
    fun windowRadius(level: Int): Int = when (clamp(level)) { 0 -> 0; 1 -> 20; 2 -> 40; 3 -> 70; else -> 110 }
}

/** Look-and-feel choices that are not about which controls exist. */
data class PanelSettings(
    /** Multiplies the automatic screen-size scaling. 1.0 fits the panel to the screen. */
    val uiScale: Float = 1f,
    /** Master switch for blur behind the glass. */
    val blurBehind: Boolean = true,
    val blurLevel: Int = 3,
    val blurMethod: BlurMethod = BlurMethod.AUTO,
    val frost: FrostStyle = FrostStyle.SMOKE,
    /** Background of the app's own pages (home page and editor). */
    val wallpaper: Wallpaper = Wallpaper.BOXES,
    /** Gap above the panel, in reference dp (see [TopOffsetSteps]). */
    val topOffset: Int = 48,
    /** Gap between the top bar (clock and round buttons) and the controls, in reference dp (see [HeaderGapSteps]). */
    val headerGap: Int = 8,
    /** The focused control turns white and its icon and text turn dark, like tvOS. */
    val invertFocus: Boolean = true,
    /**
     * Glass card behind all the controls. Off, the controls float on the blurred screen like
     * tvOS. The card is drawn even when this is off if nothing can blur the screen.
     */
    val card: Boolean = false,
    /** Size of the text in the panel, on top of the panel size (see [TextScaleSteps]). */
    val textScale: Float = 1f,
    /** Thickness of the glass edge line in dp (see [EdgeWidthSteps]); 0 draws none. */
    val edgeWidth: Float = 1.5f,
    /** Size of the round top-bar buttons in dp (see [TopSizeSteps]). */
    val topSize: Int = 44,
    /** Top-bar buttons have no glass until focused. */
    val topBorderless: Boolean = false,
    /** Menu button is the main page: it leaves the music page, and the music button only opens it. */
    val menuIsHome: Boolean = true,
    val iconStyle: IconStyle = IconStyle.FILLED,
    val iconPack: IconPack = IconPack.MATERIAL,
    val phosphorWeight: PhosphorWeight = PhosphorWeight.FILL,
    /** The Bluetooth control's icon: the Bluetooth mark, or the headphones it used to carry. */
    val bluetoothIcon: BluetoothIcon = BluetoothIcon.STANDARD,
    /**
     * Controls that are "on" get brighter glass: Wi-Fi, Bluetooth, Do Not Disturb, the sleep
     * timer, the screen hold and now playing. Off draws every control the same until focused.
     */
    val activeHighlight: Boolean = true,
    val musicStyle: MusicStyle = MusicStyle.COMPACT,
    /** How the Wi-Fi, Bluetooth and Input windows arrive and leave (see [WindowMotion]). */
    val windowMotion: WindowMotion = WindowMotion.MID_AIR,
    /**
     * Icon and text colour follow the brightness behind each control, so white never sits on a
     * white app. Off keeps every control white, as it was before.
     */
    val autoInk: Boolean = true,
    /** Package name per app slot (see [AppSlots]); null when the slot is empty. */
    val appSlots: List<String?> = List(AppSlots.size) { null },
    /** Short click on focus and on OK, as Android gives every list. Off is silent. */
    val soundCues: Boolean = true,
    /** Percent the volume moves per Left/Right press (see [VolumeStepSteps]). */
    val volumeStep: Int = 3,
    /** Log the package and activity of every screen that opens, for working out what a tile did. */
    val activityLog: Boolean = false,
    /** Wait longer and retry the screenshot, so the blur also works over video and moving scenes. */
    val patientCapture: Boolean = false,
    /** 0 = no photo. Changes whenever a new photo is saved, so the picture reloads. */
    val profilePhotoVersion: Int = 0,
    /** Colour of the profile circle when there is no photo; index into the app's palette. */
    val profileColor: Int = 0,
) {
    /** The blur strength actually in force. */
    val effectiveBlur: Int get() = if (blurBehind) BlurLevels.clamp(blurLevel) else 0
}

val UiScaleSteps: List<Float> = listOf(0.8f, 1f, 1.25f, 1.5f, 1.75f)

val TopOffsetSteps: List<Int> = listOf(0, 24, 48, 72, 96, 120)

val EdgeWidthSteps: List<Float> = listOf(0f, 0.5f, 0.8f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f, 5f)

val TopSizeSteps: List<Int> = listOf(32, 36, 40, 44, 48)

val VolumeStepSteps: List<Int> = listOf(1, 2, 3, 5, 10)

val TextScaleSteps: List<Float> = listOf(0.8f, 0.9f, 1f, 1.15f, 1.3f)

val HeaderGapSteps: List<Int> = listOf(0, 4, 8, 12, 18, 24)

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
            // Left and Right stay in their own row: the header bar and the grid never swap sideways.
            if (horizontal && (a.row == GridPacker.TOP_ROW) != (b.row == GridPacker.TOP_ROW)) return@mapNotNull null
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

/** Which Material icon family the controls use. */
enum class IconStyle { FILLED, OUTLINED, ROUNDED }

/** Where the controls' icons are drawn from: the platform's Material set, or Phosphor. */
enum class IconPack { MATERIAL, PHOSPHOR }

/** How heavy Phosphor draws its icons: its six weights, lightest first. */
enum class PhosphorWeight { THIN, LIGHT, REGULAR, BOLD, FILL, DUOTONE }

/** Which icon the Bluetooth control wears (see [PanelSettings.bluetoothIcon]). */
enum class BluetoothIcon { STANDARD, HEADPHONES }

/** Look of the now-playing page: a compact card, or a big one like iOS with the album picture filling it. */
enum class MusicStyle { COMPACT, BIG }

/** How a small window opens and closes. */
enum class WindowMotion {
    /**
     * Grows out of the control that opened it, as the control's own piece of the screen, and
     * shrinks back onto it. The window is a tile's shape at the first frame, so the picture
     * inside it is drawn stretched until it has reached its real size.
     */
    FROM_CONTROL,

    /**
     * Opens in the window's own place, a little smaller and transparent, and grows to full size;
     * closing fades it out where it stands. Nothing travels and nothing is stretched into a
     * tile's shape, which is why this is the default.
     */
    MID_AIR,

    /** No motion: the window is simply there, and a close is done at once. */
    OFF,
}
