package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-12 (wave 45, editChrome): the geometry of the scaled page, the bar and the tray, the grid dots. Plain JVM. */
class EditChromeGeometryTest {
    private val d = 2.75f
    private val w = 411 * d
    private val h = 891 * d

    @Test fun HOM_12_portraitPageIsExactlyEightyEightPercentAndInsideTheWindow() {
        val f = EditChromeGeometry.pageFrame(w, h, 24 * d, d)
        assertEquals(0.88f, f.scale, 1e-6f)
        assertEquals(w * 0.88f, f.rect.width, 0.5f); assertEquals(h * 0.88f, f.rect.height, 0.5f)
        assertEquals("centred horizontally", f.rect.left, w - f.rect.right, 0.5f)
        assertTrue(f.rect.top >= 0f && f.rect.bottom <= h + 0.5f)
    }

    @Test fun HOM_12_theBarCoversOnlyHalfOfWhatItWouldOtherwiseOverlap() {
        val safeTop = 24 * d
        val f = EditChromeGeometry.pageFrame(w, h, safeTop, d)
        val centredTop = (h - h * 0.88f) / 2f
        val bar = EditChromeGeometry.barBottomPx(safeTop, d)
        assertEquals(safeTop + 56 * d, bar, 0.01f)
        assertEquals(centredTop + 0.5f * (bar - centredTop), f.rect.top, 0.5f)
        assertTrue("the page starts below the status area", f.rect.top > safeTop)
    }

    @Test fun HOM_12_translationPutsTheScaledCentreWhereTheRectIs() {
        val f = EditChromeGeometry.pageFrame(w, h, 24 * d, d)
        assertEquals((f.rect.top + f.rect.bottom) / 2f, h / 2f + f.translationY, 0.5f)
    }

    @Test fun HOM_12_aHugeTopInsetNeverPushesThePageOffTheBottom() {
        val f = EditChromeGeometry.pageFrame(w, h, h, d)
        assertTrue(f.rect.bottom <= h + 0.5f)
    }

    @Test fun HOM_12_landscapeShrinksToClearTheBarAndTheTray() {
        val lw = 891 * d; val lh = 411 * d
        val f = EditChromeGeometry.pageFrame(lw, lh, 0f, d)
        assertTrue(f.scale < 0.88f && f.scale >= EditChromeGeometry.MIN_LANDSCAPE_SCALE)
        assertEquals((lh - 56 * d - 80 * d) / lh, f.scale, 1e-4f)
        assertTrue("the page ends above the tray", f.rect.bottom <= lh - 80 * d + 0.5f)
    }

    @Test fun HOM_12_aTinyLandscapeWindowKeepsTheFloor() {
        val f = EditChromeGeometry.pageFrame(640f, 200f, 0f, 3f)
        assertEquals(EditChromeGeometry.MIN_LANDSCAPE_SCALE, f.scale, 1e-6f)
    }

    @Test fun HOM_12_theTrayTopIsAboveItsBottomOffsetBySeventyTwoDp() {
        assertEquals(h - 20 * d - 72 * d, EditChromeGeometry.trayTopPx(h, 20 * d, d), 0.01f)
    }

    @Test fun HOM_12_theFrameAnimatesFromFullScreenToTheEditPage() {
        val page = EditChromeGeometry.pageFrame(w, h, 24 * d, d)
        val start = EditChromeGeometry.frame(page, 0f, false)
        val end = EditChromeGeometry.frame(page, 1f, false)
        assertEquals(1f, start.scale, 0f); assertEquals(0f, start.translationY, 0f); assertEquals(0f, start.cornerDp, 0f)
        assertEquals(0.88f, end.scale, 1e-6f); assertEquals(page.translationY, end.translationY, 1e-3f); assertEquals(20f, end.cornerDp, 0f)
        val mid = EditChromeGeometry.frame(page, 0.5f, false)
        assertEquals((1f + 0.88f) / 2f, mid.scale, 1e-6f)
    }

    @Test fun HOM_12_withReducedMotionThereIsNoScaleAnimationAndTheScrimCrossFades() {
        val page = EditChromeGeometry.pageFrame(w, h, 24 * d, d)
        val a = EditChromeGeometry.frame(page, 0.01f, true)
        assertEquals("the page is at rest from the first frame", 0.88f, a.scale, 1e-6f)
        assertEquals(0.01f, a.carouselAlpha, 1e-6f)
        assertEquals(1f, EditChromeGeometry.frame(page, 0f, true).scale, 0f)
    }

    @Test fun HOM_12_theScrimIsThirtyFivePercentAtRest() {
        assertEquals(0.35f, EditChromeGeometry.scrimAlpha(1f), 1e-6f)
        assertEquals(0f, EditChromeGeometry.scrimAlpha(-1f), 0f)
    }

    @Test fun HOM_12_blendGoesFromTheEditPageToThePageEditorCard() {
        val page = EditChromeGeometry.pageFrame(w, h, 24 * d, d)
        val a = EditChromeGeometry.frame(page, 1f, false)
        val b = EditCardGeometry.frame(EditCardGeometry.target(w, h, false), 1f, false)
        assertEquals(a, EditChromeGeometry.blend(a, b, 0f))
        assertEquals(b, EditChromeGeometry.blend(a, b, 1f))
        assertEquals((a.scale + b.scale) / 2f, EditChromeGeometry.blend(a, b, 0.5f).scale, 1e-6f)
    }

    @Test fun HOM_12_titleCountsFromOneAndClamps() {
        assertEquals(2 to 4, EditChromeText.titleArgs(1, 4))
        assertEquals(1 to 1, EditChromeText.titleArgs(0, 0))
        assertEquals(4 to 4, EditChromeText.titleArgs(9, 4))
    }

    @Test fun HOM_12_dotsAreAtEveryCellCorner() {
        val g = GridGeometry.compute(400f, 800f, 4, 5, 0.5f)
        val dots = EditGridDots.corners(g)
        assertEquals(5 * 6, dots.size)
        assertEquals(g.originX to g.originY, dots.first())
        assertEquals(g.originX + 4 * g.pitchX to g.originY + 5 * g.pitchY, dots.last())
    }

    @Test fun HOM_12_dotsBrightenWithinOneCellOfTheActiveRect() {
        val active = EdgeRect(100f, 100f, 200f, 200f)
        assertEquals(0.40f, EditGridDots.alpha(150f, 150f, active, 80f, 90f), 0f)   // inside
        assertEquals(0.40f, EditGridDots.alpha(200f + 89f, 150f, active, 80f, 90f), 0f) // just within one cell (the larger pitch)
        assertEquals(0.18f, EditGridDots.alpha(200f + 95f, 150f, active, 80f, 90f), 0f) // beyond
        assertEquals(0.18f, EditGridDots.alpha(150f, 150f, null, 80f, 90f), 0f)
    }

    @Test fun HOM_12_distanceToARectIsZeroInsideAndEuclideanAtACorner() {
        val r = EdgeRect(0f, 0f, 10f, 10f)
        assertEquals(0f, EditGridDots.distance(5f, 5f, r), 0f)
        assertEquals(5f, EditGridDots.distance(13f, 14f, r), 1e-4f)
    }
}
