package com.bos.sphere.core.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** PAL-24: SignalState normalisation and debounce (pure). */
class RadarSignalTest {
    @Test fun PAL_24_noNetworkIsLevelZero() {
        val s = SignalLogic.normalise(SignalKind.None, -50, true)
        assertEquals(SignalState.NoSignal, s)
        assertFalse(s.hasSignal)
    }

    @Test fun PAL_24_wifiRssiThresholds() {
        assertEquals(4, SignalLogic.normalise(SignalKind.Wifi, -50, true).level)
        assertEquals(3, SignalLogic.normalise(SignalKind.Wifi, -60, true).level)
        assertEquals(2, SignalLogic.normalise(SignalKind.Wifi, -70, true).level)
        assertEquals(1, SignalLogic.normalise(SignalKind.Wifi, -90, true).level)
    }

    @Test fun PAL_24_cellularThresholdsAndUnspecified() {
        assertEquals(4, SignalLogic.normalise(SignalKind.Cellular, -80, true).level)
        assertEquals(2, SignalLogic.normalise(SignalKind.Cellular, -100, true).level)
        assertEquals(1, SignalLogic.normalise(SignalKind.Cellular, -120, true).level)
        assertEquals(3, SignalLogic.normalise(SignalKind.Cellular, SignalLogic.UNSPECIFIED, true).level)
        assertEquals(4, SignalLogic.normalise(SignalKind.Ethernet, SignalLogic.UNSPECIFIED, true).level)
    }

    @Test fun PAL_24_unvalidatedNetworkIsCappedAtWeak() {
        val s = SignalLogic.normalise(SignalKind.Wifi, -40, false)
        assertEquals(1, s.level)
        assertFalse(s.validated)
    }

    @Test fun PAL_24_wordsForTheDescription() {
        assertEquals("good", SignalLogic.levelWord(3))
        assertEquals("Wi-Fi", SignalLogic.kindWord(SignalKind.Wifi))
    }

    @Test fun PAL_24_forcedStates() {
        assertEquals(SignalState.NoSignal, SignalState.forced(0))
        assertEquals(4, SignalState.forced(9).level)
    }

    @Test fun PAL_24_debounceDropsAFlickerAndKeepsAStableChange() {
        val a = SignalState.forced(3); val b = SignalState.NoSignal; val c = SignalState.forced(1)
        // a at 0, b for 100 ms (flicker), a again, then c stays for good.
        val out = SignalLogic.settle(listOf(0L to a, 1000L to b, 1100L to a, 5000L to c), 600)
        assertEquals(listOf(a, c), out)
        // A change that holds for the window is emitted.
        assertEquals(listOf(a, b), SignalLogic.settle(listOf(0L to a, 1000L to b), 600))
    }
}
