package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Wave 56 (looksPresets): the built-in Looks are data that applies through the theme path, matches itself, differs from the others and never replaces the owner's own wallpaper. */
class LookPresetsW56Test {
    private class Fake(var prefs: LauncherPrefs = LauncherPrefs(), var dock: DockStyle = DockStyle.Standard) {
        val applier = ThemeApplier({ prefs }, { f -> prefs = f(prefs) }, { dock }, { dock = it })
    }

    @Before fun clearUndo() = ThemeUndo.clear()

    private fun solidPrefs() = LauncherPrefs().withWallpaper(WallpaperSurface.Glance, Wallpaper(kind = WallpaperKind.Solid, solid = 0xFF222222))

    @Test fun CFG_26_w56_aLookRailHasFiveOrSixLooksWithUniqueIdsAndNames() {
        assertTrue(LookPresets.all.size in 5..6)
        assertEquals(LookPresets.all.size, LookPresets.all.map { it.id }.toSet().size)
        assertEquals(LookPresets.all.size, LookPresets.all.map { it.name }.toSet().size)
    }

    @Test fun CFG_26_w56_everyLookSetsEveryFieldAThemePackCanCarryAndSurvivesExportAndImport() {
        for (l in LookPresets.all) {
            val s = l.settings
            assertNotNull(l.id, s.themeMode); assertNotNull(l.id, s.accent); assertNotNull(l.id, s.iconShape); assertNotNull(l.id, s.iconTint); assertNotNull(l.id, s.dockStyle)
            assertNotNull(l.id, s.uiFont); assertNotNull(l.id, s.calmStyle); assertNotNull(l.id, s.iconPlate); assertNotNull(l.id, s.wallpaper)
            assertTrue(l.id, s.uiFont in UiFonts.IDS)
            val parsed = ThemePacks.parse(ThemePacks.encode(l.pack))
            assertEquals(l.id, l.pack, (parsed as ThemeParse.Ok).pack)
        }
    }

    @Test fun CFG_26_w56_everyLookAppliesCleanlyMatchesItselfAndNoOtherLook() {
        for (l in LookPresets.all) {
            val f = Fake(solidPrefs())
            f.applier.applyLook(l)
            assertTrue("${l.id} matches itself", LookPresets.matches(f.prefs, f.dock, l))
            assertEquals(l.id, LookPresets.current(f.prefs, f.dock)?.id)
            for (o in LookPresets.all) if (o.id != l.id) assertFalse("${l.id} must not match ${o.id}", LookPresets.matches(f.prefs, f.dock, o))
            val s = l.settings
            assertEquals(s.themeMode, f.prefs.themeMode); assertEquals(s.accent, f.prefs.accentColor or 0L); assertEquals(s.iconShape, f.prefs.iconShape)
            assertEquals(s.iconTint, f.prefs.iconTint); assertEquals(s.dockStyle, f.dock); assertEquals(s.uiFont, f.prefs.uiFont)
            assertEquals(s.calmStyle, f.prefs.calmStyle); assertEquals(s.iconPlate, f.prefs.iconPlate)
        }
    }

    @Test fun CFG_26_w56_aLookThatIsAppliedTwiceIsStillThatLookAndAnEditMakesItCustom() {
        val l = LookPresets.all[0]
        val f = Fake(solidPrefs())
        f.applier.applyLook(l); f.applier.applyLook(l)
        assertEquals(l.id, LookPresets.current(f.prefs, f.dock)?.id)
        f.prefs = f.prefs.copy(iconShape = if (f.prefs.iconShape == IconShape.Square) IconShape.Circle else IconShape.Square)
        assertNull("a hand edit leaves no Look picked", LookPresets.current(f.prefs, f.dock))
    }

    @Test fun CFG_26_w56_applyingALookKeepsTheOneUndoAndUndoPutsTheWholeLookBack() {
        val f = Fake(solidPrefs().copy(themeMode = ThemeMode.Light, calmStyle = CalmStyle.Beam, iconPlate = false), DockStyle.ThreeD)
        val before = f.prefs; val dockBefore = f.dock
        f.applier.applyLook(LookPresets.all[0])
        assertNotNull(ThemeUndo.snapshot.value)
        assertTrue(f.applier.undo())
        assertEquals(before, f.prefs); assertEquals(dockBefore, f.dock)
        assertNull(ThemeUndo.snapshot.value)
    }

    @Test fun CFG_26_w56_aLookNeverReplacesALiveSystemOrPictureWallpaperAndStillMatchesOverIt() {
        for (k in listOf(WallpaperKind.Live, WallpaperKind.System, WallpaperKind.Picture)) {
            val own = LauncherPrefs().withWallpaper(WallpaperSurface.Glance, Wallpaper(kind = k, solid = 0xFF445566))
            for (l in LookPresets.all) {
                val f = Fake(own)
                f.applier.applyLook(l)
                assertEquals("$k stays under ${l.id}", own.rawWallpaper(WallpaperSurface.Glance), f.prefs.rawWallpaper(WallpaperSurface.Glance))
                assertTrue("${l.id} over $k", LookPresets.matches(f.prefs, f.dock, l))
                assertEquals(l.id, LookPresets.current(f.prefs, f.dock)?.id)
            }
        }
    }

    @Test fun CFG_26_w56_aLookReplacesASolidOrGradientWallpaperAndOnlyThatOne() {
        for (l in LookPresets.all) {
            val f = Fake(solidPrefs())
            val pages = f.prefs.rawWallpaper(WallpaperSurface.Pages)
            f.applier.applyLook(l)
            val w = f.prefs.rawWallpaper(WallpaperSurface.Glance)
            when (val want = l.settings.wallpaper) {
                is ThemeWallpaper.Solid -> { assertEquals(WallpaperKind.Solid, w.kind); assertEquals(want.color, w.solid) }
                is ThemeWallpaper.Gradient -> { assertEquals(WallpaperKind.Gradient, w.kind); assertEquals(want.from, w.gradient.from); assertEquals(want.to, w.gradient.to) }
                null -> Unit
            }
            assertEquals("the pages' own wallpaper is not touched", pages, f.prefs.rawWallpaper(WallpaperSurface.Pages))
        }
        val changed = Fake(solidPrefs()).also { it.applier.applyLook(LookPresets.all[0]) }
        changed.prefs = changed.prefs.withWallpaper(WallpaperSurface.Glance, Wallpaper(kind = WallpaperKind.Solid, solid = 0xFF123456))
        assertNull("another solid colour is a custom look", LookPresets.current(changed.prefs, changed.dock))
    }

    @Test fun CFG_26_w56_packsMayCarryACalmStyleAndAnIconPlateAndOldPacksStillParse() {
        val r = ThemePacks.parse("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"calmStyle":"edgedots","iconPlate":false}}""")
        val s = (r as ThemeParse.Ok).pack.settings
        assertEquals(CalmStyle.EdgeDots, s.calmStyle); assertEquals(false, s.iconPlate)
        val bad = ThemePacks.parse("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"calmStyle":"sparkles","iconPlate":"yes"}}""") as ThemeParse.Failed
        assertEquals(setOf(ThemeField.CalmStyle, ThemeField.IconPlate), bad.errors.mapNotNull { it.field }.toSet())
        assertTrue(ThemePacks.parse("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"dockStyle":"3d"}}""") is ThemeParse.Ok)
        val ch = ThemeLook.changes(LauncherPrefs(), DockStyle.Standard, ThemeSettings(calmStyle = CalmStyle.Beam, iconPlate = false))
        assertEquals(listOf(ThemeField.CalmStyle, ThemeField.IconPlate), ch.map { it.field }); assertTrue(ch.all { it.changes })
    }

    @Test fun CFG_26_w56_resetToTheDefaultLookLeavesTheCalmStyleAndPlateAlone() {
        val f = Fake(solidPrefs().copy(calmStyle = CalmStyle.Beam, iconPlate = false))
        f.applier.reset()
        assertEquals(CalmStyle.Beam, f.prefs.calmStyle); assertFalse(f.prefs.iconPlate)
        assertEquals(ThemeMode.Dark, f.prefs.themeMode)
    }
}
