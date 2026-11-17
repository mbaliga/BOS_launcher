package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.SignalForce
import org.junit.Assert.assertEquals
import org.junit.Test

/** Wave 20 audit, PAL-24: the Lab's forced signal never reaches a build that has no Lab. */
class SignalForceGateTest {
    @Test fun PAL_24_withoutTheLabEveryStoredForceReadsAsLive() {
        for (f in SignalForce.entries) assertEquals(SignalForce.Live, SignalForceGate.effective(f, labAvailable = false))
    }

    @Test fun PAL_24_withTheLabTheStoredForceIsHonoured() {
        for (f in SignalForce.entries) assertEquals(f, SignalForceGate.effective(f, labAvailable = true))
    }

    @Test fun PAL_24_theDefaultKeepsTestsAndPreviewsOnTheStoredValue() {
        assertEquals(true, SignalForceGate.labAvailable)
    }

    @Test fun PAL_24_aPausedClockFreezesWhereItWasInsteadOfSnappingToZero() {
        val c = RadarClock()
        c.ms.longValue = 5L
        assertEquals(5L, c.now())
        c.live = androidx.compose.runtime.mutableFloatStateOf(12.5f)
        assertEquals(12_500L, c.now())
        c.live = null
        assertEquals(12_500L, c.now())
    }
}
