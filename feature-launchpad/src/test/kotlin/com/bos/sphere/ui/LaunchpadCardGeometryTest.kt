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

    @Test fun STR_01_openCardIsAboutTwoThirdsRoundedAndRunsOffTheRightEdge() {
        for (g in listOf(portrait(), landscape())) {
            val p = g.pose(1f)
            assertTrue("scale ${p.scale}", p.scale in 0.62f..0.72f)
            assertEquals(28f, p.visibleCornerDp, 1e-3f)
            assertEquals("layer radius is pre-scale so the visible radius stays 28", 28f, p.layerCornerDp * p.scale, 1e-3f)
            val b = g.cardBounds(1f)
            assertTrue("the right side runs off the screen", b.right > g.widthPx)
            assertTrue("the left of the card leaves room for the notifications", b.left > g.widthPx * 0.4f)
            assertEquals(g.nearEdgeFraction * g.widthPx, b.left, 1e-2f)
            assertTrue("most of the card stays visible", g.visibleCardBounds(1f).width / b.width > 0.7f)
        }
    }

    @Test fun STR_01_portraitAlsoKeepsTheCardVisible() {
        val g = portrait()
        assertFalse(g.landscape)
        assertTrue(g.visibleCardBounds(1f).width > g.widthPx * 0.4f)
        assertTrue(g.backgroundColumnPx > g.widthPx * 0.4f)
    }

    @Test fun STR_01_rtlMirrorsTheCardToTheLeft() {
        val ltr = portrait(CardEdge.forLaunchpad(rtl = false))
        val rtl = portrait(CardEdge.forLaunchpad(rtl = true))
        assertEquals(CardEdge.Right, CardEdge.forLaunchpad(false)); assertEquals(CardEdge.Left, CardEdge.forLaunchpad(true))
        assertEquals(ltr.pose(1f).translationX, -rtl.pose(1f).translationX, 1e-3f)
        val a = ltr.cardBounds(1f); val b = rtl.cardBounds(1f)
        assertEquals(ltr.widthPx - a.right, b.left, 1e-2f)
        assertEquals(ltr.widthPx - a.left, b.right, 1e-2f)
        assertTrue(b.left < 0f)
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

    @Test fun STR_01_neighbourPeeksInBelowTheCardAlignedWithIt() {
        val g = portrait()
        val c = g.cardBounds(1f); val n = g.neighbourBounds(1f)
        assertEquals(c.left, n.left, 1e-3f); assertEquals(c.right, n.right, 1e-3f)
        assertTrue(n.top > c.bottom)
        assertTrue("it peeks into the window", n.top < g.heightPx)
    }

    @Test fun NFR_14_reducedMotionSnapsTheCardAndCrossFadesTheBackdrop() {
        val g = portrait(reduced = true)
        assertEquals(1f, g.pose(0f).scale, 0f)
        val mid = g.pose(0.4f)
        assertEquals("no shrink in between", g.pose(1f).scale, mid.scale, 0f)
        assertEquals(0.4f, mid.backdropAlpha, 1e-4f)
        assertEquals(1f, portrait(reduced = false).pose(0.4f).backdropAlpha, 0f)
    }

    @Test fun STR_01_backgroundColumnLeavesAGutterBesideTheCard() {
        val g = landscape()
        assertEquals(g.nearEdgeFraction * g.widthPx - 16f, g.backgroundColumnPx, 1e-3f)
        assertEquals(1f - g.nearEdgeFraction, g.cardCoverFraction, 1e-6f)
    }
}
