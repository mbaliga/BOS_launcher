package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PAL-24, CFG-17: the Radar background settings are plain prefs that survive a restart and default to the calm look. */
class RadarBackgroundPrefsTest {
    @Test fun PAL_24_defaultsAreSweepOnGlowLinesOnComicOnNormalAndLive() {
        val p = LauncherPrefs()
        assertTrue(p.radarSweep); assertTrue(p.radarComicNoSignal)
        assertEquals("glow", p.radarSignalView)
        assertEquals(RadarIntensity.Normal, p.radarIntensity)
        assertEquals(SignalForce.Live, p.radarSignalForce)
    }

    @Test fun PAL_24_settingsRoundTripThroughThePlainPrefsFile() {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(radarSweep = false, radarSignalView = "off", radarComicNoSignal = false, radarIntensity = RadarIntensity.Subtle, radarSignalForce = SignalForce.Weak) }
        val back = LauncherPrefsStore(sp).prefs.value
        assertEquals(false, back.radarSweep); assertEquals("off", back.radarSignalView); assertEquals(false, back.radarComicNoSignal)
        assertEquals(RadarIntensity.Subtle, back.radarIntensity); assertEquals(SignalForce.Weak, back.radarSignalForce)
    }

    @Test fun PAL_24_unknownStoredValuesFallBackToTheDefaults() {
        val p = LauncherPrefsStore(MapSharedPreferences(mapOf("radarIntensity" to "Loud", "radarSignalForce" to "Nonsense"))).prefs.value
        assertEquals(RadarIntensity.Normal, p.radarIntensity)
        assertEquals(SignalForce.Live, p.radarSignalForce)
    }

    @Test fun PAL_24_forcedLevelsMatchTheNamedStates() {
        assertEquals(listOf(-1, 0, 1, 2, 3, 4), SignalForce.entries.map { it.level })
    }
}
