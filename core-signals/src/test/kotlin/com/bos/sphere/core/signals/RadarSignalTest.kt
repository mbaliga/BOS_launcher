package com.bos.sphere.core.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import kotlinx.coroutines.flow.toList
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
    }

    @Test fun PAL_24_forcedStates() {
        assertEquals(SignalState.NoSignal, SignalState.forced(0))
        assertEquals(4, SignalState.forced(9).level)
    }

    /** The real operator the live source uses, on virtual time (wave 23: replaces a pure copy of the rule that nothing in the app called). */
    @Test fun PAL_24_debounceDropsAFlickerAndKeepsAStableChange() = kotlinx.coroutines.test.runTest {
        val a = SignalState.forced(3); val b = SignalState.NoSignal; val c = SignalState.forced(1)
        val flicker = kotlinx.coroutines.flow.flow { emit(a); kotlinx.coroutines.delay(1000); emit(b); kotlinx.coroutines.delay(100); emit(a); kotlinx.coroutines.delay(3900); emit(c) }
        assertEquals(listOf(a, c), flicker.settled(600).toList())
        val stable = kotlinx.coroutines.flow.flow { emit(a); kotlinx.coroutines.delay(1000); emit(b) }
        assertEquals(listOf(a, b), stable.settled(600).toList())
    }
}
