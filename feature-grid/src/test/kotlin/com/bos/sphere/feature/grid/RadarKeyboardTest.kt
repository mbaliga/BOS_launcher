package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchLayout
import com.bos.sphere.core.input.InputMode
import com.bos.sphere.core.input.MenuAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarKeyboardTest {
    private fun press(n: RadarNav, vararg keys: RKey, caret: CaretContext = CaretContext(), menu: Int = 4): RadarKeyResult {
        var r = RadarKeyResult(n)
        for (k in keys) r = RadarKeyboard.reduce(r.nav, k, caret) { menu }
        return r
    }

    @Test
    fun PAL_07_keyboardOperation() {
        val start = RadarNav(count = 3)
        // Down moves in, Up moves back, the ends hold.
        assertEquals(0, press(start, RKey.Down).nav.selected)
        assertEquals(2, press(start, RKey.Down, RKey.Down, RKey.Down, RKey.Down).nav.selected)
        assertEquals(0, press(start, RKey.Down, RKey.Down, RKey.Up, RKey.Up, RKey.Up).nav.selected)
        // Enter runs the selected result's primary action; without a selection it is left to the field (Go).
        assertEquals(RadarEffect.Run(1), press(start, RKey.Down, RKey.Down, RKey.Enter).effect)
        val bare = press(start, RKey.Enter)
        assertFalse(bare.consumed)
        // Tab and Right open the action list for the result; Tab with no selection takes the top result.
        assertEquals(RadarEffect.OpenMenu(1), press(start, RKey.Down, RKey.Down, RKey.Tab).effect)
        assertEquals(RadarEffect.OpenMenu(0), press(start, RKey.Tab).effect)
        val right = press(start, RKey.Down, RKey.Right)
        assertEquals(RadarEffect.OpenMenu(0), right.effect)
        assertTrue(right.nav.menuOpen)
        // In the list: Down/Up move, Enter runs the entry and closes, Esc and Left close without closing Radar.
        val inMenu = press(start, RKey.Down, RKey.Tab, RKey.Down, RKey.Down)
        assertEquals(2, inMenu.nav.menuIndex)
        val run = press(inMenu.nav, RKey.Enter)
        assertEquals(RadarEffect.RunMenu(0, 2), run.effect)
        assertFalse(run.nav.menuOpen)
        assertEquals(RadarEffect.CloseMenu, press(inMenu.nav, RKey.Escape).effect)
        assertEquals(RadarEffect.CloseMenu, press(inMenu.nav, RKey.Left).effect)
        // Esc closes the palette when no list is open.
        assertEquals(RadarEffect.Dismiss, press(start, RKey.Escape).effect)
        // Focus never leaves: Tab and Shift+Tab are consumed even with nothing to select, and in the menu.
        assertTrue(press(RadarNav(0), RKey.Tab).consumed)
        assertTrue(press(RadarNav(0), RKey.ShiftTab).consumed)
        assertTrue(press(inMenu.nav, RKey.Tab).consumed)
        assertTrue(press(inMenu.nav, RKey.Right).nav.menuOpen)
        // Down with no results does nothing and is still consumed.
        assertTrue(press(RadarNav(0), RKey.Down).consumed)
    }

    @Test
    fun PAL_07_sameModelInAllThreeLayouts() {
        // The reducer takes only a count, so Flat, Orbit and Filmstrip cannot differ; the layout decides only what the count is.
        for (layout in SearchLayout.entries) {
            val nav = RadarNav(count = 5)
            assertEquals("$layout", RadarEffect.Run(0), press(nav, RKey.Down, RKey.Enter).effect)
        }
    }

    @Test
    fun PAL_07_countShrinkKeepsSelectionValid() {
        val n = RadarNav(count = 5, selected = 4, menuOpen = true).withCount(2)
        assertEquals(1, n.selected)
        assertTrue(n.menuOpen)
        assertFalse(RadarNav(count = 5, selected = 4, menuOpen = true).withCount(0).menuOpen)
    }

    @Test
    fun PAL_07_actionListUsesSharedMenuModel() {
        val app = ResultMenus.forKind(ResultKind.App)
        assertEquals(MenuAction.Open, app.first().action)
        assertTrue(app.any { it.action == MenuAction.AppInfo })
        assertTrue(app.last().preview)
        val web = ResultMenus.forKind(ResultKind.Web)
        assertEquals(listOf("Open", "Preview"), web.map { it.label })
    }

    @Test
    fun PAL_19_wheelFromKeyboard() {
        val none = RadarNav(count = 3)
        // Empty query: Left/Right drive the wheel.
        assertEquals(RadarEffect.WheelMove(1), press(none, RKey.Right).effect)
        assertEquals(RadarEffect.WheelMove(-1), press(none, RKey.Left).effect)
        // Typed query: the caret keeps Left/Right until it reaches an end.
        val mid = CaretContext(queryBlank = false, atStart = false, atEnd = false)
        assertFalse(press(none, RKey.Right, caret = mid).consumed)
        assertFalse(press(none, RKey.Left, caret = mid).consumed)
        assertEquals(RadarEffect.WheelMove(1), press(none, RKey.Right, caret = mid.copy(atEnd = true)).effect)
        assertEquals(RadarEffect.WheelMove(-1), press(none, RKey.Left, caret = mid.copy(atStart = true)).effect)
        // The wheel steps through Everything, Web, Actions, Apps, Contacts, Files and stops at the ends.
        assertEquals(SearchCategory.Web, ScopeWheel.step(null, 1))
        assertEquals(SearchCategory.Files, ScopeWheel.step(SearchCategory.Contacts, 1))
        assertEquals(SearchCategory.Files, ScopeWheel.step(SearchCategory.Files, 1))
        assertNull(ScopeWheel.step(null, -1))
        assertEquals(listOf(null) + SearchCategory.entries, ScopeWheel.entries)
    }

    @Test
    fun PAL_18_keyboardDefaultsToFlatUnlessPinned() {
        for (layout in listOf(SearchLayout.Orbit, SearchLayout.Filmstrip)) {
            assertEquals(layout, RadarLayoutPolicy.effective(layout, false, InputMode.Touch))
            assertEquals(SearchLayout.Flat, RadarLayoutPolicy.effective(layout, false, InputMode.Keyboard))
            assertEquals(SearchLayout.Flat, RadarLayoutPolicy.effective(layout, false, InputMode.Pointer))
            assertEquals(layout, RadarLayoutPolicy.effective(layout, true, InputMode.Keyboard))
            assertEquals(layout, RadarLayoutPolicy.effective(layout, true, InputMode.Pointer))
        }
        assertEquals(SearchLayout.Flat, RadarLayoutPolicy.effective(SearchLayout.Flat, true, InputMode.Touch))
    }
}

class RadarInputModeTest {
    @Test
    fun PAL_18_onlyRealKeyboardsCountAsKeyboard() {
        // The soft keyboard's key events use the virtual device id (-1) and must not flip Radar to Flat.
        assertFalse(RadarKeyMap.isHardwareDevice(-1))
        assertFalse(RadarKeyMap.isHardwareDevice(0))
        assertTrue(RadarKeyMap.isHardwareDevice(5))
    }
}
