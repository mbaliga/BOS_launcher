package com.bos.sphere.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** STR-01 (card layout): the pure geometry of the shrunken home card. */
class LaunchpadCardGeometryTest {
    private fun portrait(edge: CardEdge = CardEdge.Right, reduced: Boolean = false) = LaunchpadCardGeometry(400f, 900f, edge, 1f, reduced)
    private fun landscape(edge: CardEdge = CardEdge.Right, reduced: Boolean = false) = LaunchpadCardGeometry(900f, 400f, edge, 1f, reduced)

    @Test fun STR_01_closedIsTheIdentityPose() {
        for (g in listOf(portrait(), landscape())) {
            val p = g.pose(0f)
            assertEquals(1f, p.scale, 0f); assertEquals(0f, p.translationX, 0f); assertEquals(0f, p.translationY, 0f)
            assertEquals(0f, p.visibleCornerDp, 0f)
            val b = g.cardBounds(0f)
            assertEquals(0f, b.left, 1e-3f); assertEquals(g.widthPx, b.right, 1e-3f)
        }
    }

    // Wave 9 (docs/decisions/cardTune.md): was "about two thirds size, 46 to 52 percent of the width left for the notifications, most of
    // the card visible". The card now nearly leaves: about 0.93 scale and only a 7 percent sliver of its inner edge stays on screen.
    @Test fun STR_01_openCardIsAThinSliverAtTheEdgeInBothOrientations() {
        for (g in listOf(portrait(), landscape())) {
            val p = g.pose(1f)
            assertTrue("scale ${p.scale}", p.scale in 0.92f..0.95f)
            assertEquals(28f, p.visibleCornerDp, 1e-3f)
            assertEquals("layer radius is pre-scale so the visible radius stays 28", 28f, p.layerCornerDp * p.scale, 1e-3f)
            assertEquals("top and bottom are inset equally", 0f, p.translationY, 0f)
            val b = g.cardBounds(1f)
            assertTrue("the far side runs off the screen", b.right > g.widthPx)
            assertEquals(g.nearEdgeFraction * g.widthPx, b.left, 1e-2f)
            val vis = g.visibleCardBounds(1f).width / g.widthPx
            assertTrue("sliver is 6 to 8 percent of the width, was $vis", vis in 0.06f..0.08f)
            assertEquals(g.sliverPx, g.visibleCardBounds(1f).width, 1e-2f)
            assertTrue("card is vertically inset", b.top > 0f && b.bottom < g.heightPx)
        }
    }

    @Test fun STR_01_cardDimsAsItRecedesToHalfBrightnessAtRest() {
        val g = portrait()
        assertEquals(0f, g.pose(0f).scrimAlpha, 0f)
        assertEquals(0.5f, g.pose(1f).scrimAlpha, 1e-4f)
        assertTrue(g.pose(1f).scrimAlpha in 0.45f..0.55f)
        assertTrue(g.pose(0.3f).scrimAlpha < g.pose(0.6f).scrimAlpha)
    }

    @Test fun STR_01_panelContentFadesInOverTheLastPartOfTheMotion() {
        val g = portrait()
        assertEquals(0f, g.pose(0f).contentAlpha, 0f); assertEquals(0f, g.pose(0.4f).contentAlpha, 0f)
        assertEquals(1f, g.pose(1f).contentAlpha, 1e-4f)
        assertTrue(g.pose(0.7f).contentAlpha in 0.1f..0.9f)
        assertEquals("reduced motion cross-fades the whole backdrop instead", 1f, portrait(reduced = true).pose(0.4f).contentAlpha, 0f)
    }

    @Test fun STR_01_cardEdgeFollowsTheFingerOneToOne() {
        for (g in listOf(portrait(), landscape())) for (f in listOf(0.2f, 0.5f, 0.8f, 1f)) {
            val finger = f * g.dragTravelFraction * g.widthPx
            assertEquals(finger, g.cardBounds(f).left, 1e-2f)
        }
        assertEquals(RevealMath.fraction(0f, 0.5f * 0.94f * 400f, 0.94f * 400f), 0.5f, 1e-4f)
    }

    @Test fun STR_01_returnZoneIsTheSliverWidenedToATouchTarget() {
        val g = portrait()
        assertEquals(48f, g.returnZonePx, 1e-3f)
        assertTrue(g.returnZonePx >= g.sliverPx)
        val wide = LaunchpadCardGeometry(2000f, 900f, CardEdge.Right, 1f)
        assertEquals(wide.sliverPx, wide.returnZonePx, 1e-3f)
        assertEquals(g.widthPx - 48f, g.contentWidthPx, 1e-3f)
    }

    @Test fun STR_01_settleIsAHalfSecondEaseOut() { assertEquals(450, portrait().settleMs) }

    @Test fun STR_01_rtlMirrorsTheCardToTheLeft() {
        val ltr = portrait(CardEdge.forLaunchpad(rtl = false))
        val rtl = portrait(CardEdge.forLaunchpad(rtl = true))
        assertEquals(CardEdge.Right, CardEdge.forLaunchpad(false)); assertEquals(CardEdge.Left, CardEdge.forLaunchpad(true))
        assertEquals(ltr.pose(1f).translationX, -rtl.pose(1f).translationX, 1e-3f)
        val a = ltr.cardBounds(1f); val b = rtl.cardBounds(1f)
        assertEquals(ltr.widthPx - a.right, b.left, 1e-2f)
        assertEquals(ltr.widthPx - a.left, b.right, 1e-2f)
        assertTrue(b.left < 0f)
        assertEquals(ltr.sliverPx, rtl.visibleCardBounds(1f).width, 1e-2f)
        assertEquals(0f, rtl.visibleCardBounds(1f).left, 1e-2f)
    }

    @Test fun STR_01_editModeTakesTheOppositeSideToLaunchpad() {
        assertEquals(CardEdge.Left, CardEdge.forEdit(rtl = false)); assertEquals(CardEdge.Right, CardEdge.forEdit(rtl = true))
        for (rtl in listOf(false, true)) assertTrue(CardEdge.forEdit(rtl) != CardEdge.forLaunchpad(rtl))
    }

    @Test fun STR_01_poseIsMonotoneAndClamped() {
        val g = portrait()
        var last = 1f
        for (i in 0..10) { val s = g.pose(i / 10f).scale; assertTrue(s <= last + 1e-6f); last = s }
        assertEquals(g.pose(1f), g.pose(3f)); assertEquals(g.pose(0f), g.pose(-2f))
    }

    @Test fun NFR_14_reducedMotionSnapsTheCardAndCrossFadesTheBackdrop() {
        val g = portrait(reduced = true)
        assertEquals(1f, g.pose(0f).scale, 0f)
        val mid = g.pose(0.4f)
        assertEquals("no shrink in between", g.pose(1f).scale, mid.scale, 0f)
        assertEquals(0.4f, mid.backdropAlpha, 1e-4f)
        assertEquals(1f, portrait(reduced = false).pose(0.4f).backdropAlpha, 0f)
    }

    @Test fun STR_01_coverFractionIsWhatTheSliverLeaves() {
        val g = landscape()
        assertEquals(1f - g.nearEdgeFraction, g.cardCoverFraction, 1e-6f)
    }
}
