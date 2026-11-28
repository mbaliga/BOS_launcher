package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 56 (looksPresets): the calibrators' pure measurements, with no clock and no device. */
class CalibratorsW56Test {
    @Test fun CFG_27_w56_aHoldIsRoundedToTheSliderStepAndKeptOnTheSlider() {
        assertEquals(150, Calibrators.hotCornerDelay(152)); assertEquals(160, Calibrators.hotCornerDelay(155))
        assertEquals(50, Calibrators.hotCornerDelay(0)); assertEquals(50, Calibrators.hotCornerDelay(-5)); assertEquals(600, Calibrators.hotCornerDelay(60_000))
        for (ms in 0L..900L step 7) { val v = Calibrators.hotCornerDelay(ms); assertTrue(v in 50..600 && v % 10 == 0) }
    }

    @Test fun CFG_27_w56_theHoldMeterMeasuresDownToUpOnceAndIgnoresAStrayUp() {
        val m = HoldMeter()
        assertNull(m.up(500)); assertFalse(m.holding)
        m.down(1000); assertTrue(m.holding)
        assertEquals(240L, m.up(1240)); assertFalse(m.holding)
        assertNull("the same release is not counted twice", m.up(1300))
        m.down(10); m.cancel(); assertNull(m.up(20))
    }

    private fun assertFalse(b: Boolean) = org.junit.Assert.assertFalse(b)

    @Test fun CFG_27_w56_theWaitBetweenTwoTapsIsWholeSecondsOnTheStepperRange() {
        assertEquals(2, Calibrators.controlsSeconds(300)); assertEquals(4, Calibrators.controlsSeconds(4_200)); assertEquals(5, Calibrators.controlsSeconds(4_500))
        assertEquals(10, Calibrators.controlsSeconds(99_000)); assertEquals(2, Calibrators.controlsSeconds(-1))
        val m = IntervalMeter()
        assertNull(m.tap(1_000)); assertTrue(m.waiting)
        assertEquals(3_100L, m.tap(4_100)); assertFalse(m.waiting)
        assertNull("the next tap starts a new measurement", m.tap(9_000))
    }

    @Test fun CFG_27_w56_motionSensitivityIsTheThresholdJustUnderTheMeasuredShakeAndTheDetectorUsesTheSameThreshold() {
        assertEquals(0f, Calibrators.motionSensitivity(40f), 0f)
        assertEquals(1f, Calibrators.motionSensitivity(6f), 0f)
        assertEquals(0f, Calibrators.motionSensitivity(Float.NaN), 0f)
        var last = -1f
        for (peak in listOf(8f, 10f, 12f, 14f, 16f, 18f, 20f)) {
            val s = Calibrators.motionSensitivity(peak)
            assertTrue("a harder shake needs less sensitivity", last < 0f || s <= last); last = s
            assertTrue("the shake still clears the threshold it set", peak >= Calibrators.shakeThreshold(s) || s == 1f)
        }
        assertEquals(17f, Calibrators.shakeThreshold(0f), 0f); assertEquals(9f, Calibrators.shakeThreshold(1f), 0f); assertEquals(13f, Calibrators.shakeThreshold(0.5f), 1e-5f)
    }

    private fun shake(m: ShakePeakMeter, amp: Float, startMs: Long = 0): Float? {
        var out: Float? = null
        var t = startMs
        repeat(30) { m.feed(t, 0f, 0f, 9.81f); t += 20 }                          // rest
        repeat(8) { i -> val v = if (i % 2 == 0) amp else -amp; val r = m.feed(t, v, 0f, 9.81f); if (r != null) out = r; t += 40 }
        repeat(40) { val r = m.feed(t, 0f, 0f, 9.81f); if (r != null) out = r; t += 20 }
        return out
    }

    @Test fun CFG_27_w56_aShakeIsMeasuredWhenItEndsAndAStrongerOneReadsHigher() {
        val soft = shake(ShakePeakMeter(), 10f); val hard = shake(ShakePeakMeter(), 20f)
        assertNotNull(soft); assertNotNull(hard)
        assertTrue("soft $soft hard $hard", hard!! > soft!! && soft > 6f)
    }

    @Test fun CFG_27_w56_aBumpThatIsTooSoftIsNotMeasuredAndRestIsNeverAShake() {
        assertNull(shake(ShakePeakMeter(), 4f))
        val m = ShakePeakMeter(); var any: Float? = null
        repeat(200) { i -> m.feed(i * 20L, 0f, 0f, 9.81f)?.let { any = it } }
        assertNull(any)
    }
}
