package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** W26 arcDrawer: looped sections and the polar layout of the expanded drawer (pure JVM). */
class ArcDrawerMathTest {
    // ---- looped sections (clause 1)

    @Test fun ARC_01_sections_wrap_forward_past_the_last_into_the_first() {
        assertEquals(0, ArcMath.step(4, +1, 5))
        assertEquals(1, ArcMath.step(4, +2, 5))
        assertEquals(3, ArcMath.step(3, +5, 5))
    }

    @Test fun ARC_01_sections_wrap_backward_before_the_first_to_the_last() {
        assertEquals(4, ArcMath.step(0, -1, 5))
        assertEquals(3, ArcMath.step(0, -2, 5))
        assertEquals(2, ArcMath.step(2, -5, 5))
    }

    @Test fun ARC_01_one_section_stays_and_two_sections_alternate() {
        assertEquals(0, ArcMath.step(0, +1, 1)); assertEquals(0, ArcMath.step(0, -1, 1)); assertEquals(0, ArcMath.step(0, 7, 1))
        assertEquals(1, ArcMath.step(0, +1, 2)); assertEquals(0, ArcMath.step(1, +1, 2)); assertEquals(1, ArcMath.step(0, -1, 2))
        assertEquals(0, ArcMath.step(0, 1, 0)) // no sections: harmless
    }

    @Test fun ARC_01_many_sections_a_full_lap_returns_to_the_start_and_the_order_is_kept() {
        val n = 26
        var i = 5
        val seen = ArrayList<Int>()
        repeat(n) { seen += i; i = ArcMath.step(i, 1, n) }
        assertEquals(5, i)
        assertEquals((0 until n).map { (5 + it) % n }, seen)
    }

    @Test fun ARC_01_the_switcher_shows_the_first_section_repeated_after_the_last() {
        assertEquals(listOf(4, 0, 1), ArcMath.wedgeSections(4, 5))
        assertEquals(listOf(3, 4, 0), ArcMath.wedgeSections(3, 5))
        assertEquals(listOf(0, 1, 2), ArcMath.wedgeSections(0, 5))
        assertEquals(listOf(0), ArcMath.wedgeSections(0, 1))
        assertEquals(listOf(1, 0), ArcMath.wedgeSections(1, 2))
        assertEquals(emptyList<Int>(), ArcMath.wedgeSections(0, 0))
    }

    @Test fun ARC_02_wedge_hit_test_splits_the_quarter_in_thirds() {
        assertEquals(0, ArcMath.wedgeAt(5f, 3)); assertEquals(1, ArcMath.wedgeAt(45f, 3)); assertEquals(2, ArcMath.wedgeAt(89f, 3))
        assertEquals(-1, ArcMath.wedgeAt(-3f, 3))
    }

    // ---- polar layout (clause 3)

    private val w = 1080f; private val h = 2340f; private val cell = 150f; private val gap = 8f; private val inset = 110f

    private fun rings(anchor: Corner, width: Float = w, height: Float = h, hub: Float = 420f) =
        ArcMath.rings(anchor, if (ArcMath.onRight(anchor)) width - inset else inset, if (ArcMath.onBottom(anchor)) height - inset else inset, width, height, hub, cell, gap, 8)

    private fun corner(a: Corner, width: Float = w, height: Float = h) =
        (if (ArcMath.onRight(a)) width - inset else inset) to (if (ArcMath.onBottom(a)) height - inset else inset)

    @Test fun ARC_03_rings_stack_outward_one_cell_apart_and_outer_rings_hold_more_cells() {
        val r = rings(Corner.BottomLeft)
        assertTrue("several rings", r.size >= 4)
        for (i in 1 until r.size) assertEquals(cell + gap, r[i].radius - r[i - 1].radius, 1e-3f)
        for (i in 1 until minOf(r.size, 3)) assertTrue("ring $i holds at least as many as ${i - 1}", r[i].capacity >= r[i - 1].capacity)
        assertTrue(r[2].capacity > r[0].capacity)
        assertTrue(r.first().capacity >= 1)
    }

    @Test fun ARC_03_cells_of_a_ring_do_not_overlap_and_rings_do_not_touch() {
        for (a in Corner.entries) {
            val (cx, cy) = corner(a)
            val rs = rings(a)
            for (ring in rs) {
                val count = ring.capacity + 3
                val sq = (0 until count).map { j ->
                    val u = ArcMath.cellU(ring, j, 0f)
                    ArcMath.cellCorners(ArcMath.pointX(a, cx, ring.radius, u), ArcMath.pointY(a, cy, ring.radius, u), cell, ArcMath.cellRotation(a, u))
                }
                for (i in 0 until count - 1) assertTrue("$a ring ${ring.index} cells $i/${i + 1} overlap", !overlap(sq[i], sq[i + 1]))
            }
            for (i in 1 until rs.size) {
                val inner = rs[i - 1].radius + cell / 2f
                val outer = rs[i].radius - cell / 2f
                assertTrue(outer - inner >= gap - 1e-3f)
            }
        }
    }

    @Test fun ARC_03_every_reachable_cell_stays_on_screen_and_inside_the_hit_area() {
        for ((width, height) in listOf(w to h, h to w)) for (a in Corner.entries) {
            val (cx, cy) = corner(a, width, height)
            for (ring in rings(a, width, height)) {
                for (j in 0 until ring.capacity) {
                    val u = ArcMath.cellU(ring, j, 0f)
                    val c = ArcMath.cellCorners(ArcMath.pointX(a, cx, ring.radius, u), ArcMath.pointY(a, cy, ring.radius, u), cell, ArcMath.cellRotation(a, u))
                    for (i in 0 until 4) {
                        assertTrue("$a ring ${ring.index} cell $j x=${c[2 * i]}", c[2 * i] in -0.5f..width + 0.5f)
                        assertTrue("$a ring ${ring.index} cell $j y=${c[2 * i + 1]}", c[2 * i + 1] in -0.5f..height + 0.5f)
                    }
                    assertTrue(u in 0f..90f)
                }
            }
        }
    }

    @Test fun ARC_03_the_left_handed_corner_is_the_exact_mirror_of_the_right_handed_one() {
        val l = rings(Corner.BottomLeft); val r = rings(Corner.BottomRight)
        assertEquals(l.size, r.size)
        for (i in l.indices) {
            assertEquals(l[i].capacity, r[i].capacity); assertEquals(l[i].radius, r[i].radius, 1e-3f); assertEquals(l[i].lo, r[i].lo, 1e-3f)
            val (lx, ly) = corner(Corner.BottomLeft); val (rx, ry) = corner(Corner.BottomRight)
            for (j in 0 until l[i].capacity) {
                val u = ArcMath.cellU(l[i], j, 0f)
                assertEquals(ArcMath.pointX(Corner.BottomLeft, lx, l[i].radius, u) - lx, rx - ArcMath.pointX(Corner.BottomRight, rx, r[i].radius, u), 1e-2f)
                assertEquals(ArcMath.pointY(Corner.BottomLeft, ly, l[i].radius, u), ArcMath.pointY(Corner.BottomRight, ry, r[i].radius, u), 1e-3f)
                assertEquals(-ArcMath.cellRotation(Corner.BottomLeft, u), ArcMath.cellRotation(Corner.BottomRight, u), 1e-3f)
            }
        }
    }

    @Test fun ARC_03_cells_never_read_upside_down() {
        for (a in Corner.entries) for (u in 0..90 step 5) assertTrue(abs(ArcMath.cellRotation(a, u.toFloat())) <= 180f)
        for (a in listOf(Corner.BottomLeft, Corner.BottomRight)) for (u in 0..90 step 5) assertTrue("$a $u", abs(ArcMath.cellRotation(a, u.toFloat())) <= 90.01f)
    }

    @Test fun ARC_03_counts_per_ring_scroll_bounds_and_visible_range() {
        val ring = rings(Corner.BottomLeft)[1]
        val cap = ring.capacity
        // Fewer cells than fit: no scrolling, all visible.
        assertEquals(0f..0f, ArcMath.offsetBounds(ring, cap - 1))
        assertEquals(0 until cap - 1, ArcMath.visibleCells(ring, cap - 1, 0f).let { it.first..it.last })
        // Many cells: the last one can be brought to the far edge, and not further.
        val count = cap * 4
        val b = ArcMath.offsetBounds(ring, count)
        assertEquals(ring.window - count * ring.pitchDeg, b.start, 1e-3f)
        assertEquals(0f, b.endInclusive, 0f)
        assertEquals(b.start, ArcMath.clampOffset(ring, count, -9999f), 0f)
        assertEquals(0f, ArcMath.clampOffset(ring, count, 50f), 0f)
        val last = ArcMath.visibleCells(ring, count, b.start)
        assertEquals(count - 1, last.last)
        assertTrue(ArcMath.visibleCells(ring, count, 0f).first == 0)
        // Showing a far cell moves the offset until it is inside the window.
        val o = ArcMath.offsetToShow(ring, count, 0f, count - 1)
        assertEquals(b.start, o, 1e-3f)
        val o2 = ArcMath.offsetToShow(ring, count, o, 0)
        assertEquals(0f, o2, 1e-3f)
    }

    @Test fun ARC_03_the_primary_apps_are_reachable_in_portrait_and_landscape() {
        for ((width, height) in listOf(1080f to 2340f, 2340f to 1080f)) {
            val rs = ArcMath.rings(Corner.BottomRight, width - 110f, height - 110f, width, height, 420f, 150f, 8f, 8)
            assertTrue("rings in ${width}x$height", rs.isNotEmpty())
            val first = rs.first()
            assertTrue("first ring shows a cell at its leading edge", ArcMath.visibleCells(first, 30, 0f).first == 0)
            // The innermost ring is reachable by a thumb: its cells sit within 2.2x the hub radius of the corner.
            assertTrue(first.radius < 420f * 2.2f)
        }
    }

    @Test fun ARC_03_polar_round_trip() {
        val (cx, cy) = corner(Corner.BottomLeft)
        val x = ArcMath.pointX(Corner.BottomLeft, cx, 500f, 33f); val y = ArcMath.pointY(Corner.BottomLeft, cy, 500f, 33f)
        assertEquals(33f, ArcMath.uOf(Corner.BottomLeft, cx, cy, x, y), 1e-2f)
        assertEquals(500f, hypot(x - cx, y - cy), 1e-2f)
        val (rx, ry) = corner(Corner.BottomRight)
        val x2 = ArcMath.pointX(Corner.BottomRight, rx, 500f, 33f); val y2 = ArcMath.pointY(Corner.BottomRight, ry, 500f, 33f)
        assertEquals(33f, ArcMath.uOf(Corner.BottomRight, rx, ry, x2, y2), 1e-2f)
    }

    @Test fun ARC_03_ring_hit_test_by_radius() {
        val rs = rings(Corner.BottomLeft)
        assertEquals(0, ArcMath.ringAt(rs, rs[0].radius + 10f, cell, gap))
        assertEquals(2, ArcMath.ringAt(rs, rs[2].radius - 20f, cell, gap))
        assertEquals(-1, ArcMath.ringAt(rs, 10f, cell, gap))
    }

    // SAT on two convex quads (x0,y0,..x3,y3).
    private fun overlap(a: FloatArray, b: FloatArray): Boolean {
        for (poly in listOf(a, b)) for (i in 0 until 4) {
            val x1 = poly[2 * i]; val y1 = poly[2 * i + 1]; val x2 = poly[2 * ((i + 1) % 4)]; val y2 = poly[2 * ((i + 1) % 4) + 1]
            val nx = y1 - y2; val ny = x2 - x1
            fun range(p: FloatArray): Pair<Float, Float> { val ds = (0 until 4).map { p[2 * it] * nx + p[2 * it + 1] * ny }; return ds.min() to ds.max() }
            val (a0, a1) = range(a); val (b0, b1) = range(b)
            if (a1 <= b0 || b1 <= a0) return false
        }
        return true
    }
}
