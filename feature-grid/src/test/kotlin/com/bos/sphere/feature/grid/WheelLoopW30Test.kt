package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 30 (drawerLoop): the pure math of the looping app drawer ring (HOM-01, INP-18). */
class WheelLoopW30Test {
    private val eps = 1e-3f
    private val margin = WheelGeometry.LOOP_MARGIN_DEG

    /** [n] groups of one spoke each: every group is gap + step = 20 wide. */
    private fun ring(n: Int, perGroup: Int = 1) = WheelGeometry.layout(List(n) { perGroup }, perSpoke = 4, spokeStep = 15f, gap = 5f)

    @Test fun HOM_01_wrapIndex_is_cyclic_for_any_integer() {
        assertEquals(26, WheelGeometry.wrapIndex(-1, 27))
        assertEquals(0, WheelGeometry.wrapIndex(27, 27))
        assertEquals(1, WheelGeometry.wrapIndex(55, 27))
        assertEquals(0, WheelGeometry.wrapIndex(-27, 27))
        assertEquals(0, WheelGeometry.wrapIndex(5, 1))
        assertEquals(0, WheelGeometry.wrapIndex(5, 0))
    }

    @Test fun HOM_01_shortestSteps_goes_the_short_way_round_the_seam() {
        assertEquals(1, WheelGeometry.shortestSteps(26, 0, 27))   // # -> A is one step on
        assertEquals(-1, WheelGeometry.shortestSteps(0, 26, 27))  // A -> # is one step back
        assertEquals(-2, WheelGeometry.shortestSteps(1, 26, 27))  // B -> # passes A
        assertEquals(13, WheelGeometry.shortestSteps(0, 13, 27))
        assertEquals(-13, WheelGeometry.shortestSteps(0, 14, 27))
        assertEquals(2, WheelGeometry.shortestSteps(0, 2, 4))     // a tie goes forward
        assertEquals(0, WheelGeometry.shortestSteps(0, 0, 1))
    }

    @Test fun HOM_01_loops_only_when_the_ring_is_wider_than_the_drawn_window() {
        assertFalse(WheelGeometry.loops(ring(1)))
        assertFalse(WheelGeometry.loops(ring(2)))
        assertFalse(WheelGeometry.loops(ring(5)))                 // 100 < 90 + 60
        assertFalse(WheelGeometry.loops(ring(8)))                 // 160 < 150 + one group (20): a group would be drawn twice
        assertTrue(WheelGeometry.loops(ring(9)))                  // 180 >= 170
        assertTrue(WheelGeometry.loops(ring(27)))
        assertFalse(WheelGeometry.loops(WheelLayout(emptyList())))
    }

    @Test fun HOM_01_angle_to_group_with_wrap_takes_the_nearest_winding() {
        val l = ring(27)
        // From A centred, the rotation for '#' (last) is one group back, not 26 groups forward.
        val a = WheelGeometry.rotationToCenter(l, 0)
        val hash = WheelGeometry.rotationToGroup(l, 26, a)
        assertEquals(a + 20f, hash, eps)
        assertEquals(26, WheelGeometry.focusedSegment(l, hash))
        // ... and from '#' to A is one group the other way.
        assertEquals(hash - 20f, WheelGeometry.rotationToGroup(l, 0, hash), eps)
        // Index arithmetic wraps too.
        assertEquals(WheelGeometry.rotationToGroup(l, 0, a), WheelGeometry.rotationToGroup(l, 27, a), eps)
        // Far windings are reduced: from many turns away the nearest winding is still within half a turn.
        val far = a - 5 * l.span
        assertEquals(far, WheelGeometry.rotationToGroup(l, 0, far), eps)
    }

    @Test fun HOM_01_focusedSegment_wraps_past_both_ends() {
        val l = ring(27)
        for (i in 0 until 27) {
            val base = WheelGeometry.rotationToCenter(l, i)
            assertEquals(i, WheelGeometry.focusedSegment(l, base))
            assertEquals(i, WheelGeometry.focusedSegment(l, base - l.span))
            assertEquals(i, WheelGeometry.focusedSegment(l, base + 3 * l.span))
        }
        // Rotating past Z ('#' is index 26): Z, #, then A, B ...
        val z = WheelGeometry.rotationToCenter(l, 25)
        assertEquals(listOf(25, 26, 0, 1, 2), (0..4).map { WheelGeometry.focusedSegment(l, z - 20f * it) })
        // ... and back from A: '#' then Z.
        val a = WheelGeometry.rotationToCenter(l, 0)
        assertEquals(listOf(0, 26, 25), (0..2).map { WheelGeometry.focusedSegment(l, a + 20f * it) })
    }

    @Test fun HOM_01_wrapRotation_is_the_same_picture_and_bounded() {
        val l = ring(27)
        for (r in listOf(-5000f, -541f, -1f, 0f, 3f, 540f, 1234.5f)) {
            val w = WheelGeometry.wrapRotation(r, l)
            assertTrue(w >= 0f && w < l.span)
            assertEquals(WheelGeometry.focusedSegment(l, r), WheelGeometry.focusedSegment(l, w))
        }
        val short = ring(2)
        assertEquals(123f, WheelGeometry.wrapRotation(123f, short), eps) // no loop, unchanged
    }

    @Test fun HOM_01_visible_window_has_cyclic_neighbours_for_every_ring_size() {
        for (n in listOf(1, 2, 5, 27, 40)) {
            val l = ring(n)
            for (i in 0 until n) {
                val r = WheelGeometry.rotationToGroup(l, i, 0f)
                val slots = WheelGeometry.visibleSegments(l, r, margin)
                // Each group at most once, whatever the ring size.
                assertEquals("n=$n i=$i duplicates", slots.map { it.index }.distinct().size, slots.size)
                assertTrue("n=$n the focused group is drawn", slots.any { it.index == i })
                if (WheelGeometry.loops(l)) {
                    // Both neighbours (cyclically) are drawn, and the one before and after those as well within the margin.
                    val prev = WheelGeometry.wrapIndex(i - 1, n); val next = WheelGeometry.wrapIndex(i + 1, n)
                    assertTrue("n=$n i=$i previous $prev drawn", slots.any { it.index == prev })
                    assertTrue("n=$n i=$i next $next drawn", slots.any { it.index == next })
                } else {
                    assertEquals("n=$n draws every group once", n, slots.size)
                }
            }
        }
    }

    @Test fun HOM_01_at_A_the_hash_precedes_it_and_Z_before_that() {
        val l = ring(27)
        val slots = WheelGeometry.visibleSegments(l, WheelGeometry.rotationToCenter(l, 0), 0f)
        // Ring order, as drawn from the leading edge: Z (25), # (26), A (0), B (1) ...
        val order = slots.sortedBy { l.segments[it.index].start + it.copy * l.span }.map { it.index }
        assertEquals(listOf(25, 26, 0, 1, 2), order.take(5))
        // The # copy sits before A in the quadrant (copy -1), so its band angle is below A's.
        val hash = slots.first { it.index == 26 }
        assertEquals(-1, hash.copy)
        val aAngle = l.segments[0].center + WheelGeometry.rotationToCenter(l, 0)
        val hashAngle = l.segments[26].center - l.span + WheelGeometry.rotationToCenter(l, 0)
        assertEquals(45f, aAngle, eps)
        assertEquals(25f, hashAngle, eps)
        // At Z the # and A follow it.
        val atZ = WheelGeometry.visibleSegments(l, WheelGeometry.rotationToCenter(l, 25), 0f)
        assertEquals(listOf(23, 24, 25, 26, 0), atZ.sortedBy { l.segments[it.index].start + it.copy * l.span }.map { it.index }.take(5))
    }

    @Test fun HOM_01_spokes_are_drawn_cyclically_and_never_twice() {
        val l = ring(27, perGroup = 2)
        val spokes = l.segments.flatMap { it.spokeAngles }
        val r = WheelGeometry.rotationToCenter(l, 0)
        val slots = WheelGeometry.visibleSpokes(spokes, l.span, true, r, margin)
        assertTrue("spokes of # are drawn before A", slots.any { it.copy == -1 && it.index >= spokes.size - 2 })
        assertEquals(slots.size, slots.distinct().size)
        assertEquals("the same spoke is never on screen twice", slots.map { it.index }.distinct().size, slots.size)
        // A small ring is not repeated.
        val small = ring(2)
        val sp = small.segments.flatMap { it.spokeAngles }
        val s = WheelGeometry.visibleSpokes(sp, small.span, false, WheelGeometry.initialRotation(small, 4f), margin)
        assertEquals(sp.size, s.size)
        assertTrue(s.all { it.copy == 0 })
    }

    @Test fun HOM_01_a_fling_that_crosses_the_seam_lands_on_the_right_group() {
        val l = ring(27)
        val start = WheelGeometry.rotationToCenter(l, 24)       // Y
        // Flung 4 groups on (80 degrees): Y -> Z -> # -> A -> B.
        val landed = start - 80f
        assertEquals(1, WheelGeometry.focusedSegment(l, landed))
        // The unbounded value is wrapped silently at rest and means the same group.
        val rest = WheelGeometry.wrapRotation(landed, l)
        assertEquals(1, WheelGeometry.focusedSegment(l, rest))
        // Settling then takes the nearest winding of the target group from the wrapped value: no long way round.
        val settle = WheelGeometry.rotationToGroup(l, 1, rest)
        assertTrue(kotlin.math.abs(settle - rest) <= l.span / 2f)
        assertEquals(1, WheelGeometry.focusedSegment(l, settle))
    }

    @Test fun HOM_01_scrub_across_the_seam_is_continuous_and_one_turn_per_sweep() {
        val l = ring(27)
        // Absolute mapping: the sweep from f=0 to f=1 is one whole ring, A centred at both ends.
        val a0 = WheelGeometry.scrubRotation(l, 0f)
        val a1 = WheelGeometry.scrubRotation(l, 1f)
        assertEquals(0, WheelGeometry.focusedSegment(l, a0))
        assertEquals(0, WheelGeometry.focusedSegment(l, a1))
        // Just before the end the finger is on '#': the last group, one before the seam.
        assertEquals(26, WheelGeometry.focusedSegment(l, WheelGeometry.scrubRotation(l, 1f - 1f / 27f)))
        // Starting a scrub takes the winding nearest the current rotation, so it does not jump.
        val cur = WheelGeometry.rotationToCenter(l, 26) - 3 * l.span
        val n = WheelGeometry.nearestWinding(a1, cur, l.span)
        assertTrue(kotlin.math.abs(n - cur) <= l.span / 2f + eps)
        assertEquals(0, WheelGeometry.focusedSegment(l, n))
    }

    @Test fun HOM_01_a_ring_that_fits_the_quadrant_keeps_the_clamped_centred_arrangement() {
        val l = ring(2)
        assertFalse(WheelGeometry.loops(l))
        assertEquals(45f - l.span / 2f, WheelGeometry.initialRotation(l, 4f), eps)
        // rotationToGroup falls back to the clamped centring and never leaves the bounds.
        for (i in 0..1) {
            val r = WheelGeometry.rotationToGroup(l, i, 0f)
            assertTrue(r in WheelGeometry.rotationBounds(l))
        }
        val one = ring(1)
        assertEquals(0, WheelGeometry.focusedSegment(one, WheelGeometry.initialRotation(one, 4f)))
    }

    // ---- Wave 30 review (adversarial): defects found after the first implementation.

    @Test fun HOM_01_w30r_no_group_is_ever_drawn_twice_at_once() {
        for (n in 2..60) for (per in 1..4) {
            val l = ring(n, per)
            var r = -600f
            while (r < 600f) {
                val segs = WheelGeometry.visibleSegments(l, r, margin).map { it.index }
                assertEquals("n=$n per=$per rotation=$r drew a group twice: $segs", segs.size, segs.toSet().size)
                val sp = l.segments.flatMap { it.spokeAngles }
                val spokes = WheelGeometry.visibleSpokes(sp, l.span, WheelGeometry.loops(l), r, margin).map { it.index }
                assertEquals("n=$n per=$per rotation=$r drew a spoke twice: $spokes", spokes.size, spokes.toSet().size)
                r += 3.7f
            }
        }
    }

    @Test fun HOM_01_w30r_a_ring_that_does_not_loop_has_no_copy_slots_for_the_section_nodes() {
        // The accessibility nodes ask for the sections with no margin; a ring narrower than the drawn window is drawn once, so they must
        // not get a second copy at the other end (a ghost node where nothing is drawn).
        for (n in 2..8) {
            val l = ring(n)
            assertFalse("n=$n must not loop", WheelGeometry.loops(l))
            var r = -300f
            while (r < 300f) {
                assertTrue("n=$n rotation=$r", WheelGeometry.visibleSegments(l, r, 0f).all { it.copy == 0 })
                r += 5f
            }
        }
    }
}
