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

    @Test fun `focus is trapped at the panel edges`() {
        assertNull(FocusGraph.neighbour(TileId.POWER, Dir.LEFT, false))
        assertNull(FocusGraph.neighbour(TileId.POWER, Dir.UP, false))
        assertNull(FocusGraph.neighbour(TileId.SEARCH, Dir.RIGHT, false))
        assertNull(FocusGraph.neighbour(TileId.VOLUME, Dir.DOWN, false))
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
