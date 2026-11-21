package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A11yAndMotionTest {
    @Test fun NFR_13_touchTargetMinimumIs48dp() {
        assertTrue(Accessibility.meetsTouchTarget(48f, 48f))
        assertFalse(Accessibility.meetsTouchTarget(40f, 48f))
    }

    @Test fun NFR_13_readingOrderIsRowsThenColumns() {
        val spots = listOf(Accessibility.Spot("d", 10f, 100f), Accessibility.Spot("b", 200f, 5f), Accessibility.Spot("a", 10f, 0f), Accessibility.Spot("c", 100f, 100f))
        assertEquals(listOf("a", "b", "d", "c"), Accessibility.readingOrder(spots))
    }

    @Test fun NFR_13_orbitOrderIsClockwiseFromTheTop() {
        val spots = listOf(
            Accessibility.Spot("left", 0f, 100f), Accessibility.Spot("top", 100f, 0f),
            Accessibility.Spot("bottom", 100f, 200f), Accessibility.Spot("right", 200f, 100f),
        )
        assertEquals(listOf("top", "right", "bottom", "left"), Accessibility.clockwiseOrder(spots, 100f, 100f))
    }

    @Test fun NFR_13_contentDescriptionNullRatchet() {
        // Decorative images may have a null description, but the count must not grow without review. Lower this number as items get labels.
        val count = Repo.mainSources().sumOf { f -> f.readLines().count { it.contains("contentDescription = null") } }
        assertTrue("contentDescription = null count is $count", count <= 9)
    }

    @Test fun NFR_14_animationsOffMeansCrossFade() {
        assertEquals(MorphMode.Morph, MotionPrefs(1f, 1f).morphMode)
        assertEquals(MorphMode.CrossFade, MotionPrefs(0f, 1f).morphMode)
        assertEquals(MorphMode.CrossFade, MotionPrefs(1f, 0f).morphMode)
        assertEquals(MorphMode.CrossFade, MotionPrefs(1f, 1f, 0f).morphMode)
    }

    @Test fun NFR_14_durationsScaleAndCollapse() {
        assertEquals(300, MotionPrefs(1f, 1f).durationMs(300))
        assertEquals(600, MotionPrefs(2f, 1f).durationMs(300))
        assertEquals(0, MotionPrefs(0f, 1f).durationMs(300))
        assertEquals(90, MotionPrefs(0f, 1f).crossFadeMs())
    }

    @Test fun NFR_15_blackOnWhiteIs21To1() = assertEquals(21.0, Contrast.ratio(Contrast.BLACK, Contrast.WHITE), 0.01)

    @Test fun NFR_15_knownWcagPairs() {
        assertTrue(Contrast.meetsAa(0xFF767676.toInt(), Contrast.WHITE))
        assertFalse(Contrast.meetsAa(0xFF777777.toInt(), Contrast.WHITE))
        assertTrue(Contrast.meetsAa(0xFF777777.toInt(), Contrast.WHITE, largeText = true))
    }

    @Test fun NFR_15_scrimMakesWhiteTextReadableOverAnyWallpaper() {
        val wallpapers = listOf(Contrast.WHITE, 0xFFFFE000.toInt(), 0xFF808080.toInt(), 0xFFFF0000.toInt(), Contrast.BLACK)
        val a = Contrast.scrimAlphaForAny(Contrast.WHITE, wallpapers)!!
        wallpapers.forEach { w -> assertTrue("over $w", Contrast.ratio(Contrast.WHITE, Contrast.blend(Contrast.BLACK, w, a)) >= Contrast.AA_NORMAL) }
        assertEquals(0.0, Contrast.scrimAlphaFor(Contrast.WHITE, Contrast.BLACK)!!, 0.0)
    }

    @Test fun NFR_15_scrimIsImpossibleWhenTextMatchesTheScrim() {
        assertEquals(null, Contrast.scrimAlphaFor(Contrast.BLACK, Contrast.BLACK, scrim = Contrast.BLACK))
    }

    @Test fun NFR_15_bestTextPicksTheReadableColour() {
        assertEquals(Contrast.BLACK, Contrast.bestText(Contrast.WHITE))
        assertEquals(Contrast.WHITE, Contrast.bestText(0xFF101030.toInt()))
    }
}
