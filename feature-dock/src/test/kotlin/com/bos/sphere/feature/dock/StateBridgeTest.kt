package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.StateSchema
import com.bos.sphere.core.data.WorkspaceFileSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** CFG-01 / CFG-02: the state file's workspaces and sessions sections against the dock stream's real storage format. */
class StateBridgeTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun CFG_01_theBridgeKnowsTheDockDocumentVersion() {
        assertEquals(WorkspaceJson.SCHEMA_VERSION, WorkspaceFileSource.SCHEMA_VERSION)
    }

    @Test fun CFG_01_workspacesExportedFromARealDockDocumentAreImportedIntoAnotherAndLoadThere() {
        val a = File(tmp.root, "a.json"); val b = File(tmp.root, "b.json")
        val src = WorkspaceManager(FileWorkspaceStore(a), newId = { "w2" })
        src.setPins(listOf(DockItemRef.app("com.a/.A#0")))
        src.rename(DockWorkspaceState.DEFAULT_ID, "Main")
        val w2 = src.create("Work")
        src.setPaletteActions(w2, listOf("act"))
        // Flush: the manager writes through its io hook, here inline.
        val sections = WorkspaceFileSource(a).read()
        assertEquals(2, sections.getValue(StateSchema.K_WORKSPACES).length())

        val dst = WorkspaceManager(FileWorkspaceStore(b))
        assertEquals(1, dst.current.workspaces.size)
        WorkspaceFileSource(b).write(sections, WorkspaceFileSource(a).readObjects(), setOf(StateSchema.K_WORKSPACES, StateSchema.K_SESSIONS, StateSchema.K_DOCK))
        dst.reload()
        assertEquals(listOf("Main", "Work"), dst.current.workspaces.map { it.name })
        assertEquals(listOf(DockItemRef.app("com.a/.A#0")), dst.current.workspaces.first().dockPins)
        assertEquals(listOf("act"), dst.current.workspaces.last().paletteActions)
        // The dock document the import wrote is still one the dock stream accepts.
        assertTrue(WorkspaceJson.decode(b.readText()) is WorkspaceJson.Decoded.Ok)
    }

    @Test fun CFG_02_reloadLiftsTheWriteBlockAfterAnUnreadableFileIsReplaced() {
        val f = File(tmp.root, "c.json"); f.writeText("not json")
        val m = WorkspaceManager(FileWorkspaceStore(f))
        m.create("X") // not persisted: the unreadable file is left alone
        assertEquals("not json", f.readText())
        f.writeText(WorkspaceJson.encode(DockWorkspaceState(workspaces = listOf(Workspace("k", "Kept")), activeId = "k")))
        m.reload()
        assertEquals(listOf("Kept"), m.current.workspaces.map { it.name })
        m.create("Y")
        assertTrue(JSONObject(f.readText()).getJSONArray("workspaces").length() == 2)
        assertTrue(JSONArray("[]").length() == 0)
    }
}
