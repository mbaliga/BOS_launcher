package com.bos.sphere.feature.grid

import kotlin.math.hypot
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

    @Test fun perimeterStartsAtTopAfterCorner() {
        val o = FloatArray(2)
        IndicatorMath.perimeterPoint(0f, 400f, 800f, 32f, o)
        assertEquals(32f, o[0], eps); assertEquals(0f, o[1], eps)
    }

    @Test fun perimeterHalfwayIsAntipodal() {
        val o = FloatArray(2)
        IndicatorMath.perimeterPoint(0.5f, 400f, 800f, 32f, o)
        assertEquals(368f, o[0], eps); assertEquals(800f, o[1], eps)
    }

    @Test fun perimeterWrapsAndIsContinuousAndOnBoundary() {
        val w = 400f; val h = 800f; val r = 32f
        val a = FloatArray(2); val b = FloatArray(2)
        IndicatorMath.perimeterPoint(1f, w, h, r, a)
        IndicatorMath.perimeterPoint(0f, w, h, r, b)
        assertEquals(b[0], a[0], eps); assertEquals(b[1], a[1], eps)
        val steps = 2000
        val maxStep = IndicatorMath.perimeterLength(w, h, r) / steps * 1.05f
        IndicatorMath.perimeterPoint(0f, w, h, r, a)
        for (i in 1..steps) {
            IndicatorMath.perimeterPoint(i / steps.toFloat(), w, h, r, b)
            assertTrue(hypot(b[0] - a[0], b[1] - a[1]) <= maxStep)
            assertTrue(b[0] >= -eps && b[0] <= w + eps && b[1] >= -eps && b[1] <= h + eps)
            a[0] = b[0]; a[1] = b[1]
        }
    }

    @Test fun perimeterLengthMatchesFormula() {
        assertEquals(2 * 336f + 2 * 736f + 2f * Math.PI.toFloat() * 32f, IndicatorMath.perimeterLength(400f, 800f, 32f), eps)
    }

    @Test fun zeroRadiusIsPlainRectangle() {
        val o = FloatArray(2)
        IndicatorMath.perimeterPoint(0.25f, 100f, 100f, 0f, o)
        assertEquals(100f, o[0], eps); assertEquals(0f, o[1], eps)
    }

    @Test fun hashIsDeterministicAndInRange() {
        for (i in 0..100) {
            val h = IndicatorMath.hash01(i, 7)
            assertTrue(h >= 0f && h < 1f)
            assertEquals(h, IndicatorMath.hash01(i, 7), 0f)
        }
    }

    @Test fun rayEdgeDistance() {
        assertEquals(200f, IndicatorMath.rayEdgeDistance(0f, 200f, 400f), eps)
        assertEquals(400f, IndicatorMath.rayEdgeDistance((Math.PI / 2).toFloat(), 200f, 400f), eps)
    }
}
