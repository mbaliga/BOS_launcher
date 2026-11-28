package com.bos.sphere.feature.dock

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** NFR-18: widget ids kept per workspace in `liftoff_workspaces.json` live in a file that backup and transfer skip. */
class WorkspaceWidgetIdsBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun files() = File(tmp.root, "files/liftoff_workspaces.json") to File(tmp.root, "nobackup/liftoff_workspace_widgets.json")

    @Test fun NFR_18_theBackedUpDockFileHasNoWidgetIdAndANewManagerOverTheTwoFilesSeesTheSameWidgets() {
        val (main, side) = files()
        val m = WorkspaceManager(FileWorkspaceStore(main, side), newId = { "w2" })
        m.setWidgets(DockWorkspaceState.DEFAULT_ID, WorkspaceWidgets.encode(listOf(WorkspaceWidgets.Slot.Left(41), WorkspaceWidgets.Slot.Corner("TopLeft", 42, "Clock"))))
        assertFalse("main file: ${main.readText()}", main.readText().contains("left:41") || main.readText().contains("42"))
        assertTrue(side.readText().contains("left:41"))
        val m2 = WorkspaceManager(FileWorkspaceStore(main, side))
        assertEquals(WorkspaceWidgets.encode(listOf(WorkspaceWidgets.Slot.Left(41), WorkspaceWidgets.Slot.Corner("TopLeft", 42, "Clock"))), m2.current.workspaces.first().widgets)
    }

    @Test fun NFR_18_aRestoreThatBringsOnlyTheMainFileMeansNeverSavedWidgetsNotStaleIds() {
        val (main, side) = files()
        WorkspaceManager(FileWorkspaceStore(main, side)).setWidgets(DockWorkspaceState.DEFAULT_ID, WorkspaceWidgets.encode(listOf(WorkspaceWidgets.Slot.Left(41))))
        side.delete()
        val m = WorkspaceManager(FileWorkspaceStore(main, side))
        assertTrue(m.current.workspaces.first().widgets.isEmpty())
        assertFalse(WorkspaceWidgets.isSnapshot(m.current.workspaces.first().widgets))
    }

    @Test fun NFR_18_aDockFileFromAnOlderBuildThatHoldsIdsIsMigratedOnFirstRead() {
        val (main, side) = files()
        val old = WorkspaceManager(FileWorkspaceStore(main)) // no device file: the earlier behaviour
        old.setWidgets(DockWorkspaceState.DEFAULT_ID, WorkspaceWidgets.encode(listOf(WorkspaceWidgets.Slot.Left(7))))
        assertTrue(main.readText().contains("left:7"))
        val m = WorkspaceManager(FileWorkspaceStore(main, side))
        assertEquals(listOf(WorkspaceWidgets.MARKER, "left:7"), m.current.workspaces.first().widgets)
        assertFalse("ids left the backed-up file", main.readText().contains("left:7"))
        assertTrue(side.readText().contains("left:7"))
        // The file is still one the dock stream accepts.
        assertTrue(WorkspaceJson.decode(main.readText()) is WorkspaceJson.Decoded.Ok)
        assertEquals(1, JSONObject(main.readText()).getInt("schemaVersion"))
    }

    @Test fun NFR_18_aRemovedWorkspaceDropsItsWidgetListFromTheDeviceFile() {
        val (main, side) = files()
        val m = WorkspaceManager(FileWorkspaceStore(main, side), newId = { "w2" })
        val w2 = m.create("Work")
        m.setWidgets(w2, WorkspaceWidgets.encode(listOf(WorkspaceWidgets.Slot.Left(9))))
        assertTrue(side.readText().contains("left:9"))
        m.delete(w2)
        assertFalse(side.readText().contains("left:9"))
    }
}
