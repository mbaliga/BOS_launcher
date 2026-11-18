package com.bos.sphere.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 25 (STR-01): the pure maths of the menu side panel and of the home card sliding out of view. */
class LaunchpadMenuMotionTest {
    private val sizes = listOf(360f to 800f, 412f to 915f, 800f to 360f, 915f to 412f)

    @Test fun STR_01_card_isFullyOutOfViewAtFullProgress_inEverySizeAndDirection() {
        for ((wDp, hDp) in sizes) for (rtl in listOf(false, true)) {
            val w = wDp * 2.5f; val h = hDp * 2.5f
            val geo = LaunchpadCardGeometry(w, h, CardEdge.forLaunchpad(rtl), 2.5f)
            val dir = if (rtl) -1f else 1f
            val r = geo.cardBounds(1f)
            val tx = LaunchpadMenuMotion.cardTranslationPx(w, 1f, false, dir)
            if (rtl) assertTrue("rtl ${wDp}x$hDp: right=${r.right + tx}", r.right + tx <= 0f) else assertTrue("ltr ${wDp}x$hDp: left=${r.left + tx}", r.left + tx >= w)
        }
    }

    @Test fun STR_01_card_isInPlaceAtZero_andTravelsWithTheSameProgressAsThePanel() {
        assertEquals(0f, LaunchpadMenuMotion.cardTranslationPx(1000f, 0f, false), 0f)
        assertEquals(500f, LaunchpadMenuMotion.cardTranslationPx(1000f, 0.5f, false), 0.01f)
        assertEquals(-320f, LaunchpadMenuMotion.panelTranslationPx(320f, 0f, false), 0f)
        assertEquals(0f, LaunchpadMenuMotion.panelTranslationPx(320f, 1f, false), 0f)
        assertEquals(320f, LaunchpadMenuMotion.listShiftPx(320f, 1f, false), 0f)
    }

    @Test fun NFR_14_reducedMotion_fadesOnlyAndNothingTravelsUntilTheCardIsGone() {
        assertEquals(0f, LaunchpadMenuMotion.listShiftPx(320f, 1f, true), 0f)
        assertEquals(0f, LaunchpadMenuMotion.panelTranslationPx(320f, 0.4f, true), 0f)
        assertEquals(0.4f, LaunchpadMenuMotion.panelAlpha(0.4f, true), 0.001f)
        assertEquals(1f, LaunchpadMenuMotion.panelAlpha(0.4f, false), 0f)
        assertEquals(0f, LaunchpadMenuMotion.cardTranslationPx(1000f, 0.5f, true), 0f)
        assertEquals(0.5f, LaunchpadMenuMotion.cardAlpha(0.5f, true), 0.001f)
        assertEquals(1000f, LaunchpadMenuMotion.cardTranslationPx(1000f, 1f, true), 0f)
        assertEquals(0f, LaunchpadMenuMotion.cardAlpha(1f, true), 0f)
    }

    // Wave 27 (the owner's Comms menu mock-up): 58 percent of the window, at most 380 dp on a wide window.
    @Test fun STR_01_panelIs58PercentOfTheWindowAndNeverWiderThan380dp() {
        assertEquals(380f, LaunchpadMenuMotion.panelWidthDp(800f), 0f)
        assertEquals(208.8f, LaunchpadMenuMotion.panelWidthDp(360f), 0.01f)
        assertTrue(LaunchpadMenuMotion.panelWidthDp(300f) < 300f)
    }
}
