package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** W28 arcShortcuts: the pure geometry of the alphabet wheel, its app fan and the shortcut placement. */
class ArcDrawerMathTest {
    // ---- the looped alphabet

    @Test fun ARC_01_the_ring_is_a_to_z_then_hash_and_hash_sits_between_z_and_a() {
        assertEquals(27, AlphabetRing.COUNT)
        assertEquals("A", AlphabetRing.LETTERS.first()); assertEquals("Z", AlphabetRing.LETTERS[25]); assertEquals("#", AlphabetRing.LETTERS[26])
        assertEquals("#", AlphabetRing.LETTERS[AlphabetRing.step(AlphabetRing.indexOf("Z"), 1)])
        assertEquals("A", AlphabetRing.LETTERS[AlphabetRing.step(AlphabetRing.indexOf("#"), 1)])
        assertEquals("Z", AlphabetRing.LETTERS[AlphabetRing.step(AlphabetRing.indexOf("A"), -2)])
    }

    @Test fun ARC_01_the_alphabet_loops_in_both_directions_with_no_ends() {
        assertEquals(0, AlphabetRing.step(26, 1)); assertEquals(26, AlphabetRing.step(0, -1))
        assertEquals(5, AlphabetRing.step(5, 27)); assertEquals(5, AlphabetRing.step(5, -54))
        // The letters seen at the corner going round read X Y Z # A B C when the ring is turned to Z.
        val z = AlphabetRing.indexOf("Z").toFloat()
        val seen = AlphabetRing.LETTERS.indices.filter { AlphabetRing.letterU(z, it) in -5f..95f }.sortedByDescending { AlphabetRing.letterU(z, it) }.map { AlphabetRing.LETTERS[it] }
        val s = seen.joinToString("")
        assertTrue("the visible letters run X Y Z # A B: $s", s.contains("XYZ#AB") || s.contains("YZ#ABC") || s.contains("Z#ABCD") || s.contains("WXYZ#"))
    }

    @Test fun ARC_01_labels_fall_into_letters_with_hash_for_everything_else() {
        assertEquals("A", arcLetterOf("Alarm")); assertEquals("E", arcLetterOf("Énergie")); assertEquals("#", arcLetterOf("1Password"))
        assertEquals("#", arcLetterOf("")); assertEquals("#", arcLetterOf("  ")); assertEquals("#", arcLetterOf("日本語")); assertEquals("Z", arcLetterOf(" zen"))
    }

    @Test fun ARC_01_a_typed_character_jumps_to_its_letter() {
        assertEquals(0, AlphabetRing.indexOfChar('a')); assertEquals(25, AlphabetRing.indexOfChar('Z')); assertEquals(26, AlphabetRing.indexOfChar('7')); assertEquals(26, AlphabetRing.indexOfChar('#'))
        assertEquals(-1, AlphabetRing.indexOfChar('é')); assertEquals(-1, AlphabetRing.indexOfChar(' '))
    }

    @Test fun ARC_01_the_selected_letter_sits_at_the_selected_angle_and_its_neighbours_a_step_either_side() {
        for (i in 0 until 27) {
            assertEquals(AlphabetRing.SELECT_U, AlphabetRing.letterU(i.toFloat(), i), 1e-4f)
            assertEquals(i, AlphabetRing.selected(i.toFloat()))
            assertEquals(AlphabetRing.SELECT_U - AlphabetRing.STEP_DEG, AlphabetRing.letterU(i.toFloat(), AlphabetRing.wrap(i + 1)), 1e-4f)
        }
    }

    @Test fun ARC_01_hit_testing_finds_the_letter_under_a_finger_and_nothing_between_letters_off_the_ring() {
        val rot = 3f
        for (i in 0 until 27) assertEquals(i, AlphabetRing.letterAt(rot, AlphabetRing.letterU(rot, i)))
        assertEquals(3, AlphabetRing.letterAt(rot, AlphabetRing.SELECT_U + 1f))
        assertEquals("a point just past half a step belongs to the next letter", 2, AlphabetRing.letterAt(rot, AlphabetRing.SELECT_U + AlphabetRing.STEP_DEG / 2f + 0.5f))
    }

    @Test fun ARC_01_dragging_along_the_ring_rotates_it_and_the_shortest_way_to_a_letter_wraps() {
        assertEquals(1f, AlphabetRing.rotateBy(0f, AlphabetRing.STEP_DEG), 1e-4f)
        assertEquals(-2f, AlphabetRing.rotateBy(0f, -2 * AlphabetRing.STEP_DEG), 1e-4f)
        assertEquals(27f, AlphabetRing.rotationFor(25f, 0), 1e-4f) // from Z on to A: two steps forward, not 25 back
        assertEquals(-1f, AlphabetRing.rotationFor(0f, 26), 1e-4f)
        assertEquals(26, AlphabetRing.selected(-1f))
    }

    // ---- corner mapping

    @Test fun ARC_02_the_right_hand_corner_is_the_mirror_image_of_the_left() {
        val w = 400f; val h = 800f
        for (u in listOf(0f, 20f, 45f, 90f)) {
            val lx = ArcMath.pointX(Corner.BottomLeft, 30f, 100f, u); val rx = ArcMath.pointX(Corner.BottomRight, w - 30f, 100f, u)
            assertEquals(w - lx, rx, 1e-3f)
            assertEquals(ArcMath.pointY(Corner.BottomLeft, h - 30f, 100f, u), ArcMath.pointY(Corner.BottomRight, h - 30f, 100f, u), 1e-3f)
            val back = ArcMath.uOf(Corner.BottomRight, w - 30f, h - 30f, rx, ArcMath.pointY(Corner.BottomRight, h - 30f, 100f, u))
            assertEquals(u, back, 1e-2f)
        }
    }

    // ---- the fan

    private fun slots(w: Float, h: Float, anchor: Corner = Corner.BottomLeft, bottom: Boolean = true) =
        ArcFan.slots(anchor, 30f, h - 30f, w, h, 90f, 48f, 56f, 8f, 4f, useBottomChain = bottom)
            .let { s -> if (anchor == Corner.BottomLeft) s else ArcFan.slots(anchor, w - 30f, h - 30f, w, h, 90f, 48f, 56f, 8f, 4f, useBottomChain = bottom) }

    @Test fun ARC_03_the_fan_slots_never_overlap_and_stay_on_screen() {
        for ((w, h) in listOf(360f to 640f, 411f to 891f, 891f to 411f, 1280f to 800f, 280f to 653f)) for (a in listOf(Corner.BottomLeft, Corner.BottomRight)) {
            val s = slots(w, h, a)
            assertTrue("some slots at ${w}x$h", s.isNotEmpty())
            for (i in s.indices) {
                assertTrue(s[i].x - 24f >= 4f - 1e-3f && s[i].x + 24f <= w - 4f + 1e-3f && s[i].y - 24f >= 4f - 1e-3f && s[i].y + 24f <= h - 4f + 1e-3f)
                for (j in i + 1 until s.size) assertTrue("slots $i and $j clear (square icons)", maxOf(abs(s[i].x - s[j].x), abs(s[i].y - s[j].y)) >= 52f - 1e-3f)
            }
        }
    }

    @Test fun ARC_03_the_first_slot_is_just_outside_the_ring_and_slots_run_nearest_to_farthest() {
        val s = slots(411f, 891f)
        val d = s.map { hypot(it.x - 30f, it.y - 861f) }
        assertTrue("first slot clears the ring: ${d.first()}", d.first() >= 90f + 24f)
        assertTrue(d.zipWithNext().all { (a, b) -> a <= b + 2.01f })
    }

    @Test fun ARC_03_there_are_chains_along_the_left_edge_diagonals_and_the_bottom_edge() {
        val s = slots(411f, 891f)
        assertTrue("a column along the left edge", s.count { it.chain == 0 } >= 5)
        assertTrue("diagonals at several angles", s.map { it.chain }.toSet().size >= 5)
        assertTrue("a row along the bottom edge", s.count { it.chain == ArcFan.CHAINS.lastIndex } >= 4)
        assertTrue("the bottom chain is free for the shortcuts", slots(411f, 891f, bottom = false).none { it.chain == ArcFan.CHAINS.lastIndex })
    }

    @Test fun ARC_03_the_fan_shows_zero_to_sixty_apps_and_scrolls_the_overflow() {
        val cap = slots(411f, 891f).size
        assertTrue("capacity $cap", cap in 12..80)
        for (n in 0..60) {
            val r = ArcFan.shown(n, cap, 0)
            assertEquals(minOf(n, cap), r.count())
            for (start in listOf(-3, 0, 5, 99)) {
                val rr = ArcFan.shown(n, cap, start)
                assertTrue(rr.isEmpty() || (rr.first >= 0 && rr.last < n && rr.count() <= cap))
            }
        }
        assertEquals(0, ArcFan.clampStart(-4, 10, 5)); assertEquals(5, ArcFan.clampStart(99, 10, 5)); assertEquals(0, ArcFan.clampStart(3, 4, 8))
        assertEquals(-1, ArcFan.stepsFor(60f, 56f)); assertEquals(1, ArcFan.stepsFor(-60f, 56f)); assertEquals(0, ArcFan.stepsFor(40f, 56f))
    }

    // ---- shortcuts

    @Test fun ARC_04_the_wheel_row_runs_along_the_bottom_edge_from_the_ring() {
        val row = ShortcutPlacement.wheelRow(Corner.BottomLeft, 30f, 861f, 90f, 48f, 56f, 8f, 4)
        assertEquals(4, row.size)
        assertTrue(row.all { abs(it.second - 861f) < 1e-3f })
        assertEquals(30f + 90f + 24f + 8f, row[0].first, 1e-3f)
        assertEquals(56f, row[1].first - row[0].first, 1e-3f)
        val mirror = ShortcutPlacement.wheelRow(Corner.BottomRight, 381f, 861f, 90f, 48f, 56f, 8f, 4)
        assertEquals(411f - row[0].first, mirror[0].first, 1e-3f)
    }

    private val screens = listOf(360f to 640f, 411f to 891f, 891f to 411f, 1280f to 800f, 280f to 653f)

    @Test fun ARC_05_shortcuts_at_rest_never_overlap_the_dock_the_indicator_or_the_hotspots() {
        for ((w, h) in screens) for (a in listOf(Corner.BottomLeft, Corner.BottomRight)) for (lift in listOf(0f, 72f)) for (n in 1..4) {
            val c = ShortcutsAtRest.centres(a, w, h, n, lift)
            val rects = c.map { FRect(it.first - 24f, it.second - 24f, it.first + 24f, it.second + 24f) }
            val glyph = if (a == Corner.BottomLeft) FRect(0f, h - 64f, 64f, h) else FRect(w - 64f, h - 64f, w, h)
            val otherBottom = if (a == Corner.BottomLeft) FRect(w - 64f, h - 64f, w, h) else FRect(0f, h - 64f, 64f, h)
            val topLeft = FRect(0f, 0f, 64f, 64f); val topRight = FRect(w - 64f, 0f, w, 64f)
            val indicator = FRect(w / 2f - 56f, h - 76f, w / 2f + 56f, h)
            val dock = FRect(0f, h - lift, w, h)
            for ((i, r) in rects.withIndex()) {
                val ctx = "${w}x$h $a n=$n lift=$lift icon $i"
                assertTrue("$ctx on screen", r.l >= 0 && r.t >= 0 && r.r <= w && r.b <= h)
                assertFalse("$ctx glyph", r.intersects(glyph)); assertFalse("$ctx other corner", r.intersects(otherBottom))
                assertFalse("$ctx top corners", r.intersects(topLeft) || r.intersects(topRight))
                assertFalse("$ctx indicator", r.intersects(indicator))
                if (lift > 0) assertFalse("$ctx dock", r.intersects(dock))
                for (j in i + 1 until rects.size) assertFalse("$ctx overlaps $j", r.intersects(rects[j]))
            }
        }
    }

    @Test fun ARC_05_a_wide_screen_gets_a_row_along_the_bottom_and_a_narrow_one_a_column_up_the_edge() {
        val wide = ShortcutsAtRest.centres(Corner.BottomLeft, 1280f, 800f, 4, 0f)
        assertTrue(wide.all { abs(it.second - wide[0].second) < 1e-3f }); assertTrue(wide.zipWithNext().all { (a, b) -> b.first > a.first })
        val narrow = ShortcutsAtRest.centres(Corner.BottomLeft, 360f, 640f, 4, 0f)
        assertTrue(narrow.all { abs(it.first - narrow[0].first) < 1e-3f }); assertTrue(narrow.zipWithNext().all { (a, b) -> b.second < a.second })
    }
}
