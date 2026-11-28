package com.bos.sphere.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemingTest {
    private val dark = 0xFF0A0B10.toInt()
    private val light = 0xFFF3F4F8.toInt()

    @Test fun CFG_10_darkWallpaperGivesDarkVariantAndAReadableAccent() {
        val d = WallpaperPalette.derive(WallpaperSample(0xFF1B2A55.toInt(), 0xFF3B1E54.toInt(), 0xFF0E7C86.toInt(), supportsDarkText = false))
        assertEquals(ThemeVariant.Dark, d.variant)
        assertTrue("accent contrast ${WallpaperPalette.contrast(d.accentArgb.toInt(), dark)}", WallpaperPalette.contrast(d.accentArgb.toInt(), dark) >= WallpaperPalette.MIN_CONTRAST)
    }

    @Test fun CFG_10_brightWallpaperGivesLightVariantAndAReadableAccent() {
        val d = WallpaperPalette.derive(WallpaperSample(0xFFF4E8C8.toInt(), 0xFFE8C45A.toInt(), null, supportsDarkText = true))
        assertEquals(ThemeVariant.Light, d.variant)
        assertTrue(WallpaperPalette.contrast(d.accentArgb.toInt(), light) >= WallpaperPalette.MIN_CONTRAST)
    }

    @Test fun CFG_10_everyWallpaperYieldsAReadableAccentOnItsBackground() {
        val samples = listOf(0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFF123456, 0xFFABCDEF)
        for (c in samples) for (hint in listOf(false, true)) {
            val d = WallpaperPalette.derive(WallpaperSample(c.toInt(), null, null, hint))
            val bg = if (d.variant == ThemeVariant.Light) light else dark
            assertTrue("sample %x hint %s contrast %.2f".format(c, hint, WallpaperPalette.contrast(d.accentArgb.toInt(), bg)), WallpaperPalette.contrast(d.accentArgb.toInt(), bg) >= WallpaperPalette.MIN_CONTRAST)
            assertEquals(0xFFL, d.accentArgb shr 24)
        }
    }

    @Test fun CFG_10_picksTheMostVividWallpaperColour() {
        val d = WallpaperPalette.derive(WallpaperSample(0xFF777777.toInt(), 0xFF2E7D32.toInt(), 0xFF888888.toInt(), false))
        val r = (d.accentArgb shr 16) and 255; val g = (d.accentArgb shr 8) and 255
        assertTrue("green dominates: r=$r g=$g", g > r)
    }

    @Test fun CFG_10_fixedChoiceWinsWhenNotUsingTheWallpaperOrItCannotBeRead() {
        val w = WallpaperSample(0xFFF4E8C8.toInt(), null, null, true)
        assertEquals(DerivedTheme(ThemeVariant.Amoled, 0xFF123456), ThemeResolver.resolve(ThemeVariant.Amoled, 0xFF123456, PaletteSource.Fixed, w))
        assertEquals(DerivedTheme(ThemeVariant.Dark, 0xFF8E7BFF), ThemeResolver.resolve(ThemeVariant.Dark, 0xFF8E7BFF, PaletteSource.Wallpaper, null))
        assertEquals(ThemeVariant.Light, ThemeResolver.resolve(ThemeVariant.Dark, 0xFF8E7BFF, PaletteSource.Wallpaper, w).variant)
    }

    @Test fun CFG_10_densityAndTypeScaleAreClampedAndIndependent() {
        assertEquals(ThemeScaleMath.MAX_DENSITY, ThemeScaleMath.clampDensity(9f), 0f)
        assertEquals(ThemeScaleMath.MIN_TYPE, ThemeScaleMath.clampType(0.1f), 0f)
        assertEquals(1f, ThemeScaleMath.clampDensity(Float.NaN), 0f)
        val (d, f) = ThemeScaleMath.factors(1.2f, 1.2f)
        // Text pixels scale by d * f = the type scale only, whatever the density.
        assertEquals(1.2f, d * f, 1e-5f)
        val (d2, f2) = ThemeScaleMath.factors(0.9f, 1f)
        assertEquals(1f, d2 * f2, 1e-5f)
        assertEquals(0.9f, d2, 1e-5f)
    }

    @Test fun CFG_10_settingsRoundTripAndSurviveGarbage() {
        val s = ThemingSettings(PaletteSource.Wallpaper, 1.1f, 1.3f)
        val back = ThemingSettings.fromJson(s.toJson())
        assertEquals(s.source, back.source); assertEquals(1.1f, back.density, 1e-4f); assertEquals(1.3f, back.type, 1e-4f)
        assertEquals(ThemingSettings(), ThemingSettings.fromJson("nonsense"))
        assertEquals(ThemeScaleMath.MAX_DENSITY, ThemingSettings.fromJson("""{"density":50}""").density, 0f)
    }

    @Test fun CFG_10_tokensAreTheSharedDesignSystemOnes() {
        // Every variant resolves through HyleColors; none of the three needs a colour defined outside core-design.
        for (v in ThemeVariant.entries) {
            HyleTheme.apply(v, 0xFF6D5BFF)
            assertTrue(HyleColors.Background != HyleColors.Surface)
        }
        HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF)
    }
}

class SharedTokensTest {
    @Test fun CFG_10_liveColoursAreReadFromTheSharedTokenTable() {
        for (v in ThemeVariant.entries) {
            HyleTheme.apply(v, 0xFF8E7BFF)
            val t = HyleColors.tokens(v)
            assertEquals(t.background, HyleColors.Background); assertEquals(t.surface, HyleColors.Surface)
            assertEquals(t.surfaceTranslucent, HyleColors.SurfaceTranslucent)
            assertEquals(t.ink, HyleColors.Ink); assertEquals(t.inkBright, HyleColors.InkBright)
            assertEquals(t.inkDim, HyleColors.InkDim); assertEquals(t.hairline, HyleColors.Hairline)
        }
        HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF)
    }
}
