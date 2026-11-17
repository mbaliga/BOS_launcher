package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-12 (wave 13 audit): rotating or folding during edit mode swaps the arrangement; that is not an undoable edit. */
class EditRebaseW13Test {
    private var clock = 1_000L
    private fun layout(vararg keys: String) = HomeLayout(listOf(HomePage(keys.toList(), id = 1)), seeded = true)
    private fun session(l: HomeLayout) = LayoutDockEditSession(l, DockPinsView("w", listOf("x")), now = { clock })

    @Test fun HOM_12_aSizeClassSwapEndsTheHistoryAndItsEchoIsNotAnEdit() {
        val portrait = layout("a", "b")
        val s = session(portrait)
        s.observe(layout("a", "b", "c")); clock += 1_000
        assertEquals(1, s.undoDepth)
        val landscape = layout("z")
        s.rebase(landscape, DockPinsView("w", listOf("x")))   // the binding swapped the other class's arrangement in
        assertEquals(0, s.undoDepth); assertFalse(s.canUndo); assertFalse(s.canRedo)
        assertFalse("the store's echo of the swapped layout is not an edit", s.observe(landscape))
        assertNull(s.undo())                                   // undo can never write the portrait layout into the landscape class
        clock += 1_000
        assertTrue(s.observe(layout("z", "y")))                // edits after the swap undo back to the swapped layout
        assertEquals(landscape, s.undo()!!.layout)
    }

    @Test fun HOM_12_aRebaseOnAnEndedSessionDoesNothing() {
        val s = session(layout("a")); s.exit()
        s.rebase(layout("b"), DockPinsView("w", emptyList()))
        assertFalse(s.canUndo)
    }
}
