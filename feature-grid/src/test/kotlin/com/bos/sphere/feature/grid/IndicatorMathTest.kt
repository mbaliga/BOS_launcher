package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorMathTest {
    private val eps = 1e-2f

    @Test fun falloffCentreIsOneAndCornersAreFloor() {
        assertEquals(1f, IndicatorMath.falloff(0f, 0f), eps)
        assertEquals(IndicatorMath.FLOOR, IndicatorMath.falloff(1f, 1f), eps)
        assertTrue(IndicatorMath.falloff(1f, 0f) >= IndicatorMath.FLOOR)
    }

    @Test fun falloffIsMonotonicOutward() {
        var prev = 2f
        for (i in 0..20) {
            val f = IndicatorMath.falloff(i / 20f, i / 20f)
            assertTrue(f <= prev + 1e-6f)
            assertTrue(f >= IndicatorMath.FLOOR - 1e-6f)
            prev = f
        }
    }

    @Test fun ringWavePeaksAtCentre() {
        assertEquals(1f, IndicatorMath.ringWave(0.5f, 0.5f, 0.2f), 1e-6f)
        assertEquals(0f, IndicatorMath.ringWave(0.8f, 0.5f, 0.2f), 1e-6f)
        assertTrue(IndicatorMath.ringWave(0.55f, 0.5f, 0.2f) in 0f..1f)
    }

    @Test fun ringCentreSpansBeyondUnitRange() {
        assertEquals(-0.2f, IndicatorMath.ringCentre(0f, 0.2f), 1e-6f)
        assertEquals(1.2f, IndicatorMath.ringCentre(1f, 0.2f), 1e-6f)
    }

    @Test fun PAL_24_w35_urgentPulseIsAboutOnePointTwoSecondsAndStaysVisible() {
        assertEquals(1.2f, IndicatorMath.URGENT_PERIOD_S, 0f)
        assertEquals(IndicatorMath.pulse(0f, true, false), IndicatorMath.pulse(1.2f, true, false), 1e-4f)
        assertTrue(IndicatorMath.pulse(0.6f, true, false) < IndicatorMath.pulse(0f, true, false))
        for (i in 0..120) assertTrue(IndicatorMath.pulse(i / 50f, true, false) in 0.69f..1.001f)
        assertEquals("reduced motion: one static full-strength frame", 1f, IndicatorMath.pulse(0.6f, true, true), 0f)
        assertEquals("not urgent: no pulse", 1f, IndicatorMath.pulse(0.6f, false, false), 0f)
    }

    @Test fun PAL_24_w35_alertStrengthScalesWithSeverityAndUrgentIsFull() {
        assertTrue(IndicatorMath.alertStrength(0.1f, false, 0.6f) < IndicatorMath.alertStrength(0.5f, false, 0.6f))
        assertTrue(IndicatorMath.alertStrength(0.5f, false, 0.6f) < IndicatorMath.alertStrength(0.9f, false, 0.6f))
        assertEquals(1f, IndicatorMath.alertStrength(1f, true, 0.2f), 0f)
        assertEquals("zero intensity is off", 0f, IndicatorMath.alertStrength(1f, true, 0f), 0f)
    }

    @Test fun PAL_24_w35_vignetteThickensAndComicLinesGetManyAndBoldAsSeverityRises() {
        assertTrue(IndicatorMath.vignetteInner(1f) < IndicatorMath.vignetteInner(0.2f))
        assertTrue(IndicatorMath.comicFraction(0.1f, false) < IndicatorMath.comicFraction(0.8f, false))
        assertEquals(1f, IndicatorMath.comicFraction(1f, true), 0f)
        assertTrue(IndicatorMath.comicWidth(0.1f, false) < IndicatorMath.comicWidth(0.9f, false))
        assertTrue(IndicatorMath.comicWidth(0.9f, false) < IndicatorMath.comicWidth(1f, true))
    }

    @Test fun PAL_24_w35_comicLinesFractionThinsTheSameDeterministicRingEvenly() {
        val full = ComicLines(); val few = ComicLines()
        full.fill(400f, 800f, 0, true); few.fill(400f, 800f, 0, true, 0.25f)
        fun n(l: ComicLines) = l.counts.sum() / 4
        assertEquals(72, n(full)); assertEquals(18, n(few))
        val again = ComicLines(); again.fill(400f, 800f, 0, true, 0.25f)
        for (k in 0 until ComicLines.CLASSES) assertTrue(few.buffers[k].contentEquals(again.buffers[k]))
        val unchanged = ComicLines(); unchanged.fill(400f, 800f, 0, true, 1f)
        for (k in 0 until ComicLines.CLASSES) assertTrue("fraction 1 is the unchanged PAL-24 set", full.buffers[k].contentEquals(unchanged.buffers[k]))
    }
}
