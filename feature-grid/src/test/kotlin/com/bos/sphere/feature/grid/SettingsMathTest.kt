package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMathTest {
    @Test fun stepWrapsBothWays() {
        assertEquals(1, SettingsMath.stepIndex(3, 0, 1))
        assertEquals(0, SettingsMath.stepIndex(3, 2, 1))
        assertEquals(2, SettingsMath.stepIndex(3, 0, -1))
    }

    @Test fun stepClampsWhenNotWrapping() {
        assertEquals(2, SettingsMath.stepIndex(3, 2, 1, wrap = false))
        assertEquals(0, SettingsMath.stepIndex(3, 0, -1, wrap = false))
    }

    @Test fun stepHandlesEmptyAndStaleIndex() {
        assertEquals(0, SettingsMath.stepIndex(0, 0, 1))
        assertEquals(0, SettingsMath.stepIndex(3, 7, 1)) // stale index clamps to last, then wraps
    }

    @Test fun snapRoundsToStepAndClamps() {
        assertEquals(0.5f, SettingsMath.snap(0.504f, 0f..1f), 1e-5f)
        assertEquals(0.5f, SettingsMath.snap(0.2f, 0.5f..0.95f), 1e-5f)
        assertEquals(0.95f, SettingsMath.snap(2f, 0.5f..0.95f), 1e-5f)
        assertEquals(34f, SettingsMath.snap(33.6f, 14f..96f), 1e-5f)
    }

    @Test fun snapUsesRangeStartAsOrigin() {
        assertEquals(0.52f, SettingsMath.snap(0.523f, 0.5f..0.95f, 0.01f), 1e-5f)
        assertEquals(20f, SettingsMath.snap(21f, 20f..72f, 5f), 1e-5f)
    }

    @Test fun tickBandChangesAcrossTheRange() {
        assertEquals(0, SettingsMath.tickBand(0f, 0f..1f))
        assertEquals(10, SettingsMath.tickBand(0.5f, 0f..1f))
        assertEquals(20, SettingsMath.tickBand(1f, 0f..1f))
    }
}
