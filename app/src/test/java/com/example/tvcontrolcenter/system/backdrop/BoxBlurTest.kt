package com.example.tvcontrolcenter.system.backdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBlurTest {

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun red(c: Int) = (c shr 16) and 0xFF

    @Test fun `a flat image stays flat`() {
        val px = IntArray(40 * 30) { gray(123) }
        boxBlur(px, 40, 30, radius = 5)
        assertTrue(px.all { it == gray(123) })
    }

    @Test fun `a bright dot spreads out but stays centred and keeps its light`() {
        val w = 41
        val h = 41
        val px = IntArray(w * h) { gray(0) }
        px[20 * w + 20] = gray(255)
        boxBlur(px, w, h, radius = 3)
        val centre = red(px[20 * w + 20])
        assertTrue("centre should be dimmer than the dot", centre in 1..254)
        assertTrue("neighbours should light up", red(px[20 * w + 22]) > 0)
        assertEquals(red(px[20 * w + 17]), red(px[20 * w + 23]))   // symmetric left/right
        assertEquals(red(px[17 * w + 20]), red(px[23 * w + 20]))   // symmetric up/down
        val total = px.sumOf { red(it) }
        // 8-bit pixels round at every one of the six passes, so a lone dot loses some light.
        assertTrue("most of the light is kept, got $total", total in 150..270)
    }

    @Test fun `a hard edge becomes a ramp`() {
        val w = 60
        val h = 4
        val px = IntArray(w * h) { if (it % w < 30) gray(0) else gray(255) }
        boxBlur(px, w, h, radius = 4)
        val row = (0 until w).map { red(px[it]) }
        assertEquals(0, row.first())
        assertEquals(255, row.last())
        assertTrue(row.zipWithNext().all { (a, b) -> b >= a })        // never goes back down
        assertTrue(row[29] in 1..254 && row[30] in 1..254)             // the step is softened
    }

    @Test fun `blur keeps every pixel opaque and handles tiny images`() {
        val px = IntArray(1) { gray(77) }
        boxBlur(px, 1, 1, radius = 6)
        assertEquals(gray(77), px[0])
        val strip = IntArray(5) { gray(it * 50) }
        boxBlur(strip, 5, 1, radius = 9)
        assertTrue(strip.all { (it ushr 24) == 0xFF })
    }

    @Test fun `zero radius does nothing`() {
        val px = IntArray(9) { gray(it * 20) }
        val copy = px.copyOf()
        boxBlur(px, 3, 3, radius = 0)
        assertTrue(px.contentEquals(copy))
    }
}
