package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutDockUndoTest {
    private var clock = 1_000L
    private fun layout(vararg keys: String) = HomeLayout(listOf(HomePage(keys.toList(), id = 1)), seeded = true)
    private fun pins(vararg p: String) = DockPinsView("w", p.toList())
    private fun session(l: HomeLayout = layout("a", "b", "c"), p: DockPinsView = pins("x")) =
        LayoutDockEditSession(l, p, now = { clock })

    @Test
    fun HOM_12_moveToDockUndoesPagePositionAndPinInOneStep() {
        val s = session()
        s.observe(layout("a", "c"))          // item b leaves the page ...
        clock += 50
        s.observePins(pins("x", "b"))        // ... and is pinned in the same gesture
        assertEquals(1, s.undoDepth)
        val r = s.undo()!!
        assertEquals(layout("a", "b", "c"), r.layout)
        assertEquals(listOf("x"), r.pins)
        val again = s.redo()!!
        assertEquals(layout("a", "c"), again.layout)
        assertEquals(listOf("x", "b"), again.pins)
    }

    @Test
    fun HOM_12_pinFirstThenLayoutAlsoJoinsOneEntry() {
        val s = session()
        s.observePins(pins("x", "b")); clock += 10; s.observe(layout("a", "c"))
        assertEquals(1, s.undoDepth)
        val r = s.undo()!!
        assertEquals(layout("a", "b", "c"), r.layout); assertEquals(listOf("x"), r.pins)
    }

    @Test
    fun HOM_12_moveOutOfDockUnpinAndDockReorderUndo() {
        val s = session(p = pins("x", "y", "z"))
        s.observePins(pins("z", "x", "y")) // reorder
        clock += 1000
        assertEquals(listOf("x", "y", "z"), s.undo()!!.pins)
        clock += 1000
        s.observePins(pins("x", "z")); clock += 1000 // unpin y
        s.observe(layout("a", "b", "c", "y"))        // a later, separate move out of the dock onto the page
        assertEquals(2, s.undoDepth)
        val r = s.undo()!!
        assertNull(r.pins) // a layout-only edit never touches the rail
        assertEquals(layout("a", "b", "c"), r.layout)
        assertEquals(listOf("x", "y", "z"), s.undo()!!.pins)
    }

    @Test
    fun HOM_12_layoutOnlyEditNeverRevertsARailEditMadeBetweenTwoHomeEdits() {
        val s = session()
        s.observe(layout("a", "c")); s.observePins(pins("x", "b")) // edit A: move b to the dock
        clock += 5_000
        s.observePins(pins("x", "b", "rail")) // the user pins something from the rail itself
        clock += 5_000
        s.observe(layout("c"))                 // edit B: layout only
        val b = s.undo()!!
        assertNull(b.pins)                     // undoing B leaves the rail alone
        val rail = s.undo()!!                  // the rail edit is its own entry, undone in order
        assertEquals(listOf("x", "b"), rail.pins)
        assertEquals(listOf("x"), s.undo()!!.pins)
    }

    @Test
    fun HOM_12_undoOfAnEarlierEditMergesInsteadOfOverwritingALaterRailChange() {
        // merge rule on its own: the rail has changed since the edit that pinned b
        assertEquals(listOf("x", "rail"), DockPinMerge.revert(listOf("x", "b", "rail"), listOf("x"), listOf("x", "b")))
        // an unpin is put back where it was
        assertEquals(listOf("x", "y", "z", "n"), DockPinMerge.revert(listOf("x", "z", "n"), listOf("x", "y", "z"), listOf("x", "z")))
        // untouched rail: restored exactly (reorder included)
        assertEquals(listOf("x", "y"), DockPinMerge.revert(listOf("y", "x"), listOf("x", "y"), listOf("y", "x")))
    }

    @Test
    fun HOM_12_workspaceSwitchIsNotAnEditAndItsPinsAreNeverWrittenIntoAnother() {
        val s = session()
        s.observe(layout("a", "c")); s.observePins(pins("x", "b"))
        assertFalse(s.observePins(DockPinsView("other", listOf("q"))))
        val r = s.undo()!!
        assertNull(r.pins); assertNull(r.workspaceId) // the pin change belongs to workspace w, not the active one
        assertEquals(layout("a", "b", "c"), r.layout)
    }

    @Test
    fun HOM_12_depthIsTwentyAcrossLayoutAndPinEntries() {
        val s = session()
        for (i in 1..25) { clock += 1000; if (i % 2 == 0) s.observe(layout("a", "n$i")) else s.observePins(pins("x", "p$i")) }
        assertEquals(20, s.undoDepth)
        var n = 0; while (s.undo() != null) n++
        assertEquals(20, n)
        assertFalse(s.canUndo)
    }

    @Test
    fun HOM_12_scrollingStylesHaveNoPageArrangementSoUndoRestoresOnlyTheRail() {
        // list, XMB, DSi and continuous styles: the layout never changes, edits are pin-only
        val s = session()
        s.observePins(pins("x", "b")); clock += 1000
        assertFalse(s.observe(layout("a", "b", "c"))) // same layout echo is not an edit
        val r = s.undo()!!
        assertEquals(layout("a", "b", "c"), r.layout)
        assertEquals(listOf("x"), r.pins)
        assertFalse(s.canUndo); assertTrue(s.canRedo)
    }

    @Test
    fun HOM_12_echoOfOurOwnPinWriteIsNotANewEntry() {
        val s = session()
        s.observePins(pins("x", "b")); clock += 1000
        val r = s.undo()!!
        assertFalse(s.observePins(DockPinsView("w", r.pins!!)))
        assertTrue(s.canRedo)
    }
}
