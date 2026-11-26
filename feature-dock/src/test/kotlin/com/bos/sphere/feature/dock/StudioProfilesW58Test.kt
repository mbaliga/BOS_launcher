package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.HomeLayoutCodec
import com.bos.sphere.core.data.SearchLayout
import com.bos.sphere.core.data.StateCodec
import com.bos.sphere.core.data.StateDocument
import com.bos.sphere.core.data.StateSchema
import com.bos.sphere.core.data.WorkspaceFileSource
import com.bos.sphere.core.launch.LaunchTarget
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Wave 58 (studioPreview), CFG-71: phone profiles made in Liftoff Studio's Profiles panel are read by the app. The fixture
 * `web/liftoff-studio/tests/fixtures/studio-profiles-backup.json` is written by Studio's own code (`tests/make-fixtures.js`: a list started in
 * Studio, Work, Calm and Desk made from their starts, the suggested Work apps saved as an app group linked to Work, then a switch to Work);
 * here it goes through the app's Backup decoder, the workspace file writer and the dock stream's own store, as an import on the phone does.
 */
class StudioProfilesW58Test {
    @get:Rule val tmp = TemporaryFolder()

    private fun fixture(): String {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "web/liftoff-studio").isDirectory) d = d.parentFile
        return File(d!!, "web/liftoff-studio/tests/fixtures/studio-profiles-backup.json").readText()
    }

    private fun imported(): Pair<WorkspaceManager, StateCodec.Decoded> {
        val dec = StateCodec.decode(StateDocument.parse(fixture()))
        val file = File(tmp.root, "liftoff_workspaces.json")
        WorkspaceFileSource(file).write(dec.parts.extras.opaque, dec.parts.extras.objects, dec.present)
        assertTrue(WorkspaceJson.decode(file.readText()) is WorkspaceJson.Decoded.Ok)
        return WorkspaceManager(FileWorkspaceStore(file)) to dec
    }

    @Test fun CFG_71_aStudioMadeProfileListImportsAsTheAppsProfiles() {
        val (m, dec) = imported()
        val s = m.current
        assertTrue(StateSchema.K_WORKSPACES in dec.present && StateSchema.K_DOCK in dec.present)
        assertEquals(listOf("Home", "Work", "Calm", "Desk"), s.workspaces.map { it.name })
        val work = s.workspaces[1]; val calm = s.workspaces[2]; val desk = s.workspaces[3]
        assertEquals("Studio switched to Work before exporting", work.id, s.activeId)
        // Work: carries Radar with files on (its starter), its page 1 holds the suggested work apps, the calm centre shows them
        assertEquals(setOf("radar"), work.carries)
        assertEquals("true", work.settings["searchFiles"])
        assertEquals("\"${SearchLayout.Filmstrip.name}\"", work.settings["searchLayout"])
        assertEquals(setOf("com.google.android.gm", "com.microsoft.office.outlook"), work.notificationApps)
        val page = HomeLayoutCodec.decode(JSONObject(work.homeArrangement!!).getString("pages"))
        assertEquals(listOf("com.google.android.gm/.ConversationListActivityGmail#0", "com.microsoft.office.outlook/.MainActivity#0"), page.single().keys)
        // Calm: Look and Home behaviour, Glance only, Edge dots, two hotspots; calm centre: the messaging apps
        assertEquals(setOf("look", "home"), calm.carries)
        assertEquals("\"Off\"", calm.settings["homePages"]); assertEquals("\"EdgeDots\"", calm.settings["calmStyle"])
        assertEquals("\"BottomRight,BottomLeft\"", calm.settings["glanceHotspots"])
        assertEquals(setOf("com.whatsapp", "org.telegram.messenger"), calm.notificationApps)
        // Desk: dock on at the side, keyboard open
        assertEquals("true", desk.settings["dock.enabled"]); assertEquals("\"Left\"", desk.settings["dock.edge"]); assertEquals("true", desk.settings["kbOpenL"])
        // the app group Studio saved, linked to Work
        val g = s.groups.single()
        assertEquals("Work apps", g.name)
        assertEquals(listOf(LaunchTarget("com.google.android.gm", "com.google.android.gm.ConversationListActivityGmail", 0), LaunchTarget("com.microsoft.office.outlook", "com.microsoft.office.outlook.MainActivity", 0)), g.members.map { it.target })
        assertEquals(listOf(g.id), work.groupLinks)
        // Work is active: the live prefs are its values, the global value is kept aside
        assertEquals(true, dec.parts.prefs.searchFiles)
        assertEquals("false", s.globalSettings["searchFiles"])
    }

    @Test fun CFG_71_aStudioMadeListRoundTripsThroughTheDockStore() {
        val (m, _) = imported()
        val again = WorkspaceJson.decode(WorkspaceJson.encode(m.current)) as WorkspaceJson.Decoded.Ok
        assertEquals(m.current, again.state)
    }
}
