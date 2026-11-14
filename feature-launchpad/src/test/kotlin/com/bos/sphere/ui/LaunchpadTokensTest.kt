package com.bos.sphere.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.design.WallpaperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchpadTokensTest {
    @Test fun CFG_10_launchpadPaletteIsTheSharedTokensForDarkAndLight() {
        val d = HyleColors.tokens(ThemeVariant.Dark); val l = HyleColors.tokens(ThemeVariant.Light)
        assertEquals(d.background, LpDark.bg); assertEquals(d.surface, LpDark.surface); assertEquals(d.inkBright, LpDark.ink)
        assertEquals(d.inkDim, LpDark.dim); assertEquals(d.hairline, LpDark.line)
        assertEquals(l.background, LpLight.bg); assertEquals(l.surface, LpLight.surface); assertEquals(l.inkBright, LpLight.ink)
    }

    @Test fun CFG_10_accentFillKeepsWhiteTextReadableForAnyAccent() {
        for (c in listOf(0xFFFFFF00, 0xFF8E7BFF, 0xFF00E5FF, 0xFFFFFFFF, 0xFF0A5C95)) {
            val fill = lpAccentFill(Color(c.toInt()))
            assertTrue("%x".format(c), WallpaperPalette.contrast(fill.toArgb(), 0xFFFFFFFF.toInt()) >= 4.5)
        }
    }
}
