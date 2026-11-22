package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorMathTest {
    @Test fun ringWavePeaksAtCentre() {
        assertEquals(1f, IndicatorMath.ringWave(0.5f, 0.5f, 0.2f), 1e-6f)
        assertEquals(0f, IndicatorMath.ringWave(0.8f, 0.5f, 0.2f), 1e-6f)
        assertTrue(IndicatorMath.ringWave(0.55f, 0.5f, 0.2f) in 0f..1f)
    }

    @Test fun ringCentreSpansBeyondUnitRange() {
        assertEquals(-0.2f, IndicatorMath.ringCentre(0f, 0.2f), 1e-6f)
        assertEquals(1.2f, IndicatorMath.ringCentre(1f, 0.2f), 1e-6f)
    }

    @Test fun PAL_24_w55_theUrgentPulseIsSlowAndStaysVisible() {
        assertEquals("slow: a pulse takes more than three seconds", 3.2f, IndicatorMath.URGENT_PERIOD_S, 0f)
        assertEquals(IndicatorMath.pulse(0f, true, false), IndicatorMath.pulse(3.2f, true, false), 1e-4f)
        assertTrue(IndicatorMath.pulse(1.6f, true, false) < IndicatorMath.pulse(0f, true, false))
        for (i in 0..320) assertTrue(IndicatorMath.pulse(i / 50f, true, false) in 0.79f..1.001f)
        assertEquals("reduced motion: one static full-strength frame", 1f, IndicatorMath.pulse(1.6f, true, true), 0f)
        assertEquals("not urgent: no pulse", 1f, IndicatorMath.pulse(1.6f, false, false), 0f)
    }

    @Test fun PAL_24_w55_strengthIsQuietWhenHealthyRisesWithSeverityAndUrgentIsFull() {
        assertTrue(IndicatorMath.strength(0f, false) < IndicatorMath.strength(0.5f, false))
        assertTrue(IndicatorMath.strength(0.5f, false) < IndicatorMath.strength(1f, false))
        assertEquals(1f, IndicatorMath.strength(0.2f, true), 0f)
        assertTrue("healthy is well under full strength", IndicatorMath.strength(0f, false) < 0.75f)
        assertTrue(IndicatorMath.strength(1f, false) <= 1f)
    }

    @Test fun PAL_24_w55_breathIsSlowAndStillIsTheMiddle() {
        assertEquals(0.5f, IndicatorMath.breath(3f, 6f, true), 0f)
        assertEquals(0f, IndicatorMath.breath(0f, 6f, false), 1e-6f); assertEquals(1f, IndicatorMath.breath(3f, 6f, false), 1e-6f)
    }

    @Test fun PAL_24_w55_theGlowStaysWithinSixPercentOfTheHeightAndFollowsTheValue() {
        for (i in 0..10) assertTrue(IndicatorMath.glowHeightFraction(i / 10f) <= 0.06f + 1e-6f)
        assertTrue(IndicatorMath.glowHeightFraction(0f) < IndicatorMath.glowHeightFraction(1f))
    }

    @Test fun PAL_24_w55_theEdgeDotsWalkTheRectangleEvenlyAndStayInsideTheEdgeBand() {
        val w = 720f; val h = 1280f; val inset = 14f; val sp = 28f
        val n = IndicatorMath.perimeterCount(sp, inset, w, h)
        assertTrue(n > 100)
        val a = FloatArray(2); val b = FloatArray(2)
        for (i in 0 until n) {
            IndicatorMath.perimeterPoint(i, sp, inset, w, h, a)
            val onEdge = a[0] == inset || a[0] == w - inset || a[1] == inset || a[1] == h - inset
            assertTrue("dot $i is on the inset rectangle (${a[0]}, ${a[1]})", onEdge)
            assertTrue(a[0] in inset..(w - inset) && a[1] in inset..(h - inset))
        }
        IndicatorMath.perimeterPoint(0, sp, inset, w, h, a); IndicatorMath.perimeterPoint(1, sp, inset, w, h, b)
        assertEquals("along a straight run the spacing is exact", sp, b[0] - a[0], 1e-3f)
    }
}
