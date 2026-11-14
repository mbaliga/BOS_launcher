package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.DefaultGestures
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.input.DefaultShortcuts
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.core.input.MenuContext
import com.bos.sphere.core.input.ShortcutRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputAuditTest {
    @Test fun INP_07_every_launcher_action_has_a_touch_path() {
        for (c in LauncherCommand.entries) assertTrue("no touch path for command $c", TouchPaths.forCommand(c).isNotBlank())
        for (m in MenuAction.entries) assertTrue("no touch path for menu action $m", TouchPaths.forMenu(m).isNotBlank())
        for (a in CornerActionId.entries) assertTrue("no touch path for corner action $a", TouchPaths.forCornerAction(a).isNotBlank())
    }

    @Test fun INP_07_no_touch_path_is_only_a_key_or_hover() {
        val forbidden = listOf("hover", "right-click", "Ctrl", "Alt +", "press the key")
        val all = LauncherCommand.entries.map(TouchPaths::forCommand) + MenuAction.entries.map(TouchPaths::forMenu) + CornerActionId.entries.map(TouchPaths::forCornerAction)
        for (p in all) for (f in forbidden) assertFalse("'$p' mentions $f", p.contains(f, ignoreCase = true))
    }

    private val everyContext = listOf(
        MenuContext(), MenuContext(isApp = false), MenuContext(canUninstall = false), MenuContext(otherDisplays = 2),
        MenuContext(adjacentSupported = true, onHome = true), MenuContext(inDock = true, onHome = true, otherDisplays = 1),
    )

    @Test fun INP_03_long_press_secondary_click_and_menu_key_give_identical_entries() {
        for (ctx in everyContext) {
            val lp = InputMenuModel.entries(MenuTrigger.LongPress, ctx)
            assertEquals(lp, InputMenuModel.entries(MenuTrigger.SecondaryClick, ctx))
            assertEquals(lp, InputMenuModel.entries(MenuTrigger.MenuKey, ctx))
            assertTrue(lp.isNotEmpty())
        }
    }

    @Test fun HOM_21_quick_sheet_entry_set_does_not_depend_on_how_it_was_opened() {
        // The home opens its quick sheet from one function whatever the trigger; the model underneath is the same for all three.
        val ctx = MenuContext(isApp = false, onHome = true)
        val sets = MenuTrigger.entries.map { InputMenuModel.entries(it, ctx) }
        assertEquals(1, sets.toSet().size)
    }

    @Test fun INP_04_sheet_lists_every_registered_command_with_a_binding() {
        val rows = ShortcutSheetModel.rows(ShortcutRegistry())
        assertEquals(LauncherCommand.entries.toSet(), rows.map { it.command }.toSet())
        assertTrue(rows.all { it.binding.isNotBlank() && it.binding != "Not set" })
    }

    @Test fun INP_17_each_corner_has_a_default_shortcut() {
        val corners = listOf(LauncherCommand.CornerTopLeft, LauncherCommand.CornerTopRight, LauncherCommand.CornerBottomLeft, LauncherCommand.CornerBottomRight)
        for (c in corners) assertTrue(DefaultShortcuts.all.first { it.command == c }.default != null)
    }

    // ---- INP-08

    private val d1 = Density(1f)
    private val screen = IntSize(1080, 2400)

    @Test fun INP_08_default_gestures_do_not_fire_from_the_system_gesture_edges() {
        val swipes = DefaultGestures.keys.filter { it in listOf(HomeGesture.SwipeUp, HomeGesture.SwipeDown, HomeGesture.SwipeLeft, HomeGesture.SwipeRight) }
        assertTrue(swipes.isNotEmpty())
        for (g in swipes) {
            val edge = when (g) {
                HomeGesture.SwipeUp -> Offset(500f, 2395f)
                HomeGesture.SwipeDown -> Offset(500f, 5f)
                HomeGesture.SwipeLeft -> Offset(1078f, 1200f)
                else -> Offset(2f, 1200f)
            }
            assertTrue("$g from its system edge must be ignored", d1.swipeStartsInSystemZone(edge, screen, g))
            assertFalse("$g from mid-screen must still work", d1.swipeStartsInSystemZone(Offset(540f, 1200f), screen, g))
        }
    }

    @Test fun INP_08_pinch_double_tap_and_long_press_are_assignable() {
        for (g in listOf(HomeGesture.PinchIn, HomeGesture.PinchOut, HomeGesture.DoubleTap, HomeGesture.LongPress, HomeGesture.SwipeUp, HomeGesture.SwipeLeft)) {
            assertEquals(com.bos.sphere.core.data.GestureKind.Touch, g.kind)
        }
        assertFalse(HomeGesture.LongPress in DefaultGestures.keys) // unbound by default: the quick sheet stays the long-press
    }

    @Test fun INP_08_gestures_that_are_not_swipes_are_never_blocked() {
        for (g in listOf(HomeGesture.PinchIn, HomeGesture.DoubleTap, HomeGesture.LongPress)) assertFalse(d1.swipeStartsInSystemZone(Offset(1f, 1f), screen, g))
    }
}
