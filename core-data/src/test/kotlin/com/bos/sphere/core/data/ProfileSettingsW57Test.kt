package com.bos.sphere.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wave 57 (profiles): the settings a profile carries (CFG-46), the switch and precedence rule (CFG-48), the starters (CFG-45) and the
 * Backup's global values (CFG-49), all pure.
 */
class ProfileSettingsW57Test {
    private fun root(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "app/src/main").isDirectory) d = d.parentFile
        return d!!
    }

    private val schemaKeys: Map<String, String> by lazy {
        val a = JSONObject(File(root(), "web/liftoff-studio/schema.json").readText()).getJSONArray("fields")
        (0 until a.length()).associate { a.getJSONObject(it).getString("key") to a.getJSONObject(it).getString("scope") }
    }

    @Test fun CFG_46_everyCarriedKeyIsAStudioSchemaKeyOfTheRightScope() {
        for (k in ProfileSettings.ALL_KEYS) {
            val scope = schemaKeys[k]
            assertTrue("$k is not a key of web/liftoff-studio/schema.json", scope != null)
            val want = when {
                k.startsWith(ProfileSettings.GESTURE) -> "shortcuts"
                k.startsWith(ProfileSettings.DOCK) -> "dock"
                k == ProfileSettings.HAPTIC -> "prefFile"
                else -> "settings"
            }
            assertEquals(k, want, scope)
        }
        assertEquals("no key in two groups", ProfileSettings.ALL_KEYS.size, ProfileSettings.ALL_KEYS.toSet().size)
        assertFalse("device-bound keys never ride along", ProfileSettings.ALL_KEYS.any { it in StateSchema.DEVICE_BOUND_PREF_KEYS })
    }

    @Test fun CFG_46_theFiveGroupsCoverWhatTheOwnerNamed() {
        assertEquals(listOf("look", "home", "radar", "side", "sound"), ProfileGroup.entries.map { it.id })
        val look = ProfileGroup.Look.keys
        listOf("themeMode", "accent", "uiFont", "iconShape", "calmStyle", "wpGlanceSolid", "wpGlanceFrom", "wpGlanceTo").forEach { assertTrue(it, it in look) }
        val home = ProfileGroup.Home.keys
        listOf("glanceHotspots", "clockStyle", "gestures.SwipeUp", "dock.enabled").forEach { assertTrue(it, it in home) }
        assertTrue("searchFiles" in ProfileGroup.Radar.keys)
        assertTrue("hapticStrength" in ProfileGroup.Sound.keys && "uiSounds" in ProfileGroup.Sound.keys)
    }

    @Test fun CFG_46_prefsRoundTripThroughTheTextsWithoutLoss() {
        val p = LauncherPrefs(themeMode = ThemeMode.Light, accentColor = 0xFF33AA55, uiSoundVolume = 0.25f, glanceHotspots = setOf(Corner.TopLeft), searchFiles = false, calmStyle = CalmStyle.Beam, homePages = HomePages.Paginated)
        val texts = ProfileSettings.readPrefs(p)
        assertEquals("\"Light\"", texts["themeMode"])
        assertEquals("0.25", texts["uiSoundVolume"])
        assertEquals("false", texts["searchFiles"])
        val back = ProfileSettings.writePrefs(LauncherPrefs(), texts)
        assertEquals(ThemeMode.Light, back.themeMode)
        assertEquals(0xFF33AA55, back.accentColor)
        assertEquals(0.25f, back.uiSoundVolume, 0.0001f)
        assertEquals(setOf(Corner.TopLeft), back.glanceHotspots)
        assertEquals(CalmStyle.Beam, back.calmStyle)
        assertEquals(HomePages.Paginated, back.homePages)
        assertEquals("only the carried keys change", LauncherPrefs().iconScale, back.iconScale, 0f)
        // writing nothing changes nothing; a null clears an optional key back to "never chosen"
        assertEquals(p, ProfileSettings.writePrefs(p, emptyMap()))
        assertNull(ProfileSettings.writePrefs(p, mapOf("calmStyle" to "null")).calmStyle)
    }

    @Test fun CFG_46_textsAreTheBackupEncodingAndSurviveJson() {
        val m = mapOf("themeMode" to "\"Dark\"", "accent" to "4287527935", "uiSoundVolume" to "0.4", "dock.enabled" to "null", "searchFiles" to "true")
        val o = ProfileSettings.toJson(m)
        assertEquals("Dark", o.getString("themeMode"))
        assertEquals(4287527935L, o.getLong("accent"))
        assertTrue(o.isNull("dock.enabled"))
        assertEquals(m, ProfileSettings.fromJson(JSONObject(o.toString())))
        assertEquals("unknown keys are not taken", emptyMap<String, String>(), ProfileSettings.fromJson(JSONObject().put("notAKey", 1)))
    }

    @Test fun CFG_46_theGlanceWallpaperKindRidesAlongOnlyAsSolidOrGradient() {
        assertTrue(ProfileSettings.capturable("wpGlanceKind", "\"gradient\""))
        assertTrue(ProfileSettings.capturable("wpGlanceKind", "\"solid\""))
        assertFalse(ProfileSettings.capturable("wpGlanceKind", "\"picture\""))
        assertFalse(ProfileSettings.capturable("wpGlanceKind", "\"live\""))
        assertTrue(ProfileSettings.capturable("wpGlanceSolid", "123"))
    }

    // ---- CFG-48: switch and precedence

    private val A = setOf("themeMode", "accent") // what profile A carries
    private val B = setOf("themeMode", "searchFiles") // what profile B carries

    @Test fun CFG_48_enteringAProfileWritesItsValuesAndKeepsTheGlobalOnesAside() {
        val live = mapOf("themeMode" to "\"Dark\"", "accent" to "1", "searchFiles" to "false")
        // From a profile that carries nothing into B.
        val p = ProfileSwitch.plan(live, emptySet(), emptyMap(), B, mapOf("themeMode" to "\"Light\"", "searchFiles" to "true"))
        assertEquals(mapOf("themeMode" to "\"Light\"", "searchFiles" to "true"), p.writes)
        assertEquals("the global values of B's keys wait in the stash", mapOf("themeMode" to "\"Dark\"", "searchFiles" to "false"), p.stash)
        assertEquals(emptyMap<String, String>(), p.leaving)
    }

    @Test fun CFG_48_leavingAProfileSavesItsCarriedKeysAndRestoresTheGlobalOnesTheNextDoesNotCarry() {
        // A is active: its themeMode and accent are its own; the global values are in the stash. The user changed accent while in A.
        val live = mapOf("themeMode" to "\"Amoled\"", "accent" to "7", "searchFiles" to "false")
        val stash = mapOf("themeMode" to "\"Dark\"", "accent" to "1")
        val p = ProfileSwitch.plan(live, A, stash, B, mapOf("themeMode" to "\"Light\"", "searchFiles" to "true"))
        assertEquals("A keeps what it has now, including the change made while it was active", mapOf("themeMode" to "\"Amoled\"", "accent" to "7"), p.leaving)
        assertEquals("accent is not B's: it goes back to its global value", "1", p.writes["accent"])
        assertEquals("\"Light\"", p.writes["themeMode"])
        assertEquals("true", p.writes["searchFiles"])
        assertEquals(mapOf("themeMode" to "\"Dark\"", "searchFiles" to "false"), p.stash)
    }

    @Test fun CFG_48_aSettingTheActiveProfileDoesNotCarryStaysGlobal() {
        // In A the user changed searchFiles (not A's): it is global, so it is still there after a switch to a profile that carries nothing.
        val live = mapOf("themeMode" to "\"Amoled\"", "accent" to "7", "searchFiles" to "true")
        val p = ProfileSwitch.plan(live, A, mapOf("themeMode" to "\"Dark\"", "accent" to "1"), emptySet(), emptyMap())
        assertFalse("searchFiles" in p.writes)
        assertEquals("true", p.after["searchFiles"])
        assertEquals(mapOf("themeMode" to "\"Dark\"", "accent" to "1"), p.writes)
        assertEquals(emptyMap<String, String>(), p.stash)
    }

    @Test fun CFG_48_switchingThereAndBackRestoresEveryValue() {
        val start = mapOf("themeMode" to "\"Dark\"", "accent" to "1", "searchFiles" to "false")
        var live = start
        var stash = emptyMap<String, String>()
        var aSettings = mapOf("themeMode" to "\"Light\"", "accent" to "9")
        // Home (carries nothing) -> A
        ProfileSwitch.plan(live, emptySet(), stash, A, aSettings).also { live = live + it.writes; stash = it.stash }
        assertEquals("\"Light\"", live["themeMode"])
        // A -> Home
        ProfileSwitch.plan(live, A, stash, emptySet(), emptyMap()).also { live = live + it.writes; stash = it.stash; aSettings = it.leaving }
        assertEquals(start, live)
        assertEquals(mapOf("themeMode" to "\"Light\"", "accent" to "9"), aSettings)
    }

    @Test fun CFG_48_aCarriedKeyTheProfileHasNoValueForTakesTheGlobalValue() {
        val live = mapOf("themeMode" to "\"Dark\"")
        val p = ProfileSwitch.plan(live, emptySet(), emptyMap(), setOf("themeMode"), emptyMap())
        assertEquals(emptyMap<String, String>(), p.writes)
        assertEquals(mapOf("themeMode" to "\"Dark\""), p.stash)
    }

    // ---- CFG-45: starters

    private val apps = listOf(
        SuggestApp("gm", "com.google.android.gm", "Gmail"),
        SuggestApp("slack", "com.Slack", "Slack"),
        SuggestApp("wa", "com.whatsapp", "WhatsApp"),
        SuggestApp("clock", "com.google.android.deskclock", "Clock"),
        SuggestApp("yt", "com.google.android.youtube", "YouTube"),
    )

    @Test fun CFG_45_theWorkStarterPutsWorkAppsOnPage1TheirNotificationsAndRadarFiles() {
        assertEquals(setOf(ProfileGroup.Radar), ProfileStarters.carries(ProfileStart.Work))
        val s = ProfileStarters.settingsFor(ProfileStart.Work, ProfileStarters.carries(ProfileStart.Work), mapOf("searchFiles" to "false", "searchWeb" to "true", "themeMode" to "\"Dark\""))
        assertEquals("true", s["searchFiles"])
        assertEquals("the other Radar keys ride along as they are now", "true", s["searchWeb"])
        assertFalse("Look is not carried", "themeMode" in s)
        assertEquals(listOf("gm", "slack"), ProfileStarters.firstPage(ProfileStart.Work, apps))
        assertEquals(setOf("com.google.android.gm", "com.Slack"), ProfileStarters.notificationApps(ProfileStart.Work, apps))
        val arr = JSONObject(ProfileStarters.arrangement(listOf("gm", "slack")))
        assertEquals(listOf("gm", "slack"), HomeLayoutCodec.decode(arr.getString("pages")).single().keys)
        assertEquals(0, arr.getJSONArray("dock").length())
    }

    @Test fun CFG_45_theCalmStarterIsGlanceOnlyCalmStyleTwoHotspotsAndOnlyMessagesAndAlerts() {
        val c = ProfileStarters.carries(ProfileStart.Calm)
        assertEquals(setOf(ProfileGroup.Look, ProfileGroup.Home), c)
        val s = ProfileStarters.settingsFor(ProfileStart.Calm, c, emptyMap())
        assertEquals("\"Off\"", s["homePages"])
        assertEquals("\"EdgeDots\"", s["calmStyle"])
        assertEquals("\"BottomRight,BottomLeft\"", s["glanceHotspots"])
        assertEquals(setOf("com.whatsapp", "com.google.android.deskclock"), ProfileStarters.notificationApps(ProfileStart.Calm, apps))
        // unticking Look drops the calm style but keeps the Home values
        val noLook = ProfileStarters.settingsFor(ProfileStart.Calm, setOf(ProfileGroup.Home), emptyMap())
        assertFalse("calmStyle" in noLook)
        assertEquals("\"Off\"", noLook["homePages"])
    }

    @Test fun CFG_45_theDeskStarterTurnsTheDockOnAtTheSideAndOpensTheKeyboard() {
        val s = ProfileStarters.settingsFor(ProfileStart.Desk, ProfileStarters.carries(ProfileStart.Desk), emptyMap())
        assertEquals("true", s["dock.enabled"])
        assertEquals("\"Left\"", s["dock.edge"])
        assertEquals("true", s["kbOpenP"])
        assertEquals("true", s["kbOpenL"])
        assertNull("Desk keeps every app's notifications", ProfileStarters.notificationApps(ProfileStart.Desk, apps))
        assertEquals(emptySet<ProfileGroup>(), ProfileStarters.carries(ProfileStart.Current))
    }

    // ---- CFG-49: the Backup carries the global values of the active profile's keys

    @Test fun CFG_49_theWorkspaceFileSourceCarriesTheGlobalValuesThroughExportAndImport() {
        val dir = kotlin.io.path.createTempDirectory("w57").toFile()
        try {
            val f = File(dir, "liftoff_workspaces.json")
            f.writeText(JSONObject().put("schemaVersion", 1).put("activeId", "w").put("globalSettings", JSONObject().put("themeMode", "Dark"))
                .put("workspaces", org.json.JSONArray().put(JSONObject().put("id", "w").put("name", "Work").put("carries", org.json.JSONArray().put("look")).put("settings", JSONObject().put("themeMode", "Light")))).toString())
            val src = WorkspaceFileSource(f)
            val dock = src.readObjects().getValue(StateSchema.K_DOCK)
            assertEquals("Dark", dock.getJSONObject("globalSettings").getString("themeMode"))
            val ws = src.read().getValue(StateSchema.K_WORKSPACES)
            assertEquals("the profile's settings ride in the workspaces section", "Light", ws.getJSONObject(0).getJSONObject("settings").getString("themeMode"))
            // import into a fresh file
            val g = File(dir, "other.json")
            WorkspaceFileSource(g).write(mapOf(StateSchema.K_WORKSPACES to ws), mapOf(StateSchema.K_DOCK to dock), setOf(StateSchema.K_WORKSPACES, StateSchema.K_DOCK))
            val back = JSONObject(g.readText())
            assertEquals("Dark", back.getJSONObject("globalSettings").getString("themeMode"))
            assertEquals("look", back.getJSONArray("workspaces").getJSONObject(0).getJSONArray("carries").getString(0))
            // a Backup from before profiles (no globalSettings) clears a stale stash when it brings its own workspaces
            WorkspaceFileSource(g).write(mapOf(StateSchema.K_WORKSPACES to org.json.JSONArray().put(JSONObject().put("id", "h").put("name", "Home"))), mapOf(StateSchema.K_DOCK to JSONObject().put("activeId", "h")), setOf(StateSchema.K_WORKSPACES, StateSchema.K_DOCK))
            assertFalse(JSONObject(g.readText()).has("globalSettings"))
        } finally { dir.deleteRecursively() }
    }
}
