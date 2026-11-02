package com.example.tvcontrolcenter.domain.model

import com.example.tvcontrolcenter.domain.model.TileId.ACCESSIBILITY
import com.example.tvcontrolcenter.domain.model.TileId.BLUETOOTH
import com.example.tvcontrolcenter.domain.model.TileId.DND
import com.example.tvcontrolcenter.domain.model.TileId.EDIT
import com.example.tvcontrolcenter.domain.model.TileId.GAMEPAD
import com.example.tvcontrolcenter.domain.model.TileId.HOME
import com.example.tvcontrolcenter.domain.model.TileId.LOCK
import com.example.tvcontrolcenter.domain.model.TileId.NOW_PLAYING
import com.example.tvcontrolcenter.domain.model.TileId.POWER
import com.example.tvcontrolcenter.domain.model.TileId.RECENTS
import com.example.tvcontrolcenter.domain.model.TileId.SCREENSHOT
import com.example.tvcontrolcenter.domain.model.TileId.SEARCH
import com.example.tvcontrolcenter.domain.model.TileId.SETTINGS
import com.example.tvcontrolcenter.domain.model.TileId.SLEEP_TIMER
import com.example.tvcontrolcenter.domain.model.TileId.VOLUME
import com.example.tvcontrolcenter.domain.model.TileId.WIFI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {

    private val default = PanelLayout.Default

    private fun shown(withNowPlaying: Boolean, layout: PanelLayout = default) =
        layout.visibleItems().filter { it.id != NOW_PLAYING || withNowPlaying }

    private fun packed(withNowPlaying: Boolean, layout: PanelLayout = default) =
        GridPacker.pack(shown(withNowPlaying, layout))

    /** What the panel navigates over: the top bar plus the grid. */
    private fun nav(withNowPlaying: Boolean, layout: PanelLayout = default) =
        GridPacker.pinned(shown(withNowPlaying, layout)) + packed(withNowPlaying, layout)

    private fun cell(placed: List<Placed>, id: TileId) = placed.first { it.id == id }.let { it.col to it.row }

    // --- packing ---------------------------------------------------------------------------

    @Test fun `default layout packs into the expected cells`() {
        val p = packed(withNowPlaying = true)
        assertEquals(0 to 0, cell(p, POWER))
        assertEquals(2 to 0, cell(p, WIFI))
        assertEquals(2 to 1, cell(p, DND))
        assertEquals(0 to 2, cell(p, BLUETOOTH))
        assertEquals(2 to 2, cell(p, SLEEP_TIMER))
        assertEquals(0 to 3, cell(p, GAMEPAD))
        assertEquals(3 to 3, cell(p, SEARCH))
        assertEquals(0 to 4, cell(p, NOW_PLAYING))
        assertEquals(0 to 5, cell(p, VOLUME))
        assertEquals(6, GridPacker.rowCount(p))
        assertTrue(p.none { it.id == SETTINGS || it.id == EDIT })
    }

    @Test fun `top bar buttons are right aligned above the grid`() {
        val bar = GridPacker.pinned(shown(true))
        assertEquals(listOf(SETTINGS, HOME, RECENTS, EDIT), bar.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), bar.map { it.col })
        assertTrue(bar.all { it.row == GridPacker.TOP_ROW })
        val two = PanelLayout(default.items.map { if (it.id == RECENTS || it.id == EDIT) it else it.copy(size = TileSize.SMALL) })
        assertEquals(listOf(2, 3), GridPacker.pinned(two.visibleItems()).map { it.col })
    }

    @Test fun `the top bar holds at most four buttons`() {
        assertEquals(4, default.pinnedItems().size)
        assertEquals(default, default.resize(WIFI, TileSize.TOP))
        val room = default.resize(HOME, TileSize.SMALL)
        assertEquals(TileSize.TOP, room.resize(WIFI, TileSize.TOP).item(WIFI)?.size)
        assertEquals(default, default.resize(EDIT, TileSize.TOP)) // already there: no change
    }

    @Test fun `the panel closes the gap when nothing is playing`() {
        val p = packed(withNowPlaying = false)
        assertEquals(0 to 4, cell(p, VOLUME))
        assertEquals(5, GridPacker.rowCount(p))
    }

    @Test fun `hidden controls are not placed`() {
        val ids = packed(true).map { it.id }
        assertFalse(SCREENSHOT in ids)
        assertTrue(WIFI in ids)
        assertFalse(HOME in ids) // pinned to the top bar, not on the grid
    }

    @Test fun `no two controls overlap and all stay inside the columns`() {
        val everything = PanelLayout(default.items.map { it.copy(visible = true) })
        for (layout in listOf(default, everything)) {
            val cells = HashSet<Pair<Int, Int>>()
            for (p in GridPacker.pack(layout.visibleItems())) {
                assertTrue(p.col >= 0 && p.col + p.cols <= GridPacker.COLUMNS)
                for (r in p.row until p.row + p.rows) for (c in p.col until p.col + p.cols) {
                    assertTrue("cell $c,$r used twice", cells.add(c to r))
                }
            }
        }
    }

    @Test fun `every size can be given to every control`() {
        for (size in TileSize.entries) {
            val layout = PanelLayout(default.items.map { it.copy(size = size, visible = true) })
            val expected = if (size == TileSize.TOP) 0 else layout.items.size
            assertEquals(expected, GridPacker.pack(layout.items).size)
        }
    }

    @Test fun `small controls fill holes beside bigger ones`() {
        val items = listOf(
            ControlItem(POWER, TileSize.SMALL),
            ControlItem(WIFI, TileSize.WIDE),
            ControlItem(DND, TileSize.SMALL),
        )
        val p = GridPacker.pack(items)
        assertEquals(0 to 0, cell(p, POWER))
        assertEquals(1 to 0, cell(p, WIFI))
        assertEquals(3 to 0, cell(p, DND))
    }

    // --- focus -----------------------------------------------------------------------------

    private fun go(p: List<Placed>, from: TileId, vararg dirs: Dir): TileId? {
        var at: TileId? = from
        for (d in dirs) at = GridNav.neighbour(p, at ?: return null, d)
        return at
    }

    @Test fun `d-pad walks the default layout`() {
        val p = nav(true)
        assertEquals(WIFI, go(p, POWER, Dir.RIGHT))
        assertEquals(DND, go(p, POWER, Dir.RIGHT, Dir.DOWN))
        assertEquals(POWER, go(p, POWER, Dir.RIGHT, Dir.DOWN, Dir.LEFT))
        assertEquals(BLUETOOTH, go(p, POWER, Dir.DOWN))
        assertEquals(SLEEP_TIMER, go(p, BLUETOOTH, Dir.RIGHT))
        assertEquals(GAMEPAD, go(p, BLUETOOTH, Dir.DOWN))
        assertEquals(SLEEP_TIMER, go(p, SEARCH, Dir.UP))
        assertEquals(NOW_PLAYING, go(p, LOCK, Dir.DOWN))
        assertEquals(VOLUME, go(p, NOW_PLAYING, Dir.DOWN))
        assertEquals(ACCESSIBILITY, go(p, NOW_PLAYING, Dir.UP))
    }

    @Test fun `the top bar connects to the grid in both directions`() {
        val p = nav(true)
        assertEquals(SETTINGS, go(p, POWER, Dir.UP))
        assertEquals(POWER, go(p, SETTINGS, Dir.DOWN))
        assertEquals(POWER, go(p, HOME, Dir.DOWN))
        assertEquals(WIFI, go(p, RECENTS, Dir.DOWN))
        assertEquals(WIFI, go(p, EDIT, Dir.DOWN))
        assertEquals(RECENTS, go(p, EDIT, Dir.LEFT))
        assertEquals(EDIT, go(p, SETTINGS, Dir.RIGHT, Dir.RIGHT, Dir.RIGHT))
    }

    @Test fun `focus steps over a hidden now playing control`() {
        val p = nav(false)
        assertEquals(VOLUME, go(p, LOCK, Dir.DOWN))
        assertEquals(ACCESSIBILITY, go(p, VOLUME, Dir.UP))
    }

    @Test fun `focus is trapped at the outer edges`() {
        val p = nav(true)
        assertNull(go(p, SETTINGS, Dir.UP))
        assertNull(go(p, SETTINGS, Dir.LEFT))
        assertNull(go(p, EDIT, Dir.RIGHT))
        assertNull(go(p, POWER, Dir.LEFT))
        assertNull(go(p, WIFI, Dir.RIGHT))
        assertNull(go(p, NOW_PLAYING, Dir.LEFT))
        assertNull(go(p, VOLUME, Dir.DOWN))
    }

    @Test fun `every shown control is reachable from the first one`() {
        val everything = PanelLayout(default.items.map { it.copy(visible = true) })
        val shuffled = PanelLayout(everything.items.reversed().map { it.copy(size = TileSize.entries[it.id.ordinal % 6]) })
        for (layout in listOf(default, everything, shuffled)) for (np in listOf(false, true)) {
            val p = nav(np, layout)
            val seen = mutableSetOf(p.first().id)
            val queue = ArrayDeque(listOf(p.first().id))
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                for (d in Dir.entries) GridNav.neighbour(p, at, d)?.let { if (seen.add(it)) queue.add(it) }
            }
            assertEquals(p.map { it.id }.toSet(), seen)
        }
    }

    // --- layout edits ----------------------------------------------------------------------

    @Test fun `encode and decode round trip`() {
        val edited = default.resize(WIFI, TileSize.TALL).setVisible(DND, false).move(HOME, -1)
        assertEquals(edited, PanelLayout.decode(edited.encode()))
    }

    @Test fun `unusable stored text falls back to the default`() {
        assertEquals(default, PanelLayout.decode(null))
        assertEquals(default, PanelLayout.decode(""))
        assertEquals(default, PanelLayout.decode("nonsense,POWER:HUGE:1,FOO:SMALL:1"))
        assertEquals(default, PanelLayout.decode("POWER:SMALL:0"))
    }

    @Test fun `decode drops repeats and appends controls it has never seen as hidden`() {
        val l = PanelLayout.decode("WIFI:LARGE:1,WIFI:SMALL:1,POWER:WIDE:1")
        assertEquals(listOf(WIFI, POWER), l.items.take(2).map { it.id })
        assertEquals(TileSize.LARGE, l.item(WIFI)?.size)
        assertEquals(TileId.entries.size, l.items.size)
        assertTrue(l.items.drop(2).none { it.visible })
    }

    @Test fun `resize steps through the sizes and wraps`() {
        val small = default.resize(HOME, TileSize.SMALL)
        assertEquals(TileSize.WIDE, small.resizeBy(HOME, 1).item(HOME)?.size)
        assertEquals(TileSize.TOP, small.resizeBy(HOME, -1).item(HOME)?.size)
        assertEquals(TileSize.SMALL, small.resizeBy(HOME, 6).item(HOME)?.size)
    }

    @Test fun `every control can be resized to every size`() {
        // Start with an empty top bar so the four-button limit cannot get in the way.
        val roomy = PanelLayout(default.items.map { if (it.size == TileSize.TOP) it.copy(size = TileSize.SMALL) else it })
        for (item in roomy.items) for (size in TileSize.entries) {
            assertEquals(size, roomy.resize(item.id, size).item(item.id)?.size)
        }
    }

    @Test fun `hiding keeps the slot and showing moves the control to the end`() {
        val hidden = default.setVisible(DND, false)
        assertEquals(default.items.map { it.id }, hidden.items.map { it.id })
        assertFalse(hidden.item(DND)!!.visible)
        val shown = hidden.setVisible(DND, true)
        assertEquals(DND, shown.items.last().id)
        assertTrue(shown.item(DND)!!.visible)
    }

    @Test fun `move swaps with the next visible neighbour and skips hidden ones`() {
        val l = PanelLayout(
            listOf(
                ControlItem(WIFI, TileSize.SMALL),
                ControlItem(DND, TileSize.SMALL, visible = false),
                ControlItem(HOME, TileSize.SMALL),
            ),
        )
        assertEquals(listOf(HOME, DND, WIFI), l.move(WIFI, 1).items.map { it.id })
        assertEquals(l, l.move(WIFI, -1))
        assertEquals(l, l.move(HOME, 1))
        assertEquals(l, l.move(DND, 1))
    }

    @Test fun `the default layout lists every control exactly once`() {
        assertEquals(TileId.entries.toSet(), default.items.map { it.id }.toSet())
        assertEquals(TileId.entries.size, default.items.size)
        assertTrue(RECENTS in default.pinnedItems().map { it.id })
    }
}
