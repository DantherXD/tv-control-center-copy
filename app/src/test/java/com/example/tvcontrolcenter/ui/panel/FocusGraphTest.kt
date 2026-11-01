package com.example.tvcontrolcenter.ui.panel

import com.example.tvcontrolcenter.domain.model.TileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusGraphTest {

    private fun reachable(hasNowPlaying: Boolean): Set<TileId> {
        val seen = mutableSetOf(TileId.POWER)
        val queue = ArrayDeque(listOf(TileId.POWER))
        while (queue.isNotEmpty()) {
            val t = queue.removeFirst()
            for (d in Dir.entries) {
                val n = FocusGraph.neighbour(t, d, hasNowPlaying) ?: continue
                if (seen.add(n)) queue.add(n)
            }
        }
        return seen
    }

    @Test fun `every visible tile is reachable from power without now playing`() {
        assertEquals(FocusGraph.visibleTiles(false).toSet(), reachable(false))
    }

    @Test fun `every visible tile is reachable from power with now playing`() {
        assertEquals(FocusGraph.visibleTiles(true).toSet(), reachable(true))
    }

    @Test fun `hidden now playing tile is never a focus target`() {
        for (t in FocusGraph.visibleTiles(false)) for (d in Dir.entries) {
            assertNotEquals(TileId.NOW_PLAYING, FocusGraph.neighbour(t, d, false))
        }
    }

    @Test fun `no tile points at itself and none points at a hidden tile`() {
        for (np in listOf(false, true)) for (t in FocusGraph.visibleTiles(np)) for (d in Dir.entries) {
            val n = FocusGraph.neighbour(t, d, np) ?: continue
            assertNotEquals(t, n)
            assertTrue(n in FocusGraph.visibleTiles(np))
        }
    }

    @Test fun `focus is trapped at the outer edges`() {
        assertNull(FocusGraph.neighbour(TileId.POWER, Dir.UP, false))
        assertNull(FocusGraph.neighbour(TileId.SETTINGS, Dir.UP, false))
        assertNull(FocusGraph.neighbour(TileId.SETTINGS, Dir.LEFT, false))
        assertNull(FocusGraph.neighbour(TileId.SCREENSHOT, Dir.DOWN, false))
        assertNull(FocusGraph.neighbour(TileId.SEARCH, Dir.RIGHT, false))
        assertNull(FocusGraph.neighbour(TileId.VOLUME, Dir.DOWN, false))
    }

    @Test fun `power opens onto the quick actions on its left and back`() {
        assertEquals(TileId.DISPLAY, FocusGraph.neighbour(TileId.POWER, Dir.LEFT, false))
        assertEquals(TileId.POWER, FocusGraph.neighbour(TileId.DISPLAY, Dir.RIGHT, false))
        assertEquals(TileId.POWER, FocusGraph.neighbour(TileId.CAPTIONS, Dir.RIGHT, false))
    }

    @Test fun `quick actions form a two column grid`() {
        assertEquals(TileId.DISPLAY, FocusGraph.neighbour(TileId.SETTINGS, Dir.RIGHT, false))
        assertEquals(TileId.SOUND, FocusGraph.neighbour(TileId.SETTINGS, Dir.DOWN, false))
        assertEquals(TileId.SCREENSHOT, FocusGraph.neighbour(TileId.HOME, Dir.DOWN, false))
        assertEquals(TileId.NOTIFICATIONS, FocusGraph.neighbour(TileId.SCREENSHOT, Dir.RIGHT, false))
    }

    @Test fun `quick action rows connect to the matching main rows`() {
        assertEquals(TileId.BLUETOOTH, FocusGraph.neighbour(TileId.APPS, Dir.RIGHT, false))
        assertEquals(TileId.GAMEPAD, FocusGraph.neighbour(TileId.RECENTS, Dir.RIGHT, false))
        assertEquals(TileId.APPS, FocusGraph.neighbour(TileId.BLUETOOTH, Dir.LEFT, false))
        assertEquals(TileId.RECENTS, FocusGraph.neighbour(TileId.GAMEPAD, Dir.LEFT, false))
    }

    @Test fun `notifications lead to now playing only when it is shown`() {
        assertEquals(TileId.VOLUME, FocusGraph.neighbour(TileId.NOTIFICATIONS, Dir.RIGHT, false))
        assertEquals(TileId.NOW_PLAYING, FocusGraph.neighbour(TileId.NOTIFICATIONS, Dir.RIGHT, true))
    }

    @Test fun `quick action links are reciprocal within the grid`() {
        val grid = listOf(
            listOf(TileId.SETTINGS, TileId.DISPLAY), listOf(TileId.SOUND, TileId.CAPTIONS),
            listOf(TileId.SCREENSAVER, TileId.APPS), listOf(TileId.HOME, TileId.RECENTS),
            listOf(TileId.SCREENSHOT, TileId.NOTIFICATIONS),
        )
        for (row in grid) {
            assertEquals(row[1], FocusGraph.neighbour(row[0], Dir.RIGHT, false))
            assertEquals(row[0], FocusGraph.neighbour(row[1], Dir.LEFT, false))
        }
        for (col in 0..1) for (r in 0 until grid.size - 1) {
            assertEquals(grid[r + 1][col], FocusGraph.neighbour(grid[r][col], Dir.DOWN, false))
            assertEquals(grid[r][col], FocusGraph.neighbour(grid[r + 1][col], Dir.UP, false))
        }
    }

    @Test fun `top block order matches the reference layout`() {
        assertEquals(TileId.WIFI, FocusGraph.neighbour(TileId.POWER, Dir.RIGHT, false))
        assertEquals(TileId.DND, FocusGraph.neighbour(TileId.WIFI, Dir.DOWN, false))
        assertEquals(TileId.BLUETOOTH, FocusGraph.neighbour(TileId.POWER, Dir.DOWN, false))
        assertEquals(TileId.SLEEP_TIMER, FocusGraph.neighbour(TileId.BLUETOOTH, Dir.RIGHT, false))
    }

    @Test fun `bottom row goes through now playing only when it is shown`() {
        assertEquals(TileId.VOLUME, FocusGraph.neighbour(TileId.LOCK, Dir.DOWN, false))
        assertEquals(TileId.NOW_PLAYING, FocusGraph.neighbour(TileId.LOCK, Dir.DOWN, true))
        assertEquals(TileId.VOLUME, FocusGraph.neighbour(TileId.NOW_PLAYING, Dir.DOWN, true))
        assertEquals(TileId.NOW_PLAYING, FocusGraph.neighbour(TileId.VOLUME, Dir.UP, true))
    }

    @Test fun `restore keeps a visible tile and falls back otherwise`() {
        assertEquals(TileId.LOCK, FocusGraph.restore(TileId.LOCK, false))
        assertEquals(TileId.POWER, FocusGraph.restore(TileId.NOW_PLAYING, false))
        assertEquals(TileId.NOW_PLAYING, FocusGraph.restore(TileId.NOW_PLAYING, true))
        assertFalse(TileId.NOW_PLAYING in FocusGraph.visibleTiles(false))
    }
}
