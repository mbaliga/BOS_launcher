package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-12 (wave 45, editChrome): Cancel puts back the snapshot taken on entry, however many changes were made, and asks only when something changed. */
class EditCancelW45Test {
    private var clock = 1_000L
    private fun layout(vararg keys: String) = HomeLayout(listOf(HomePage(keys.toList(), id = 1)), seeded = true)
    private fun session(l: HomeLayout, pins: List<String> = listOf("x"), limit: Int = 20) = LayoutDockEditSession(l, DockPinsView("w", pins), limit = limit, now = { clock })
    private fun edit(s: LayoutDockEditSession, l: HomeLayout) { clock += 1_000; s.observe(l) }

    @Test fun HOM_12_nothingChangedMeansNoConfirmAndNothingToRestore() {
        val s = session(layout("a"))
        assertFalse(s.hasChanges)
        assertNull(s.cancel())
        assertFalse(s.active)
    }

    @Test fun HOM_12_cancelRestoresTheEntrySnapshot() {
        val start = layout("a", "b")
        val s = session(start)
        edit(s, layout("a", "b", "c")); edit(s, layout("b", "c"))
        assertTrue(s.hasChanges)
        val r = s.cancel()!!
        assertEquals(start, r.layout)
        assertNull("the rail did not change", r.pins)
        assertFalse(s.active); assertFalse(s.canUndo)
    }

    @Test fun HOM_12_cancelReachesBackPastTheTwentyChangeUndoLimit() {
        val start = layout("a")
        val s = session(start, limit = 3)
        for (i in 1..8) edit(s, layout("a", *(1..i).map { "k$it" }.toTypedArray()))
        assertEquals("the stack holds only the last 3", 3, s.undoDepth)
        assertEquals(start, s.cancel()!!.layout)
    }

    @Test fun HOM_12_changesThatWereUndoneAreNoChange() {
        val start = layout("a")
        val s = session(start)
        edit(s, layout("a", "b")); s.undo()
        assertFalse("back at the snapshot: Cancel does not ask", s.hasChanges)
    }

    @Test fun HOM_12_cancelRestoresTheRailToo() {
        val s = session(layout("a"), pins = listOf("x"))
        clock += 1_000; s.observePins(DockPinsView("w", listOf("x", "y")))
        assertTrue(s.hasChanges)
        val r = s.cancel()!!
        assertEquals("w", r.workspaceId); assertEquals(listOf("x"), r.pins)
    }

    @Test fun HOM_12_aRailOfAnotherWorkspaceIsLeftAlone() {
        val s = session(layout("a"), pins = listOf("x"))
        edit(s, layout("a", "b"))
        s.observePins(DockPinsView("other", listOf("q"))) // a workspace switch is not an edit
        val r = s.cancel()!!
        assertNull(r.pins); assertNull(r.workspaceId)
    }

    @Test fun HOM_12_aSizeClassSwapMovesTheSnapshot() {
        val s = session(layout("a"))
        edit(s, layout("a", "b"))
        val landscape = layout("z")
        s.rebase(landscape, DockPinsView("w", listOf("x")))
        assertFalse("the swap is not part of the session", s.hasChanges)
        edit(s, layout("z", "y"))
        assertEquals(landscape, s.cancel()!!.layout)
    }

    @Test fun HOM_12_redoDepthCountsWhatWasUndone() {
        val s = session(layout("a"))
        edit(s, layout("a", "b")); edit(s, layout("a", "b", "c"))
        assertEquals(0, s.redoDepth)
        s.undo(); s.undo()
        assertEquals(2, s.redoDepth)
    }
}
