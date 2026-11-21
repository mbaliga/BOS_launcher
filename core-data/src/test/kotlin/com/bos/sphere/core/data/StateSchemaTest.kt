package com.bos.sphere.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StateSchemaTest {
    @Test
    fun CFG_03_everyPriorVersionHasAFixtureAndMigratesToCurrent() {
        // Each version below the current one must have a migration step and a fixture file, so a new schema version
        // cannot be added without a tested migration from the previous one.
        val prior = StateSchema.OLDEST until StateSchema.CURRENT
        assertEquals(prior.toSet(), StateMigrations.migratableVersions)
        for (v in prior) {
            val doc = StateDocument.parse(StateTestSupport.resource("state_v$v.json"))
            assertEquals(v, doc.schemaVersion)
            val res = StateMigrations.toCurrent(doc)
            assertEquals(StateSchema.CURRENT, res.doc.schemaVersion)
            assertEquals((v until StateSchema.CURRENT).toList(), res.steps)
            // The result decodes and a re-export of it is itself current.
            val dec = StateCodec.decode(doc)
            val again = StateCodec.export(dec.parts, 1L)
            assertEquals(StateSchema.CURRENT, again.schemaVersion)
        }
    }

    @Test
    fun CFG_03_v1FixtureKeepsItsContent() {
        val dec = StateCodec.decode(StateDocument.parse(StateTestSupport.resource("state_v1.json")))
        val p = dec.parts
        assertEquals(HomeStyle.List, p.prefs.homeStyle)
        assertEquals(5, p.prefs.portraitGrid.columns)
        assertEquals(listOf("tile:clock", "com.example.mail/.Main#0"), p.layout.pages.single().keys)
        assertEquals(setOf("com.example.old/.Main#0"), p.layout.removed)
        assertEquals(CornerSlot.Single(CornerItem.App("com.example.mail/.Main#0")), p.corners[Corner.TopLeft])
        assertEquals(null, p.gestures[HomeGesture.PinchIn])
        assertEquals(CornerItem.Action(CornerActionId.AllApps), p.gestures[HomeGesture.DoubleTap])
        assertEquals("r1", p.launchpadRules.single().id)
        assertEquals("keep-me", p.extras.unknownPrefs.getString("flavour"))
        assertEquals(1, p.extras.unknownTop.getJSONObject("futureThing").getInt("a"))
    }

    @Test
    fun CFG_03_unknownFieldsSurviveAMigrationAndARoundTrip() {
        val v1 = StateDocument.parse(StateTestSupport.resource("state_v1.json"))
        val migrated = StateMigrations.toCurrent(v1).doc
        assertEquals(3, migrated.json.getJSONObject("futureThing").getJSONArray("b").length())
        // Document level: parse, encode, parse keeps everything.
        val reparsed = StateDocument.parse(migrated.encode())
        assertEquals(migrated.json.toString(), reparsed.json.toString())
        // Through the typed model and back: unknown top-level and unknown settings keys are re-emitted.
        val exported = StateCodec.export(StateCodec.decode(v1).parts, 5L).json
        assertEquals("keep-me", exported.getJSONObject("settings").getJSONObject("prefs").getString("flavour"))
        assertEquals(1, exported.getJSONObject("futureThing").getInt("a"))
    }

    @Test
    fun CFG_03_aFileFromANewerBuildReadsAndKeepsItsUnknownFields() {
        val cur = StateCodec.export(StateParts(), 1L).json
        cur.put(StateSchema.K_VERSION, StateSchema.CURRENT + 3)
        cur.put("holograms", JSONObject().put("x", true))
        val dec = StateCodec.decode(StateDocument.parse(cur.toString()))
        assertTrue(dec.newer)
        assertTrue(StateCodec.export(dec.parts, 2L).json.has("holograms"))
    }

    @Test
    fun CFG_03_rejectsFilesThatAreNotStateFiles() {
        for (bad in listOf("not json", "{}", "{\"format\":\"other\",\"schemaVersion\":1}", "{\"format\":\"liftoff-state\"}")) {
            try { StateDocument.parse(bad); fail("accepted $bad") } catch (_: StateFormatException) { }
        }
        try { StateMigrations.toCurrent(StateDocument(JSONObject("{\"format\":\"liftoff-state\",\"schemaVersion\":0}"))); fail() } catch (_: StateFormatException) { }
    }

    @Test
    fun CFG_11_settingsAreASeparableDocument() {
        val prefs = LauncherPrefs(homeStyle = HomeStyle.Dsi, showLabels = true, accentColor = 0xFF123456)
        val full = StateCodec.export(StateParts(prefs = prefs), 1L)
        val settings = full.settingsDocument()!!
        assertEquals(StateSchema.SETTINGS_FORMAT, settings.getString("format"))
        assertTrue(settings.has("settingsVersion"))
        // The settings document alone carries no layout, shortcuts or workspaces ...
        assertFalse(settings.has("layout") || settings.has("shortcuts") || settings.has("workspaces"))
        // ... and imports on its own, touching only settings.
        val dec = StateCodec.decode(StateDocument.parse(settings.toString()))
        assertEquals(setOf(StateSchema.K_FORMAT, StateSchema.K_VERSION, StateSchema.K_SETTINGS), dec.present)
        assertEquals(HomeStyle.Dsi, dec.parts.prefs.homeStyle)
        assertEquals(0xFF123456, dec.parts.prefs.accentColor)
    }
}
