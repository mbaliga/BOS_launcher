package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PAL-24, CFG-17, HOM-16: the background settings (two slots, intensity, Lab signal override) are plain prefs that survive a
 * restart and default to Spire + Breathing dots. Wave 15: the wave 14 Radar toggles (sweep, signal view, comic lines) became
 * items and rules of the new model, so their tests became tests of the slots.
 */
class RadarBackgroundPrefsTest {
    @Test fun PAL_24_defaultsAreSpireAndBreathingDotsNormalAndLive() {
        val p = LauncherPrefs()
        assertEquals(BgItem.Spire, p.backgroundSlot1); assertEquals(BgItem.BreathingDots, p.backgroundSlot2)
        assertEquals(RadarIntensity.Normal, p.radarIntensity)
        assertEquals(SignalForce.Live, p.radarSignalForce)
    }

    @Test fun PAL_24_settingsRoundTripThroughThePlainPrefsFile() {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(backgroundSlot1 = BgItem.Laser, backgroundSlot2 = BgItem.BottomGlow, radarIntensity = RadarIntensity.Subtle, radarSignalForce = SignalForce.Weak) }
        val back = LauncherPrefsStore(sp).prefs.value
        assertEquals(BgItem.Laser, back.backgroundSlot1); assertEquals(BgItem.BottomGlow, back.backgroundSlot2)
        assertEquals(RadarIntensity.Subtle, back.radarIntensity); assertEquals(SignalForce.Weak, back.radarSignalForce)
        assertEquals("laser", sp.getString("bgSlot1", null)); assertEquals("bottom-glow", sp.getString("bgSlot2", null))
    }

    @Test fun PAL_24_unknownStoredValuesFallBackToTheDefaults() {
        val p = LauncherPrefsStore(MapSharedPreferences(mapOf("radarIntensity" to "Loud", "radarSignalForce" to "Nonsense", "bgSlot1" to "plasma", "bgSlot2" to "none"))).prefs.value
        assertEquals(RadarIntensity.Normal, p.radarIntensity)
        assertEquals(SignalForce.Live, p.radarSignalForce)
        assertEquals(BgItem.Spire, p.backgroundSlot1); assertEquals(BgItem.None, p.backgroundSlot2)
    }

    @Test fun PAL_24_forcedLevelsMatchTheNamedStates() {
        assertEquals(listOf(-1, 0, 1, 2, 3, 4), SignalForce.entries.map { it.level })
    }

    @Test fun HOM_16_aStoredPairThatBreaksTheRulesIsRepairedOnWriteAndOnRead() {
        val store = LauncherPrefsStore(MapSharedPreferences())
        store.update { it.copy(backgroundSlot1 = BgItem.Beam, backgroundSlot2 = BgItem.Halo) } // two living wallpapers
        assertEquals(BgItem.Beam, store.prefs.value.backgroundSlot1); assertEquals(BgItem.None, store.prefs.value.backgroundSlot2)
        val read = LauncherPrefsStore(MapSharedPreferences(mapOf("bgSlot1" to "glow-lines", "bgSlot2" to "glow-lines"))).prefs.value
        assertEquals(BgItem.GlowLines, read.backgroundSlot1); assertEquals(BgItem.None, read.backgroundSlot2)
    }

    @Test fun HOM_16_everyOldBackgroundChoiceMapsToTheDefaultPairAndTheOldKeysAreDeleted() {
        val old = mapOf("bgKind" to "Live", "bgPreset" to "aurora", "bgA" to 1L, "bgB" to 2L, "wallpaper" to true, "searchBdStyle" to "Radar", "searchBackdrop" to false, "radarSweep" to false, "radarSignalView" to "off", "radarComic" to false, "style" to "Dsi")
        val sp = MapSharedPreferences(old)
        val store = LauncherPrefsStore(sp)
        assertEquals(BackgroundChoice.DEFAULT, BackgroundChoice(store.prefs.value.backgroundSlot1, store.prefs.value.backgroundSlot2))
        assertEquals(HomeStyle.Dsi, store.prefs.value.homeStyle)
        store.update { it.copy(iconScale = 0.5f) }
        RetiredBackgroundKeys.KEYS.forEach { assertFalse("$it is deleted", sp.contains(it)) }
        assertTrue(sp.contains("style"))
    }
}

/** Wave 30 (radarDim): Radar's own background setting, exactly two options, default Dimmed wallpaper. */
class RadarBackgroundW30Test {
    @Test fun PAL_24_w30_radarBackgroundHasExactlyTwoOptionsAndDefaultsToDimmed() {
        assertEquals(listOf("Dimmed wallpaper", "True black"), RadarBackground.entries.map { it.label })
        assertEquals(RadarBackground.Dimmed, LauncherPrefs().radarBackground)
        assertEquals(RadarBackground.Dimmed, LauncherPrefsStore(MapSharedPreferences()).prefs.value.radarBackground)
    }

    @Test fun PAL_24_w30_radarBackgroundPersistsInTheLauncherPrefsFileAndAnUnknownValueFallsBack() {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(radarBackground = RadarBackground.TrueBlack) }
        assertEquals("TrueBlack", sp.getString("radarBackground", null))
        assertEquals(RadarBackground.TrueBlack, LauncherPrefsStore(sp).prefs.value.radarBackground)
        assertEquals(RadarBackground.Dimmed, LauncherPrefsStore(MapSharedPreferences(mapOf("radarBackground" to "Bright"))).prefs.value.radarBackground)
    }
}
