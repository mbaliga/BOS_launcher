package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.input.PuckLayout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CFG-15, INP-19, NFR-13 (wave 38, drawerRing): the pure geometry of the rotatable shortcut ring (N segments around a Back hub that reaches the
 * window edge), on the phone the owner uses (411 x 891 dp) and on smaller windows.
 */
class PuckSectionsGeometryTest {
    private val corners = Corner.entries
    private fun ringFor(minSide: Float): SectionRing { val h = PuckLayout.compute(minSide); return PuckSectionsGeometry.ring(h.hubRadiusDp, h.chipRadiusDp) }
    private val phone get() = ringFor(411f)
    private fun window(anchor: Corner, w: Float = 411f, h: Float = 891f) = RingWindow.of(anchor, w, h)
    private fun norm(a: Float) = ((a % 360f) + 360f) % 360f
    private fun angDiff(a: Float, b: Float): Float = abs(((a - b + 540f) % 360f + 360f) % 360f - 180f)

    // ---- hub

    @Test fun CFG_15_w38_theBackHubReachesToWithin8dpOfTheNearestWindowEdge() {
        val r = phone
        assertTrue("hub ${r.backRadius}", r.backRadius >= PuckSectionsGeometry.EDGE_DP - 8f && r.backRadius <= PuckSectionsGeometry.EDGE_DP)
        assertEquals(59f, r.backRadius, 0.01f)
        assertEquals("the ring wraps the hub with no gap", r.backRadius, r.inner, 0f)
    }

    @Test fun CFG_15_w38_theHubIsAtLeast44dpOnASmallWindowAndNeverTouchesTheLabelsOrTheRingsEdge() {
        assertEquals(44f, PuckSectionsGeometry.ring(60f, 46f).backRadius, 0f)
        for (min in listOf(300f, 320f, 360f, 411f, 600f, 900f)) {
            val r = ringFor(min); val h = PuckLayout.compute(min)
            assertTrue("$min: hub ${r.backRadius}", r.backRadius >= 44f && r.backRadius <= 63f)
            assertTrue("$min: ring depth ${r.thickness}", r.thickness >= PuckSectionsGeometry.MIN_RING_DEPTH_DP - 0.01f)
            assertTrue("$min: the ring ends clear of the group labels (${r.outer} vs ${h.chipRadiusDp})", r.outer <= h.chipRadiusDp - 6.9f)
        }
    }

    @Test fun CFG_15_w38_theHubIsWholeInsideTheWindowSoTheArrowIsCentredAndAHubTheWindowCutsHasItsArrowOnWhatShows() {
        for (a in corners) assertEquals(0f to 0f, PuckSectionsGeometry.hubArrowOffset(phone, window(a)))
        // a hub cut by the left edge (centre 30 dp in, radius 59): the visible part runs from the edge to the hub's right side
        val cut = RingWindow(30f, 400f, 411f, 891f)
        val (dx, dy) = PuckSectionsGeometry.hubArrowOffset(phone, cut)
        assertEquals((0f + 89f) / 2f - 30f, dx, 0.01f); assertEquals(0f, dy, 0.01f)
    }

    // ---- segments

    @Test fun CFG_15_w38_theRingIsCutIntoNEqualSegmentsByNDividersAroundTheWholeCircle() {
        for (n in 6..8) for (a in corners) {
            val d = PuckSectionsGeometry.dividerAngles(a, n, 1f)
            assertEquals(n, d.size)
            val sorted = d.sorted()
            for (i in sorted.indices) assertEquals("$a n=$n", 360f / n, norm(sorted[(i + 1) % n] - sorted[i]), 0.01f)
            // every divider is half a segment from the neighbouring centres
            for (i in 0 until n) assertEquals(360f / n / 2f, angDiff(PuckSectionsGeometry.centerAngle(a, n, 1f, i), d[i]), 0.01f)
        }
    }

    @Test fun CFG_15_w38_segmentAtFindsTheSegmentHoldingAnAngleAtEveryPositionAndSize() {
        for (n in 6..8) for (a in corners) for (p in listOf(0f, 1f, 2.4f, 5.5f, -1f)) for (i in 0 until n) {
            val c = PuckSectionsGeometry.centerAngle(a, n, p, i)
            assertEquals("$a n=$n p=$p slot $i", i, PuckSectionsGeometry.segmentAt(a, n, p, c))
            assertEquals(i, PuckSectionsGeometry.segmentAt(a, n, p, c + 360f / n * 0.45f))
            assertEquals(i, PuckSectionsGeometry.segmentAt(a, n, p, c - 360f / n * 0.45f))
        }
    }

    @Test fun CFG_15_w38_atRestSegmentOneIsCentredInTheQuadrantAndSlotZeroSitsOnTheThumbsSide() {
        // BottomLeft: the horizontal edge is the bottom; slot 0 is the one nearest the right (the quadrant's 0 degrees end)
        val bl = PuckSectionsGeometry.centerAngle(Corner.BottomLeft, 8, 1f, 1)
        assertEquals(315f, bl, 0.01f)
        assertEquals(0f, norm(PuckSectionsGeometry.centerAngle(Corner.BottomLeft, 8, 1f, 0)), 0.01f)
        assertEquals(270f, norm(PuckSectionsGeometry.centerAngle(Corner.BottomLeft, 8, 1f, 2)), 0.01f)
        // BottomRight is the exact mirror: slot 0 on the far side of the quadrant from the vertical edge
        assertEquals(180f, norm(PuckSectionsGeometry.centerAngle(Corner.BottomRight, 8, 1f, 0)), 0.01f)
        assertEquals(270f, norm(PuckSectionsGeometry.centerAngle(Corner.BottomRight, 8, 1f, 2)), 0.01f)
    }

    @Test fun CFG_15_w38_theOtherCornerIsTheExactMirrorOfTheLeftHandOne() {
        for (n in 6..8) for (p in listOf(0f, 1f, 3.3f)) for (i in 0 until n) {
            val l = PuckSectionsGeometry.centerAngle(Corner.BottomLeft, n, p, i)
            val r = PuckSectionsGeometry.centerAngle(Corner.BottomRight, n, p, i)
            assertEquals("n=$n p=$p slot $i", 0f, angDiff(180f - l, r), 0.01f)
            val tl = PuckSectionsGeometry.centerAngle(Corner.TopLeft, n, p, i)
            val tr = PuckSectionsGeometry.centerAngle(Corner.TopRight, n, p, i)
            assertEquals(0f, angDiff(180f - tl, tr), 0.01f)
            // top is the bottom flipped vertically
            assertEquals(0f, angDiff(-l, tl), 0.01f)
        }
    }

    // ---- visible segments

    @Test fun CFG_15_w38_aboutHalfOfTheSegmentsShowAtOnceAtEightAndTheOnesAtTheHubSideOfTheQuadrantAreWhole() {
        val a = Corner.BottomLeft; val w = window(a); val r = phone
        val v = PuckSectionsGeometry.visibleSegments(a, 8, 1f, r, w).map { it.index }.toSet()
        assertEquals("slots 7, 0, 1, 2 and the edge of 3", setOf(7, 0, 1, 2, 3), v)
        for (i in listOf(0, 1, 2)) {
            val seg = PuckSectionsGeometry.visibleSegment(a, 8, 1f, i, r, w)!!
            assertEquals("whole at the mid radius", PuckSectionsGeometry.arcAt(8, r.mid), seg.arcDp, 1.5f)
        }
        assertTrue("two slots look at the screen edge only partly", PuckSectionsGeometry.visibleSegment(a, 8, 1f, 3, r, w)!!.arcDp < PuckSectionsGeometry.arcAt(8, r.mid) * 0.5f)
    }

    @Test fun CFG_15_w38_aSegmentThatTheWindowHidesAtTheMidRadiusIsNotVisibleAndItsSliverByTheHubIsNoTarget() {
        val a = Corner.BottomLeft; val w = window(a); val r = phone
        assertNull(PuckSectionsGeometry.visibleSegment(a, 8, 1f, 5, r, w))
        // slot 4 points at the left edge: only a 4 dp rim of it shows beside the hub
        assertNull(PuckSectionsGeometry.visibleSegment(a, 8, 1f, 4, r, w))
        val c = PuckSectionsGeometry.centerAngle(a, 8, 1f, 4)
        val rad = Math.toRadians(c.toDouble()); val rr = r.backRadius + 2f
        assertEquals(PuckSectionsGeometry.Hit.None, PuckSectionsGeometry.hit(a, 8, 1f, r, (rr * cos(rad)).toFloat(), (rr * sin(rad)).toFloat(), w))
    }

    @Test fun CFG_15_w38_turningTheRingBringsOtherSegmentsIntoView() {
        val a = Corner.BottomLeft; val w = window(a); val r = phone
        val before = PuckSectionsGeometry.visibleSegments(a, 8, 1f, r, w).map { it.index }.toSet()
        val after = PuckSectionsGeometry.visibleSegments(a, 8, 4f, r, w).map { it.index }.toSet()
        assertTrue("slot 4 (hidden before) shows after a turn of three", 4 in after && 4 !in before)
        assertEquals("front slot", 4, PuckSectionsGeometry.frontSlot(8, 4f))
        for (n in 6..8) for (anchor in corners) {
            val vis = PuckSectionsGeometry.visibleSegments(anchor, n, 1f, r, window(anchor)).size
            assertTrue("$anchor n=$n shows $vis of $n", vis in 3..n - 1)
        }
    }

    @Test fun CFG_15_w38_theVisibleRunMidAngleOfACutSegmentIsInsideTheWindow() {
        // Still used for the long-press name pill: the middle of the longest visible run (not the glyph, wave 43).
        val a = Corner.BottomLeft; val w = window(a); val r = phone
        val whole = PuckSectionsGeometry.visibleSegment(a, 8, 1f, 1, r, w)!!
        assertEquals(PuckSectionsGeometry.centerAngle(a, 8, 1f, 1), whole.midAngleDeg, 1.01f)
        val cut = PuckSectionsGeometry.visibleSegment(a, 8, 1f, 3, r, w)!!
        assertEquals(236f, cut.midAngleDeg, 1.6f)
        val (gx, gy) = PuckSectionsGeometry.glyphOffset(cut.midAngleDeg, r)
        assertTrue(w.contains(w.cx + gx, w.cy + gy))
    }

    private val g = PuckSectionsGeometry.GLYPH_DP
    private fun angleOf(p: Pair<Float, Float>) = norm(Math.toDegrees(kotlin.math.atan2(p.second.toDouble(), p.first.toDouble())).toFloat())

    @Test fun INP_19_w43_theGlyphCentreIsTheSegmentMidPointForEveryRotationCornerAndCount() {
        val r = phone
        for (a in corners) for (n in 6..8) for (step in 0..(4 * n)) {
            val p = step / 4f // whole and in-between rotations
            for (i in 0 until n) {
                val c = PuckSectionsGeometry.glyphCentre(a, n, p, i, r)
                assertEquals("$a n=$n p=$p slot $i: mid radius", r.mid, kotlin.math.hypot(c.first, c.second), 0.01f)
                assertEquals("$a n=$n p=$p slot $i: mid angle", 0f, angDiff(angleOf(c), PuckSectionsGeometry.centerAngle(a, n, p, i)), 0.01f)
                // and when it is drawn, it is drawn exactly there (no shift)
                val placed = PuckSectionsGeometry.glyphPlacement(a, n, p, i, r, window(a), g)
                if (placed != null) assertEquals(c, placed)
            }
        }
    }

    @Test fun INP_19_w43_aGlyphIsNeverMovedOrResizedWhateverTheWindowAndTheOneBoxIsAlwaysSquare() {
        val r = phone
        val windows = listOf(411f to 891f, 360f to 640f, 891f to 411f, 600f to 960f)
        for ((ww, hh) in windows) for (a in corners) for (p in 0 until 8) for (i in 0 until 8) {
            val w = window(a, ww, hh)
            val c = PuckSectionsGeometry.glyphCentre(a, 8, p.toFloat(), i, r)
            val placed = PuckSectionsGeometry.glyphPlacement(a, 8, p.toFloat(), i, r, w, g)
            assertTrue("$ww x $hh $a p=$p slot $i: either not drawn or at the exact segment centre", placed == null || placed == c)
        }
        assertEquals(24f, PuckSectionsGeometry.GLYPH_DP, 0f)
        // the fixed size does not depend on the segment count or the ring (it used to be derived from the segment)
        for (n in 6..8) assertEquals(24f, PuckSectionsGeometry.GLYPH_DP, 0f)
    }

    @Test fun INP_19_w43_noDrawnGlyphBoxCrossesTheWindowEdgeAndACutOneIsSkippedNotSqueezed() {
        val r = phone
        var skipped = 0; var drawn = 0
        for ((ww, hh) in listOf(411f to 891f, 891f to 411f, 360f to 640f)) for (a in corners) for (n in 6..8) for (p in 0 until n) for (i in 0 until n) {
            val w = window(a, ww, hh)
            val c = PuckSectionsGeometry.glyphCentre(a, n, p.toFloat(), i, r)
            val cx = w.cx + c.first; val cy = w.cy + c.second
            val inside = cx - g / 2f >= 2f && cx + g / 2f <= w.width - 2f && cy - g / 2f >= 2f && cy + g / 2f <= w.height - 2f
            val placed = PuckSectionsGeometry.glyphPlacement(a, n, p.toFloat(), i, r, w, g)
            assertEquals("$ww x $hh $a n=$n p=$p slot $i", inside, placed != null)
            if (placed == null) skipped++ else drawn++
        }
        assertTrue("some glyphs are skipped at an edge ($skipped) and some are drawn ($drawn)", skipped > 0 && drawn > 0)
    }

    @Test fun INP_19_w43_turningTheRingBringsAHiddenGlyphFullyIntoView() {
        val a = Corner.BottomLeft; val w = window(a); val r = phone
        // slot 7 sits on the bottom edge at rest (position 1): its box is cut, so it is not drawn
        assertNull(PuckSectionsGeometry.glyphPlacement(a, 8, 1f, 7, r, w, g))
        // its segment is still there as a tap target
        assertNotNull(PuckSectionsGeometry.visibleSegment(a, 8, 1f, 7, r, w, g))
        // turning the ring brings it to the thumb side, where it shows whole at its exact centre
        val front = (0..7).first { p -> PuckSectionsGeometry.glyphPlacement(a, 8, p.toFloat(), 7, r, w, g) != null }
        val placed = PuckSectionsGeometry.glyphPlacement(a, 8, front.toFloat(), 7, r, w, g)!!
        assertEquals(PuckSectionsGeometry.glyphCentre(a, 8, front.toFloat(), 7, r), placed)
        // and every slot is whole in view for some rotation
        for (i in 0 until 8) assertTrue("slot $i", (0 until 8).any { p -> PuckSectionsGeometry.glyphPlacement(a, 8, p.toFloat(), i, r, w, g) != null })
    }

    @Test fun INP_19_w43_aSegmentIsATapTargetWhenItsGlyphBoxShowsOrAtLeast10dpOfItShowsAtTheMidRadius() {
        val r = phone
        for (a in corners) for (p in 0 until 8) for (i in 0 until 8) {
            val w = window(a)
            val withGlyph = PuckSectionsGeometry.visibleSegment(a, 8, p.toFloat(), i, r, w, g)
            val without = PuckSectionsGeometry.visibleSegment(a, 8, p.toFloat(), i, r, w)
            if (without != null) assertNotNull("$a p=$p slot $i: the 10 dp rule still holds", withGlyph)
            if (PuckSectionsGeometry.glyphPlacement(a, 8, p.toFloat(), i, r, w, g) != null) assertNotNull("$a p=$p slot $i: a drawn glyph is on a target", withGlyph)
        }
    }

    @Test fun INP_19_w43_theLabelSitsAtAFixedOffsetUnderTheGlyphAndShowsOnlyWhenItFitsWhole() {
        val r = phone
        val w = window(Corner.BottomLeft)
        val c = PuckSectionsGeometry.glyphCentre(Corner.BottomLeft, 8, 1f, 1, r)
        assertTrue(PuckSectionsGeometry.labelVisible(w, c, g))
        // the label's box is the glyph's bottom + 1 dp, 56 x 12 dp: fits inside this window
        val px = w.cx + c.first; val top = w.cy + c.second + g / 2f + PuckSectionsGeometry.LABEL_GAP_DP
        assertTrue(px - 28f >= 2f && top + 12f <= w.height - 2f)
        // a glyph that shows whole but whose label would cross the bottom edge shows no label
        val low = RingWindow(w.cx, w.cy, w.width, w.cy + c.second + g / 2f + 6f)
        assertTrue(PuckSectionsGeometry.glyphVisible(low, c, g)); assertFalse(PuckSectionsGeometry.labelVisible(low, c, g))
        // a label never shows without its glyph
        val edge = RingWindow(0f, 0f, 400f, 800f)
        assertFalse(PuckSectionsGeometry.labelVisible(edge, 3f to 3f, g))
    }

    // ---- rotation, snap

    @Test fun CFG_15_w38_theRingFollowsAFingerInEveryCorner() {
        for (a in corners) for (n in 6..8) {
            val p0 = 1f
            val p1 = p0 + PuckSectionsGeometry.positionDelta(a, n, 17f)
            for (i in 0 until n) {
                val moved = PuckSectionsGeometry.centerAngle(a, n, p1, i) - PuckSectionsGeometry.centerAngle(a, n, p0, i)
                assertEquals("$a n=$n slot $i follows the finger 17 degrees clockwise", 17f, ((moved + 540f) % 360f + 360f) % 360f - 180f, 0.01f)
            }
        }
    }

    @Test fun CFG_15_w38_releaseSnapsToAWholeSegmentPositionSoASegmentSitsCentred() {
        assertEquals(2f, PuckSectionsGeometry.settleTarget(1.6f, 0f), 0f)
        assertEquals(1f, PuckSectionsGeometry.settleTarget(1.4f, 0f), 0f)
        assertEquals(-1f, PuckSectionsGeometry.settleTarget(-0.6f, 0f), 0f)
        // a fling carries it on by what 0.18 s of velocity covers, at most three segments
        assertEquals(3f, PuckSectionsGeometry.settleTarget(1.2f, 8f), 0f)
        assertEquals(4f, PuckSectionsGeometry.settleTarget(1.2f, 400f), 0f)
        assertEquals(-2f, PuckSectionsGeometry.settleTarget(1.2f, -400f), 0f)
        // no inertia (reduced motion): the nearest segment, the velocity ignored
        assertEquals(1f, PuckSectionsGeometry.settleTarget(1.2f, 400f, projectSec = 0f), 0f)
        for (n in 6..8) assertEquals(PuckSectionsGeometry.wrap(-1f, n), (n - 1).toFloat(), 0f)
    }

    // ---- touch ownership and hit testing

    @Test fun CFG_15_w38_aTouchOnTheHubIsBackOnTheRingIsASegmentAndOutsideItBelongsToTheOuterWheel() {
        val a = Corner.BottomLeft; val r = phone; val w = window(a)
        assertEquals(PuckSectionsGeometry.Hit.Back, PuckSectionsGeometry.hit(a, 8, 1f, r, 0f, 0f, w))
        assertEquals(PuckSectionsGeometry.Hit.Back, PuckSectionsGeometry.hit(a, 8, 1f, r, r.backRadius - 1f, 0f, w))
        val mid = r.mid
        assertEquals(PuckSectionsGeometry.Hit.Slot(1), PuckSectionsGeometry.hit(a, 8, 1f, r, 0f - 0f + (mid * cos(Math.toRadians(315.0))).toFloat(), (mid * sin(Math.toRadians(315.0))).toFloat(), w))
        assertEquals(PuckSectionsGeometry.Hit.None, PuckSectionsGeometry.hit(a, 8, 1f, r, r.outer + 1f, 0f, w))
        for (deg in 0 until 360 step 7) {
            val rad = Math.toRadians(deg.toDouble())
            val inside = PuckSectionsGeometry.ownsTouch(r, ((r.outer - 0.5f) * cos(rad)).toFloat(), ((r.outer - 0.5f) * sin(rad)).toFloat())
            val outside = PuckSectionsGeometry.ownsTouch(r, ((r.outer + 0.5f) * cos(rad)).toFloat(), ((r.outer + 0.5f) * sin(rad)).toFloat())
            assertTrue("$deg inside is the ring's", inside); assertFalse("$deg outside is the outer wheel's", outside)
        }
    }

    @Test fun CFG_15_w38_theRingsAndTheGroupBandNeverShareATouchAtAnyWindowSize() {
        for (min in listOf(300f, 360f, 411f, 600f)) {
            val r = ringFor(min); val h = PuckLayout.compute(min)
            // the group labels start (their ink) at the label radius minus a few dp; the ring ends before that
            assertTrue("$min: ring ${r.outer} < labels ${h.chipRadiusDp}", r.outer < h.chipRadiusDp)
            // and the outer wheel's band starts at the hub's edge plus 3 dp (WheelMetrics.bandInner)
            assertTrue("$min: ring ${r.outer} < band ${h.hubRadiusDp + 3f}", r.outer < h.hubRadiusDp + 3f)
        }
    }

    // ---- tap area sizes

    @Test fun NFR_13_w38_aWholeSegmentIsAtLeast44dpAcrossTheArcEverywhereAtEightAndWiderAtSixAndSeven() {
        val r = phone
        for (n in 6..8) {
            assertTrue("n=$n inner ${PuckSectionsGeometry.arcAt(n, r.inner)}", PuckSectionsGeometry.arcAt(n, r.inner) >= PuckSectionsGeometry.MIN_ARC_DP)
            assertTrue(PuckSectionsGeometry.arcAt(n, r.mid) >= PuckSectionsGeometry.MIN_ARC_DP)
            assertTrue(PuckSectionsGeometry.arcAt(n, r.outer) >= PuckSectionsGeometry.MIN_ARC_DP)
        }
        // the numbers quoted in the decision note
        assertEquals(46.3f, PuckSectionsGeometry.arcAt(8, r.inner), 0.2f)
        assertEquals(93.3f, PuckSectionsGeometry.arcAt(8, r.outer), 0.3f)
        assertTrue("radially it is thumb sized", r.thickness >= 48f)
    }

    @Test fun NFR_13_w38_theSegmentsAtTheMiddleOfTheQuadrantKeepTheirFullTapAreaWhateverThePositionAtRest() {
        for (a in corners) for (n in 6..8) for (front in 0 until n) {
            val r = phone; val w = window(a)
            val v = PuckSectionsGeometry.visibleSegment(a, n, front.toFloat(), front, r, w)
            assertNotNull("$a n=$n front=$front", v)
            assertEquals("$a n=$n: the centred segment is whole at the mid radius", PuckSectionsGeometry.arcAt(n, r.mid), v!!.arcDp, 1.5f)
            assertTrue("$a n=$n: tap box is ${v.bounds.width} x ${v.bounds.height}", v.bounds.width >= 44f && v.bounds.height >= 44f)
        }
    }

    @Test fun NFR_13_w38_everyVisibleSegmentAtRestHasABoxInsideTheWindowAndATapAreaOfAtLeast44dpOnSomeSideForAllCornersAndSizes() {
        for (a in corners) for (n in 6..8) for (front in 0 until n) for ((w0, h0) in listOf(411f to 891f, 891f to 411f, 360f to 640f)) {
            val r = ringFor(minOf(w0, h0)); val w = window(a, w0, h0)
            for (v in PuckSectionsGeometry.visibleSegments(a, n, front.toFloat(), r, w)) {
                assertTrue("inside window", v.bounds.left >= -0.01f && v.bounds.top >= -0.01f && v.bounds.right <= w0 + 0.01f && v.bounds.bottom <= h0 + 0.01f)
                assertTrue("$a n=$n slot ${v.index}: ${v.bounds.width} x ${v.bounds.height}", maxOf(v.bounds.width, v.bounds.height) >= 20f)
            }
        }
    }
}
