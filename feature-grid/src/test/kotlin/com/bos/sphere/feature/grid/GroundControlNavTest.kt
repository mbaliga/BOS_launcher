package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundControlNavTest {
    private val kinds = listOf(EditKind.Flip, EditKind.Adjust, EditKind.Activate)
    private fun run(start: GState, vararg keys: GKey, cats: Int = 4, items: Int = 3): GResult {
        var r = GResult(start)
        for (k in keys) r = GroundControlNav.reduce(r.state, k, cats, items) { kinds[it] }
        return r
    }

    @Test
    fun CFG_13_leftRightCategoryUpDownRows() {
        val s = GState()
        assertEquals(1, run(s, GKey.Right).state.category)
        assertEquals(GEffect.CategoryChanged, run(s, GKey.Right).effect)
        assertEquals(GEffect.Rejected, run(s, GKey.Left).effect) // already first
        assertEquals(3, run(s, GKey.Right, GKey.Right, GKey.Right, GKey.Right).state.category)
        assertEquals(0, run(s, GKey.Down).state.item)
        assertEquals(2, run(s, GKey.Down, GKey.Down, GKey.Down, GKey.Down).state.item)
        assertEquals(-1, run(s, GKey.Down, GKey.Up, GKey.Up).state.item) // back onto the bar
        // Changing category with the cursor on a row lands on the new category's first row.
        assertEquals(0, run(s, GKey.Down, GKey.Down, GKey.Right).state.item)
    }

    @Test
    fun CFG_13_enterEditsAndEscBacksOut() {
        val s = GState()
        // Toggle flips, action activates, slider enters edit mode.
        assertEquals(GEffect.Flip(0), run(s, GKey.Down, GKey.Enter).effect)
        assertEquals(GEffect.Activate(2), run(s, GKey.Down, GKey.Down, GKey.Down, GKey.Enter).effect)
        val editing = run(s, GKey.Down, GKey.Down, GKey.Enter)
        assertTrue(editing.state.editing)
        // While editing, Left/Right/Up/Down adjust and do not change category or row.
        assertEquals(GEffect.Adjust(1, +1), run(editing.state, GKey.Right).effect)
        assertEquals(GEffect.Adjust(1, -1), run(editing.state, GKey.Left).effect)
        assertEquals(1, run(editing.state, GKey.Right, GKey.Down).state.item)
        // Esc leaves editing first, then closes.
        val out = run(editing.state, GKey.Escape)
        assertFalse(out.state.editing)
        assertEquals(GEffect.None, out.effect)
        assertEquals(GEffect.Close, run(out.state, GKey.Escape).effect)
        // A page backs out before anything else.
        val pane = GState(item = 2, paneOpen = true)
        assertEquals(GEffect.ClosePane, run(pane, GKey.Escape).effect)
        assertFalse(run(pane, GKey.Escape).state.paneOpen)
        assertEquals(GEffect.None, run(pane, GKey.Right).effect)
        // Search closes before Ground Control does, and Left/Right leave the caret alone.
        val searching = GState(searching = true, item = 0)
        assertEquals(GEffect.CloseSearch, run(searching, GKey.Escape).effect)
        assertEquals(0, run(searching, GKey.Right).state.category)
    }

    @Test
    fun CFG_13_adjustingRowsByKeys() {
        val g = Glyphs.Search
        var slider = 0.5f
        val sl = SettingItem.Slider("s", g, slider, 0f..1f, "") { slider = it }
        assertTrue(GroundControlNav.adjust(sl, 1)); assertEquals(0.55f, slider, 0.001f)
        var count = 3
        val st = SettingItem.Stepper("n", g, count, 1..4) { count = it }
        assertTrue(GroundControlNav.adjust(st, 1)); assertEquals(4, count)
        assertFalse(GroundControlNav.adjust(SettingItem.Stepper("n", g, 4, 1..4) {}, 1)) // at the end
        var pick = "b"
        val ch = SettingItem.Choice("c", g, listOf("a", "b", "c"), pick, { it }) { pick = it }
        GroundControlNav.adjust(ch, 1); assertEquals("c", pick)
        var flag = false
        GroundControlNav.adjust(SettingItem.Toggle("t", g, flag) { flag = it }, 1); assertTrue(flag)
    }

    @Test
    fun CFG_13_rowKinds() {
        val g = Glyphs.Search
        assertEquals(EditKind.Flip, GroundControlNav.kindOf(SettingItem.Toggle("t", g, false) {}))
        assertEquals(EditKind.Adjust, GroundControlNav.kindOf(SettingItem.Stepper("s", g, 1, 0..3) {}))
        assertEquals(EditKind.Adjust, GroundControlNav.kindOf(SettingItem.Choice("c", g, listOf(1, 2), 1, { it.toString() }) {}))
        assertEquals(EditKind.Activate, GroundControlNav.kindOf(SettingItem.Choice("c", g, listOf(1, 2), 1, { it.toString() }, preview = {}) {}))
        assertEquals(EditKind.Activate, GroundControlNav.kindOf(SettingItem.Action("a", g) {}))
    }
}
