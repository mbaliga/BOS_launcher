package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoTest {
    @Test
    fun HOM_12_undoesLastTwentyChangesOnly() {
        val e = EditSession(0)
        e.enter()
        repeat(25) { i -> e.apply { it + 1 } }
        assertEquals(25, e.state)
        var undone = 0
        while (e.undo()) undone++
        assertEquals(20, undone)
        assertEquals(5, e.state)
    }

    @Test
    fun HOM_12_noOpChangeIsNotAStep_andRedoWorks() {
        val e = EditSession(listOf("a"))
        assertFalse(e.apply { it })
        e.apply { it + "b" }
        e.apply { it + "c" }
        e.undo()
        assertEquals(listOf("a", "b"), e.state)
        assertTrue(e.canRedo)
        e.redo()
        assertEquals(listOf("a", "b", "c"), e.state)
        e.undo(); e.apply { it + "z" }
        assertFalse(e.canRedo)
    }

    @Test
    fun HOM_12_undoRestoresWholeHomeState() {
        val cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 12 }
        val start = HomeState(pages = listOf(com.bos.sphere.core.data.HomePage(listOf("a", "b", "c"), id = 1L)))
        val e = EditSession(start)
        e.enter()
        e.apply { HomeEdit.createFolder(it, listOf("a", "b"), "f", "F", cap) }
        e.apply { HomeEdit.moveToDock(it, "c", 0, 4, cap) }
        assertEquals(listOf("c"), e.state.dock)
        e.undo(); e.undo()
        assertEquals(start, e.state)
    }

    @Test
    fun HOM_12_enteringEditModeStartsFreshHistory() {
        val e = EditSession(1)
        e.enter(); e.apply { 2 }; e.exit(); e.enter()
        assertFalse(e.canUndo)
    }
}
