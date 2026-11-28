package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NFR-11: for every surface the "Restore open screens" setting can reopen, a new set of store objects over the same persisted
 * files (a process restart) gives the surface back when the setting is on, and never in safe mode. The list is the enum, so a
 * surface added later is covered here at once.
 */
class OpenSurfaceRestartTest {
    /** One process: the stores a start reads, built over the persisted maps of the previous one. */
    private class Process(val prefsMain: MapSharedPreferences, val device: MapSharedPreferences, val misc: MapSharedPreferences) {
        val prefs = LauncherPrefsStore(prefsMain, device)
        val surfaces = OpenSurfaceStore(misc)
    }

    @Test fun NFR_11_everyRestorableSurfaceComesBackAfterARestartWhenTheSettingIsOn() {
        assertTrue("both home surfaces are restorable and nothing else", OpenSurface.entries.toSet() == setOf(OpenSurface.Drawer, OpenSurface.Search))
        for (s in OpenSurface.entries) {
            val main = MapSharedPreferences(); val dev = MapSharedPreferences(); val misc = MapSharedPreferences()
            val first = Process(main, dev, misc)
            first.prefs.update { it.copy(restoreOpenSurfaces = true) }
            first.surfaces.record(s)
            val second = Process(main, dev, misc) // restart
            assertTrue("the setting survived the restart", second.prefs.prefs.value.restoreOpenSurfaces)
            assertEquals("$s", s, second.surfaces.takeRestore(second.prefs.prefs.value.restoreOpenSurfaces, safeMode = false))
            assertNull("only once per process", second.surfaces.takeRestore(true, false))
        }
    }

    @Test fun NFR_11_safeModeNeverRestoresAnySurfaceEvenWithTheSettingOn() {
        for (s in OpenSurface.entries) {
            val misc = MapSharedPreferences()
            OpenSurfaceStore(misc).record(s)
            assertNull("$s", OpenSurfaceStore(misc).takeRestore(enabled = true, safeMode = true))
            // The safe-mode start did not erase the record: the next normal start still restores it.
            assertEquals(s, OpenSurfaceStore(misc).takeRestore(enabled = true, safeMode = false))
        }
    }

    @Test fun NFR_11_withTheSettingOffNoSurfaceComesBackAndTurningItOnLaterRestoresTheRecordedOne() {
        for (s in OpenSurface.entries) {
            val main = MapSharedPreferences(); val dev = MapSharedPreferences(); val misc = MapSharedPreferences()
            val p = Process(main, dev, misc)
            p.surfaces.record(s)
            val off = Process(main, dev, misc)
            assertNull(off.surfaces.takeRestore(off.prefs.prefs.value.restoreOpenSurfaces, false))
            off.prefs.update { it.copy(restoreOpenSurfaces = true) }
            val on = Process(main, dev, misc)
            assertEquals(s, on.surfaces.takeRestore(on.prefs.prefs.value.restoreOpenSurfaces, false))
        }
    }

    @Test fun NFR_11_aSurfaceClosedByTheUserIsNotRestoredAfterARestart() {
        for (s in OpenSurface.entries) {
            val misc = MapSharedPreferences()
            val a = OpenSurfaceStore(misc); a.record(s); a.record(null)
            assertNull(OpenSurfaceStore(misc).takeRestore(true, false))
        }
    }
}
