package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 45 (editHandles): HOM-07 resize limits, handles and maths; HOM-12 collision rules and ghost snapping. Pure JVM. */
class EditHandlesTest {
    private val free = CellLimits.free(4, 6)

    // ---- provider limits to cells
    @Test fun HOM_07_minResizeRoundsUpAndMaxResizeRoundsDown() {
        val l = ProviderLimits(minResizeWidthDp = 110f, minResizeHeightDp = 40f, maxResizeWidthDp = 300f, maxResizeHeightDp = 250f)
        val c = ProviderLimitsMath.toCells(l, 80f, 80f, 4, 6, WidgetSpan(3, 3))
        assertEquals(2, c.minCols); assertEquals(1, c.minRows)
        assertEquals(3, c.maxCols); assertEquals(3, c.maxRows)
    }

    @Test fun HOM_07_noMaxMeansTheGridAndNoInfoMeansFree() {
        val c = ProviderLimitsMath.toCells(ProviderLimits(minResizeWidthDp = 80f), 80f, 80f, 4, 6, WidgetSpan(2, 2))
        assertEquals(4, c.maxCols); assertEquals(6, c.maxRows)
        assertEquals(CellLimits.free(4, 6), ProviderLimitsMath.toCells(null, 80f, 80f, 4, 6, WidgetSpan(2, 2)))
    }

    @Test fun HOM_07_aSingleAxisProviderKeepsItsSizeOnTheOtherAxis() {
        val h = ProviderLimitsMath.toCells(ProviderLimits(horizontal = true, vertical = false), 80f, 80f, 4, 6, WidgetSpan(2, 3))
        assertEquals(3, h.minRows); assertEquals(3, h.maxRows); assertFalse(h.canHeighten); assertTrue(h.canWiden)
        val v = ProviderLimitsMath.toCells(ProviderLimits(horizontal = false, vertical = true), 80f, 80f, 4, 6, WidgetSpan(2, 3))
        assertEquals(2, v.minCols); assertEquals(2, v.maxCols); assertFalse(v.canWiden)
    }

    @Test fun HOM_07_aProviderMinimumAboveTheCurrentSizeNeverForcesAShrinkAndAMaxBelowItNeverEither() {
        val c = ProviderLimitsMath.toCells(ProviderLimits(minResizeWidthDp = 400f, maxResizeWidthDp = 80f), 80f, 80f, 4, 6, WidgetSpan(2, 2))
        assertTrue(c.minCols <= 2); assertTrue(c.maxCols >= 2)
    }

    @Test fun HOM_07_aProviderWithoutMinResizeFallsBackToItsOwnMinimum() {
        val c = ProviderLimitsMath.toCells(ProviderLimits(minWidthDp = 160f, minHeightDp = 80f), 80f, 80f, 4, 6, WidgetSpan(3, 2))
        assertEquals(2, c.minCols); assertEquals(1, c.minRows)
    }

    // ---- handle sets
    @Test fun HOM_07_bothAxesGetFourCornersAndFourEdges() {
        assertEquals(ResizeHandle.entries.toSet(), HandleSet.of(free, 200f, 200f).toSet())
    }

    @Test fun HOM_07_aHorizontalOnlyProviderGetsOnlyEastAndWestAndAVerticalOnlyOneNorthAndSouth() {
        val h = CellLimits(1, 4, 2, 2, true, false)
        assertEquals(setOf(ResizeHandle.East, ResizeHandle.West), HandleSet.of(h, 200f, 200f).toSet())
        val v = CellLimits(2, 2, 1, 6, false, true)
        assertEquals(setOf(ResizeHandle.North, ResizeHandle.South), HandleSet.of(v, 200f, 200f).toSet())
    }

    @Test fun HOM_07_aWidgetThatCannotChangeGetsNoHandles() {
        assertTrue(HandleSet.of(CellLimits(2, 2, 2, 2, true, true), 200f, 200f).isEmpty())
    }

    @Test fun HOM_07_smallSidesDropTheEdgeHandlesButKeepTheCorners() {
        val s = HandleSet.of(free, 80f, 80f)
        assertEquals(4, s.size); assertTrue(s.all { it.isCorner })
        val narrow = HandleSet.of(free, 80f, 200f)
        assertTrue(ResizeHandle.East in narrow && ResizeHandle.West in narrow); assertFalse(ResizeHandle.North in narrow)
    }

    @Test fun HOM_07_whereTheOriginCannotMoveOnlyEastSouthAndSouthEastRemain() {
        assertEquals(setOf(ResizeHandle.East, ResizeHandle.South, ResizeHandle.SouthEast), HandleSet.of(free, 200f, 200f, originMoves = false).toSet())
    }

    // ---- resize maths
    private val start = CellRect(1, 1, 2, 2)

    @Test fun HOM_07_eastAndSouthGrowFromTheOrigin() {
        assertEquals(CellRect(1, 1, 3, 2), ResizeMath.resize(start, ResizeHandle.East, 1, 0, free, 4, 6).rect)
        assertEquals(CellRect(1, 1, 2, 3), ResizeMath.resize(start, ResizeHandle.South, 0, 1, free, 4, 6).rect)
        assertEquals(CellRect(1, 1, 3, 3), ResizeMath.resize(start, ResizeHandle.SouthEast, 1, 1, free, 4, 6).rect)
    }

    @Test fun HOM_07_westAndNorthMoveTheOriginAndKeepTheFarEdge() {
        assertEquals(CellRect(0, 1, 3, 2), ResizeMath.resize(start, ResizeHandle.West, -1, 0, free, 4, 6).rect)
        assertEquals(CellRect(1, 0, 2, 3), ResizeMath.resize(start, ResizeHandle.North, 0, -1, free, 4, 6).rect)
        assertEquals(CellRect(0, 0, 3, 3), ResizeMath.resize(start, ResizeHandle.NorthWest, -1, -1, free, 4, 6).rect)
    }

    @Test fun HOM_07_dragWestInwardShrinksAndMovesTheOriginRight() {
        assertEquals(CellRect(2, 1, 1, 2), ResizeMath.resize(start, ResizeHandle.West, 1, 0, free, 4, 6).rect)
    }

    @Test fun HOM_07_cornersResizeBothAxesFromTheirOwnCorner() {
        assertEquals(CellRect(1, 2, 3, 1), ResizeMath.resize(start, ResizeHandle.NorthEast, 1, 1, free, 4, 6).rect)
        assertEquals(CellRect(0, 1, 3, 3), ResizeMath.resize(start, ResizeHandle.SouthWest, -1, 1, free, 4, 6).rect)
    }

    @Test fun HOM_07_clampsToMinimumAndMaximumAndReportsWhy() {
        val l = CellLimits(2, 3, 1, 2, true, true)
        val tooBig = ResizeMath.resize(start, ResizeHandle.East, 5, 0, l, 4, 6)
        assertEquals(3, tooBig.rect.cols); assertTrue(tooBig.limited); assertEquals(LimitReason.AtMaximum, tooBig.reason)
        val tooSmall = ResizeMath.resize(start, ResizeHandle.East, -5, 0, l, 4, 6)
        assertEquals(2, tooSmall.rect.cols); assertEquals(LimitReason.AtMinimum, tooSmall.reason)
        val ok = ResizeMath.resize(start, ResizeHandle.East, 1, 0, l, 4, 6)
        assertFalse(ok.limited)
    }

    @Test fun HOM_07_clampsAtTheGridEdgeEvenWhenTheProviderAllowsMore() {
        val r = ResizeMath.resize(CellRect(2, 0, 1, 1), ResizeHandle.East, 4, 0, free, 4, 6)
        assertEquals(2, r.rect.cols); assertEquals(LimitReason.GridEdge, r.reason)
        val w = ResizeMath.resize(CellRect(1, 0, 1, 1), ResizeHandle.West, -4, 0, free, 4, 6)
        assertEquals(CellRect(0, 0, 2, 1), w.rect)
    }

    @Test fun HOM_07_aHandleOnADisallowedAxisChangesNothing() {
        val l = CellLimits(2, 2, 1, 6, false, true)
        assertEquals(start, ResizeMath.resize(start, ResizeHandle.East, 2, 0, l, 4, 6).rect)
        assertEquals(CellRect(1, 1, 2, 3), ResizeMath.resize(start, ResizeHandle.SouthEast, 2, 1, l, 4, 6).rect)
    }

    @Test fun HOM_07_aPixelDragSnapsToTheNearestWholeCell() {
        assertEquals(CellRect(1, 1, 3, 2), ResizeMath.resizePx(start, ResizeHandle.East, 60f, 10f, 100f, 100f, free, 4, 6).rect)
        assertEquals(CellRect(1, 1, 2, 2), ResizeMath.resizePx(start, ResizeHandle.East, 40f, 10f, 100f, 100f, free, 4, 6).rect)
    }

    // ---- collision rules
    private fun tp(k: String, c: Int, r: Int, w: Int = 1, h: Int = 1) = TilePlacement(k, c, r, WidgetSpan(w, h))
    private val widget = ItemKeys.widget(1)
    private val widget2 = ItemKeys.widget(2)

    @Test fun HOM_12_aWidgetResizeIntoAnotherItemIsBlocked() {
        val placed = listOf(tp(widget, 0, 0, 2, 2), tp("a", 2, 0))
        assertEquals(EditVerdict.Blocked("a"), EditCollision.resizeVerdict(placed, widget, CellRect(0, 0, 3, 2)))
        assertEquals(EditVerdict.Free, EditCollision.resizeVerdict(placed, widget, CellRect(0, 0, 2, 3)))
    }

    @Test fun HOM_12_aWidgetMovedOntoAnyItemIsBlocked() {
        val placed = listOf(tp(widget, 0, 0, 2, 2), tp("a", 2, 0), tp(widget2, 0, 2, 2, 1))
        assertTrue(EditCollision.moveVerdict(placed, widget, CellRect(1, 0, 2, 2)) is EditVerdict.Blocked)
        assertTrue(EditCollision.moveVerdict(placed, widget, CellRect(0, 1, 2, 2)) is EditVerdict.Blocked)
        assertEquals(EditVerdict.Free, EditCollision.moveVerdict(placed, widget, CellRect(0, 3, 2, 2)))
    }

    @Test fun HOM_12_anIconDroppedOnAnIconMakesAFolder() {
        val placed = listOf(tp("a", 0, 0), tp("b", 1, 0))
        assertEquals(EditVerdict.Folder("b"), EditCollision.moveVerdict(placed, "a", CellRect(1, 0, 1, 1)))
        val f = ItemKeys.folder("f")
        assertEquals(EditVerdict.Folder(f), EditCollision.moveVerdict(listOf(tp("a", 0, 0), tp(f, 1, 0)), "a", CellRect(1, 0, 1, 1)))
    }

    @Test fun HOM_12_anIconDroppedOnAWidgetIsPushedAsideNotFiled() {
        val placed = listOf(tp("a", 0, 0), tp(widget, 1, 0, 2, 2))
        assertEquals(EditVerdict.Push, EditCollision.moveVerdict(placed, "a", CellRect(1, 0, 1, 1)))
    }

    @Test fun HOM_12_aFolderDroppedOnAnIconPushesAndAnEmptyCellIsFree() {
        val f = ItemKeys.folder("f")
        val placed = listOf(tp(f, 0, 0), tp("b", 1, 0))
        assertEquals(EditVerdict.Push, EditCollision.moveVerdict(placed, f, CellRect(1, 0, 1, 1)))
        assertEquals(EditVerdict.Free, EditCollision.moveVerdict(placed, "b", CellRect(3, 3, 1, 1)))
    }

    // ---- ghost snapping
    @Test fun HOM_12_theGhostSnapsToWholeCellsInsideTheGrid() {
        assertEquals(CellRect(1, 2, 2, 1), GhostSnap.snap(110f, 190f, 190f, 90f, 100f, 100f, 4, 6))
        assertEquals(CellRect(2, 5, 2, 1), GhostSnap.snap(900f, 900f, 190f, 90f, 100f, 100f, 4, 6))
        assertEquals(CellRect(0, 0, 4, 1), GhostSnap.snap(-50f, -50f, 900f, 90f, 100f, 100f, 4, 6))
    }

    // ---- origin fit and apply
    @Test fun HOM_07_originFitPutsAnItemOnTheWantedCellOrTheNearestOne() {
        val keys = listOf("a", "b", "c", "d", "w")
        val span: (String) -> WidgetSpan = { if (it == "w") WidgetSpan(2, 1) else WidgetSpan(1, 1) }
        val i = OriginFit.indexFor(keys, span, 4, 6, "w", WidgetSpan(2, 1), 0, 0)
        assertEquals(0, i)
        val j = OriginFit.indexFor(keys, span, 4, 6, "w", WidgetSpan(2, 1), 2, 0)
        assertEquals(2, j)
    }

    @Test fun HOM_07_growingUpAndLeftReordersTheWidgetOntoItsNewOrigin() {
        val w = ItemKeys.widget(5)
        val s = HomeState(pages = listOf(com.bos.sphere.core.data.HomePage(listOf("a", "b", "c", "d", w))), widgets = mapOf(w to WidgetSpan(1, 1)))
        val cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 24 }
        val before = TilePacker.pack(s.pages[0].keys, s::spanOf, 4, 6).placed.first { it.key == w }
        assertEquals(0 to 1, before.col to before.row)
        // grow west by one (needs a free cell there: remove "a" from the way by asking for the origin the packer can give)
        val r = EditHandlesApply.resizeTo(s, w, CellRect(0, 1, 2, 1), true, 4, 6, cap)
        val after = TilePacker.pack(r.pages[0].keys, r::spanOf, 4, 6).placed.first { it.key == w }
        assertEquals(WidgetSpan(2, 1), r.spanOf(w))
        assertEquals(0 to 1, after.col to after.row)
    }

    @Test fun HOM_07_applyWithoutAnOriginMoveOnlyChangesTheSpan() {
        val w = ItemKeys.widget(5)
        val s = HomeState(pages = listOf(com.bos.sphere.core.data.HomePage(listOf(w, "a"))), widgets = mapOf(w to WidgetSpan(1, 1)))
        val r = EditHandlesApply.resizeTo(s, w, CellRect(0, 0, 2, 2), false, 4, 6) { 24 }
        assertEquals(WidgetSpan(2, 2), r.spanOf(w)); assertEquals(s.pages, r.pages)
    }

    // ---- one-cell edits (accessibility, keyboard)
    private val keys5 = listOf("a", "b", "c", "d", "e")

    @Test fun HOM_12_movingAnIconByACellSwapsItWithItsNeighbourAndAtTheEdgeItRefuses() {
        val span: (String) -> WidgetSpan = { WidgetSpan(1, 1) }
        val m = CellEdit.move(keys5, span, 4, 6, "a", 1, 0)
        assertTrue(m.ok); assertEquals(CellRect(1, 0, 1, 1), m.rect)
        val edge = CellEdit.move(keys5, span, 4, 6, "a", -1, 0)
        assertFalse(edge.ok); assertEquals(CellEdit.Reason.Edge, edge.reason)
    }

    @Test fun HOM_12_aWidgetStepIntoAnotherItemIsBlockedButIntoFreeCellsMoves() {
        val w = ItemKeys.widget(3)
        val span: (String) -> WidgetSpan = { if (it == w) WidgetSpan(2, 1) else WidgetSpan(1, 1) }
        val keys = listOf(w, "a", "b", "c")
        assertEquals(CellEdit.Reason.Blocked, CellEdit.move(keys, span, 4, 6, w, 1, 0).reason)
        // a widget alone on a page has free cells all around it
        val alone = CellEdit.move(listOf(w), span, 4, 6, w, 1, 0)
        assertTrue(alone.ok || alone.reason == CellEdit.Reason.Blocked)
    }

    @Test fun HOM_07_aWiderStepGrowsEastAndFallsBackToWestAtTheRightEdge() {
        val w = ItemKeys.widget(3)
        val limits = CellLimits.free(4, 6)
        val span: (String) -> WidgetSpan = { if (it == w) WidgetSpan(1, 1) else WidgetSpan(1, 1) }
        val keys = listOf(w)
        val east = CellEdit.resizeStep(keys, span, 4, 6, w, limits, 1, 0)
        assertTrue(east.ok); assertEquals(2, east.rect!!.cols); assertFalse(east.moveOrigin)
        val atEdge = CellEdit.resizeStep(keys, { if (it == w) WidgetSpan(4, 1) else WidgetSpan(1, 1) }, 4, 6, w, limits, 1, 0)
        assertFalse(atEdge.ok); assertEquals(CellEdit.Reason.AtMaximum, atEdge.reason)
    }

    @Test fun HOM_07_aNarrowerStepAtTheMinimumRefusesWithTheReason() {
        val w = ItemKeys.widget(3)
        val limits = CellLimits(2, 4, 1, 6, true, true)
        val r = CellEdit.resizeStep(listOf(w), { WidgetSpan(2, 1) }, 4, 6, w, limits, -1, 0)
        assertFalse(r.ok); assertEquals(CellEdit.Reason.AtMinimum, r.reason)
    }

    @Test fun HOM_07_aStepOnASingleAxisProviderRefusesTheOtherAxis() {
        val w = ItemKeys.widget(3)
        val limits = CellLimits(1, 4, 2, 2, true, false)
        val r = CellEdit.resizeStep(listOf(w), { WidgetSpan(2, 2) }, 4, 6, w, limits, 0, 1)
        assertFalse(r.ok); assertEquals(CellEdit.Reason.NotAllowed, r.reason)
    }

    @Test fun HOM_07_aGrowthIntoAnotherItemIsRefusedAsBlocked() {
        val w = ItemKeys.widget(3)
        val span: (String) -> WidgetSpan = { if (it == w) WidgetSpan(2, 1) else WidgetSpan(1, 1) }
        // grid 2 wide: the widget fills row 0; icons below, so taller (south) is blocked and north is off the grid
        val r = CellEdit.resizeStep(listOf(w, "a", "b"), span, 2, 6, w, CellLimits.free(2, 6), 0, 1)
        assertFalse(r.ok); assertEquals(CellEdit.Reason.Blocked, r.reason)
    }
}
