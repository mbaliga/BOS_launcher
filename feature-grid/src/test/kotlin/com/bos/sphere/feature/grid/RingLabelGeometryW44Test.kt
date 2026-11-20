package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** INP-18 (wave 44, ringLabels): the pure geometry of the labels set on the wheel's arc. */
class RingLabelGeometryW44Test {
    private val G = RingLabelGeometry

    @Test fun INP_18_w44_flipOnlyOnTheLowerHalf() {
        assertFalse(G.flipped(300f)); assertFalse(G.flipped(0f)); assertFalse(G.flipped(180f)); assertFalse(G.flipped(359f))
        assertTrue(G.flipped(45f)); assertTrue(G.flipped(90f)); assertTrue(G.flipped(135f)); assertTrue(G.flipped(1f))
    }

    @Test fun INP_18_w44_noGlyphIsEverUpsideDownAtAnyAngle() {
        var a = 0f
        while (a < 360f) {
            val rot = G.glyphRotation(a)
            assertTrue("angle $a rotation $rot", abs(rot) <= 90.001f)
            // the glyph's up side (rotation applied to -y) never points below the horizontal
            val up = Math.cos(Math.toRadians(rot.toDouble()))
            assertTrue("angle $a text up-vector tilt $rot", up >= -1e-6)
            a += 0.5f
        }
    }

    @Test fun INP_18_w44_rotationIsTangentToTheCircle() {
        // normal run: baseline direction is the circle's clockwise tangent (angle + 90); flipped: the counter-clockwise one (angle - 90)
        assertEquals(WheelGeometry.normalize180(300f + 90f), G.glyphRotation(300f), 1e-3f)
        assertEquals(WheelGeometry.normalize180(45f - 90f), G.glyphRotation(45f), 1e-3f)
        // top of the circle (270): level text; right (0 or 360): vertical-ish and still readable bottom-to-top direction rule: 90 not past
        assertEquals(0f, G.glyphRotation(270f), 1e-3f)
        assertEquals(0f, G.glyphRotation(90f), 1e-3f)
    }

    @Test fun INP_18_w44_mirroredCornerMirrorsTheRotationAndKeepsTheReadingDirection() {
        for (a in listOf(10f, 30f, 45f, 77f, 100f, 170f)) {
            val mirror = 180f - a
            assertEquals("flip at $a and its mirror agree", G.flipped(a), G.flipped(mirror))
            assertEquals("rotation at $a mirrors", -G.glyphRotation(a), G.glyphRotation(mirror), 1e-3f)
        }
        for (a in listOf(190f, 250f, 300f, 350f)) {
            val mirror = 180f - a // also the upper half
            assertEquals(G.flipped(a), G.flipped(mirror))
            assertEquals(-G.glyphRotation(a), G.glyphRotation(mirror), 1e-3f)
        }
    }

    @Test fun INP_18_w44_glyphAnglesAreCentredOnTheSlotAndFollowTheReadingDirection() {
        val adv = listOf(10f, 20f, 10f, 20f)
        val up = G.glyphAngles(300f, adv, 100f, false)
        assertTrue("normal runs advance clockwise", up.zipWithNext().all { (a, b) -> b > a })
        val total = adv.sum()
        val first = up.first(); val last = up.last()
        assertEquals(300f - G.degOf(total / 2f - adv.first() / 2f, 100f), first, 1e-3f)
        assertEquals(300f + G.degOf(total / 2f - adv.last() / 2f, 100f), last, 1e-3f)
        val down = G.glyphAngles(60f, adv, 100f, true)
        assertTrue("flipped runs advance counter-clockwise", down.zipWithNext().all { (a, b) -> b < a })
        assertEquals(60f, (down.first() + down.last()) / 2f, 5f)
    }

    @Test fun INP_18_w44_eachGlyphIsOnTheArcAtItsOwnAngle() {
        val adv = List(8) { 9f }
        val a = G.glyphAngles(315f, adv, 126f, false)
        // consecutive glyph centres are one advance apart ALONG THE ARC: the angle step times the radius
        for ((x, y) in a.zipWithNext()) assertEquals(9f, Math.toRadians((y - x).toDouble()).toFloat() * 126f, 0.01f)
        // the rotation grows with the angle, so the baseline bends (a straight run would have one rotation)
        val rots = a.map { G.glyphRotation(it) }
        assertTrue(rots.zipWithNext().all { (p, q) -> q > p })
    }

    @Test fun INP_18_w44_baselineKeepsTheInkInTheSameBandOnBothSides() {
        val up = G.baselineRadius(100f, 8f, false); val down = G.baselineRadius(100f, 8f, true)
        assertEquals(96f, up, 1e-4f); assertEquals(104f, down, 1e-4f)
        // normal ink spans baseline..baseline+h, flipped ink baseline-h..baseline: both 96..104
        assertEquals(up + 8f, down, 1e-4f)
    }

    private fun slotsFor(r: Float, d: Float) = G.slots(
        centresDeg = listOf(283f, 315f, 347f), textSpansDeg = listOf(14f, 24f, 18f), midRadiusPx = r * d,
        innerPx = (r - 7f) * d, outerPx = (r + 14f) * d, minArcPx = 48f * d, marginDeg = G.degOf(6f * d, r * d),
    )

    @Test fun INP_18_w44_slotsNeverOverlapAndShareTheirEdgeAtMost() {
        for (r in listOf(90f, 126f, 160f)) {
            val s = slotsFor(r, 2.5f)
            for ((a, b) in s.zipWithNext()) assertTrue("slots of radius $r overlap: $a $b", a.endDeg <= b.startDeg + 1e-3f)
        }
    }

    @Test fun INP_18_w44_slotIsAtLeast48dpAlongTheArcWhereTheNeighboursAllow() {
        val s = slotsFor(126f, 2.5f)
        for (sl in s) assertTrue("slot ${sl.spanDeg} deg is ${Math.toRadians(sl.spanDeg.toDouble()) * 126} dp", Math.toRadians(sl.spanDeg.toDouble()) * 126f >= 47.9f)
        // tight: neighbours 10 degrees apart at radius 60 leaves less than 48 dp: they split the gap instead of overlapping
        val tight = G.slots(listOf(0f, 10f), listOf(5f, 5f), 60f, 50f, 80f, 48f, 1f)
        assertEquals(tight[0].endDeg, tight[1].startDeg, 1e-3f)
    }

    @Test fun INP_18_w44_slotHitTestUsesTheCurvedPatch() {
        val s = slotsFor(126f, 1f)
        val cat = s[1]
        assertTrue(cat.contains(126f, 315f))
        assertTrue("inside radially, off axis by 10 deg", cat.contains(126f, 325f))
        assertFalse("past the hub's edge", cat.contains(145f, 315f))
        assertFalse("inside the shortcut ring", cat.contains(100f, 315f))
        assertFalse("a neighbour's angle", cat.contains(126f, 283f))
        assertEquals(1, G.hit(s, 130f, 316f)); assertEquals(0, G.hit(s, 130f, 283f)); assertEquals(2, G.hit(s, 130f, 350f)); assertEquals(-1, G.hit(s, 130f, 200f))
    }
}
