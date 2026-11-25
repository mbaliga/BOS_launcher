package com.bos.sphere.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class EquatorMergeTest {

    private val apps = listOf("a", "b", "c", "d")

    private fun merge(placements: List<WidgetPlacement>, appList: List<String> = apps) =
        EquatorMerge.merge(appList, placements) { "W${it.appWidgetId}" }

    @Test
    fun noWidgetsLeavesAppsAsTheyAre() {
        assertEquals(apps, merge(emptyList()))
    }

    @Test
    fun widgetsTakeTheirSlotIndex() {
        assertEquals(
            listOf("W1", "a", "b", "W2", "c", "d"),
            merge(listOf(placement(2, slot = 3), placement(1, slot = 0))),
        )
    }

    @Test
    fun tiesKeepIdOrderAndNeverOverlap() {
        assertEquals(
            listOf("a", "W3", "W4", "b", "c", "d"),
            merge(listOf(placement(4, slot = 1), placement(3, slot = 1))),
        )
    }

    @Test
    fun slotsPastTheEndClampToTheEnd() {
        assertEquals(listOf("a", "b", "W1", "W2"), merge(listOf(placement(2, slot = 50), placement(1, slot = 9)), listOf("a", "b")))
    }

    @Test
    fun hubWidgetsAreNotOnTheEquator() {
        assertEquals(apps, merge(listOf(placement(1, WidgetSurface.Hub, slot = 0))))
    }

    @Test
    fun widgetsAloneFormTheEquatorWhenThereAreNoApps() {
        assertEquals(listOf("W1", "W2"), merge(listOf(placement(1, slot = 4), placement(2, slot = 7)), emptyList()))
    }

    @Test
    fun insertingLandsAtTheRequestedIndexAndKeepsOthersInOrder() {
        var list = listOf(placement(1, slot = 1), placement(2, slot = 3))
        assertEquals(listOf("a", "W1", "b", "W2", "c", "d"), merge(list))
        list = PlacementEdits.insertOnEquator(list, placement(9), index = 3)
        assertEquals(listOf("a", "W1", "b", "W9", "W2", "c", "d"), merge(list))
        list = PlacementEdits.insertOnEquator(list, placement(8), index = 0)
        assertEquals(listOf("W8", "a", "W1", "b", "W9", "W2", "c", "d"), merge(list))
    }

    @Test
    fun insertingBeforeWidgetsParkedPastTheEnd() {
        // Apps were uninstalled; widget 1 (slot 10) now sits at the end of a two-app list.
        var list = listOf(placement(1, slot = 10))
        list = PlacementEdits.insertOnEquator(list, placement(2), index = 2)
        assertEquals(listOf("a", "b", "W2", "W1"), merge(list, listOf("a", "b")))
    }

    @Test
    fun removingClosesTheGapSoNeighboursStay() {
        var list = listOf(placement(1, slot = 1), placement(2, slot = 3))
        list = PlacementEdits.remove(list, 1)
        assertEquals(listOf("a", "b", "W2", "c", "d"), merge(list))
    }

    @Test
    fun movingBetweenEquatorAndHub() {
        var list = listOf(placement(1, slot = 1), placement(2, WidgetSurface.Hub, slot = 0))
        list = PlacementEdits.moveToHub(list, 1)
        assertEquals(apps, merge(list))
        assertEquals(listOf(2, 1), PlacementEdits.hubStack(list).map { it.appWidgetId })

        list = PlacementEdits.moveToEquator(list, 2, index = 2)
        assertEquals(listOf("a", "b", "W2", "c", "d"), merge(list))
        assertEquals(listOf(1), PlacementEdits.hubStack(list).map { it.appWidgetId })
    }

    @Test
    fun hubStackOrdersBySlotThenId() {
        val list = PlacementEdits.appendToHub(
            listOf(placement(5, WidgetSurface.Hub, slot = 2), placement(3, WidgetSurface.Hub, slot = 0)),
            placement(9),
        )
        assertEquals(listOf(3, 5, 9), PlacementEdits.hubStack(list).map { it.appWidgetId })
        assertEquals(3, list.first { it.appWidgetId == 9 }.slot)
    }

    @Test
    fun hubSizeIsKeptAcrossMoves() {
        var list = listOf(placement(1, WidgetSurface.Hub, hubSize = HubSize.Small))
        list = PlacementEdits.setHubSize(list, 1, HubSize.Large)
        list = PlacementEdits.moveToEquator(list, 1, 0)
        list = PlacementEdits.moveToHub(list, 1)
        assertEquals(HubSize.Large, list.single().hubSize)
    }
}
