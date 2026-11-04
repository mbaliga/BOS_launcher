package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteMathTest {
    @Test
    fun `rings fill inner to outer with the planned capacities`() {
        assertEquals(0, PaletteMath.ringsFor(0))
        assertEquals(1, PaletteMath.ringsFor(4))
        assertEquals(2, PaletteMath.ringsFor(5))
        assertEquals(5, PaletteMath.ringsFor(PaletteMath.TOTAL))
        assertEquals(40, PaletteMath.TOTAL)
        val slots = PaletteMath.layout(12, Corner.TopLeft)
        assertEquals(listOf(4, 6, 2), (0..2).map { r -> slots.count { it.ring == r } })
    }

    @Test
    fun `tiles stay inside the quadrant facing the screen for every corner`() {
        for (c in Corner.entries) {
            val start = PaletteMath.quadrantStart(c)
            PaletteMath.layout(40, c).forEach {
                assertTrue(it.startDeg >= start && it.startDeg + it.sweepDeg <= start + 90f)
            }
        }
    }

    @Test
    fun `hit finds the tile at each tile centre and misses outside the rings`() {
        for (c in Corner.entries) {
            val slots = PaletteMath.layout(30, c)
            slots.forEach { assertEquals(it.index, PaletteMath.hit(PaletteMath.centre(it).x, PaletteMath.centre(it).y, slots)) }
            assertEquals(-1, PaletteMath.hit(-1f, -1f, slots))
        }
        // A point far beyond the outer ring is a miss unless slack allows it.
        val slots = PaletteMath.layout(8, Corner.TopLeft)
        assertEquals(-1, PaletteMath.hit(300f, 300f, slots))
    }

    @Test
    fun `slack lets a sliding thumb keep the nearest tile`() {
        val slots = PaletteMath.layout(8, Corner.TopLeft)
        val s = slots[0]
        val c = PaletteMath.centre(s)
        // 1 dp past the outer edge, in the gap before the next ring, along the same ray.
        val k = (s.outerDp + 1f) / s.midDp
        assertEquals(-1, PaletteMath.hit(c.x * k, c.y * k, slots, slackDp = 0f))
        assertEquals(s.index, PaletteMath.hit(c.x * k, c.y * k, slots, slackDp = 4f))
    }
}
