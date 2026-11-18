package com.bos.sphere.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** STR-14: the Recents panel reuses LaunchpadCardGeometry with the opposite edge: the card on the left, mirrored in right-to-left, reduced motion snaps. */
class RecentsCardGeometryTest {
    private fun portrait(rtl: Boolean = false, reduced: Boolean = false) = LaunchpadCardGeometry(400f, 900f, CardEdge.forRecents(rtl), 1f, reduced)
    private fun landscape(rtl: Boolean = false, reduced: Boolean = false) = LaunchpadCardGeometry(900f, 400f, CardEdge.forRecents(rtl), 1f, reduced)

    @Test fun STR_14_recentsIsTheMirrorImageOfLaunchpad() {
        assertEquals(CardEdge.Right, CardEdge.forLaunchpad(false)); assertEquals(CardEdge.Left, CardEdge.forRecents(false))
        assertEquals(CardEdge.Left, CardEdge.forLaunchpad(true)); assertEquals(CardEdge.Right, CardEdge.forRecents(true))
        for (rtl in listOf(false, true)) assertTrue(CardEdge.forLaunchpad(rtl) != CardEdge.forRecents(rtl))
    }

    @Test fun STR_14_theCardMovesLeftToASliverAtTheLeftEdgeInBothOrientations() {
        for (g in listOf(portrait(), landscape())) {
            val p = g.pose(1f)
            assertTrue("moves left", p.translationX < 0f)
            val mirror = LaunchpadCardGeometry(g.widthPx, g.heightPx, CardEdge.Right, 1f).pose(1f)
            assertEquals("same scale as Comms", mirror.scale, p.scale, 0f)
            assertEquals("exact mirror of the Comms pose", -mirror.translationX, p.translationX, 1e-3f)
            val vis = g.visibleCardBounds(1f)
            assertEquals("sliver on the left edge", 0f, vis.left, 1e-3f)
            assertEquals(g.sliverPx, vis.width, 1e-2f)
            assertTrue("sliver is 6 to 8 percent of the width", vis.width / g.widthPx in 0.06f..0.08f)
        }
    }

    @Test fun STR_14_rightToLeftPutsTheCardOnTheRightLikeLaunchpadDoesInLeftToRight() {
        for (g in listOf(portrait(rtl = true), landscape(rtl = true))) {
            assertTrue(g.pose(1f).translationX > 0f)
            val vis = g.visibleCardBounds(1f)
            assertEquals(g.widthPx, vis.right, 1e-3f)
            assertEquals(g.sliverPx, vis.width, 1e-2f)
        }
    }

    @Test fun STR_14_closedIsTheIdentityAndTheSettleIsTheSame450Ms() {
        for (g in listOf(portrait(), landscape())) {
            val p = g.pose(0f)
            assertEquals(1f, p.scale, 0f); assertEquals(0f, p.translationX, 0f)
            assertEquals(450, g.settleMs)
            assertEquals(0.5f, g.pose(1f).scrimAlpha, 1e-4f)
        }
    }

    @Test fun STR_14_reducedMotionSnapsTheCardAndCrossFadesTheBackdrop() {
        val g = portrait(reduced = true)
        assertEquals(g.pose(1f).translationX, g.pose(0.2f).translationX, 0f)
        assertEquals(0.2f, g.pose(0.2f).backdropAlpha, 1e-4f)
        assertEquals(0f, g.pose(0f).translationX, 0f)
        assertEquals(1f, g.pose(0.2f).contentAlpha, 0f)
    }

    @Test fun STR_14_theReturnZoneIsAtLeast48DpOnTheCardSliver() {
        val g = LaunchpadCardGeometry(300f, 600f, CardEdge.forRecents(false), 2f)
        assertTrue(g.returnZonePx >= 48f * 2f)
    }
}
