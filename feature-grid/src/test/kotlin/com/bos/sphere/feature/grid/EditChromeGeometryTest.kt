package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ---- W48 (editGaps): the hint and the tray keep clear of the dimmed hotspots
    private fun hotspots(f: EditChromeGeometry.PageFrame, dd: Float): List<EdgeRect> {
        val s = EditChromeGeometry.hotspotSidePx(f, dd); val r = f.rect
        return listOf(EdgeRect(r.left, r.top, r.left + s, r.top + s), EdgeRect(r.right - s, r.top, r.right, r.top + s), EdgeRect(r.left, r.bottom - s, r.left + s, r.bottom), EdgeRect(r.right - s, r.bottom - s, r.right, r.bottom))
    }
    private fun overlaps(a: EdgeRect, b: EdgeRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
    private val windows = listOf(411f to 891f, 320f to 568f, 360f to 640f, 393f to 852f, 891f to 411f, 640f to 360f, 800f to 1280f)

    @Test fun HOM_12_w48_theHintLiesBelowTheTopHotspotsAndTheMusicStripInEveryWindow() {
        for ((wd, hd) in windows) {
            val ww = wd * d; val hh = hd * d
            val f = EditChromeGeometry.pageFrame(ww, hh, 24 * d, d)
            val top = EditChromeGeometry.hintTopPx(f, 24 * d, d)
            val hint = EdgeRect(0f, top, ww, top + EditChromeGeometry.HINT_DP * d)
            hotspots(f, d).forEach { assertFalse("${wd}x$hd: hint over a hotspot", overlaps(hint, it)) }
            assertTrue("${wd}x$hd: under the bar and the music strip", top >= EditChromeGeometry.barBottomPx(24 * d, d) + EditChromeGeometry.MUSIC_STRIP_DP * d - 0.5f)
        }
    }

    @Test fun HOM_12_w48_theTrayLiesAboveTheBottomHotspotsOrBelowThePageInEveryWindow() {
        for ((wd, hd) in windows) for (requested in listOf(8f, 60f, 112f)) {
            val ww = wd * d; val hh = hd * d
            val f = EditChromeGeometry.pageFrame(ww, hh, 24 * d, d)
            val bottom = EditChromeGeometry.trayBottomDp(f, d, requested)
            assertTrue("${wd}x$hd: never lowered", bottom >= requested)
            val tray = EdgeRect(0f, hh - (bottom + EditChromeGeometry.TRAY_DP) * d, ww, hh - bottom * d)
            hotspots(f, d).forEach { assertFalse("${wd}x$hd req $requested: tray over a hotspot", overlaps(tray, it)) }
            assertEquals("idempotent: the chrome applies it again to the effective value", bottom, EditChromeGeometry.trayBottomDp(f, d, bottom), 0.01f)
        }
    }

    @Test fun HOM_12_w48_landscapeKeepsTheTrayWhereItWasBecauseThePageEndsAboveIt() {
        val f = EditChromeGeometry.pageFrame(891 * d, 411 * d, 0f, d)
        assertEquals(8f, EditChromeGeometry.trayBottomDp(f, d, 8f), 0.01f)
    }

    @Test fun HOM_12_w48_theActionBarReserveFollowsTheLoweredHint() {
        val f = EditChromeGeometry.pageFrame(w, h, 24 * d, d)
        val hintTop = EditChromeGeometry.hintTopPx(f, 24 * d, d) / d
        assertEquals(hintTop + EditChromeGeometry.HINT_DP, EditChromeGeometry.actionBarReserveDp(24f, true, 60f, hintTopDp = hintTop).first, 0.01f)
        assertEquals(24f + 56f, EditChromeGeometry.actionBarReserveDp(24f, false, 60f, hintTopDp = hintTop).first, 0.01f)
    }

    @Test fun HOM_12_w48_theGridDotsAreDarkAndStrongerOnTheLightTheme() {
        assertTrue(EditGridDots.alpha(0f, 0f, null, 100f, 100f, light = true) > EditGridDots.alpha(0f, 0f, null, 100f, 100f, light = false))
        assertEquals(EditChromeGeometry.DOT_NEAR_ALPHA_LIGHT, EditGridDots.alpha(5f, 5f, EdgeRect(0f, 0f, 10f, 10f), 100f, 100f, light = true), 0f)
        assertEquals(androidx.compose.ui.graphics.Color.White, EditGridDots.ink(false))
        assertTrue("dark ink on light", EditGridDots.ink(true).red < 0.2f && EditGridDots.ink(true).green < 0.2f)
        // contrast of the dot colour against a light wallpaper (0xF2F2F7) at the resting alpha: white would be ~1.0:1
        fun lum(c: androidx.compose.ui.graphics.Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        val wall = androidx.compose.ui.graphics.Color(0xFFF2F2F7)
        val dotOnLight = lum(EditGridDots.ink(true)) * EditChromeGeometry.DOT_ALPHA_LIGHT + lum(wall) * (1 - EditChromeGeometry.DOT_ALPHA_LIGHT)
        val whiteOnLight = 1f * EditChromeGeometry.DOT_ALPHA + lum(wall) * (1 - EditChromeGeometry.DOT_ALPHA)
        assertTrue("the dark dot differs from the wallpaper by far more than a white one", (lum(wall) - dotOnLight) > 4 * kotlin.math.abs(whiteOnLight - lum(wall)))
    }
}
