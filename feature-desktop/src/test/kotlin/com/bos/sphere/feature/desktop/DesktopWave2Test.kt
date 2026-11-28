package com.bos.sphere.feature.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 2 for the external display stream: per-display focus (EXT-03), profile editing (EXT-09), listing and offer wiring (EXT-02). */
class DesktopWave2Test {
    @Test fun EXT_03_onlyOneOfTwoSurfacesIsFocusedAndWindowFocusDecidesWhich() {
        val f = SurfaceFocus(); val a = Any(); val b = Any()
        assertNull(f.focused())
        f.setVisible(a, true); f.setVisible(b, true)
        assertTrue(f.isFocused(b)); assertFalse(f.isFocused(a))          // the one shown last
        f.setWindowFocus(a, true)
        assertTrue(f.isFocused(a)); assertFalse(f.isFocused(b))          // the one the user is in
        f.setWindowFocus(a, false)
        assertTrue(f.isFocused(b))
    }

    @Test fun EXT_03_aHiddenOrClosedSurfaceNeverKeepsFocus() {
        val f = SurfaceFocus(); val a = Any(); val b = Any()
        f.setVisible(a, true); f.setVisible(b, true); f.setWindowFocus(b, true)
        f.setVisible(b, false)
        assertTrue(f.isFocused(a))
        f.setWindowFocus(b, true)                                         // a stale focus event for a hidden surface
        assertTrue(f.isFocused(a))
        f.forget(a)
        assertNull(f.focused())
    }

    @Test fun EXT_03_theSharedRegistryFiltersByTokenAndOnlyDrivesTheFocusedSurface() {
        val a = Any(); val b = Any()
        DesktopSurfaces.register(a, SurfaceInfo(2, 2, "res:1920x1080:a")); DesktopSurfaces.register(b, SurfaceInfo(3, 3, "res:2560x1440:b"))
        try {
            DesktopSurfaces.setVisible(a, true); DesktopSurfaces.setVisible(b, true)
            DesktopSurfaces.setWindowFocus(b, true)
            assertTrue(DesktopSurfaces.isFocused(b)); assertFalse(DesktopSurfaces.isFocused(a)); assertTrue(DesktopSurfaces.isFocused(null))
            assertTrue(DesktopInputBus.focused.value)
            DesktopSurfaces.unregister(b); DesktopSurfaces.unregister(a)
            assertFalse(DesktopInputBus.focused.value)
        } finally { DesktopSurfaces.unregister(a); DesktopSurfaces.unregister(b) }
    }

    @Test fun EXT_09_densityAndDockModeAreEditedPerDisplayAndRememberedAcrossReconnect() {
        val repo = DesktopProfileRepository(MapKeyValue())
        val tv = "res:3840x2160:tv"; val monitor = "res:1920x1080:monitor"
        repo.save(tv, ProfileEdits.withDensity(repo.load(tv), 320))
        repo.save(monitor, ProfileEdits.withDockMode(repo.load(monitor), DockMode.Left))
        assertEquals(320, repo.load(tv).densityOverrideDpi); assertEquals(DockMode.Bottom, repo.load(tv).dockMode)
        assertEquals(DockMode.Left, repo.load(monitor).dockMode); assertNull(repo.load(monitor).densityOverrideDpi)
        assertEquals(listOf(monitor, tv), repo.identities())                // what Ground Control lists
        assertNull(ProfileEdits.withDensity(repo.load(tv), null).densityOverrideDpi)  // back to automatic
        assertNull(ProfileEdits.withDensity(repo.load(tv), 0).densityOverrideDpi)
    }

    @Test fun EXT_09_everyChoiceChangesTheLayoutItDescribes() {
        val base = DesktopProfile()
        val s0 = ProfileEdits.summary(base, 1920, 1080, 160)
        val s1 = ProfileEdits.summary(ProfileEdits.withDensity(base, 320), 1920, 1080, 160)
        assertTrue(s0 != s1)
        for (d in ProfileEdits.densityChoices) assertTrue(ProfileEdits.summary(ProfileEdits.withDensity(base, d), 1920, 1080, 160).isNotBlank())
        assertEquals(listOf(null, 120, 160, 213, 240, 320), ProfileEdits.densityChoices)
        assertEquals("Automatic", ProfileEdits.densityLabel(null)); assertEquals("240 dpi", ProfileEdits.densityLabel(240))
        for (m in DockMode.entries) DesktopMetrics.compute(1280, 720, 160, dockMode = ProfileEdits.withDockMode(base, m).dockMode)
    }

    @Test fun EXT_09_profileEditsNeverTouchTheArrangement() {
        val p = DesktopProfile(items = listOf(PlacedItem(PlacedItem.KIND_APP, "a#0", 1f, 2f, 3f, 4f)))
        assertEquals(p.items, ProfileEdits.withDensity(ProfileEdits.withDockMode(p, DockMode.Right), 213).items)
    }

    @Test fun EXT_06_migrateSettingDecidesBetweenCloseAndMoveToPhone() {
        val me = SurfaceInfo(2, 2, "id")
        assertEquals(DisconnectAction.Close, DesktopPolicy.onDisconnected(me, 2, listOf(me), 0, migrate = false))
        assertEquals(DisconnectAction.MigrateToPrimary, DesktopPolicy.onDisconnected(me, 2, listOf(me), 0, migrate = true))
        val onPrimary = SurfaceInfo(9, 0, "other")
        assertEquals(DisconnectAction.Close, DesktopPolicy.onDisconnected(me, 2, listOf(me, onPrimary), 0, migrate = true)) // never two on the primary
    }
}
