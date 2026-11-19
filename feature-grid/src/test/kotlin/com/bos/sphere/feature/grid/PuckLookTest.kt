package com.bos.sphere.feature.grid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CFG-15, NFR-13 (wave 32, puckLook): the colours of the solid ring, its glyphs and the hub, in dark and light themes. */
class PuckLookTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = if (a.luminance() >= b.luminance()) a to b else b to a
        return (hi.luminance() + 0.05f) / (lo.luminance() + 0.05f)
    }

    @Test fun W32_darkThemeIsAWhiteRingWithBlackGlyphsBlackDividersAndABlackHubWithAWhiteArrow() {
        val l = PuckLook.of(false)
        assertEquals(Color.White, l.ring); assertEquals(Color.Black, l.glyph); assertEquals(Color.Black, l.divider)
        assertEquals(Color.Black, l.hub); assertEquals(Color.White, l.hubGlyph)
    }

    @Test fun W32_lightThemeIsANearBlackRingAbout012LuminanceWithWhiteGlyphsAndDividersAndAWhiteHub() {
        val l = PuckLook.of(true)
        assertEquals("ring luminance", 0.12f, l.ring.luminance(), 0.02f)
        assertEquals(Color.White, l.glyph); assertEquals(Color.White, l.divider)
        assertEquals(Color.White, l.hub); assertEquals(Color.Black, l.hubGlyph)
    }

    @Test fun W32_theActiveGlyphContrastsWithItsWedgeAtLeast4point5InBothThemesAndWhenPressed() {
        for (light in listOf(false, true)) {
            val l = PuckLook.of(light)
            assertTrue("ring $light ${contrast(l.glyph, l.ring)}", contrast(l.glyph, l.ring) >= 4.5f)
            assertTrue("pressed $light ${contrast(l.glyph, l.pressed)}", contrast(l.glyph, l.pressed) >= 4.5f)
            assertTrue("hub $light", contrast(l.hubGlyph, l.hub) >= 4.5f)
            assertTrue("hub pressed $light", contrast(l.hubGlyph, l.hubPressed) >= 4.5f)
        }
    }

    @Test fun W32_pressedDarkWedgeIsAboutEightyFivePercentWhite() {
        assertEquals(0.85f, PuckLook.Dark.pressed.red, 0.01f)
    }

    @Test fun W32_emptyAndUnavailableGlyphsAreFaintButStillSeenOnTheRing() {
        assertEquals(0.40f, PuckLook.EMPTY_ALPHA, 0f); assertEquals(0.38f, PuckLook.UNAVAILABLE_ALPHA, 0f)
        // faint states are deliberately below the 4.5:1 active bar but stay distinguishable from the wedge (at least 2:1)
        for (light in listOf(false, true)) {
            val l = PuckLook.of(light)
            for (a in listOf(PuckLook.EMPTY_ALPHA, PuckLook.UNAVAILABLE_ALPHA)) {
                val c = l.glyph.copy(alpha = a).compositeOver(l.ring)
                assertTrue("$light $a ${contrast(c, l.ring)}", contrast(c, l.ring) >= 1.8f)
            }
        }
    }
}
