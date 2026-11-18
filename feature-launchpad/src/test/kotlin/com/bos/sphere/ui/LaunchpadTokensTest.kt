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
        // Wave 27: the dark look is the owner's Comms mock-up palette (near-black navy page, #1A1A1A panel); only the light look is the shared tokens.
        assertEquals(CommsStyle.DarkBg, LpDark.bg); assertEquals(CommsStyle.DarkPanel, LpDark.surface); assertEquals(CommsStyle.DarkInk, LpDark.ink)
        assertEquals(CommsStyle.DarkDim, LpDark.dim); assertEquals(CommsStyle.DarkLine, LpDark.line)
        assertEquals(l.background, LpLight.bg); assertEquals(l.surface, LpLight.surface); assertEquals(l.inkBright, LpLight.ink)
    }

    @Test fun CFG_10_accentFillKeepsWhiteTextReadableForAnyAccent() {
        for (c in listOf(0xFFFFFF00, 0xFF8E7BFF, 0xFF00E5FF, 0xFFFFFFFF, 0xFF0A5C95)) {
            val fill = lpAccentFill(Color(c.toInt()))
            assertTrue("%x".format(c), WallpaperPalette.contrast(fill.toArgb(), 0xFFFFFFFF.toInt()) >= 4.5)
        }
    }
}
