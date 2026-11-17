package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-23: the pure rules of the Glance home. */
class GlanceModelTest {
    // ---- content presets
    @Test fun HOM_23_defaultContentIsTimeDateAndTheMusicLine() {
        assertEquals(setOf(GlanceItem.Time, GlanceItem.Date, GlanceItem.NowPlaying), LauncherPrefs().glanceContent)
        assertEquals(GlancePreset.TimeDate, GlanceContent.presetOf(GlanceContent.DEFAULT))
    }

    @Test fun HOM_23_presetsReplaceOnlyTheClockPartAndKeepMusicAndBattery() {
        val start = setOf(GlanceItem.Time, GlanceItem.NowPlaying, GlanceItem.Battery)
        assertEquals(setOf(GlanceItem.Date, GlanceItem.Weather, GlanceItem.NowPlaying, GlanceItem.Battery), GlanceContent.applyPreset(start, GlancePreset.DateWeather))
        assertEquals(setOf(GlanceItem.Time, GlanceItem.NowPlaying, GlanceItem.Battery), GlanceContent.applyPreset(start, GlancePreset.Time))
        assertEquals(start, GlanceContent.applyPreset(start, GlancePreset.Custom))
        for (p in GlancePreset.entries.filter { it != GlancePreset.Custom }) assertEquals(p, GlanceContent.presetOf(GlanceContent.applyPreset(start, p)))
    }

    @Test fun HOM_23_aFreeCombinationIsCustom() {
        assertEquals(GlancePreset.Custom, GlanceContent.presetOf(setOf(GlanceItem.Weather)))
        assertEquals(GlancePreset.Custom, GlanceContent.presetOf(emptySet()))
        assertEquals(setOf(GlanceItem.Time), GlanceContent.toggle(setOf(GlanceItem.Time, GlanceItem.Date), GlanceItem.Date))
    }

    @Test fun HOM_23_contentRoundTripsAndAnEmptyStringIsNothing() {
        val all = GlanceItem.entries.toSet()
        assertEquals(all, GlanceContent.decode(GlanceContent.encode(all)))
        assertEquals(emptySet<GlanceItem>(), GlanceContent.decode(""))
        assertEquals(GlanceContent.DEFAULT, GlanceContent.decode(null))
        assertEquals(setOf(GlanceItem.Time), GlanceContent.decode("Time,Hologram"))
    }

    // ---- hotspots
    @Test fun HOM_23_hotspotPresetsAreFourTwoOneNone() {
        assertEquals(4, GlanceHotspots.preset(HotspotPreset.Four)!!.size)
        assertEquals(setOf(Corner.BottomLeft, Corner.BottomRight), GlanceHotspots.preset(HotspotPreset.Two))
        assertEquals(1, GlanceHotspots.preset(HotspotPreset.One)!!.size)
        assertEquals(emptySet<Corner>(), GlanceHotspots.preset(HotspotPreset.None))
        for (p in HotspotPreset.entries.filter { it != HotspotPreset.Custom }) assertEquals(p, GlanceHotspots.presetOf(GlanceHotspots.preset(p)!!))
        assertEquals(HotspotPreset.Custom, GlanceHotspots.presetOf(setOf(Corner.TopLeft, Corner.BottomRight, Corner.TopRight)))
        assertEquals(GlanceHotspots.ALL, LauncherPrefs().glanceHotspots)
    }

    @Test fun HOM_23_hotspotsRoundTripAndNoneIsAValidStoredState() {
        val three = setOf(Corner.TopLeft, Corner.BottomLeft, Corner.BottomRight)
        assertEquals(three, GlanceHotspots.decode(GlanceHotspots.encode(three)))
        assertEquals(emptySet<Corner>(), GlanceHotspots.decode(""))
        assertEquals(GlanceHotspots.ALL, GlanceHotspots.decode(null))
    }

    @Test fun HOM_23_theWholeCompositionRotatesWithTheDevice() {
        // Only the physical top left is on: at rotation 0 it is the screen top left; at every rotation exactly one screen corner draws,
        // and it is the one over which the physical top left sits.
        val on = setOf(Corner.TopLeft)
        assertEquals(setOf(Corner.TopLeft), GlanceHotspots.enabledOnScreen(on, 0, true))
        for (r in 0..3) {
            val screen = GlanceHotspots.enabledOnScreen(on, r, true)
            assertEquals(1, screen.size)
            assertEquals(Corner.TopLeft, CornerMapping.physicalCornerAt(screen.single(), r))
        }
        // without "follow the device" the screen corner is the stored corner
        assertEquals(on, GlanceHotspots.enabledOnScreen(on, 1, false))
        // all four on stays four on in every rotation
        for (r in 0..3) assertEquals(4, GlanceHotspots.enabledOnScreen(GlanceHotspots.ALL, r, true).size)
    }

    @Test fun HOM_23_aPaletteRunsItsFirstItemAndNothingIsNoHotspot() {
        assertNull(GlanceHotspots.itemOf(CornerSlot.Empty))
        assertEquals(CornerItem.Action(CornerActionId.Camera), GlanceHotspots.itemOf(DefaultCorners.getValue(Corner.TopLeft)))
    }

    // ---- the controls timer, with a fake clock
    private val hide = 4000L

    @Test fun HOM_23_aTapPopsTheControlsInOnlyWhileSomethingPlays() {
        assertFalse(ControlsPop().tapSurface(1000, playing = false, hideMs = hide).visible)
        val s = ControlsPop().tapSurface(1000, playing = true, hideMs = hide)
        assertTrue(s.visible); assertEquals(5000L, s.hideAt)
    }

    @Test fun HOM_23_controlsPopOutAfterTheTimeoutAndNotBefore() {
        val s = ControlsPop().tapSurface(1000, true, hide)
        assertTrue(s.expired(4999).visible)
        assertFalse(s.expired(5000).visible)
    }

    @Test fun HOM_23_anyInteractionWithTheControlsRestartsTheTimer() {
        var s = ControlsPop().tapSurface(0, true, hide)
        s = s.interact(3000, hide)
        assertEquals(7000L, s.hideAt)
        assertTrue(s.expired(5000).visible)
        assertFalse(s.expired(7000).visible)
    }

    @Test fun HOM_23_theTimerIsPausedWhileAFingerIsDownAndRestartsOnRelease() {
        var s = ControlsPop().tapSurface(0, true, hide)
        s = s.fingerDown()
        assertTrue(s.expired(60_000).visible)
        s = s.fingerUp(61_000, hide)
        assertTrue(s.expired(64_999).visible)
        assertFalse(s.expired(65_000).visible)
    }

    @Test fun HOM_23_aTapOutsideOrTheEndOfPlaybackPopsThemOut() {
        val s = ControlsPop().tapSurface(0, true, hide)
        assertFalse(s.tapSurface(500, true, hide).visible)   // tap outside while shown
        assertFalse(s.tapSurface(500, false, hide).visible)  // playback ended
        assertFalse(s.dismiss().visible)
    }

    @Test fun HOM_23_interactionsOnHiddenControlsDoNothing() {
        val h = ControlsPop()
        assertEquals(h, h.interact(5, hide)); assertEquals(h, h.fingerDown()); assertEquals(h, h.fingerUp(5, hide)); assertEquals(h, h.expired(99))
    }

    @Test fun HOM_23_theTimeoutIsClampedTwoToTenSeconds() {
        assertEquals(4, LauncherPrefs().glanceControlsSeconds)
        assertEquals(2, GlanceControls.clampSeconds(0)); assertEquals(10, GlanceControls.clampSeconds(99)); assertEquals(7, GlanceControls.clampSeconds(7))
    }

    // ---- weather to effect
    private fun w(code: Int) = WeatherInfo(12f, code, 0L)

    @Test fun HOM_23_weatherConditionsMapToEffects() {
        assertEquals(GlanceWeatherEffect.ClearDay, GlanceWeather.effectOf(w(0), true))
        assertEquals(GlanceWeatherEffect.ClearNight, GlanceWeather.effectOf(w(0), false))
        assertEquals(GlanceWeatherEffect.ClearNight, GlanceWeather.effectOf(w(1), false))
        assertEquals(GlanceWeatherEffect.Clouds, GlanceWeather.effectOf(w(3), true))
        assertEquals(GlanceWeatherEffect.Fog, GlanceWeather.effectOf(w(45), true))
        assertEquals(GlanceWeatherEffect.Rain, GlanceWeather.effectOf(w(63), true))
        assertEquals(GlanceWeatherEffect.Rain, GlanceWeather.effectOf(w(81), false))
        assertEquals(GlanceWeatherEffect.Snow, GlanceWeather.effectOf(w(73), true))
        assertEquals(GlanceWeatherEffect.Thunder, GlanceWeather.effectOf(w(95), true))
        assertEquals(GlanceWeatherEffect.Clouds, GlanceWeather.effectOf(w(1234), true))
    }

    @Test fun HOM_23_noWeatherMeansNoLayer() { assertNull(GlanceWeather.effectOf(null, true)) }

    @Test fun HOM_23_daylightIsAPlainHourRule() {
        assertFalse(GlanceWeather.isDaylight(5)); assertTrue(GlanceWeather.isDaylight(6)); assertTrue(GlanceWeather.isDaylight(19)); assertFalse(GlanceWeather.isDaylight(20))
    }

    @Test fun HOM_23_thunderFlashesAreRareSoftAndSlow() {
        // never in the first 6 s, one flash per period, the gap between flash starts is at least 20 s, soft, and a single pulse well under 3 Hz.
        assertEquals(0f, GlanceWeather.flashAlpha(0), 0f)
        assertTrue(GlanceWeather.FLASH_PERIOD_MS >= GlanceWeather.FLASH_MIN_GAP_MS)
        var starts = 0; var max = 0f; var prev = 0f
        var t = 0L
        while (t < 120_000L) {
            val a = GlanceWeather.flashAlpha(t); max = maxOf(max, a)
            if (a > 0f && prev == 0f) starts++
            prev = a; t += 50
        }
        assertEquals(5, starts) // flashes at 6, 29, 52, 75 and 98 s: one per period
        assertTrue(max <= GlanceWeather.FLASH_PEAK + 1e-6f)
        assertTrue(1000f / GlanceWeather.FLASH_LENGTH_MS <= GlanceWeather.FLASH_MAX_HZ)
        assertTrue(GlanceWeather.mayFlash(30_000, 0)); assertFalse(GlanceWeather.mayFlash(10_000, 0)); assertTrue(GlanceWeather.mayFlash(1, -1))
    }

    @Test fun HOM_23_theAmbienceNeverRunsUnderReducedMotionOrScreenOffOrHidden() {
        assertTrue(GlanceWeather.animates(false, true, true))
        assertFalse(GlanceWeather.animates(true, true, true)); assertFalse(GlanceWeather.animates(false, false, true)); assertFalse(GlanceWeather.animates(false, true, false))
        assertTrue(GlanceWeather.strength(RadarIntensity.Subtle) < GlanceWeather.strength(RadarIntensity.Normal))
    }

    // ---- the landing default and its migration
    @Test fun HOM_22_theDefaultLandingIsGlance() { assertEquals(HomeLanding.Glance, LauncherPrefs().homeLanding); assertEquals("Glance", HomeLanding.Glance.label) }

    @Test fun HOM_22_migrationKeepsExplicitChoicesAndMovesTheOldDefaultToGlance() {
        assertEquals(HomeLanding.Glance, LandingMigration.resolve(null, false))
        assertEquals(HomeLanding.Glance, LandingMigration.resolve("Selected", false))   // the old default cannot be told from a choice
        assertEquals(HomeLanding.Glance, LandingMigration.resolve("Minimal", false))
        assertEquals(HomeLanding.Recents, LandingMigration.resolve("Recents", false))
        assertEquals(HomeLanding.AllApps, LandingMigration.resolve("AllApps", false))
        assertEquals(HomeLanding.Selected, LandingMigration.resolve("Selected", true))  // an explicit pick sticks
        assertEquals(HomeLanding.Glance, LandingMigration.resolve("Hologram", true))
        assertEquals(HomeLanding.Glance, LandingMigration.resolve("Glance", true))
        assertTrue(LandingMigration.markerOnSave(HomeLanding.Selected, false)); assertFalse(LandingMigration.markerOnSave(HomeLanding.Glance, false)); assertTrue(LandingMigration.markerOnSave(HomeLanding.Glance, true))
    }

    @Test fun HOM_22_theLandingChoiceSticksThroughTheStore() {
        val sp = MapSharedPreferences()
        val store = LauncherPrefsStore(sp)
        assertEquals(HomeLanding.Glance, store.prefs.value.homeLanding)
        store.update { it.copy(clockSize = 70f) }
        assertEquals(HomeLanding.Glance, LauncherPrefsStore(sp).prefs.value.homeLanding)
        store.update { it.copy(homeLanding = HomeLanding.Selected) }
        assertEquals(HomeLanding.Selected, LauncherPrefsStore(sp).prefs.value.homeLanding)
        store.update { it.copy(homeLanding = HomeLanding.Glance) }
        assertEquals(HomeLanding.Glance, LauncherPrefsStore(sp).prefs.value.homeLanding)
    }

    @Test fun HOM_22_anOldInstallWithTheDefaultSelectedLandsOnGlanceAndRecentsIsKept() {
        assertEquals(HomeLanding.Glance, LauncherPrefsStore(MapSharedPreferences(mapOf("homeLanding" to "Selected"))).prefs.value.homeLanding)
        assertEquals(HomeLanding.Recents, LauncherPrefsStore(MapSharedPreferences(mapOf("homeLanding" to "Recents"))).prefs.value.homeLanding)
    }

    @Test fun HOM_23_theGlanceSettingsArePersistedPlainPrefs() {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(glanceContent = setOf(GlanceItem.Date, GlanceItem.Weather), glanceHotspots = setOf(Corner.BottomLeft), glanceControlsSeconds = 9, glanceWeatherIntensity = RadarIntensity.Normal, glanceIndicator = false) }
        val back = LauncherPrefsStore(sp).prefs.value
        assertEquals(setOf(GlanceItem.Date, GlanceItem.Weather), back.glanceContent)
        assertEquals(setOf(Corner.BottomLeft), back.glanceHotspots)
        assertEquals(9, back.glanceControlsSeconds); assertEquals(RadarIntensity.Normal, back.glanceWeatherIntensity); assertFalse(back.glanceIndicator)
        // exported with the other settings
        val doc = SettingsCodec.encode(back)
        assertEquals(back.glanceContent, SettingsCodec.decode(doc).prefs.glanceContent)
        assertEquals(back.glanceHotspots, SettingsCodec.decode(doc).prefs.glanceHotspots)
    }
}
