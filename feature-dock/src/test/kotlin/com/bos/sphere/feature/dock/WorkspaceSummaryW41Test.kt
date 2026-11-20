package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.HomeLayoutCodec
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.design.L10n
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Wave 41 (settingsPopups), WSP-07: what the Workspaces page says about each workspace, and copying a workspace. Pure JVM. */
class WorkspaceSummaryW41Test {
    @Before fun en() = L10n.setLanguage("en")
    @After fun reset() = L10n.setLanguage("en")

    private fun arrangement(pages: Int, dock: List<String> = emptyList()) =
        JSONObject().put("pages", HomeLayoutCodec.encode(List(pages) { HomePage() })).put("removed", JSONArray()).put("dock", JSONArray(dock)).toString()

    @Test fun WSP_07_pageCountReadsTheSavedArrangement() {
        assertEquals(3, WorkspaceSummary.pageCount(arrangement(3)))
        assertEquals(1, WorkspaceSummary.pageCount(arrangement(1)))
        assertNull("a workspace never left has no arrangement", WorkspaceSummary.pageCount(null))
        assertNull(WorkspaceSummary.pageCount("not json"))
    }

    @Test fun WSP_07_theOneLineSummaryNamesPagesAndDockItems() {
        assertEquals("3 pages · 5 dock items", WorkspaceSummary.line(3, 5))
        assertEquals("1 page · 1 dock item", WorkspaceSummary.line(1, 1))
        assertEquals("2 pages · No dock items", WorkspaceSummary.line(2, 0))
        assertEquals("unknown pages are left out, not guessed", "4 dock items", WorkspaceSummary.line(null, 4))
    }

    @Test fun WSP_07_theDockCountComesFromTheArrangementOrTheLivePins() {
        val w = Workspace("a", "A", dockPins = listOf(DockItemRef.app("x")))
        assertEquals(1, WorkspaceSummary.dockCount(w, null, null))
        assertEquals(2, WorkspaceSummary.dockCount(w, arrangement(1, listOf("pin:app:p", "pin:app:q")), null))
        assertEquals("the active workspace's live pins win", 3, WorkspaceSummary.dockCount(w, arrangement(1, listOf("pin:app:p")), List(3) { DockItemRef.app("k$it") }))
    }

    @Test fun WSP_08_theCalmCentreValueSaysAllAppsOrHowManyChosen() {
        assertEquals("All apps", WorkspaceSummary.calmValue(Workspace("a", "A")))
        assertEquals("1 chosen app", WorkspaceSummary.calmValue(Workspace("a", "A", notificationApps = setOf("p"))))
        assertEquals("3 chosen apps", WorkspaceSummary.calmValue(Workspace("a", "A", notificationApps = setOf("p", "q", "r"))))
    }

    @Test fun WSP_07_rowsMarkTheActiveOneAndUseTheLiveHomeForIt() {
        val state = DockWorkspaceState(
            activeId = "b",
            workspaces = listOf(Workspace("a", "Home", homeArrangement = arrangement(2, listOf("pin:app:x"))), Workspace("b", "Work", homeArrangement = arrangement(1))),
        )
        val rows = WorkspaceSummary.rows(state, liveArrangement = arrangement(4, listOf("pin:app:1", "pin:app:2")))
        assertEquals(listOf(false, true), rows.map { it.active })
        assertEquals("2 pages · 1 dock item", rows[0].summary)
        assertEquals("the active row reads the live pages, not the stale save", 4, rows[1].pages)
        assertEquals("Active: Work", WorkspaceSummary.activeLine(state))
    }

    @Test fun WSP_07_aDuplicateGetsAFreeName() {
        assertEquals("Home copy", WorkspaceSummary.copyName("Home", listOf("Home")))
        assertEquals("Home copy 2", WorkspaceSummary.copyName("Home", listOf("Home", "Home copy")))
        assertEquals("Home copy 3", WorkspaceSummary.copyName("Home", listOf("Home", "Home copy", "Home copy 2")))
    }

    @Test fun WSP_07_aTypedNameThatIsTakenIsRefusedIgnoringCaseAndSpaces() {
        assertTrue(WorkspaceSummary.nameTaken(" work ", listOf("Home", "Work")))
        assertFalse(WorkspaceSummary.nameTaken("Play", listOf("Home", "Work")))
        assertFalse("renaming a workspace to its own name is fine", WorkspaceSummary.nameTaken("Work", listOf("Home", "Work"), except = "Work"))
    }

    // ---- the manager --------------------------------------------------------------------------------------------------------

    private class Hooks : WorkspaceHooks {
        var home: String? = "LIVE"
        override fun captureHome() = home
    }

    private var n = 0
    private fun manager(h: Hooks = Hooks()) = WorkspaceManager(InMemoryWorkspaceStore(), { h }, newId = { "id${n++}" })

    @Test fun WSP_07_addingFromTheActiveCopiesTheLiveHomeDockAndCalmCentreButNotWidgets() {
        val m = manager()
        m.setPins(listOf(DockItemRef.app("a")))
        m.setNotificationApps(DockWorkspaceState.DEFAULT_ID, setOf("p.mail"))
        m.setWidgets(DockWorkspaceState.DEFAULT_ID, listOf("w1"))
        m.setPaletteActions(DockWorkspaceState.DEFAULT_ID, listOf("act"))
        val id = m.create("Work", copyFrom = DockWorkspaceState.DEFAULT_ID)
        val w = m.current.workspaces.first { it.id == id }
        assertEquals("Work", w.name)
        assertEquals("the live home, since the stored one is only saved on leaving", "LIVE", w.homeArrangement)
        assertEquals(listOf(DockItemRef.app("a")), w.dockPins)
        assertEquals(setOf("p.mail"), w.notificationApps)
        assertEquals(listOf("act"), w.paletteActions)
        assertTrue("hosted widget ids cannot be shared by two workspaces", w.widgets.isEmpty())
        assertEquals("a new workspace is not made active", DockWorkspaceState.DEFAULT_ID, m.current.activeId)
    }

    @Test fun WSP_07_addingEmptyStartsBlank() {
        val m = manager()
        m.setPins(listOf(DockItemRef.app("a")))
        val id = m.create("Play", copyFrom = null)
        val w = m.current.workspaces.first { it.id == id }
        assertNull(w.homeArrangement); assertTrue(w.dockPins.isEmpty()); assertNull(w.notificationApps)
        assertNotEquals(m.current.active.id, id)
    }

    @Test fun WSP_07_duplicatingAnotherWorkspaceCopiesItsSavedArrangement() {
        val m = manager()
        val b = m.create("Work")
        m.switchTo(b); Hooks()
        val dup = m.create("Home copy", copyFrom = DockWorkspaceState.DEFAULT_ID)
        assertEquals("the one not in use is copied from its saved arrangement", m.current.workspaces.first { it.id == DockWorkspaceState.DEFAULT_ID }.homeArrangement, m.current.workspaces.first { it.id == dup }.homeArrangement)
    }

    @Test fun WSP_07_theLastWorkspaceCannotBeDeleted() {
        val m = manager()
        m.delete(DockWorkspaceState.DEFAULT_ID)
        assertEquals(1, m.current.workspaces.size)
    }
}
