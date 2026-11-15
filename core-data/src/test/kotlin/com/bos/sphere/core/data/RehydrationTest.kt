package com.bos.sphere.core.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** NFR-11: a restart (every in-memory object dropped, only the files left) brings back the stores added since wave 1, and the opt-in surface restore. */
class RehydrationTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun NFR_11_theDeviceBoundFileTheKeyboardSlotAndTheWorkWidgetRecordSurviveARestart() {
        val main = MapSharedPreferences(); val corners = MapSharedPreferences(); val device = MapSharedPreferences()
        val extrasFile = File(tmp.root, "extras.json")
        LauncherPrefsStore(main, device).update { it.copy(leftPage = LeftPage.Widget, leftWidgetId = 12, backgroundPicturePath = "/nb/background_9.img", restoreOpenSurfaces = true) }
        ShortcutStore(corners, device).setCorner(Corner.BottomRight, CornerSlot.Widget(5, "Clock"))
        StateExtrasStore(extrasFile).write(StateExtras(keyboardShortcuts = "OpenPalette=C:76", widgets = listOf(PendingWidget("corner.TopLeft", "Mail", "com.mail/.W", work = true))))

        // New process: new store objects over the same persisted maps and files.
        val prefs = LauncherPrefsStore(main, device).prefs.value
        assertEquals(12, prefs.leftWidgetId); assertEquals("/nb/background_9.img", prefs.backgroundPicturePath); assertTrue(prefs.restoreOpenSurfaces)
        assertEquals(CornerSlot.Widget(5, "Clock"), ShortcutStore(corners, device).corners.value[Corner.BottomRight])
        val e = StateExtrasStore(extrasFile).read()
        assertEquals("OpenPalette=C:76", e.keyboardShortcuts)
        assertEquals(listOf(PendingWidget("corner.TopLeft", "Mail", "com.mail/.W", work = true)), e.widgets)
    }

    @Test
    fun NFR_11_snapshotsPuckActionsAndTheStateFileSurviveARestart() {
        val dir = File(tmp.root, "snaps")
        val a = SnapshotStore(dir, 5) { 1_000L }
        a.take("manual", "{\"v\":1}", force = true)
        val b = SnapshotStore(dir, 5) { 2_000L }
        assertEquals(1, b.list().size); assertEquals("{\"v\":1}", b.read(b.list().first().id))
        val sp = MapSharedPreferences()
        PuckActionsStore(sp).set(listOf(CornerActionId.Phone, CornerActionId.Messages))
        assertEquals(listOf(CornerActionId.Phone, CornerActionId.Messages), PuckActionsStore(sp).actions.value)
        val ex = File(tmp.root, "ex.json")
        StateExtrasStore(ex).setSection(StateSchema.K_WORKSPACES, JSONArray().put("w"))
        assertEquals("w", StateExtrasStore(ex).section(StateSchema.K_WORKSPACES).getString(0))
    }

    @Test
    fun NFR_11_aRestartReopensTheLastSurfaceOnlyWhenTheUserSwitchedItOn() {
        val sp = MapSharedPreferences()
        OpenSurfaceStore(sp).record(OpenSurface.Drawer)
        assertNull("off by default: the home starts closed", OpenSurfaceStore(sp).takeRestore(enabled = false, safeMode = false))
        assertEquals(OpenSurface.Drawer, OpenSurfaceStore(sp).takeRestore(enabled = true, safeMode = false))
        OpenSurfaceStore(sp).record(OpenSurface.Search)
        assertEquals(OpenSurface.Search, OpenSurfaceStore(sp).takeRestore(true, false))
    }

    @Test
    fun NFR_11_theRestoreNeverAppliesInSafeModeAndOnlyOncePerProcess() {
        val sp = MapSharedPreferences()
        OpenSurfaceStore(sp).record(OpenSurface.Search)
        assertNull(OpenSurfaceStore(sp).takeRestore(true, safeMode = true))
        val once = OpenSurfaceStore(sp)
        assertEquals(OpenSurface.Search, once.takeRestore(true, false))
        assertNull("a rotation (second composition) does not reopen a surface the user closed", once.takeRestore(true, false))
    }

    @Test
    fun NFR_11_closingASurfaceClearsItSoTheNextRestartStartsOnTheHome() {
        val sp = MapSharedPreferences()
        val s = OpenSurfaceStore(sp)
        s.record(OpenSurface.Drawer); s.record(null)
        assertNull(OpenSurfaceStore(sp).last())
        assertNull(OpenSurfaceStore(sp).takeRestore(true, false))
        sp.edit().putString("openSurface", "Settings").apply() // an unknown name (older or newer build) is ignored
        assertNull(OpenSurfaceStore(sp).last())
    }

    @Test
    fun NFR_11_theOptionIsOffByDefaultAndTravelsInTheSettingsExportOnly() {
        assertFalse(LauncherPrefs().restoreOpenSurfaces)
        val doc = SettingsCodec.encode(LauncherPrefs(restoreOpenSurfaces = true))
        assertTrue(doc.getJSONObject("prefs").getBoolean("restoreSurfaces"))
        assertTrue(SettingsCodec.decode(doc).prefs.restoreOpenSurfaces)
        assertTrue("the surface itself is a moment and is not exported", StateSchema.NEVER_EXPORTED_STORES.contains(OpenSurfaceStore.FILE))
    }
}
