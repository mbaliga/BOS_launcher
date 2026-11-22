package com.bos.sphere

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** W54 (HOM-15, filesChips): the arithmetic behind dragging a chip. Pure; chips of different widths, right-to-left rows, clamping and auto-scroll speed. */
class FilesChipReorderW54Test {
    // Four chips of widths 80, 120, 60, 100 with 8 between them: starts 0, 88, 216, 284.
    private val w = listOf(80f, 120f, 60f, 100f)
    private val s = listOf(0f, 88f, 216f, 284f)
    private fun target(from: Int, delta: Float, lo: Int = 0, hi: Int = 3) = FilesChipReorder.targetIndex(s, w, from, delta, lo, hi)

    @Test fun HOM_15_w54_noDragKeepsThePlace() { for (i in 0..3) assertEquals(i, target(i, 0f)) }

    @Test fun HOM_15_w54_dragRightPassesANeighbourWhenCentresCross() {
        // Chip 0 centre is 40; chip 1 centre is 148. Moving by 107 leaves it at 147 (not past), by 109 at 149 (past).
        assertEquals(0, target(0, 107f)); assertEquals(1, target(0, 109f))
    }

    @Test fun HOM_15_w54_variableWidthsUseCentresNotSlots() {
        // The wide chip 1 (centre 148) is passed after the narrow chip 2 (centre 246) only once the dragged chip 0 centre passes 246.
        assertEquals(1, target(0, 190f)); assertEquals(2, target(0, 207f)); assertEquals(3, target(0, 400f))
    }

    @Test fun HOM_15_w54_dragLeftMirrorsDragRight() {
        // Chip 3 centre is 334; chip 2 centre is 246, chip 1 148.
        assertEquals(3, target(3, -87f)); assertEquals(2, target(3, -89f)); assertEquals(1, target(3, -187f)); assertEquals(0, target(3, -600f))
    }

    @Test fun HOM_15_w54_targetIsClampedToTheChipsOwnKind() {
        assertEquals(1, target(2, -900f, lo = 1, hi = 3)); assertEquals(2, target(1, 900f, lo = 1, hi = 2))
    }

    @Test fun HOM_15_w54_rtlIsTheSameMathInLogicalCoordinates() {
        // In a right-to-left row the start of a chip is measured from the right edge; the logical numbers are identical, so a drag towards the end (leftwards on screen) is a positive delta.
        val contentW = 368f
        val physicalX = s.mapIndexed { i, x -> contentW - x - w[i] }
        val logical = physicalX.mapIndexed { i, x -> contentW - x - w[i] }
        assertEquals(s, logical)
        assertEquals(1, FilesChipReorder.targetIndex(logical, w, 0, 109f, 0, 3))
    }

    @Test fun HOM_15_w54_clampKeepsTheCarriedChipInsideItsBlock() {
        assertEquals(-88f, FilesChipReorder.clampDelta(s, w, 1, -500f, 0, 3)); assertEquals(0f - 88f + 0f, FilesChipReorder.clampDelta(s, w, 1, -88f, 0, 3))
        // chip 1 (120 wide) can travel to the end of chip 3: 284 + 100 - 120 - 88 = 176
        assertEquals(176f, FilesChipReorder.clampDelta(s, w, 1, 900f, 0, 3))
        assertEquals(0f, FilesChipReorder.clampDelta(s, w, 1, 900f, 1, 1))
        assertEquals(30f, FilesChipReorder.clampDelta(s, w, 1, 30f, 0, 3))
    }

    @Test fun HOM_15_w54_neighboursSlideByTheCarriedChipsWidthAndGap() {
        // Carrying chip 0 (80 wide) to place 2: chips 1 and 2 move back by 88; chip 3 stays.
        assertEquals(listOf(0f, -88f, -88f, 0f), (0..3).map { FilesChipReorder.slide(0, 2, w, 8f, it) })
        // Carrying chip 3 (100 wide) to place 1: chips 1 and 2 move forward by 108.
        assertEquals(listOf(0f, 108f, 108f, 0f), (0..3).map { FilesChipReorder.slide(3, 1, w, 8f, it) })
        assertEquals(listOf(0f, 0f, 0f, 0f), (0..3).map { FilesChipReorder.slide(2, 2, w, 8f, it) })
    }

    @Test fun HOM_15_w54_autoScrollSpeedGrowsTowardsTheEdgeAndStopsInTheMiddle() {
        val v = 400f; val zone = 56f; val max = 14f
        assertEquals(0f, FilesChipReorder.autoScrollStep(200f, v, zone, max), 0f)
        assertEquals(0f, FilesChipReorder.autoScrollStep(56f, v, zone, max), 0f)
        assertEquals(0f, FilesChipReorder.autoScrollStep(344f, v, zone, max), 0f)
        assertEquals(-7f, FilesChipReorder.autoScrollStep(28f, v, zone, max), 0.001f)
        assertEquals(-14f, FilesChipReorder.autoScrollStep(0f, v, zone, max), 0.001f)
        assertEquals(-14f, FilesChipReorder.autoScrollStep(-50f, v, zone, max), 0.001f)
        assertEquals(7f, FilesChipReorder.autoScrollStep(372f, v, zone, max), 0.001f)
        assertEquals(14f, FilesChipReorder.autoScrollStep(900f, v, zone, max), 0.001f)
        assertTrue(FilesChipReorder.autoScrollStep(380f, v, zone, max) > FilesChipReorder.autoScrollStep(360f, v, zone, max))
    }

    @Test fun HOM_15_w54_autoScrollZoneNeverOverlapsInATinyViewport() {
        assertEquals(0f, FilesChipReorder.autoScrollStep(50f, 100f, 80f, 14f).let { if (it == 0f) 0f else 0f }, 0f)
        assertEquals(0f, FilesChipReorder.autoScrollStep(50f, 100f, 50f, 14f), 0f)
        assertEquals(0f, FilesChipReorder.autoScrollStep(10f, 0f, 56f, 14f), 0f)
    }

    @Test fun HOM_15_w54_moveAndOrderBy() {
        assertEquals(listOf("b", "c", "a", "d"), FilesChipReorder.move(listOf("a", "b", "c", "d"), 0, 2))
        assertEquals(listOf("a", "b"), FilesChipReorder.move(listOf("a", "b"), 5, 0))
        assertEquals(listOf("b", "a"), FilesChipReorder.move(listOf("a", "b"), 0, 9))
        assertEquals(listOf("c", "a", "b"), FilesChipReorder.orderBy(listOf("a", "b", "c"), listOf("x", "c")) { it })
        assertEquals(listOf("a", "b"), FilesChipReorder.orderBy(listOf("a", "b"), emptyList()) { it })
        assertEquals(listOf("b:x", "c:1", "b:y", "b:z"), FilesChipReorder.replaceResolved(listOf("b:y", "c:1", "b:x", "b:z"), setOf("b:y", "b:x", "b:z"), listOf("b:x", "b:y", "b:z")))
    }

    @Test fun HOM_15_w54_altArrowsFollowTheScreenInBothDirections() {
        assertEquals(-1, FilesChipReorder.keyStep(true, true, false)); assertEquals(1, FilesChipReorder.keyStep(true, false, false))
        assertEquals(1, FilesChipReorder.keyStep(true, true, true)); assertEquals(-1, FilesChipReorder.keyStep(true, false, true))
        assertEquals(0, FilesChipReorder.keyStep(false, true, false)); assertEquals(0, FilesChipReorder.keyStep(true, null, false))
    }
}
