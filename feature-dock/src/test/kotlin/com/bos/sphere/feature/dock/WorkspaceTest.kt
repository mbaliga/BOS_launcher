package com.bos.sphere.feature.dock

import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.Bounds
import com.bos.sphere.core.launch.GroupLayout
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.core.launch.Placement
import com.bos.sphere.core.launch.Session
import com.bos.sphere.core.launch.SessionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTest {
    private class Hooks : WorkspaceHooks {
        var home: String? = "H0"; val applied = mutableListOf<String?>(); var scope: Set<String>? = null; var switched = 0
        override fun captureHome() = home
        override fun applyHome(arrangement: String?) { applied += arrangement; home = arrangement }
        override fun applyNotificationScope(allowed: Set<String>?) { scope = allowed }
        override fun onSwitched(workspace: Workspace) { switched++ }
    }
    private var n = 0
    private fun manager(store: WorkspaceStore = InMemoryWorkspaceStore(), hooks: Hooks = Hooks()) = WorkspaceManager(store, { hooks }, newId = { "id${n++}" })

    @Test fun WSP_07_switchKeepsEachWorkspacesOwnHomeAndPins() {
        val hooks = Hooks(); val m = manager(hooks = hooks)
        m.setPins(listOf(DockItemRef.app("a")))
        val b = m.create("Work")
        m.switchTo(b)
        assertEquals(listOf<DockItemRef>(), m.current.active.dockPins)
        m.setPins(listOf(DockItemRef.app("w")))
        hooks.home = "WORK-HOME"
        m.switchTo(DockWorkspaceState.DEFAULT_ID)
        assertEquals(listOf(DockItemRef.app("a")), m.current.active.dockPins)
        assertEquals("H0", hooks.applied.last())      // the first workspace's arrangement comes back
        m.switchTo(b)
        assertEquals("WORK-HOME", hooks.applied.last())
        assertEquals(3, hooks.switched)
    }

    @Test fun WSP_07_switchingIsFastAndDoesNotWaitOnStorage() {
        val slow = object : WorkspaceStore { override fun read(): String? = null; override fun write(text: String) { Thread.sleep(400) } }
        val queued = mutableListOf<() -> Unit>()
        val m = WorkspaceManager(slow, io = { queued += it })
        val b = m.create("B")
        val t0 = System.nanoTime(); m.switchTo(b); m.next(); m.previous()
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("switching took $ms ms", ms < 150)
        assertTrue(queued.isNotEmpty())
    }

    @Test fun WSP_07_nextAndPreviousWrapAndPaletteProviderSwitches() {
        val m = manager(); val b = m.create("B"); m.create("C")
        m.next(); assertEquals(b, m.current.activeId)
        m.previous(); assertEquals(DockWorkspaceState.DEFAULT_ID, m.current.activeId)
        m.previous(); assertEquals("C", m.current.active.name)
        val p = WorkspacePaletteProvider({ m.current }, { m.switchTo(it) }, {})
        val hit = p.results("B").single()
        assertEquals("Switch to B", hit.title); hit.run()
        assertEquals("B", m.current.active.name)
    }

    @Test fun WSP_07_theLastWorkspaceCannotBeDeleted() {
        val m = manager(); m.delete(DockWorkspaceState.DEFAULT_ID)
        assertEquals(1, m.current.workspaces.size)
        val b = m.create("B"); m.switchTo(b); m.delete(b)
        assertEquals(listOf(DockWorkspaceState.DEFAULT_ID), m.current.workspaces.map { it.id }); assertEquals(DockWorkspaceState.DEFAULT_ID, m.current.activeId)
    }

    @Test fun WSP_08_notificationFilterAppliesOnSwitchAndOnlyAsDisplayScope() {
        val hooks = Hooks(); val m = manager(hooks = hooks)
        val b = m.create("Focus"); m.setNotificationApps(b, setOf("chat.app"))
        m.switchTo(b); assertEquals(setOf("chat.app"), hooks.scope)
        m.switchTo(DockWorkspaceState.DEFAULT_ID); assertNull(hooks.scope)
    }

    private val group = AppGroup("Dev", listOf(LaunchRequest(LaunchTarget("a", "a.Main", 1)), LaunchRequest(LaunchTarget("b", null, 2), displayId = 3, bounds = Bounds(0, 0, 10, 20), placement = Placement.Adjacent)), GroupLayout.Cascade, "g1")

    @Test fun WSP_07_stateRoundTripsThroughJsonWithSchemaVersion() {
        val s = DockWorkspaceState(
            dock = DockSettings(true, DockMode.Collapsed, DockEdge.Right), activeId = "w2",
            workspaces = listOf(Workspace("default", "Home", "{\"p\":1}", listOf(DockItemRef.app("a#1"), DockItemRef.group("g1")), listOf("w1"), listOf("act"), setOf("x", "y")), Workspace("w2", "Work")),
            groups = listOf(group),
            sessions = listOf(Session("s1", "Desk", listOf(SessionEntry(LaunchTarget("a", "a.Main", 0), 5, "Monitor", Bounds(1, 2, 3, 4), Placement.Adjacent), SessionEntry(LaunchTarget("b"))), GroupLayout.Full, 99L)),
            schedules = listOf(ScheduledLaunch("sc", "Stand-up", 12345L, RunTarget(RunKind.Group, "g1"))),
        )
        val text = WorkspaceJson.encode(s)
        assertTrue(text.contains("\"schemaVersion\""))
        assertEquals(s, (WorkspaceJson.decode(text) as WorkspaceJson.Decoded.Ok).state)
    }

    @Test fun WSP_07_newerSchemaAndCorruptTextAreNotOverwrittenSilently() {
        assertTrue(WorkspaceJson.decode("{\"schemaVersion\":99}") is WorkspaceJson.Decoded.TooNew)
        assertTrue(WorkspaceJson.decode("not json") is WorkspaceJson.Decoded.Corrupt)
        assertTrue(WorkspaceJson.decode("{\"x\":1}") is WorkspaceJson.Decoded.Corrupt)
    }

    @Test fun WSP_07_newerOrCorruptDocumentIsNeverWrittenOver() {
        for (text in listOf("{\"schemaVersion\":99,\"x\":1}", "not json")) {
            val store = InMemoryWorkspaceStore(text)
            val m = manager(store)
            m.create("W"); m.setPins(listOf(DockItemRef.app("a")))
            assertEquals(text, store.text)
        }
    }

    @Test fun WSP_09_oneBadSessionDoesNotDiscardTheRest() {
        val good = WorkspaceJson.encode(DockWorkspaceState(sessions = listOf(Session("s1", "One", listOf(SessionEntry(LaunchTarget("p")))))))
        val broken = good.replace("\"sessions\":[", "\"sessions\":[{\"id\":\"bad\"},")
        val s = (WorkspaceJson.decode(broken) as WorkspaceJson.Decoded.Ok).state
        assertEquals(listOf("s1"), s.sessions.map { it.id })
    }

    @Test fun WSP_07_stateSurvivesProcessDeathThroughTheStore() {
        val store = InMemoryWorkspaceStore()
        val m1 = manager(store); m1.setPins(listOf(DockItemRef.app("a"))); m1.saveGroup(group); m1.create("W")
        val m2 = manager(store)
        assertEquals(m1.current, m2.current)
        assertFalse(m2.current.groups.isEmpty())
    }

    @Test fun WSP_05_groupsAreSavedReplacedDeletedAndLeaveThePins() {
        val m = manager(); m.saveGroup(group)
        m.setPins(listOf(DockItemRef.group("g1")))
        m.saveGroup(group.copy(name = "Dev2")); assertEquals(1, m.current.groups.size); assertEquals("Dev2", m.current.groups.single().name)
        m.deleteGroup("g1"); assertTrue(m.current.groups.isEmpty()); assertTrue(m.current.active.dockPins.isEmpty())
    }

    @Test fun WSP_05_groupIsAPaletteResultAndRunsFromIt() {
        var ran: AppGroup? = null
        val p = GroupPaletteProvider({ listOf(group) }, { ran = it })
        assertTrue(p.results("").isEmpty())
        val hit = p.results("de").single(); assertEquals("Dev", hit.title); hit.run()
        assertEquals(group, ran)
    }

    @Test fun WSP_09_sessionsAreSavedAndDeleted() {
        val m = manager(); m.saveSession(Session("s", "n", emptyList())); assertEquals(1, m.current.sessions.size)
        m.deleteSession("s"); assertTrue(m.current.sessions.isEmpty())
    }

    @Test fun WSP_11_scheduleIsStoredAndRemoved() {
        val m = manager(); m.addSchedule(ScheduledLaunch("a", "x", 1, RunTarget(RunKind.App, "k")))
        assertEquals(1, m.current.schedules.size); m.removeSchedule("a"); assertTrue(m.current.schedules.isEmpty())
    }

    @Test fun WSP_12_explicitIntentsResolveByIdOrName() {
        val m = manager(); val b = m.create("Focus"); m.saveGroup(group)
        val sw = AutomationIntents.parse(AutomationIntents.ACTION_SWITCH_WORKSPACE, "focus", null, null)!!
        assertEquals(RunTarget(RunKind.Workspace, b), AutomationIntents.resolve(sw, m.current))
        val gr = AutomationIntents.parse(AutomationIntents.ACTION_LAUNCH_GROUP, null, null, "g1")!!
        assertEquals(RunTarget(RunKind.Group, "g1"), AutomationIntents.resolve(gr, m.current))
        assertNull(AutomationIntents.parse("other", "x", null, null))
        assertNull(AutomationIntents.resolve(RunTarget(RunKind.Session, "none"), m.current))
        assertEquals(RunKind.Session, AutomationIntents.parse(AutomationIntents.ACTION_RUN, null, "session", "s")!!.kind)
    }

    @Test fun WSP_05_appKeyParsesToATarget() {
        assertEquals(LaunchTarget("com.x", "com.x.Main", 7), AppKeys.target("com.x/.Main#7"))
        assertEquals(LaunchTarget("com.x", "org.y.Z", 0), AppKeys.target("com.x/org.y.Z#0"))
        assertNull(AppKeys.target("broken"))
    }
}
