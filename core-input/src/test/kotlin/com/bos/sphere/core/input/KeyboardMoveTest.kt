package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardMoveTest {
    private fun t(i: Int, n: Int, c: Int, d: FocusDir, prev: Boolean = false, next: Boolean = false) = KeyboardMove.target(i, n, c, d, prev, next)

    @Test fun INP_09_arrows_move_an_item_one_place_in_the_grid() {
        assertEquals(KeyboardMove.Target(0, 5), t(4, 12, 4, FocusDir.Right))
        assertEquals(KeyboardMove.Target(0, 3), t(4, 12, 4, FocusDir.Left))
        assertEquals(KeyboardMove.Target(0, 0), t(4, 12, 4, FocusDir.Up))
        assertEquals(KeyboardMove.Target(0, 8), t(4, 12, 4, FocusDir.Down))
    }

    @Test fun INP_09_edges_cross_pages_or_stop() {
        assertNull(t(0, 12, 4, FocusDir.Left))
        assertEquals(KeyboardMove.Target(-1, KeyboardMove.END), t(0, 12, 4, FocusDir.Left, prev = true))
        assertNull(t(11, 12, 4, FocusDir.Right))
        assertEquals(KeyboardMove.Target(1, 0), t(11, 12, 4, FocusDir.Right, next = true))
        assertNull(t(2, 12, 4, FocusDir.Up))
    }

    @Test fun INP_09_down_into_a_short_last_row_lands_on_its_last_item() {
        assertEquals(KeyboardMove.Target(0, 9), t(6, 10, 4, FocusDir.Down))
        assertNull(t(9, 10, 4, FocusDir.Down))
    }

    @Test fun INP_05_edit_mode_toggles_with_its_command_and_leaves_on_dismiss() {
        assertTrue(EditModeState.next(false, LauncherCommand.EditMode))
        assertFalse(EditModeState.next(true, LauncherCommand.EditMode))
        assertFalse(EditModeState.next(true, LauncherCommand.Dismiss))
        assertTrue(EditModeState.next(true, LauncherCommand.OpenPalette))
    }
}
