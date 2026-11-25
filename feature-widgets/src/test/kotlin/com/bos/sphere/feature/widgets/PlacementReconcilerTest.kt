package com.bos.sphere.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementReconcilerTest {

    @Test
    fun dropsPlacementsTheHostForgotAndDeletesOrphanIds() {
        val placements = listOf(placement(1), placement(2), placement(3))
        val plan = PlacementReconciler.plan(
            placements = placements,
            hostIds = listOf(1, 3, 40, 41),
            pendingIds = emptySet(),
        ) { true }
        assertEquals(listOf(placement(1), placement(3)), plan.keep)
        assertEquals(listOf(placement(2)), plan.dropped)
        assertEquals(listOf(40, 41), plan.idsToDelete)
        assertTrue(plan.unavailable.isEmpty())
    }

    @Test
    fun neverDeletesAnIdABindFlowIsWaitingOn() {
        val plan = PlacementReconciler.plan(listOf(placement(1)), listOf(1, 9), pendingIds = setOf(9)) { true }
        assertTrue(plan.idsToDelete.isEmpty())
    }

    @Test
    fun keepsPlacementsWithoutProviderInfoButMarksThemUnavailable() {
        val plan = PlacementReconciler.plan(
            listOf(placement(1), placement(2)),
            hostIds = listOf(1, 2),
            pendingIds = emptySet(),
        ) { it != 2 }
        assertEquals(2, plan.keep.size)
        assertEquals(setOf(2), plan.unavailable)
        assertTrue(plan.idsToDelete.isEmpty())
    }

    @Test
    fun emptyHostDropsEverything() {
        val plan = PlacementReconciler.plan(listOf(placement(1)), emptyList(), emptySet()) { true }
        assertEquals(listOf(placement(1)), plan.dropped)
        assertTrue(plan.keep.isEmpty())
    }

    @Test
    fun keepsUnreadableLinesIdsAsReserved() {
        val plan = PlacementReconciler.plan(
            listOf(placement(1)),
            hostIds = listOf(1, 7, 8),
            pendingIds = emptySet(),
            reservedIds = setOf(7),
        ) { true }
        assertEquals(listOf(8), plan.idsToDelete)
    }

    @Test
    fun markingRestoredPlacementsNegatesTheirIds() {
        val marked = PlacementReconciler.markRestorePending(listOf(placement(5, slot = 2), placement(9)))
        assertEquals(listOf(-5, -9), marked.map { it.appWidgetId })
        assertTrue(marked.all { it.isRestorePending })
        assertEquals(listOf(5, 9), marked.map { it.restoredFromId })
        assertEquals(2, marked.first().slot)
        // Idempotent: already-pending placements stay as they are.
        assertEquals(marked, PlacementReconciler.markRestorePending(marked))
    }

    @Test
    fun reconcileBeforeTheRestoreBroadcastKeepsPlacementsAndIds() {
        // Restored prefs hold the old device's ids (marked pending); the host already holds the
        // restored widgets under new ids 1 and 2. Nothing may be dropped or deleted yet.
        val restored = PlacementReconciler.markRestorePending(listOf(placement(5), placement(9)))
        val plan = PlacementReconciler.plan(
            restored,
            hostIds = listOf(1, 2),
            pendingIds = emptySet(),
            restoreWindowOpen = true,
        ) { true }
        assertEquals(restored, plan.keep)
        assertTrue(plan.dropped.isEmpty())
        assertTrue(plan.idsToDelete.isEmpty())
        assertEquals(setOf(-5, -9), plan.restoring)
        assertTrue(plan.unavailable.isEmpty())
    }

    @Test
    fun localPlacementsAreStillReconciledWhileARestoreIsPending() {
        val list = listOf(placement(3), placement(4), placement(-5))
        val plan = PlacementReconciler.plan(list, hostIds = listOf(3, 40), pendingIds = emptySet(), restoreWindowOpen = true) {
            it != 3
        }
        assertEquals(listOf(placement(3), placement(-5)), plan.keep)
        assertEquals(listOf(placement(4)), plan.dropped)
        assertEquals(setOf(3), plan.unavailable)
        // 40 might be the restored widget of -5: held until the broadcast says.
        assertTrue(plan.idsToDelete.isEmpty())
    }

    @Test
    fun restorePlacementsAreDroppedOnceTheirWindowCloses() {
        val plan = PlacementReconciler.plan(
            listOf(placement(1), placement(-5)),
            hostIds = listOf(1, 40),
            pendingIds = emptySet(),
            restoreWindowOpen = false,
        ) { true }
        assertEquals(listOf(placement(1)), plan.keep)
        assertEquals(listOf(placement(-5)), plan.dropped)
        assertEquals(listOf(40), plan.idsToDelete)
        assertTrue(plan.restoring.isEmpty())
    }

    @Test
    fun restoreRemapsAllIdsAtOnce() {
        // old 5 -> new 9 and old 9 -> new 12 must not chain 5 -> 9 -> 12.
        val result = PlacementReconciler.remapRestored(
            listOf(placement(-5, slot = 0), placement(-9, slot = 1)),
            oldIds = intArrayOf(5, 9),
            newIds = intArrayOf(9, 12),
        )
        assertEquals(listOf(9, 12), result.placements.map { it.appWidgetId })
        assertEquals(listOf(0, 1), result.placements.map { it.slot })
        assertEquals(2, result.remapped)
    }

    @Test
    fun restoreLeavesPlacementsTheBroadcastDidNotNameWaiting() {
        val result = PlacementReconciler.remapRestored(
            listOf(placement(-5), placement(-9)),
            oldIds = intArrayOf(5),
            newIds = intArrayOf(9),
        )
        assertEquals(listOf(9, -9), result.placements.map { it.appWidgetId })
        assertEquals("com.example.app-5", result.placements.first().providerPackage)
        assertEquals(1, result.remapped)
    }

    @Test
    fun aSecondPartialBroadcastLeavesEarlierRemapsIntact() {
        val first = PlacementReconciler.remapRestored(
            listOf(placement(-5), placement(-6), placement(-7)),
            oldIds = intArrayOf(5, 6),
            newIds = intArrayOf(20, 21),
        ).placements
        // The system only sends records it hasn't sent yet: the second broadcast names 7 alone,
        // and must not touch 20 / 21 (which are new ids now, not old ones).
        val second = PlacementReconciler.remapRestored(first, oldIds = intArrayOf(7), newIds = intArrayOf(22))
        assertEquals(listOf(20, 21, 22), second.placements.map { it.appWidgetId })
        assertEquals(1, second.remapped)
    }

    @Test
    fun restoreNeverTouchesLocalPlacements() {
        // A local widget whose id equals an old id in the broadcast stays as it is.
        val list = listOf(placement(5), placement(-6))
        val result = PlacementReconciler.remapRestored(list, intArrayOf(5, 6), intArrayOf(30, 5))
        // -6 -> 5 would collide with the local 5, so it keeps waiting.
        assertEquals(list, result.placements)
        assertEquals(0, result.remapped)
    }

    @Test
    fun restoreWithMismatchedArraysChangesNothing() {
        val list = listOf(placement(-5))
        val result = PlacementReconciler.remapRestored(list, intArrayOf(5, 6), intArrayOf(7))
        assertEquals(list, result.placements)
        assertEquals(0, result.remapped)
    }

    @Test
    fun restoreWindowRestartsWhenItsStartLiesInTheFuture() {
        val day = 24L * 60 * 60 * 1000
        assertEquals(1_000L, RestoreWindow.start(recorded = 0L, now = 1_000L))
        assertEquals(500L, RestoreWindow.start(recorded = 500L, now = 1_000L))
        // Recorded with a clock that was ahead (early setup): restart now, never wait forever.
        assertEquals(1_000L, RestoreWindow.start(recorded = 9 * day, now = 1_000L))
        assertTrue(RestoreWindow.isOpen(start = 0L, now = 3 * day - 1))
        assertFalse(RestoreWindow.isOpen(start = 0L, now = 3 * day))
    }
}
