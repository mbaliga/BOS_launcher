package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.ProfileStart
import com.bos.sphere.core.data.ProfileStarters
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 57 (profiles): the profile model on the workspace manager: migration from workspaces (CFG-49), switching with the carried
 * settings in one step and its undo (CFG-48), the precedence rule, which groups ride along (CFG-46) and linked app groups (CFG-47).
 */
class ProfileManagerW57Test {
    private class Hooks : WorkspaceHooks {
        var ready0 = true
        var home: String? = """{"pages":"[]","removed":[],"dock":[]}"""
        val applied = mutableListOf<String?>()
        val switched = mutableListOf<String>()
        override val ready: Boolean get() = ready0
        override fun captureHome() = home
        override fun applyHome(arrangement: String?) { applied += arrangement }
        override fun onSwitched(workspace: Workspace) { switched += workspace.name }
    }

    private val hooks = Hooks()
    private val port = MapProfileSettings(mutableMapOf("themeMode" to "\"Dark\"", "accent" to "1", "searchFiles" to "false", "searchWeb" to "true"))
    private var seq = 0
    private fun manager(store: InMemoryWorkspaceStore = InMemoryWorkspaceStore()) = WorkspaceManager(store, { hooks }, newId = { "p${seq++}" }, settingsPort = { port })

    /** A document written by a build from before profiles (wave 56): two workspaces, no profile fields. */
    private val v1 = JSONObject().put("schemaVersion", 1).put("activeId", "default")
        .put("dock", JSONObject().put("enabled", true).put("mode", "PinnedPlusRecent").put("edge", "Bottom"))
        .put("workspaces", JSONArray()
            .put(JSONObject().put("id", "default").put("name", "Home").put("home", JSONObject.NULL).put("pins", JSONArray().put("app:a")).put("widgets", JSONArray()).put("actions", JSONArray()).put("notifyApps", JSONObject.NULL))
            .put(JSONObject().put("id", "w2").put("name", "Work").put("home", """{"pages":"[]","removed":[],"dock":["pin:app:x"]}""").put("pins", JSONArray()).put("widgets", JSONArray()).put("actions", JSONArray().put("a1")).put("notifyApps", JSONArray().put("com.Slack"))))
        .put("groups", JSONArray()).put("sessions", JSONArray()).put("schedules", JSONArray()).toString()

    @Test fun CFG_49_workspacesBecomeProfilesWithTheSameNamesAndContentsAndNoSettings() {
        val m = manager(InMemoryWorkspaceStore(v1))
        assertEquals(listOf("Home", "Work"), m.current.workspaces.map { it.name })
        val work = m.current.workspaces[1]
        assertEquals(setOf("com.Slack"), work.notificationApps)
        assertEquals(listOf("a1"), work.paletteActions)
        assertTrue(work.homeArrangement!!.contains("pin:app:x"))
        assertTrue(m.current.workspaces.all { it.carries.isEmpty() && it.settings.isEmpty() && it.groupLinks.isEmpty() })
        assertTrue(m.current.globalSettings.isEmpty())
    }

    @Test fun CFG_49_aMigratedProfileSwitchesExactlyAsTheWorkspaceDidAndTouchesNoSetting() {
        val m = manager(InMemoryWorkspaceStore(v1))
        val before = port.values.toMap()
        assertTrue(m.switchTo("w2"))
        assertEquals("w2", m.current.activeId)
        assertEquals(0, port.writes)
        assertEquals(before, port.values)
        assertEquals("""{"pages":"[]","removed":[],"dock":["pin:app:x"]}""", hooks.applied.last())
        assertEquals(listOf("Work"), hooks.switched)
    }

    @Test fun CFG_49_theDocumentOfAProfileWithoutSettingsIsUnchangedAndTheNewFieldsRoundTrip() {
        val m = manager(InMemoryWorkspaceStore(v1))
        val plain = WorkspaceJson.encode(m.current)
        assertFalse("no profile fields are written for a workspace that has none", plain.contains("carries") || plain.contains("globalSettings") || plain.contains("groupLinks"))
        val s = m.current.copy(
            globalSettings = mapOf("themeMode" to "\"Dark\""),
            workspaces = m.current.workspaces.map { if (it.id == "w2") it.copy(carries = setOf("look", "radar"), settings = mapOf("themeMode" to "\"Light\"", "searchFiles" to "true"), groupLinks = listOf("g1")) else it },
        )
        val back = (WorkspaceJson.decode(WorkspaceJson.encode(s)) as WorkspaceJson.Decoded.Ok).state
        assertEquals(s, back)
        // a group id this build does not know is dropped from carries, not kept as a broken entry
        val odd = WorkspaceJson.encode(s).replace("\"radar\"", "\"future\"")
        assertEquals(setOf("look"), (WorkspaceJson.decode(odd) as WorkspaceJson.Decoded.Ok).state.workspaces[1].carries)
    }

    private fun twoProfiles(): Pair<WorkspaceManager, String> {
        val m = manager()
        val work = m.createProfile("Work", null, setOf("look", "radar"), mapOf("themeMode" to "\"Light\"", "searchFiles" to "true"), homeArrangement = ProfileStarters.arrangement(listOf("k1")))
        return m to work
    }

    @Test fun CFG_48_switchingAppliesTheCarriedSettingsInOneWriteAndTheWorkspaceContents() {
        val (m, work) = twoProfiles()
        m.switchTo(work)
        assertEquals(1, port.writes)
        assertEquals("\"Light\"", port.values["themeMode"])
        assertEquals("true", port.values["searchFiles"])
        assertEquals("accent is carried by Work's Look group but Work has no value for it: it keeps the global value", "1", port.values["accent"])
        assertEquals(ProfileStarters.arrangement(listOf("k1")), hooks.applied.last())
        assertEquals(mapOf("themeMode" to "\"Dark\"", "searchFiles" to "false", "accent" to "1"), m.current.globalSettings.filterKeys { it in setOf("themeMode", "searchFiles", "accent") })
    }

    @Test fun CFG_48_switchBackRestoresTheSettingsAndTheHomeThatWereLeft() {
        val (m, work) = twoProfiles()
        val before = port.values.toMap()
        hooks.home = "HOME-ARRANGEMENT"
        m.switchTo(work)
        assertEquals(ProfileSwitchUndo("default", "Home", work), m.undo.value)
        assertTrue(m.switchBack())
        assertEquals("default", m.current.activeId)
        assertEquals(before, port.values.filterKeys { it in before })
        assertEquals("HOME-ARRANGEMENT", hooks.applied.last())
        assertNull("one undo", m.undo.value)
        assertFalse(m.switchBack())
    }

    @Test fun CFG_48_aChangeWhileAProfileIsActiveGoesToTheProfileOnlyForWhatItCarries() {
        val (m, work) = twoProfiles()
        m.switchTo(work)
        port.values["themeMode"] = "\"Amoled\"" // Look: Work carries it
        port.values["searchWeb"] = "false" // Radar: Work carries it
        m.syncActiveSettings()
        assertEquals("\"Amoled\"", m.current.active.settings["themeMode"])
        assertEquals("false", m.current.active.settings["searchWeb"])
        // back to Home: Home carries nothing, so it sees the global values
        m.switchTo("default")
        assertEquals("\"Dark\"", port.values["themeMode"])
        assertEquals("true", port.values["searchWeb"])
        assertEquals("\"Amoled\"", m.current.workspaces.first { it.id == work }.settings["themeMode"])
        // a change made in Home to a key Home does not carry is global: Work sees it only for the keys it does not carry
        port.values["uiSounds"] = "false"
        m.switchTo(work)
        assertEquals("false", port.values["uiSounds"])
        assertEquals("\"Amoled\"", port.values["themeMode"])
    }

    @Test fun CFG_48_aSwitchWhileTheHomeIsNotComposedStillAppliesTheSettings() {
        val (m, work) = twoProfiles()
        hooks.ready0 = false
        m.switchTo(work)
        assertEquals(work, m.current.activeId)
        assertEquals("\"Light\"", port.values["themeMode"])
        assertEquals("default", m.current.captureDue)
    }

    @Test fun CFG_48_theDockKeysAreTheManagersOwnDockSettings() {
        val m = manager()
        val desk = m.createProfile("Desk", null, setOf("home"), ProfileStarters.settingsFor(ProfileStart.Desk, ProfileStarters.carries(ProfileStart.Desk), m.liveSettings()))
        assertNull(m.current.dock.enabled)
        m.switchTo(desk)
        assertEquals(true, m.current.dock.enabled)
        assertEquals(DockEdge.Left, m.current.dock.edge)
        assertEquals("true", port.values["kbOpenL"])
        m.switchTo("default")
        assertNull("the dock is back to its global setting", m.current.dock.enabled)
        assertEquals(DockEdge.Bottom, m.current.dock.edge)
    }

    @Test fun CFG_46_addingAGroupToTheActiveProfileKeepsTheLiveValuesAndDroppingItRestoresTheGlobalOnes() {
        val m = manager()
        m.setCarries("default", setOf("look"))
        assertEquals("\"Dark\"", m.current.active.settings["themeMode"])
        assertEquals("\"Dark\"", m.current.globalSettings["themeMode"])
        port.values["themeMode"] = "\"Light\""; m.syncActiveSettings()
        assertEquals("\"Light\"", m.current.active.settings["themeMode"])
        m.setCarries("default", emptySet())
        assertEquals("the global value comes back", "\"Dark\"", port.values["themeMode"])
        assertTrue(m.current.active.settings.isEmpty())
        assertTrue(m.current.globalSettings.isEmpty())
    }

    @Test fun CFG_46_aGroupAddedToAnotherProfileStartsFromTheGlobalValues() {
        val (m, work) = twoProfiles()
        m.setCarries(work, setOf("look", "radar", "sound"))
        val w = m.current.workspaces.first { it.id == work }
        assertEquals("kept", "\"Light\"", w.settings["themeMode"])
        assertTrue(w.carries.contains("sound"))
        m.setCarries(work, setOf("radar"))
        assertFalse("themeMode" in m.current.workspaces.first { it.id == work }.settings)
    }

    @Test fun CFG_45_duplicateOfTheActiveProfileTakesItsSettingsAsTheyAreNow() {
        val (m, work) = twoProfiles()
        m.switchTo(work)
        port.values["themeMode"] = "\"Amoled\""
        val copy = m.create("Work copy", work)
        assertEquals("\"Amoled\"", m.current.workspaces.first { it.id == copy }.settings["themeMode"])
        assertEquals(setOf("look", "radar"), m.current.workspaces.first { it.id == copy }.carries)
    }

    @Test fun CFG_47_linkingAGroupPinsItToThatProfilesDockAndDeletingTheGroupUnlinksIt() {
        val (m, work) = twoProfiles()
        m.saveGroup(AppGroup("Work apps", listOf(LaunchRequest(LaunchTarget("a")), LaunchRequest(LaunchTarget("b"))), id = "g1"))
        m.linkGroup(work, "g1", pin = true)
        val w = m.current.workspaces.first { it.id == work }
        assertEquals(listOf("g1"), w.groupLinks)
        assertTrue(DockItemRef.group("g1") in w.dockPins)
        assertEquals(listOf(HomeDockKeys.encode(DockItemRef.group("g1"))), HomeDockKeys.dockOf(w.homeArrangement))
        // linking to the active profile pins to the live dock
        m.linkGroup("default", "g1", pin = true)
        assertTrue(DockItemRef.group("g1") in m.current.active.dockPins)
        m.deleteGroup("g1")
        assertTrue(m.current.workspaces.all { "g1" !in it.groupLinks && DockItemRef.group("g1") !in it.dockPins })
    }

    @Test fun CFG_48_deletingTheProfileAnUndoNamesClearsTheUndo() {
        val (m, work) = twoProfiles()
        m.switchTo(work)
        m.delete("default")
        assertNull(m.undo.value)
        assertEquals(listOf("Work"), m.current.workspaces.map { it.name })
    }
}
