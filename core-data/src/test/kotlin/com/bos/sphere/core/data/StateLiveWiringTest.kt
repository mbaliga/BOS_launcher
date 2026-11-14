package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** CFG-01 to CFG-04 through the live stores: the sections other streams own, nested unknown fields, destructive-edit hooks. */
class StateLiveWiringTest {
    @get:Rule val tmp = TemporaryFolder()

    @After fun clearSink() = DestructiveEdits.install(null)

    /** One app process: the real stores over in-memory preferences, the live sections over temp files, like [StateService]. */
    private inner class Proc(name: String) {
        val prefs = LauncherPrefsStore(MapSharedPreferences())
        val layout = HomeLayoutStore(MapSharedPreferences())
        val shortcuts = ShortcutStore(MapSharedPreferences())
        val launchpad = StateTestSupport.FakeLaunchpad()
        val extrasStore = StateExtrasStore(File(tmp.root, "$name-extras.json"))
        var aliasRaw: String? = null
        val workspaceFile = File(tmp.root, "$name-workspaces.json")
        val prefFiles = HashMap<String, MapSharedPreferences>()
        var hidden: Set<String> = emptySet()
        val live = LiveExtras(
            extrasStore,
            listOf(
                PaletteAliasSource({ aliasRaw }) { aliasRaw = it },
                WorkspaceFileSource(workspaceFile),
                PrefFilesSource({ prefFiles.getOrPut(it) { MapSharedPreferences() } }),
            ),
        )
        val applier = StateApplier(
            prefs, layout, shortcuts, launchpad, live::read, live::write,
            { LaunchpadPrefs(launchpad.mode, launchpad.per) }, { LaunchpadGestures.defaults + launchpad.gestures },
            object : HiddenSink { override fun get() = hidden; override fun set(keys: Set<String>) { hidden = keys } },
        )
        fun export(provider: (Int) -> String? = { null }) = StateCodec.export(applier.current(), 1L, "t", provider)
        fun import(doc: StateDocument): StateCodec.Decoded = StateCodec.decode(doc).also { applier.apply(it.parts, it.present) }
    }

    private val workspaceDoc = """
        {"schemaVersion":1,"dock":{"enabled":true,"mode":"PinnedPlusRecent","edge":"Bottom"},"activeId":"w2",
         "workspaces":[{"id":"default","name":"Home","pins":["app:a/.A#0"],"widgets":[11],"actions":[],"notifyApps":null},
                       {"id":"w2","name":"Work","pins":[],"widgets":[],"actions":["x"],"notifyApps":["com.mail"]}],
         "groups":[{"id":"g1","name":"Duo","layout":"Tiled","members":[]}],
         "sessions":[{"id":"s1","name":"Morning","layout":"Tiled","savedAt":5,"entries":[]}],
         "schedules":[{"id":"c1","label":"Later","at":999,"kind":"App","target":"x"}]}
    """.trimIndent()

    // ---- CFG-01: every section the spec lists is in the file and comes from live data ---------------------------------

    @Test
    fun CFG_01_paletteAliasesAreExportedFromTheirStoreAndRestoredIntoIt() {
        val a = Proc("a"); val b = Proc("b")
        a.aliasRaw = "com.spotify.music\ttunes\ncom.x\tx alias"
        val doc = a.export()
        val arr = doc.json.getJSONArray(StateSchema.K_ALIASES)
        assertEquals(2, arr.length())
        assertEquals("tunes", arr.getJSONObject(0).getString("alias"))
        b.import(doc)
        assertEquals(a.aliasRaw, b.aliasRaw)
    }

    @Test
    fun CFG_01_workspacesSessionsAndGroupsAreExportedFromTheDockFileAndRestoredIntoIt() {
        val a = Proc("a"); val b = Proc("b")
        a.workspaceFile.writeText(workspaceDoc)
        val doc = a.export()
        val ws = doc.json.getJSONArray(StateSchema.K_WORKSPACES)
        assertEquals(listOf("Home", "Work"), (0 until ws.length()).map { ws.getJSONObject(it).getString("name") })
        // Widget ids are device-bound and never leave.
        assertEquals(0, ws.getJSONObject(0).getJSONArray("widgets").length())
        assertEquals("Morning", doc.json.getJSONArray(StateSchema.K_SESSIONS).getJSONObject(0).getString("name"))
        assertEquals("Duo", doc.json.getJSONObject(StateSchema.K_DOCK).getJSONArray("groups").getJSONObject(0).getString("name"))
        assertFalse("schedules are tied to a moment and stay home", doc.encode().contains("Later"))

        // The destination has its own file with a schedule and a different active workspace.
        b.workspaceFile.writeText("""{"schemaVersion":1,"activeId":"keep","workspaces":[{"id":"keep","name":"Mine"}],"schedules":[{"id":"z","label":"Mine too","at":1,"kind":"App","target":"y"}],"futureField":7}""")
        b.import(doc)
        val out = JSONObject(b.workspaceFile.readText())
        assertEquals(listOf("default", "w2"), (0 until out.getJSONArray("workspaces").length()).map { out.getJSONArray("workspaces").getJSONObject(it).getString("id") })
        assertEquals("w2", out.getString("activeId"))
        assertEquals("Morning", out.getJSONArray("sessions").getJSONObject(0).getString("name"))
        assertEquals("Duo", out.getJSONArray("groups").getJSONObject(0).getString("name"))
        assertEquals("schedules stay", "Mine too", out.getJSONArray("schedules").getJSONObject(0).getString("label"))
        assertEquals("fields this build does not know stay", 7, out.getInt("futureField"))
        assertEquals(1, out.getInt("schemaVersion"))
    }

    @Test
    fun CFG_01_aWorkspaceFileFromANewerBuildIsNeverOverwrittenByAnImport() {
        val a = Proc("a"); val b = Proc("b")
        a.workspaceFile.writeText(workspaceDoc)
        val newer = """{"schemaVersion":9,"workspaces":[{"id":"n","name":"Future"}]}"""
        b.workspaceFile.writeText(newer)
        b.import(a.export())
        assertEquals(newer, b.workspaceFile.readText())
    }

    @Test
    fun CFG_01_landscapeCornersAndHiddenAppsAreCarried() {
        val a = Proc("a"); val b = Proc("b")
        a.shortcuts.setLandscapeCorner(Corner.TopLeft, CornerSlot.Single(CornerItem.App("x/.X#0")))
        a.hidden = setOf("secret/.S#0")
        val doc = a.export()
        b.import(doc)
        assertEquals(CornerSlot.Single(CornerItem.App("x/.X#0")), b.shortcuts.landscapeCorners.value[Corner.TopLeft])
        assertEquals(setOf("secret/.S#0"), b.hidden)
    }

    @Test
    fun CFG_01_allowListedPreferenceFilesAreCarriedAndNothingElse() {
        val a = Proc("a"); val b = Proc("b")
        a.prefFiles["home_tags"] = MapSharedPreferences(mapOf("tags" to "work:com.mail"))
        a.prefFiles["radar_ui"] = MapSharedPreferences(mapOf("resultScale" to 1.25f))
        a.prefFiles["launch_history"] = MapSharedPreferences(mapOf("events" to "SECRET-HISTORY"))
        val doc = a.export()
        assertFalse(doc.encode().contains("SECRET-HISTORY"))
        assertTrue(doc.json.getJSONObject(StateSchema.K_PREF_FILES).has("home_tags"))
        b.import(doc)
        assertEquals("work:com.mail", b.prefFiles["home_tags"]!!.getString("tags", null))
        assertEquals(1.25f, b.prefFiles["radar_ui"]!!.getFloat("resultScale", 0f))
        assertNull(b.prefFiles["launch_history"])
    }

    @Test
    fun CFG_01_keyboardShortcutOverridesRideInTheShortcutsSection() {
        val a = Proc("a"); val b = Proc("b")
        a.extrasStore.write(StateExtras(keyboardShortcuts = "Search=CA:76"))
        val doc = a.export()
        assertEquals("Search=CA:76", doc.json.getJSONObject(StateSchema.K_SHORTCUTS).getString("keyboard"))
        b.import(doc)
        assertEquals("Search=CA:76", b.extrasStore.read().keyboardShortcuts)
    }

    @Test
    fun CFG_01_exportCarriesNoPersonalContent() {
        val a = Proc("a")
        a.prefFiles["launch_history"] = MapSharedPreferences(mapOf("events" to "SENTINEL"))
        a.prefFiles["weather"] = MapSharedPreferences(mapOf("lat" to "SENTINEL"))
        a.prefFiles["steps"] = MapSharedPreferences(mapOf("n" to "SENTINEL"))
        a.workspaceFile.writeText(workspaceDoc)
        assertFalse(a.export().encode().contains("SENTINEL"))
    }

    @Test
    fun CFG_03_aV1FileDoesNotWipeWorkspacesOrAliasesAlreadyOnTheInstall() {
        val b = Proc("b")
        b.aliasRaw = "p\tmine"
        b.workspaceFile.writeText(workspaceDoc)
        b.import(StateDocument.parse(StateTestSupport.resource("state_v1.json")))
        assertEquals("p\tmine", b.aliasRaw)
        assertEquals("Morning", JSONObject(b.workspaceFile.readText()).getJSONArray("sessions").getJSONObject(0).getString("name"))
    }

    // ---- CFG-02: widgets carry their provider so they can be re-added --------------------------------------------------

    @Test
    fun CFG_02_pendingWidgetsCarryTheirProviderComponent() {
        val a = Proc("a")
        a.shortcuts.setCorner(Corner.TopRight, CornerSlot.Widget(77, "Weather"))
        a.shortcuts.setLandscapeCorner(Corner.BottomLeft, CornerSlot.Widget(78, "Clock"))
        a.prefs.update { it.copy(leftPage = LeftPage.Widget, leftWidgetId = 9) }
        val doc = a.export { id -> "pkg$id/.Provider" }
        val back = StateCodec.decode(doc).parts.extras.widgets.associateBy { it.slot }
        assertEquals("pkg77/.Provider", back["corner.TopRight"]!!.provider)
        assertEquals("pkg78/.Provider", back["lcorner.BottomLeft"]!!.provider)
        assertEquals("pkg9/.Provider", back["leftPage"]!!.provider)
        assertFalse(doc.encode().contains("\"id\":77"))
    }

    @Test
    fun CFG_02_importKeepsTheLocalWidgetAndListsTheImportedOneAsPending() {
        val a = Proc("a"); val b = Proc("b")
        a.shortcuts.setCorner(Corner.TopRight, CornerSlot.Widget(77, "Weather"))
        b.shortcuts.setCorner(Corner.TopRight, CornerSlot.Widget(3, "Mine"))
        val dec = b.import(a.export { "pkg/.P" })
        assertEquals(CornerSlot.Widget(3, "Mine"), b.shortcuts.corners.value[Corner.TopRight])
        assertEquals(listOf("corner.TopRight"), dec.parts.extras.widgets.map { it.slot })
        assertEquals("pkg/.P", b.extrasStore.read().widgets.single().provider)
    }

    // ---- CFG-03: nested unknown fields survive the live stores ---------------------------------------------------------

    private fun futureFile(): StateDocument {
        val base = StateCodec.export(
            StateParts(
                layout = HomeLayout(listOf(HomePage(listOf("a/.A#0"), id = 4L), HomePage(listOf("b/.B#0"), id = 5L)), seeded = true),
                corners = DefaultCorners + (Corner.TopLeft to CornerSlot.Single(CornerItem.App("a/.A#0"))),
            ),
            1L,
        ).json
        base.getJSONObject("layout").put("layoutFuture", "L")
        base.getJSONObject("layout").getJSONArray("pages").getJSONObject(0).put("title", "Games")
        base.getJSONObject("shortcuts").put("shortcutsFuture", 1)
        base.getJSONObject("shortcuts").getJSONObject("corners").getJSONObject("TopLeft").put("badge", "new")
        base.getJSONObject("shortcuts").getJSONObject("corners").put("MiddleLeft", JSONObject().put("t", "empty"))
        base.getJSONObject("shortcuts").getJSONObject("gestures").put("TripleTap", "action:Search")
        base.getJSONObject("launchpad").put("launchpadFuture", true)
        base.getJSONObject("settings").put("settingsFuture", "S")
        base.put("topFuture", 3)
        return StateDocument(base)
    }

    @Test
    fun CFG_03_unknownFieldsNestedInKnownSectionsSurviveImportThenExportThroughTheLiveStores() {
        val b = Proc("b")
        b.import(futureFile())
        val out = b.export().json
        assertEquals("L", out.getJSONObject("layout").getString("layoutFuture"))
        val page4 = (0 until 2).map { out.getJSONObject("layout").getJSONArray("pages").getJSONObject(it) }.first { it.getLong("id") == 4L }
        assertEquals("Games", page4.getString("title"))
        assertEquals(1, out.getJSONObject("shortcuts").getInt("shortcutsFuture"))
        assertEquals("new", out.getJSONObject("shortcuts").getJSONObject("corners").getJSONObject("TopLeft").getString("badge"))
        assertTrue(out.getJSONObject("shortcuts").getJSONObject("corners").has("MiddleLeft"))
        assertEquals("action:Search", out.getJSONObject("shortcuts").getJSONObject("gestures").getString("TripleTap"))
        assertTrue(out.getJSONObject("launchpad").getBoolean("launchpadFuture"))
        assertEquals("S", out.getJSONObject("settings").getString("settingsFuture"))
        assertEquals(3, out.getInt("topFuture"))
    }

    @Test
    fun CFG_03_nestedFieldsFollowTheirThingAndGoWhenItGoes() {
        val b = Proc("b")
        b.import(futureFile())
        // The page that carried "title" is deleted, and the corner that carried "badge" is rebound.
        b.layout.setPages(b.layout.layout.value.pages.filter { it.id != 4L })
        b.shortcuts.setCorner(Corner.TopLeft, CornerSlot.Single(CornerItem.App("other/.O#0")))
        val txt = b.export().encode()
        assertFalse(txt.contains("Games"))
        assertFalse(txt.contains("\"badge\""))
        assertTrue("fields of things that are still there stay", txt.contains("layoutFuture"))
    }

    @Test
    fun CFG_03_aSettingsOnlyImportKeepsNestedFieldsOfOtherSections() {
        val b = Proc("b")
        b.import(futureFile())
        val settingsOnly = StateDocument.parse(StateCodec.export(StateParts(prefs = LauncherPrefs(homeStyle = HomeStyle.Dsi)), 2L).settingsDocument()!!.toString())
        b.import(settingsOnly)
        assertTrue(b.export().encode().contains("layoutFuture"))
        assertEquals(HomeStyle.Dsi, b.prefs.prefs.value.homeStyle)
    }

    // ---- CFG-04: destructive edits announce themselves before they are written ----------------------------------------

    @Test
    fun CFG_04_removingFromHomeClearingACornerClearingAGestureAndDroppingAPageEachSnapshotFirst() {
        val p = Proc("p")
        val seen = ArrayList<String>()
        p.layout.setPages(listOf(HomePage(listOf("a/.A#0"), id = 1L), HomePage(listOf("b/.B#0"), id = 2L)))
        p.shortcuts.setCorner(Corner.TopLeft, CornerSlot.Single(CornerItem.App("a/.A#0")))
        DestructiveEdits.install { seen += it }

        p.layout.setPages(listOf(HomePage(listOf("a/.A#0"), id = 1L)))
        p.layout.removeFromHome("a/.A#0", p.layout.layout.value.pages)
        p.shortcuts.setCorner(Corner.TopLeft, CornerSlot.Empty)
        p.shortcuts.setGesture(HomeGesture.entries.first { DefaultGestures[it] != null }, null)
        assertEquals(listOf("remove from home", "remove from home", "clear corner", "clear gesture"), seen)
    }

    @Test
    fun CFG_04_ordinaryEditsDoNotSnapshot() {
        val p = Proc("p")
        p.layout.setPages(listOf(HomePage(listOf("a/.A#0", "b/.B#0"), id = 1L)))
        val seen = ArrayList<String>()
        DestructiveEdits.install { seen += it }
        p.layout.setPages(listOf(HomePage(listOf("b/.B#0", "a/.A#0"), id = 1L)))
        p.layout.setPages(listOf(HomePage(listOf("b/.B#0", "a/.A#0"), id = 1L), HomePage(emptyList(), id = 2L)))
        p.shortcuts.setCorner(Corner.TopRight, CornerSlot.Single(CornerItem.App("x/.X#0")))
        assertTrue(seen.isEmpty())
    }

    @Test
    fun CFG_04_aSnapshotTakenByTheHookRestoresTheStateBeforeTheEdit() {
        val p = Proc("p")
        val snaps = SnapshotStore(File(tmp.root, "snaps"), 5) { 1L }
        p.layout.setPages(listOf(HomePage(listOf("a/.A#0"), id = 1L), HomePage(listOf("b/.B#0"), id = 2L)))
        DestructiveEdits.install { snaps.take(it, StateCodec.export(p.applier.current(), 1L).encode(), force = true) }
        p.layout.setPages(listOf(HomePage(listOf("a/.A#0"), id = 1L)))
        assertEquals(1, p.layout.layout.value.pages.size)
        val dec = StateCodec.decode(StateDocument.parse(snaps.read(snaps.list().single().id)!!))
        p.applier.apply(dec.parts, dec.present)
        assertEquals(2, p.layout.layout.value.pages.size)
        assertNotNull(snaps.latest())
    }
}
