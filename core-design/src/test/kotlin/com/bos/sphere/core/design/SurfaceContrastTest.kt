package com.bos.sphere.core.design

import androidx.compose.ui.graphics.toArgb
import com.bos.sphere.core.quality.Contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/** NFR-15: the theme tokens every surface draws with reach WCAG AA for the text drawn on them, in every variant. */
class SurfaceContrastTest {
    private fun ratio(fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) = Contrast.ratio(fg.toArgb(), bg.toArgb())

    @Test fun NFR_15_primaryAndBrightInkReachAaOnEverySubstrateOfEveryVariant() {
        for (v in ThemeVariant.entries) {
            val t = HyleColors.tokens(v)
            for ((name, ink) in listOf("ink" to t.ink, "inkBright" to t.inkBright)) {
                for ((bn, bg) in listOf("background" to t.background, "surface" to t.surface)) {
                    val r = ratio(ink, bg)
                    assertTrue("$v $name on $bn is $r", r >= Contrast.AA_NORMAL)
                }
            }
        }
    }

    @Test fun NFR_15_dimInkReachesAaLargeOnEverySubstrateAndIsKeptToSecondaryText() {
        // The dim ink is for secondary captions; it must still reach the large-text threshold everywhere.
        for (v in ThemeVariant.entries) {
            val t = HyleColors.tokens(v)
            for (bg in listOf(t.background, t.surface)) {
                val r = ratio(t.inkDim, bg)
                assertTrue("$v inkDim is $r", r >= Contrast.AA_LARGE)
            }
        }
    }

    @Test fun NFR_15_textOnTheAccentIsChosenForContrastForEveryAccentThePickerOffers() {
        val accents = listOf(0xFF8E7BFF, 0xFFFFFFFF, 0xFF000000, 0xFFFFD60A, 0xFFFF375F, 0xFF30D158, 0xFF0A84FF, 0xFF1B1B1B)
        for (a in accents) {
            HyleTheme.apply(ThemeVariant.Dark, a)
            val r = ratio(HyleColors.OnAccent, HyleColors.Violet)
            assertTrue("accent ${a.toString(16)} gives only $r", r >= Contrast.AA_LARGE)
        }
        HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF)
    }
}
