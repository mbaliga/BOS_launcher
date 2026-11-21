package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-12 (edit card): the pure geometry of the shrunk home, by orientation, RTL and reduced motion. */
class EditCardGeometryTest {
    private val top = 140f; private val bottom = 70f; private val band = 130f

    private fun portrait(rtl: Boolean = false) = EditCardGeometry.target(1080f, 2340f, rtl, top, bottom, band)
    private fun landscape(rtl: Boolean = false) = EditCardGeometry.target(2340f, 1080f, rtl, top, bottom, band)

    @Test fun HOM_12_portraitCardIsAboutSeventyPercentPushedLeftAndPartlyOffScreen() {
        val t = portrait()
        assertEquals(CardSide.Left, t.side)
        assertTrue("scale ${t.scale}", t.scale in 0.6f..0.72f)
        assertTrue("left edge runs off screen", t.card.left < 0f)
        assertEquals(1080f * t.scale, t.card.width, 0.5f)
        assertEquals(2340f * t.scale, t.card.height, 0.5f)
        assertEquals(top, t.card.top, 0.01f)
    }

    @Test fun HOM_12_portraitLeavesAColumnOnTheRightAndABandUnderTheCard() {
        val t = portrait()
        assertEquals(t.card.right, t.carousel.left, 0.01f); assertEquals(1080f, t.carousel.right, 0.01f)
        assertTrue("column is at least a quarter of the width", t.carousel.width >= 1080f * 0.25f)
        assertEquals(t.card.bottom, t.addBand.top, 0.01f)
        assertEquals(0f, t.addBand.left, 0.01f); assertEquals(1080f, t.addBand.right, 0.01f)
        assertTrue("the band keeps the bar clear", t.addBand.bottom <= 2340f - bottom + 0.01f)
        assertTrue("the band is as tall as asked or taller", t.addBand.height >= band - 1f)
    }

    @Test fun HOM_12_landscapePutsTheAddCarouselAtTheFootOfTheRightColumn() {
        val t = landscape()
        assertTrue(t.landscape)
        assertTrue("scale ${t.scale}", t.scale in 0.5f..0.7f)
        assertEquals(t.card.right, t.carousel.left, 0.01f)
        assertEquals(t.carousel.bottom, t.addBand.top, 0.01f)
        assertEquals(t.card.right, t.addBand.left, 0.01f)
        assertTrue("column wide enough for thumbnails", t.carousel.width >= 2340f * 0.25f)
        assertTrue(t.carousel.height > 0f)
    }

    @Test fun HOM_12_rtlMirrorsTheSidesAndTheCarousel() {
        val l = portrait(false); val r = portrait(true)
        assertEquals(CardSide.Right, r.side)
        assertEquals(l.scale, r.scale, 0f)
        assertEquals(1080f - l.card.right, r.card.left, 0.5f)
        assertEquals(1080f - l.card.left, r.card.right, 0.5f)
        assertEquals(0f, r.carousel.left, 0.01f); assertEquals(r.card.left, r.carousel.right, 0.01f)
        assertEquals(-l.translationX, r.translationX, 0.5f)
        val rl = landscape(true)
        assertEquals(CardSide.Right, rl.side); assertEquals(0f, rl.carousel.left, 0.01f)
    }

    @Test fun HOM_12_launchpadMirrorUsesTheOtherSide() {
        val t = EditCardGeometry.target(1080f, 2340f, rtl = false, side = CardSide.Right)
        assertEquals(CardSide.Right, t.side); assertTrue(t.card.right > 1080f); assertEquals(0f, t.carousel.left, 0.01f)
    }

    @Test fun HOM_12_translationPutsTheScaledCentreOnTheCard() {
        val t = portrait()
        val cx = 1080f / 2 + t.translationX; val cy = 2340f / 2 + t.translationY
        assertEquals((t.card.left + t.card.right) / 2, cx, 0.5f); assertEquals((t.card.top + t.card.bottom) / 2, cy, 0.5f)
    }

    @Test fun HOM_12_cardNeverCoversTheReservedBars() {
        for (t in listOf(portrait(), landscape(), EditCardGeometry.target(400f, 600f, false, 200f, 150f, 130f))) {
            assertTrue(t.card.top >= 0f); assertTrue(t.card.bottom <= (if (t.landscape) 1080f else 2340f).coerceAtLeast(600f))
        }
        val small = EditCardGeometry.target(400f, 600f, false, 200f, 150f, 130f)
        assertTrue("the card shrinks to leave room: ${small.card.bottom} vs ${small.addBand.top}", small.card.bottom <= small.addBand.top + 0.01f)
    }

    @Test fun HOM_12_progressInterpolatesFromFullScreenToTheCard() {
        val t = portrait()
        val start = EditCardGeometry.frame(t, 0f, false); val end = EditCardGeometry.frame(t, 1f, false); val mid = EditCardGeometry.frame(t, 0.5f, false)
        assertEquals(1f, start.scale, 0f); assertEquals(0f, start.translationX, 0f); assertEquals(0f, start.cornerDp, 0f)
        assertEquals(t.scale, end.scale, 1e-4f); assertEquals(t.translationX, end.translationX, 0.01f); assertEquals(EditCardGeometry.CORNER_DP, end.cornerDp, 1e-4f)
        assertTrue(mid.scale < 1f && mid.scale > t.scale); assertEquals(0.5f, mid.carouselAlpha, 1e-4f)
    }

    @Test fun NFR_14_reducedMotionReplacesTheShrinkWithACrossFade() {
        val t = portrait()
        val a = EditCardGeometry.frame(t, 0.3f, true); val b = EditCardGeometry.frame(t, 0.9f, true); val off = EditCardGeometry.frame(t, 0f, true)
        assertEquals(t.scale, a.scale, 0f); assertEquals(t.scale, b.scale, 0f) // no intermediate scale, ever
        assertEquals(0.3f, a.carouselAlpha, 1e-4f); assertEquals(0.9f, b.carouselAlpha, 1e-4f) // the carousel fades
        assertEquals(1f, off.scale, 0f); assertFalse(off.carouselAlpha > 0f)
    }

    @Test fun HOM_12_hitsCardTellsCardFromBackground() {
        val t = portrait()
        assertTrue(EditCardGeometry.hitsCard(t, t.card.left + 10, t.card.top + 10))
        assertFalse(EditCardGeometry.hitsCard(t, t.carousel.left + 20, t.carousel.top + 20))
    }
}
