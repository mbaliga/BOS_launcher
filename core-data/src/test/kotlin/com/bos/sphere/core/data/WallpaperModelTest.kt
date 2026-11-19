package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOM-16, CFG-03 (wave 26, owner clause 10): a wallpaper per surface (Glance, Pages), each exactly one of solid, gradient or live;
 * the pages share the Glance wallpaper by default; an install from before keeps its background as the Glance wallpaper.
 */
class WallpaperModelTest {
    private fun read(vararg kv: Pair<String, Any?>) = LauncherPrefsStore(MapSharedPreferences(mapOf(*kv))).prefs.value

    @Test fun HOM_16_theDefaultIsLiveOnGlanceAndThePagesShareIt() {
        val p = LauncherPrefs()
        assertEquals(WallpaperKind.Live, p.wallpaper(WallpaperSurface.Glance).kind)
        assertTrue(p.pagesSameAsGlance)
        assertEquals(p.wallpaper(WallpaperSurface.Glance), p.wallpaper(WallpaperSurface.Pages))
        assertTrue(p.sharesWallpaper(WallpaperSurface.Pages))
        assertEquals(listOf("solid", "gradient", "live", "system", "picture"), WallpaperKind.entries.map { it.id }) // wave 34: exactly these five
    }

    @Test fun CFG_03_anInstallFromBeforeKeepsItsBackgroundAsTheGlanceWallpaperWithSameAsGlanceOn() {
        val p = read("bgSlot1" to "halo", "bgSlot2" to "radar-sweep", "style" to "Grid")
        assertEquals(WallpaperKind.Live, p.glanceWallpaperKind)
        assertEquals(BackgroundChoice(BgItem.Halo, BgItem.RadarSweep), p.wallpaper(WallpaperSurface.Glance).live)
        assertTrue(p.pagesSameAsGlance)
        assertEquals(BackgroundChoice(BgItem.Halo, BgItem.RadarSweep), p.wallpaper(WallpaperSurface.Pages).live)
    }

    @Test fun HOM_16_theSwitchOffGivesThePagesTheirOwnWallpaperAndTheGlanceKeepsItsOwn() {
        val g = Wallpaper(WallpaperKind.Gradient, gradient = GradientSpec(0xFF112233, 0xFF445566, 45))
        val p = LauncherPrefs().withWallpaper(WallpaperSurface.Pages, g).copy(pagesSameAsGlance = false)
        assertEquals(g, p.wallpaper(WallpaperSurface.Pages))
        assertEquals(WallpaperKind.Live, p.wallpaper(WallpaperSurface.Glance).kind)
        assertFalse(p.sharesWallpaper(WallpaperSurface.Pages))
        assertNotEquals(p.wallpaper(WallpaperSurface.Glance), p.wallpaper(WallpaperSurface.Pages))
        assertEquals(g, p.copy(pagesSameAsGlance = true).rawWallpaper(WallpaperSurface.Pages)) // the switch only chooses, it never discards
        assertEquals(p.wallpaper(WallpaperSurface.Glance), p.copy(pagesSameAsGlance = true).wallpaper(WallpaperSurface.Pages))
    }

    @Test fun CFG_03_everyKindRoundTripsThroughTheStoreAndTheKindsKeepEachOthersSettings() {
        val sp = MapSharedPreferences()
        val store = LauncherPrefsStore(sp)
        store.update {
            it.withWallpaper(WallpaperSurface.Glance, Wallpaper(WallpaperKind.Solid, solid = 0xFFAA5500, gradient = GradientSpec(0xFF010203, 0xFF040506, 300), live = BackgroundChoice(BgItem.Beam, BgItem.GlowLines)))
                .withWallpaper(WallpaperSurface.Pages, Wallpaper(WallpaperKind.Gradient, gradient = GradientSpec(0xFF0A0B0C, 0xFF0D0E0F, 270), live = BackgroundChoice(BgItem.Shaft, BgItem.None)))
                .copy(pagesSameAsGlance = false)
        }
        val back = LauncherPrefsStore(sp).prefs.value
        assertEquals(store.prefs.value, back)
        assertEquals(WallpaperKind.Solid, back.glanceWallpaperKind); assertEquals(0xFFAA5500, back.glanceSolid)
        assertEquals(GradientSpec(0xFF010203, 0xFF040506, 300), back.rawWallpaper(WallpaperSurface.Glance).gradient)
        assertEquals(BackgroundChoice(BgItem.Beam, BgItem.GlowLines), back.rawWallpaper(WallpaperSurface.Glance).live)
        assertEquals(BackgroundChoice(BgItem.Shaft, BgItem.None), back.wallpaper(WallpaperSurface.Pages).live)
        assertFalse(back.pagesSameAsGlance)
    }

    @Test fun CFG_03_brokenStoredWallpaperValuesFallBackToTheDefaults() {
        val p = read("wpGlanceKind" to "plasma", "wpGlanceAngle" to -90, "wpGlanceSolid" to 0x00112233L, "wpPagesSlot1" to "beam", "wpPagesSlot2" to "halo", "wpPagesKind" to 7)
        assertEquals(WallpaperKind.Live, p.glanceWallpaperKind)
        assertEquals(270, p.glanceGradAngle)
        assertEquals(0xFF112233L, p.glanceSolid) // always opaque
        assertEquals(BackgroundChoice.normalised(BgItem.Beam, BgItem.Halo), BackgroundChoice(p.pagesSlot1, p.pagesSlot2)) // two living wallpapers: the second slot is emptied
        assertEquals(WallpaperKind.Live, p.pagesWallpaperKind)
    }

    @Test fun CFG_03_aSettingsExportCarriesTheWallpaperOfBothSurfacesAndImportRestoresIt() {
        val p = LauncherPrefs().withWallpaper(WallpaperSurface.Pages, Wallpaper(WallpaperKind.Gradient, gradient = GradientSpec(0xFF123456, 0xFF654321, 135))).copy(pagesSameAsGlance = false, glanceWallpaperKind = WallpaperKind.Solid, glanceSolid = 0xFF336699)
        val json = SettingsCodec.encode(p)
        val prefsJson = json.getJSONObject("prefs")
        for (k in listOf("wpGlanceKind", "wpGlanceSolid", "wpPagesSame", "wpPagesKind", "wpPagesFrom", "wpPagesTo", "wpPagesAngle", "bgSlot1")) assertTrue("$k exported", prefsJson.has(k))
        val back = SettingsCodec.decode(json).prefs
        assertEquals(p.copy(onboardingDone = true), back)
    }

    @Test fun HOM_16_gradientAnglesAreNormalisedAndColoursOpaque() {
        assertEquals(GradientSpec(0xFF000001, 0xFF000002, 350), GradientSpec(0x00000001, 0x00000002, -10).normalised())
        assertEquals(0, GradientSpec(angle = 360).normalised().angle)
    }
}
