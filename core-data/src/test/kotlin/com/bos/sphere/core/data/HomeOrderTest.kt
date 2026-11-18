package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 26 (HOM-01, HOM-23): the single HomeOrder abstraction and the deck rules. */
class HomeOrderTest {
    @Test fun HOM_23_leftToRightIsTheDefaultAndRightToLeftMirrorsTheLaunchpadAndRecentsEdges() {
        assertEquals(HomeOrder.LeftToRight, LauncherPrefs().homeOrder)
        assertEquals(PhysicalSide.Left, HomeOrder.LeftToRight.launchpadSide); assertEquals(PhysicalSide.Right, HomeOrder.LeftToRight.recentsSide)
        assertEquals(PhysicalSide.Right, HomeOrder.RightToLeft.launchpadSide); assertEquals(PhysicalSide.Left, HomeOrder.RightToLeft.recentsSide)
        assertEquals(HomeOrder.LeftToRight, HomeOrder.parse(null)); assertEquals(HomeOrder.RightToLeft, HomeOrder.parse("RightToLeft"))
    }

    @Test fun HOM_23_swipeDirectionsAreMirrored() {
        // Left to right: the finger moves left to go forward; right to left: it moves right.
        assertTrue(HomeOrder.LeftToRight.isForwardDrag(-40f)); assertFalse(HomeOrder.LeftToRight.isForwardDrag(40f))
        assertTrue(HomeOrder.RightToLeft.isForwardDrag(40f)); assertFalse(HomeOrder.RightToLeft.isForwardDrag(-40f))
        assertTrue(HomeOrder.LeftToRight.isTowardsLaunchpad(40f)); assertTrue(HomeOrder.RightToLeft.isTowardsLaunchpad(-40f))
        assertFalse(HomeOrder.LeftToRight.isTowardsLaunchpad(0f))
    }

    @Test fun HOM_23_columnsAndPagesStartAtTheOrdersStartEdge() {
        assertEquals(10f, HomeOrder.LeftToRight.physicalLeft(10f, 30f, 200f), 0f)
        assertEquals(160f, HomeOrder.RightToLeft.physicalLeft(10f, 30f, 200f), 0f)
        assertEquals(0, HomeOrder.LeftToRight.visualIndex(0, 3)); assertEquals(2, HomeOrder.RightToLeft.visualIndex(0, 3))
        for (o in HomeOrder.entries) for (i in 0 until 4) assertEquals(i, o.logicalIndex(o.visualIndex(i, 4), 4))
        assertEquals(0, HomeOrder.LeftToRight.initialScroll(900)); assertEquals(900, HomeOrder.RightToLeft.initialScroll(900))
    }

    @Test fun HOM_23_hotspotCornersSwapLeftAndRightInRightToLeftOnly() {
        for (c in Corner.entries) assertEquals(c, HomeOrder.LeftToRight.corner(c))
        assertEquals(Corner.TopRight, HomeOrder.RightToLeft.corner(Corner.TopLeft)); assertEquals(Corner.BottomLeft, HomeOrder.RightToLeft.corner(Corner.BottomRight))
        for (c in Corner.entries) assertEquals(c, HomeOrder.RightToLeft.corner(HomeOrder.RightToLeft.corner(c)))
    }

    @Test fun HOM_01_theDeckIsGlanceThenPagesAndGlanceOffStartsOnThePages() {
        assertEquals(HomeShown.Glance, HomeDeck.startSurface(true)); assertEquals(HomeShown.Pages, HomeDeck.startSurface(false))
        assertEquals(HomeShown.Pages, HomeDeck.forward(HomeShown.Glance))
        assertEquals(HomeShown.Glance, HomeDeck.backwardFromFirstPage(true)); assertEquals(null, HomeDeck.backwardFromFirstPage(false))
        assertEquals(HomeShown.Pages, HomeDeck.afterGlanceSetting(false, HomeShown.Glance)); assertEquals(HomeShown.Glance, HomeDeck.afterGlanceSetting(true, HomeShown.Glance))
    }

    @Test fun HOM_01_addPageFromGlanceCreatesPageOneAndLaterOnesGoAfterTheLastPage() {
        val none = HomeDeck.addPage(emptyList(), null)
        assertEquals(1, none.pages.size); assertEquals(0, none.index)
        val blank = listOf(HomePage(id = 1L))
        val first = HomeDeck.addPage(blank, null)
        assertSame(blank, first.pages); assertEquals(0, first.index) // the one untouched blank page IS page 1
        val used = listOf(HomePage(listOf("a"), id = 1L))
        val second = HomeDeck.addPage(used, null)
        assertEquals(2, second.pages.size); assertEquals(1, second.index); assertEquals(listOf("a"), second.pages[0].keys)
        val mid = HomeDeck.addPage(listOf(HomePage(listOf("a"), id = 1L), HomePage(listOf("b"), id = 2L)), 0)
        assertEquals(3, mid.pages.size); assertEquals(1, mid.index); assertTrue(mid.pages[1].keys.isEmpty())
    }

    @Test fun HOM_01_theIndicatorCountsGlanceAsTheFirstStop() {
        assertEquals(3, HomeDeck.stops(true, 2, false)); assertEquals(2, HomeDeck.stops(false, 2, false))
        assertEquals(2, HomeDeck.stops(true, 5, true)) // the scroll is one stop
        assertEquals(2, HomeDeck.stops(true, 0, false))
    }
}
