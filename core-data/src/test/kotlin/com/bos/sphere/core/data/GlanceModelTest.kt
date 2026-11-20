package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-23: the pure rules of the Glance home. */
class GlanceModelTest {
    // ---- the music toggle (wave 38: replaces the Glance content set)
    @Test fun HOM_23_showMusicIsOnByDefault() { assertTrue(LauncherPrefs().showMusic) }

    @Test fun HOM_23_aStoredShowMusicWinsAndTheOldContentSetIsOnlyMigrationInput() {
        assertTrue(GlanceMusic.resolve(null, null))
        assertTrue(GlanceMusic.resolve(null, "Time,Date,NowPlaying"))
        assertFalse(GlanceMusic.resolve(null, "Time,Date"))
        assertFalse("an empty stored set was a deliberate nothing", GlanceMusic.resolve(null, ""))
        assertFalse(GlanceMusic.resolve(false, "Time,NowPlaying"))
        assertTrue(GlanceMusic.resolve(true, "Time"))
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

    @Test fun HOM_23_theGlanceSettingsArePersistedPlainPrefs() {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(showMusic = false, glanceHotspots = setOf(Corner.BottomLeft), glanceControlsSeconds = 9, glanceIndicator = false) }
        val back = LauncherPrefsStore(sp).prefs.value
        assertFalse(back.showMusic)
        assertEquals(setOf(Corner.BottomLeft), back.glanceHotspots)
        assertEquals(9, back.glanceControlsSeconds); assertFalse(back.glanceIndicator)
        // exported with the other settings
        val doc = SettingsCodec.encode(back)
        assertFalse(SettingsCodec.decode(doc).prefs.showMusic)
        assertEquals(back.glanceHotspots, SettingsCodec.decode(doc).prefs.glanceHotspots)
    }

    @Test fun GFX_38_anOldInstallKeepsItsMusicChoiceAndTheClockKeysAreGone() {
        // an old install that had turned the music line off (content set without NowPlaying) and a weather strength
        val old = MapSharedPreferences(mapOf("glanceContent" to "Time,Date,Weather", "glanceWeather" to "Normal", "glanceIndicator" to true))
        val store = LauncherPrefsStore(old)
        assertFalse(store.prefs.value.showMusic)
        store.update { it.copy(glanceControlsSeconds = 5) }
        assertFalse("the retired keys are deleted on the first save", old.contains("glanceContent") || old.contains("glanceWeather"))
        assertTrue(old.contains("showMusic"))
        assertFalse(LauncherPrefsStore(old).prefs.value.showMusic)
        // an old install with the default set keeps the music on; a fresh one is on
        assertTrue(LauncherPrefsStore(MapSharedPreferences(mapOf("glanceContent" to "Time,Date,NowPlaying"))).prefs.value.showMusic)
        assertTrue(LauncherPrefsStore(MapSharedPreferences()).prefs.value.showMusic)
        // an unreadable value never crashes
        assertTrue(LauncherPrefsStore(MapSharedPreferences(mapOf("glanceContent" to "garbage,,"))).prefs.value.glanceControlsSeconds > 0)
    }

    @Test fun GFX_38_theRetiredKeysArePurgedAndDroppedOnImportButTheContentSetWaitsForShowMusic() {
        assertTrue("glanceWeather" in RetiredPrefKeys.BY_FILE.getValue("launcher_prefs")); assertTrue("glanceWeather" in RetiredPrefKeys.DROPPED_ON_IMPORT)
        assertEquals("showMusic", RetiredPrefKeys.AFTER_MIGRATION.getValue("launcher_prefs")["glanceContent"])
        val doc = SettingsCodec.encode(LauncherPrefs())
        assertTrue(SettingsCodec.decode(doc).prefs.showMusic)
    }

    @Test fun GFX_38_theSuggestedAppKeyIsRetiredAndDeletedOnTheFirstSave() {
        val old = MapSharedPreferences(mapOf("suggestion" to true))
        val store = LauncherPrefsStore(old)
        store.update { it.copy(glanceControlsSeconds = 6) }
        assertFalse(old.contains("suggestion"))
        assertTrue("suggestion" in RetiredPrefKeys.BY_FILE.getValue("launcher_prefs")); assertTrue("suggestion" in RetiredPrefKeys.DROPPED_ON_IMPORT)
    }

    @Test fun GFX_41_theClockStylesHaveBigDigitalAsTheDefaultAndRoundTripWithTheOtherClockPrefs() {
        val d = LauncherPrefs()
        assertEquals(ClockStyle.BigDigital, d.clockStyle); assertEquals(ClockFormat.System, d.clockFormat)
        assertFalse("the date is off by default", d.glanceClockDate); assertFalse(d.glanceClockAccent); assertEquals(ClockSize.Large, d.glanceClockSize)
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(clockStyle = ClockStyle.Word, clockFormat = ClockFormat.Hour12, glanceClockDate = true, glanceClockAccent = true, glanceClockSize = ClockSize.Small) }
        val back = LauncherPrefsStore(sp).prefs.value
        assertEquals(ClockStyle.Word, back.clockStyle); assertEquals(ClockFormat.Hour12, back.clockFormat)
        assertTrue(back.glanceClockDate); assertTrue(back.glanceClockAccent); assertEquals(ClockSize.Small, back.glanceClockSize)
        assertEquals(ClockStyle.BigDigital, LauncherPrefsStore(MapSharedPreferences(mapOf("clockStyle" to "Hologram", "glanceClockSize" to "Huge"))).prefs.value.clockStyle)
        assertEquals(ClockSize.Large, LauncherPrefsStore(MapSharedPreferences(mapOf("glanceClockSize" to "Huge"))).prefs.value.glanceClockSize)
        assertEquals(ClockStyle.Off, LauncherPrefsStore(MapSharedPreferences(mapOf("clockStyle" to "Off"))).prefs.value.clockStyle)
    }

    @Test fun GFX_41_theWaveThirtyEightClockChoicesMigrateKeepingTheTwelveOrTwentyFourHourValue() {
        val analog = LauncherPrefsStore(MapSharedPreferences(mapOf("clockStyle" to "Analog", "clockFormat" to "Hour24"))).prefs.value
        assertEquals(ClockStyle.AnalogClassic, analog.clockStyle); assertEquals(ClockFormat.Hour24, analog.clockFormat)
        val digital = LauncherPrefsStore(MapSharedPreferences(mapOf("clockStyle" to "Digital", "clockFormat" to "Hour12"))).prefs.value
        assertEquals(ClockStyle.BigDigital, digital.clockStyle); assertEquals(ClockFormat.Hour12, digital.clockFormat)
        // the migrated value is written under the new name on the next save
        val sp = MapSharedPreferences(mapOf("clockStyle" to "Analog"))
        LauncherPrefsStore(sp).update { it.copy(glanceControlsSeconds = 5) }
        assertEquals("AnalogClassic", sp.getString("clockStyle", null))
        assertEquals(ClockStyle.fromStored("Digital"), ClockStyle.BigDigital); assertEquals(null, ClockStyle.fromStored(null))
    }

    @Test fun GFX_38_theOldClockKeysAreRetiredButTheUsersFormatSurvives() {
        val old = MapSharedPreferences(mapOf(
            "clockPosition" to "Bottom", "clockLayout" to "Stacked", "clockSize" to 40f, "clockDate" to true, "clockDay" to true, "clockWeather" to true, "weatherF" to true,
            "strip" to "Time:Left,Battery:Right", "healthSource" to "HealthConnect", "clockFormat" to "Hour12",
        ))
        val store = LauncherPrefsStore(old)
        assertEquals("the 12 hour choice is kept", ClockFormat.Hour12, store.prefs.value.clockFormat)
        store.update { it.copy(glanceControlsSeconds = 5) }
        for (k in ClockRetired.KEYS) { assertFalse("$k is deleted on the first save", old.contains(k)); assertTrue("$k is retired", k in RetiredPrefKeys.BY_FILE.getValue("launcher_prefs")); assertTrue(k in RetiredPrefKeys.DROPPED_ON_IMPORT) }
        assertEquals(ClockFormat.Hour12, LauncherPrefsStore(old).prefs.value.clockFormat)
        // garbage of any shape starts normally
        assertEquals(ClockStyle.BigDigital, LauncherPrefsStore(MapSharedPreferences(mapOf("clockPosition" to 7, "strip" to "x:y:z,,", "clockSize" to "big"))).prefs.value.clockStyle)
    }

    @Test fun HOM_12_clockTileOptionsPersistAndDefaultToTheOldLook() {
        val sp = MapSharedPreferences(emptyMap())
        val d = LauncherPrefsStore(sp).prefs.value
        assertFalse(d.clockTileAnalog); assertEquals(ClockFormat.System, d.clockTileFormat); assertTrue(d.clockTileDate)
        LauncherPrefsStore(sp).update { it.copy(clockTileAnalog = true, clockTileFormat = ClockFormat.Hour12, clockTileDate = false) }
        val back = LauncherPrefsStore(sp).prefs.value
        assertTrue(back.clockTileAnalog); assertEquals(ClockFormat.Hour12, back.clockTileFormat); assertFalse(back.clockTileDate)
        assertEquals(ClockFormat.System, LauncherPrefsStore(MapSharedPreferences(mapOf("clockTileFormat" to "Nonsense"))).prefs.value.clockTileFormat)
    }
}
