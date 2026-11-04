package com.bos.sphere.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** App Widget pages (`:feature-pages`) reuse placements, reconciliation and restore on the Page surface. */
class PageSurfaceTest {

    @Test
    fun aPageWidgetIsAPlacementOnThePageSurface() {
        val equator = placement(1, WidgetSurface.Equator, slot = 2)
        val (list, displaced) = PlacementEdits.putOnPage(listOf(equator), placement(5, WidgetSurface.Equator, slot = 3))
        assertEquals(listOf(equator, placement(5, WidgetSurface.Page, slot = 3)), list)
        assertTrue(displaced.isEmpty())
        assertEquals(placement(5, WidgetSurface.Page, slot = 3), PlacementEdits.onPage(list, 3))
        assertNull(PlacementEdits.onPage(list, 2)) // slot 2 is an equator slot, not a page
    }

    @Test
    fun choosingAnotherWidgetForAPageDisplacesTheOldOne() {
        val old = placement(5, WidgetSurface.Page, slot = 3)
        val other = placement(6, WidgetSurface.Page, slot = 4)
        val (list, displaced) = PlacementEdits.putOnPage(listOf(old, other), placement(9, WidgetSurface.Page, slot = 3))
        assertEquals(listOf(other, placement(9, WidgetSurface.Page, slot = 3)), list)
        assertEquals(listOf(5), displaced)
    }

    @Test
    fun pageWidgetsRoundTripThroughTheCodec() {
        val list = listOf(placement(5, WidgetSurface.Page, slot = 3), placement(-8, WidgetSurface.Page, slot = 1))
        assertEquals(list, PlacementCodec.decode(PlacementCodec.encode(list)))
    }

    @Test
    fun theEquatorAndTheHubIgnorePageWidgets() {
        val apps = listOf("a", "b", "c")
        val placements = listOf(placement(5, WidgetSurface.Page, slot = 1), placement(6, WidgetSurface.Hub, slot = 0))
        assertEquals(apps, EquatorMerge.merge(apps, placements) { "w${it.appWidgetId}" })
        assertEquals(listOf(placement(6, WidgetSurface.Hub, slot = 0)), PlacementEdits.hubStack(placements))
    }

    @Test
    fun removingAPageWidgetLeavesEquatorSlotsAlone() {
        val list = listOf(placement(1, WidgetSurface.Equator, slot = 4), placement(5, WidgetSurface.Page, slot = 2))
        assertEquals(listOf(placement(1, WidgetSurface.Equator, slot = 4)), PlacementEdits.remove(list, 5))
    }

    @Test
    fun reconciliationKeepsPageWidgetsAndTheirIds() {
        val page = placement(5, WidgetSurface.Page, slot = 2)
        val plan = PlacementReconciler.plan(listOf(page), hostIds = listOf(5, 7), pendingIds = emptyList()) { true }
        assertEquals(listOf(page), plan.keep)
        assertEquals(listOf(7), plan.idsToDelete)
        val gone = PlacementReconciler.plan(listOf(page), hostIds = listOf(7), pendingIds = emptyList()) { true }
        assertEquals(listOf(page), gone.dropped)
    }

    @Test
    fun aRestoredPageWidgetKeepsItsSlotSoItsPageFindsIt() {
        val pending = placement(-12, WidgetSurface.Page, slot = 2)
        val remapped = PlacementReconciler.remapRestored(listOf(pending), intArrayOf(12), intArrayOf(40))
        assertEquals(listOf(pending.copy(appWidgetId = 40)), remapped.placements)
        assertEquals(40, PlacementEdits.onPage(remapped.placements, 2)?.appWidgetId)
    }

    @Test
    fun aPageWidgetIsToldThePagesRealSize() {
        assertEquals(listOf(SizeDp(411f, 780f)), WidgetSizing.pageSizes(411.4f, 779.6f))
        assertEquals(listOf(SizeDp(1f, 1f)), WidgetSizing.pageSizes(0f, -3f))
    }
}
