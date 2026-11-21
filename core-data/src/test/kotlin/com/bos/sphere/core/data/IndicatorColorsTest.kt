package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IndicatorColorsTest {
    private val low = 0xFFFF0000L
    private val mid = 0xFFFFFF00L
    private val high = 0xFF00FF00L
    private val idle = 0xFF808080L

    private fun c(r: IndicatorReading, invert: Boolean = false) = IndicatorColors.argb(r, low, mid, high, idle, invert)
    private fun live(v: Float) = IndicatorReading(ReadingKind.Live, v, "")

    @Test fun offIsBlack() = assertEquals(0xFF000000L, c(IndicatorReading(ReadingKind.Off, 0.9f, "")))
    @Test fun idleIsIdle() = assertEquals(idle, c(IndicatorReading(ReadingKind.Idle, 0.9f, "")))

    @Test fun endpointsAndMid() {
        assertEquals(low, c(live(0f)))
        assertEquals(mid, c(live(0.5f)))
        assertEquals(high, c(live(1f)))
    }

    @Test fun interpolates() = assertEquals(0xFFFF7F00L, c(live(0.25f)))

    @Test fun invertFlips() {
        assertEquals(high, c(live(0f), invert = true))
        assertEquals(low, c(live(1f), invert = true))
    }

    @Test fun clamps() {
        assertEquals(high, c(live(5f)))
        assertEquals(low, c(live(-1f)))
        assertEquals(low, c(live(Float.NaN)))
    }
}
