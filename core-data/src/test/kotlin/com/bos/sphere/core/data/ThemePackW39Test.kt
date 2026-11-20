package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Wave 39 (dockThemes): the theme pack file, its limits, and applying and undoing a look. All pure JVM. */
class ThemePackW39Test {
    @Before fun reset() { ThemeUndo.clear() }

    private val full = """
        {"format":"liftoff-theme","formatVersion":1,"name":"Dusk","author":"A. Person","version":"1.2",
         "settings":{"themeMode":"amoled","accent":"#FF8800","iconShape":"circle","iconTint":"mono","dockStyle":"3d",
                     "wallpaper":{"kind":"gradient","from":"#112233","to":"#445566","angle":45}}}
    """.trimIndent()

    private fun ok(text: String) = (ThemePacks.parse(text) as ThemeParse.Ok).pack
    private fun errors(text: String) = (ThemePacks.parse(text) as ThemeParse.Failed).errors

    @Test fun CFG_10_pack01_aFullPackParsesEverySetting() {
        val p = ok(full)
        assertEquals("Dusk", p.name); assertEquals("A. Person", p.author); assertEquals("1.2", p.version)
        val s = p.settings
        assertEquals(ThemeMode.Amoled, s.themeMode); assertEquals(0xFFFF8800L, s.accent); assertEquals(IconShape.Circle, s.iconShape)
        assertEquals(IconTint.Mono, s.iconTint); assertEquals(DockStyle.ThreeD, s.dockStyle)
        assertEquals(ThemeWallpaper.Gradient(0xFF112233L, 0xFF445566L, 45), s.wallpaper)
    }

    @Test fun CFG_10_pack01_aPackMayCarryOnlySomeSettingsAndAuthorAndVersionAreOptional() {
        val p = ok("""{"format":"liftoff-theme","formatVersion":1,"name":"Just dock","settings":{"dockStyle":"transparent"}}""")
        assertNull(p.author); assertNull(p.version)
        assertEquals(DockStyle.Transparent, p.settings.dockStyle); assertNull(p.settings.themeMode); assertNull(p.settings.wallpaper)
    }

    @Test fun CFG_10_pack02_unknownKeysAreIgnoredAtEveryLevel() {
        val p = ok("""{"format":"liftoff-theme","formatVersion":1,"name":"X","script":"rm -rf","url":"http://x","settings":{"dockStyle":"3d","launch":"com.evil","wallpaper":{"kind":"solid","color":"#010203","path":"/sdcard/x.png"}}}""")
        assertEquals(DockStyle.ThreeD, p.settings.dockStyle)
        assertEquals(ThemeWallpaper.Solid(0xFF010203L), p.settings.wallpaper)
        // nothing outside the allowed settings can survive the round trip
        assertFalse(ThemePacks.encode(p).contains("script")); assertFalse(ThemePacks.encode(p).contains("http"))
    }

    @Test fun CFG_10_pack03_theFileIsLimitedTo64KB() {
        val pad = "x".repeat(ThemePacks.MAX_BYTES)
        val big = """{"format":"liftoff-theme","formatVersion":1,"name":"X","pad":"$pad","settings":{"dockStyle":"3d"}}"""
        assertEquals(ThemeErrorCode.TooLarge, errors(big).single().code)
        assertEquals(ThemeErrorCode.TooLarge, (ThemePacks.parse(big.toByteArray()) as ThemeParse.Failed).errors.single().code)
        // multi-byte characters count as bytes, not characters
        val wide = """{"format":"liftoff-theme","formatVersion":1,"name":"X","pad":"${"é".repeat(ThemePacks.MAX_BYTES / 2 + 10)}","settings":{"dockStyle":"3d"}}"""
        assertEquals(ThemeErrorCode.TooLarge, errors(wide).single().code)
        assertTrue(ThemePacks.parse("""{"format":"liftoff-theme","formatVersion":1,"name":"X","settings":{"dockStyle":"3d"}}""") is ThemeParse.Ok)
    }

    @Test fun CFG_10_pack03_stringsAreLimitedTo80CharactersAndRefuseControlAndFormatCharacters() {
        val long81 = "n".repeat(81)
        assertEquals(ThemeError(ThemeErrorCode.TextTooLong, ThemeField.Name), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"$long81","settings":{"dockStyle":"3d"}}""").single())
        assertTrue(ThemePacks.parse("""{"format":"liftoff-theme","formatVersion":1,"name":"${"n".repeat(80)}","settings":{"dockStyle":"3d"}}""") is ThemeParse.Ok)
        assertEquals(ThemeError(ThemeErrorCode.TextTooLong, ThemeField.Author), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","author":"$long81","settings":{"dockStyle":"3d"}}""").single())
        assertEquals(ThemeError(ThemeErrorCode.BadText, ThemeField.Name), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a\u0007b","settings":{"dockStyle":"3d"}}""").single())
        // a right-to-left override can make a name read backwards: refused
        assertEquals(ThemeError(ThemeErrorCode.BadText, ThemeField.Author), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","author":"x‮y","settings":{"dockStyle":"3d"}}""").single())
        assertEquals(ThemeError(ThemeErrorCode.Missing, ThemeField.Name), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"  ","settings":{"dockStyle":"3d"}}""").single())
    }

    @Test fun CFG_10_pack04_coloursAreValidated() {
        assertEquals(0xFFAABBCCL, ThemePacks.parseColour("#aabbcc")); assertEquals(0xFF123456L, ThemePacks.parseColour("#80123456")) // alpha ignored: opaque
        listOf("red", "#12345", "#1234567", "123456", "#GGGGGG", "#12 456", "", null).forEach { assertNull("'$it'", ThemePacks.parseColour(it)) }
        assertEquals(ThemeError(ThemeErrorCode.BadColour, ThemeField.Accent), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"accent":"purple"}}""").single())
        assertEquals(ThemeError(ThemeErrorCode.BadColour, ThemeField.Accent), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"accent":12345}}""").single())
        assertEquals(ThemeError(ThemeErrorCode.BadColour, ThemeField.Wallpaper), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"wallpaper":{"kind":"solid","color":"nope"}}}""").single())
    }

    @Test fun CFG_10_pack05_wallpaperCanOnlyBeSolidOrGradientNeverAPictureVideoFileOrLive() {
        listOf("picture", "video", "file", "live", "system", "signal", "").forEach { k ->
            val e = errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"wallpaper":{"kind":"$k","uri":"content://x","color":"#000000"}}}""")
            assertEquals("kind '$k'", ThemeError(ThemeErrorCode.BadWallpaper, ThemeField.Wallpaper), e.single())
        }
        assertEquals(ThemeErrorCode.BadWallpaper, errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"wallpaper":"http://x/y.png"}}""").single().code)
        assertEquals(ThemeErrorCode.BadValue, errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"wallpaper":{"kind":"gradient","from":"#000000","to":"#ffffff","angle":9999}}}""").single().code)
    }

    @Test fun CFG_10_pack06_valuesOutsideTheClosedSetsAreRefusedWithAClearField() {
        assertEquals(ThemeError(ThemeErrorCode.BadValue, ThemeField.Mode), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"themeMode":"neon"}}""").single())
        assertEquals(ThemeError(ThemeErrorCode.BadValue, ThemeField.IconShape), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"iconShape":"star"}}""").single())
        assertEquals(ThemeError(ThemeErrorCode.BadValue, ThemeField.DockStyle), errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"dockStyle":["3d"]}}""").single())
        val many = errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"themeMode":"x","iconShape":"y","iconTint":"z","accent":"q"}}""")
        assertEquals("every problem is listed, not only the first", 4, many.size)
    }

    @Test fun CFG_10_pack07_notAThemeBadJsonTooNewAndEmptyAreTold() {
        assertEquals(ThemeErrorCode.NotJson, errors("not json {").single().code)
        assertEquals(ThemeErrorCode.NotJson, errors("[1,2]").single().code)
        assertEquals(ThemeErrorCode.NotATheme, errors("""{"name":"a","settings":{"dockStyle":"3d"}}""").single().code)
        assertEquals(ThemeErrorCode.NotATheme, errors("""{"format":"liftoff-state","formatVersion":1}""").single().code)
        assertEquals(ThemeErrorCode.TooNew, errors("""{"format":"liftoff-theme","formatVersion":2,"name":"a","settings":{"dockStyle":"3d"}}""").single().code)
        assertEquals(ThemeErrorCode.BadFormatVersion, errors("""{"format":"liftoff-theme","formatVersion":"1","name":"a"}""").single().code)
        assertEquals(ThemeErrorCode.BadFormatVersion, errors("""{"format":"liftoff-theme","formatVersion":0,"name":"a"}""").single().code)
        assertEquals(ThemeErrorCode.NothingToApply, errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"unknown":1}}""").single().code)
        assertEquals(ThemeErrorCode.NothingToApply, errors("""{"format":"liftoff-theme","formatVersion":1,"name":"a"}""").single().code)
    }

    @Test fun CFG_10_pack08_exportThenParseRoundTrips() {
        val p = ok(full)
        assertEquals(p, ok(ThemePacks.encode(p)))
        val prefs = LauncherPrefs(themeMode = ThemeMode.Light, accentColor = 0xFF3366FF, iconShape = IconShape.Squircle, iconTint = IconTint.Accent)
            .withWallpaper(WallpaperSurface.Glance, Wallpaper(kind = WallpaperKind.Solid, solid = 0xFF204060))
        val captured = ThemeLook.capture(prefs, DockStyle.Transparent, "My look")
        assertEquals(captured, ok(ThemePacks.encode(captured)))
        assertEquals(ThemeWallpaper.Solid(0xFF204060L), captured.settings.wallpaper)
        assertTrue(ThemePacks.encode(captured).length < ThemePacks.MAX_BYTES)
    }

    @Test fun CFG_10_pack08_aWallpaperThatIsNotSolidOrGradientIsLeftOutOfAnExport() {
        listOf(WallpaperKind.Live, WallpaperKind.System, WallpaperKind.Picture).forEach { k ->
            val prefs = LauncherPrefs().withWallpaper(WallpaperSurface.Glance, Wallpaper(kind = k))
            assertNull("$k", ThemeLook.capture(prefs, DockStyle.Standard, "x").settings.wallpaper)
        }
    }

    @Test fun CFG_10_pack09_fileNameIsSafeAndHasTheExtension() {
        assertEquals("My-look.liftofftheme", ThemePacks.fileName("My look"))
        assertEquals("theme.liftofftheme", ThemePacks.fileName("///"))
        assertFalse(ThemePacks.fileName("../../etc/passwd").contains("/"))
        assertTrue(ThemePacks.fileName("n".repeat(200)).length <= 40 + ".liftofftheme".length)
    }

    @Test fun CFG_10_pack10_applyChangesOnlyWhatThePackCarries() {
        val before = LauncherPrefs(themeMode = ThemeMode.Light, accentColor = 0xFF111111, iconShape = IconShape.Square, iconTint = IconTint.Accent, recentsLimit = 7)
        val after = ThemeLook.apply(before, ok("""{"format":"liftoff-theme","formatVersion":1,"name":"a","settings":{"accent":"#00FF00","wallpaper":{"kind":"solid","color":"#0A0B0C"}}}""").settings)
        assertEquals(0xFF00FF00L, after.accentColor); assertEquals(ThemeMode.Light, after.themeMode); assertEquals(IconShape.Square, after.iconShape); assertEquals(7, after.recentsLimit)
        val w = after.rawWallpaper(WallpaperSurface.Glance)
        assertEquals(WallpaperKind.Solid, w.kind); assertEquals(0xFF0A0B0CL, w.solid)
        assertEquals("the pages' own wallpaper is not touched", before.rawWallpaper(WallpaperSurface.Pages), after.rawWallpaper(WallpaperSurface.Pages))
    }

    @Test fun CFG_10_pack11_previewChipsListOnlyCarriedSettingsWithBeforeAndAfter() {
        val prefs = LauncherPrefs(themeMode = ThemeMode.Dark, accentColor = 0xFF8E7BFF, iconShape = IconShape.Rounded)
        val c = ThemeLook.changes(prefs, DockStyle.Standard, ok(full).settings)
        assertEquals(listOf(ThemeField.Mode, ThemeField.Accent, ThemeField.IconShape, ThemeField.IconTint, ThemeField.DockStyle, ThemeField.Wallpaper), c.map { it.field })
        val mode = c.first { it.field == ThemeField.Mode }; assertEquals("Dark", mode.before); assertEquals("AMOLED", mode.after); assertTrue(mode.changes)
        val dock = c.first { it.field == ThemeField.DockStyle }; assertEquals("Standard", dock.before); assertEquals("3D", dock.after)
        val acc = c.first { it.field == ThemeField.Accent }; assertEquals(listOf(0xFF8E7BFFL), acc.beforeColors); assertEquals(listOf(0xFFFF8800L), acc.afterColors)
        assertEquals(2, c.first { it.field == ThemeField.Wallpaper }.afterColors.size)
        val same = ThemeLook.changes(prefs, DockStyle.Standard, ThemeSettings(themeMode = ThemeMode.Dark)).single()
        assertFalse("an unchanged setting says so", same.changes)
        assertEquals(1, ThemeLook.changes(prefs, DockStyle.Standard, ThemeSettings(dockStyle = DockStyle.ThreeD)).size)
    }

    private class Fake(var prefs: LauncherPrefs = LauncherPrefs(), var dock: DockStyle = DockStyle.Standard) {
        val applier = ThemeApplier({ prefs }, { f -> prefs = f(prefs) }, { dock }, { dock = it })
    }

    @Test fun CFG_10_pack12_applyThenUndoRestoresTheWholePreviousLookIncludingAWallpaperKindAPackCannotCarry() {
        val live = LauncherPrefs(themeMode = ThemeMode.Light, accentColor = 0xFF123456, iconShape = IconShape.Original, iconTint = IconTint.Mono)
        val f = Fake(live, DockStyle.Transparent)
        assertEquals(WallpaperKind.Live, f.prefs.rawWallpaper(WallpaperSurface.Glance).kind)
        f.applier.apply(ok(full))
        assertEquals(ThemeMode.Amoled, f.prefs.themeMode); assertEquals(DockStyle.ThreeD, f.dock)
        assertEquals(WallpaperKind.Gradient, f.prefs.rawWallpaper(WallpaperSurface.Glance).kind)
        assertTrue(ThemeUndo.snapshot.value != null)
        assertTrue(f.applier.undo())
        assertEquals(live, f.prefs); assertEquals(DockStyle.Transparent, f.dock)
        assertNull("one undo, then nothing to undo", ThemeUndo.snapshot.value)
        assertFalse(f.applier.undo())
    }

    @Test fun CFG_10_pack13_resetGoesToTheDefaultLookAndCanBeUndone() {
        val custom = LauncherPrefs(themeMode = ThemeMode.Amoled, accentColor = 0xFFABCDEF, iconShape = IconShape.Circle, iconTint = IconTint.Accent)
            .withWallpaper(WallpaperSurface.Glance, Wallpaper(kind = WallpaperKind.Solid, solid = 0xFF445566))
        val f = Fake(custom, DockStyle.ThreeD)
        f.applier.reset()
        val d = LauncherPrefs()
        assertEquals(d.themeMode, f.prefs.themeMode); assertEquals(d.accentColor, f.prefs.accentColor); assertEquals(d.iconShape, f.prefs.iconShape); assertEquals(d.iconTint, f.prefs.iconTint)
        assertEquals(d.rawWallpaper(WallpaperSurface.Glance), f.prefs.rawWallpaper(WallpaperSurface.Glance)); assertEquals(DockStyle.Standard, f.dock)
        assertTrue(f.applier.undo()); assertEquals(custom, f.prefs); assertEquals(DockStyle.ThreeD, f.dock)
    }

    @Test fun CFG_10_pack14_dockStyleIdsAreStableAndDefaultsToStandard() {
        assertEquals(listOf("standard", "transparent", "3d"), DockStyle.entries.map { it.id })
        assertEquals(DockStyle.Standard, DockStyle.orDefault(null)); assertEquals(DockStyle.Standard, DockStyle.orDefault("glass")); assertEquals(DockStyle.ThreeD, DockStyle.orDefault("3d"))
    }

    @Test fun CFG_10_pack15_theStoreHasNoAddressYetAndThemesCarryNoLink() {
        assertNull(ThemeStore.ADDRESS); assertFalse(ThemeStore.isConfigured())
        // a link in a theme's text is only text: nothing in the model can hold or open one
        val p = ok("""{"format":"liftoff-theme","formatVersion":1,"name":"http://evil.example/x","settings":{"dockStyle":"3d"}}""")
        assertEquals("http://evil.example/x", p.name)
        assertTrue(ThemePacks.encode(p).contains("evil")) // it stays a name, never a field that is read as an address
    }
}
