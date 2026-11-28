package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/**
 * Wave 59 (glanceTop), CFG-75 to CFG-79: the music line's width rule (only a top corner that DRAWS something reserves room), the "Music line width" setting
 * and the end of the hotspot presets (the stored set is kept as it is).
 */
class GlanceTopW59Test {
    private val empty = mapOf<Corner, CornerSlot>(
        Corner.TopLeft to CornerSlot.Empty, Corner.TopRight to CornerSlot.Empty,
        Corner.BottomRight to CornerSlot.Single(CornerItem.Action(CornerActionId.Search)), Corner.BottomLeft to CornerSlot.Single(CornerItem.Action(CornerActionId.AllApps)),
    )

    @Test fun CFG_75_anEnabledButEmptyTopCornerDrawsNothingAndReservesNoRoom() {
        // the owner's case: all four on, nothing assigned at the top
        val drawn = GlanceHotspots.drawnOnScreen(GlanceHotspots.ALL, empty, 0, true)
        assertFalse(Corner.TopLeft in drawn); assertFalse(Corner.TopRight in drawn)
        assertEquals(setOf(Corner.BottomRight, Corner.BottomLeft), drawn)
        // the factory assignments do draw at the top
        assertEquals(GlanceHotspots.ALL, GlanceHotspots.drawnOnScreen(GlanceHotspots.ALL, DefaultCorners, 0, true))
        // a corner that holds something but is off draws nothing either
        assertEquals(setOf(Corner.TopLeft, Corner.BottomRight, Corner.BottomLeft), GlanceHotspots.drawnOnScreen(GlanceHotspots.ALL - Corner.TopRight, DefaultCorners, 0, true))
    }

    @Test fun CFG_75_everyKindOfAssignmentButNothingDraws() {
        assertFalse(GlanceHotspots.draws(CornerSlot.Empty)); assertFalse(GlanceHotspots.draws(null))
        assertTrue(GlanceHotspots.draws(CornerSlot.Single(CornerItem.Action(CornerActionId.Camera))))
        assertTrue(GlanceHotspots.draws(CornerSlot.Single(CornerItem.App("pkg/.A#0"))))
        assertTrue(GlanceHotspots.draws(CornerSlot.Palette("P", emptyList())))
        assertTrue(GlanceHotspots.draws(CornerSlot.Complication(ComplicationKind.Battery)))
        assertTrue(GlanceHotspots.draws(CornerSlot.ComplicationCluster(listOf(ComplicationKind.Date))))
        assertTrue(GlanceHotspots.draws(CornerSlot.Widget(3, "Weather")))
    }

    @Test fun CFG_75_theDrawnRuleFollowsTheRotationLikeTheEnabledSet() {
        // turned a quarter (rotation 1) the physical top right shows at the screen top left: empty there, so the screen top left draws nothing
        val tr = DefaultCorners + (Corner.TopRight to CornerSlot.Empty)
        val drawn = GlanceHotspots.drawnOnScreen(GlanceHotspots.ALL, tr, 1, true)
        assertFalse(Corner.TopLeft in drawn); assertTrue(Corner.TopRight in drawn)
        // without "Follow device rotation" the screen corner is the stored key
        assertFalse(Corner.TopRight in GlanceHotspots.drawnOnScreen(GlanceHotspots.ALL, tr, 1, false))
    }

    @Test fun CFG_76_musicLineWidthDefaultsToFitAroundCornersAndPersists() {
        assertEquals(MusicLineWidth.FitAroundCorners, LauncherPrefs().musicLineWidth)
        assertEquals(listOf("FitAroundCorners", "FullWidth"), MusicLineWidth.entries.map { it.name })
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(musicLineWidth = MusicLineWidth.FullWidth) }
        assertEquals("FullWidth", sp.getString("musicLineWidth", null))
        assertEquals(MusicLineWidth.FullWidth, LauncherPrefsStore(sp).prefs.value.musicLineWidth)
        // garbage or nothing stored starts on the default
        assertEquals(MusicLineWidth.FitAroundCorners, LauncherPrefsStore(MapSharedPreferences(mapOf("musicLineWidth" to "Huge"))).prefs.value.musicLineWidth)
        assertEquals(MusicLineWidth.FitAroundCorners, LauncherPrefsStore(MapSharedPreferences()).prefs.value.musicLineWidth)
    }

    @Test fun CFG_76_musicLineWidthTravelsInTheSettingsFile() {
        val doc = SettingsCodec.encode(LauncherPrefs(musicLineWidth = MusicLineWidth.FullWidth))
        assertEquals("FullWidth", doc.getJSONObject("prefs").getString("musicLineWidth"))
        assertEquals(MusicLineWidth.FullWidth, SettingsCodec.decode(doc).prefs.musicLineWidth)
        // an older file without the key reads as the default
        assertEquals(MusicLineWidth.FitAroundCorners, SettingsCodec.decode(JSONObject().put("prefs", JSONObject().put("showMusic", true))).prefs.musicLineWidth)
    }

    @Test fun CFG_77_theRemovedPresetsLeaveTheStoredHotspotSetAsItIs() {
        // what the presets wrote (Two (bottom), One, None) and a custom set all load unchanged: there is no preset key to migrate, only the set
        for (stored in listOf("BottomRight,BottomLeft", "BottomRight", "", "TopLeft,BottomRight", "TopLeft,TopRight,BottomRight,BottomLeft")) {
            val store = LauncherPrefsStore(MapSharedPreferences(mapOf("glanceHotspots" to stored)))
            assertEquals(stored, GlanceHotspots.decode(stored), store.prefs.value.glanceHotspots)
            store.update { it.copy(showMusic = !it.showMusic) } // any later save writes the same set back
            assertEquals(GlanceHotspots.decode(stored), LauncherPrefsStore(MapSharedPreferences(mapOf("glanceHotspots" to GlanceHotspots.encode(store.prefs.value.glanceHotspots)))).prefs.value.glanceHotspots)
        }
        // the Calm start still asks for the two bottom hotspots
        assertEquals("\"BottomRight,BottomLeft\"", ProfileStarters.overrides(ProfileStart.Calm)["glanceHotspots"])
    }
}
