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

    // Wave 58 (defects58, CFG-62) replaces HOM_23_hotspotCornersSwapLeftAndRightInRightToLeftOnly: the order mirrors which side Comms and Recents open on;
    // the hotspots stay physical (a corner keeps its assignment in both orders), so the never-called corner mapping is gone.
    @Test fun HOM_23_theOrderMirrorsCommsAndRecentsButNotTheHotspots() {
        assertEquals(PhysicalSide.Right, HomeOrder.RightToLeft.launchpadSide); assertEquals(PhysicalSide.Left, HomeOrder.RightToLeft.recentsSide)
        assertTrue("no corner mapping: hotspots are physical", HomeOrder::class.java.methods.none { it.name == "corner" })
        val doc = java.io.File(generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "app/src/main").isDirectory }, "core-data/src/main/kotlin/com/bos/sphere/core/data/HomeOrder.kt").readText()
        assertFalse("the order's description no longer promises swapped hotspot roles", doc.contains("hotspots' left and right roles"))
    }

    @Test fun HOM_01_theDeckIsGlanceThenPagesAndGlanceOffStartsOnThePages() {
        assertEquals(HomeShown.Glance, HomeDeck.startSurface(true)); assertEquals(HomeShown.Pages, HomeDeck.startSurface(false))
        assertEquals(HomeShown.Glance, HomeDeck.surfaceAt(true, 0)); assertEquals(HomeShown.Pages, HomeDeck.surfaceAt(true, 1)); assertEquals(HomeShown.Pages, HomeDeck.surfaceAt(false, 0))
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


    @Test fun HOM_01_w28_theHomeIsOnePagerWhoseStopsAreGlanceThenThePagesStopAndPagesOffRemovesTheExtraStop() {
        // Glance on: Off = Glance only; Paginated / Continuous = Glance + the pages stop
        assertEquals(1, HomeDeck.outerCount(true, HomePages.Off)); assertEquals(2, HomeDeck.outerCount(true, HomePages.Paginated)); assertEquals(2, HomeDeck.outerCount(true, HomePages.Continuous))
        assertEquals(null, HomeDeck.pagesIndex(true, HomePages.Off)); assertEquals(1, HomeDeck.pagesIndex(true, HomePages.Paginated))
        // Glance off: the first page is home even with Pages Off
        assertEquals(1, HomeDeck.outerCount(false, HomePages.Off)); assertEquals(0, HomeDeck.pagesIndex(false, HomePages.Off)); assertEquals(0, HomeDeck.pagesIndex(false, HomePages.Continuous))
        assertEquals(1, HomeDeck.visiblePages(HomePages.Off, 5)); assertEquals(5, HomeDeck.visiblePages(HomePages.Paginated, 5))
    }

    @Test fun HOM_23_w28_theOneIndicatorCountsEveryStopIncludingGlanceAsTheFirstDot() {
        assertEquals(1, HomeDeck.stops(true, HomePages.Off, 3)); assertEquals(4, HomeDeck.stops(true, HomePages.Paginated, 3)); assertEquals(2, HomeDeck.stops(true, HomePages.Continuous, 3))
        assertEquals(3, HomeDeck.stops(false, HomePages.Paginated, 3)); assertEquals(1, HomeDeck.stops(false, HomePages.Off, 3)); assertEquals(1, HomeDeck.stops(false, HomePages.Continuous, 3))
        // which dot is lit, and where a tap on a dot goes (outer stop, inner page)
        assertEquals(0, HomeDeck.lit(true, 0, 0, HomePages.Paginated)); assertEquals(1, HomeDeck.lit(true, 1, 0, HomePages.Paginated)); assertEquals(3, HomeDeck.lit(true, 1, 2, HomePages.Paginated))
        assertEquals(1, HomeDeck.lit(true, 1, 7, HomePages.Continuous)); assertEquals(2, HomeDeck.lit(false, 0, 2, HomePages.Paginated))
        assertEquals(0 to null, HomeDeck.target(true, HomePages.Paginated, 0)); assertEquals(1 to 0, HomeDeck.target(true, HomePages.Paginated, 1)); assertEquals(1 to 2, HomeDeck.target(true, HomePages.Paginated, 3))
        assertEquals(1 to null, HomeDeck.target(true, HomePages.Continuous, 1)); assertEquals(0 to 2, HomeDeck.target(false, HomePages.Paginated, 2))
    }

    @Test fun HOM_23_theIndicatorRowIsDrawnWheneverThereIsMoreThanOneStopWhateverTheGlanceIndicatorPreference() {
        assertTrue(HomeDeck.indicatorShown(true, false, HomePages.Paginated, 2))
        assertTrue(HomeDeck.indicatorShown(true, false, HomePages.Continuous, 0))
        assertFalse("Glance alone with its indicator off draws nothing", HomeDeck.indicatorShown(true, false, HomePages.Off, 0))
        assertTrue(HomeDeck.indicatorShown(true, true, HomePages.Off, 0))
        assertFalse("one regular page and no Glance draws nothing", HomeDeck.indicatorShown(false, true, HomePages.Paginated, 1))
    }

    @Test fun HOM_23_aSwipeBackHandsOverToGlanceOnlyWhenGlanceIsOnAndNotEditingAndNoCardIsOpen() {
        assertTrue(HomeDeck.backSwipeToGlance(true, editing = false, cardOpen = false))
        assertFalse("Comms opens from the edge only, never from a swipe in the middle of the first page", HomeDeck.backSwipeToGlance(false, editing = false, cardOpen = false))
        assertFalse("dragging an icon in edit mode is not a swipe", HomeDeck.backSwipeToGlance(true, editing = true, cardOpen = false))
        assertFalse(HomeDeck.backSwipeToGlance(true, editing = false, cardOpen = true))
    }
}
