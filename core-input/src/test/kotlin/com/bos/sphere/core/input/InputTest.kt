package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputTest {
    @Test fun INP_06_last_event_wins() {
        val t = InputModeTracker()
        t.onKey(); assertEquals(InputMode.Keyboard, t.mode.value); assertTrue(t.showFocusRing); assertFalse(t.showHover)
        t.onPointer(); assertEquals(InputMode.Pointer, t.mode.value); assertTrue(t.showHover); assertFalse(t.showFocusRing)
        t.onTouch(); assertEquals(InputMode.Touch, t.mode.value); assertFalse(t.showFocusRing || t.showHover)
    }

    @Test fun INP_04_every_default_binding_is_unique() {
        val chords = DefaultShortcuts.all.mapNotNull { it.default }
        assertEquals(chords.size, chords.toSet().size)
    }
    @Test fun INP_05_default_set_covers_every_command() {
        assertEquals(LauncherCommand.entries.toSet(), DefaultShortcuts.all.map { it.command }.toSet())
    }
    @Test fun INP_10_rebind_detects_conflicts_and_resets() {
        val r = ShortcutRegistry()
        val taken = r.binding(LauncherCommand.OpenDrawer)!!
        assertEquals(ShortcutRegistry.Rebind.Conflict(LauncherCommand.OpenDrawer), r.rebind(LauncherCommand.OpenPalette, taken))
        val free = KeyChord(50, ctrl = true, alt = true)
        assertEquals(ShortcutRegistry.Rebind.Ok, r.rebind(LauncherCommand.OpenPalette, free))
        assertEquals(LauncherCommand.OpenPalette, r.commandFor(free))
        r.reset(LauncherCommand.OpenPalette)
        assertEquals(KeyChord(76, ctrl = true), r.binding(LauncherCommand.OpenPalette))
    }
    @Test fun INP_10_bindings_export_and_import_round_trip() {
        val a = ShortcutRegistry()
        a.rebind(LauncherCommand.OpenPalette, KeyChord(50, ctrl = true, alt = true))
        a.rebind(LauncherCommand.Dismiss, null)
        val raw = a.export()
        val b = ShortcutRegistry().also { it.import(raw) }
        assertEquals(a.all(), b.all())
        assertNull(b.binding(LauncherCommand.Dismiss))
    }
    @Test fun key_chord_encoding_round_trips() {
        val c = KeyChord(42, ctrl = true, shift = true)
        assertEquals(c, KeyChord.decode(c.encode()))
        assertNull(KeyChord.decode("garbage"))
    }

    @Test fun INP_03_long_press_right_click_and_menu_key_are_identical() {
        val ctx = MenuContext(otherDisplays = 1, adjacentSupported = true, onHome = true)
        assertEquals(ContextMenus.onLongPress(ctx), ContextMenus.onSecondaryClick(ctx))
        assertEquals(ContextMenus.onLongPress(ctx), ContextMenus.onMenuKey(ctx))
    }
    @Test fun PAL_08_app_actions_include_the_required_set_when_supported() {
        val actions = ContextMenus.forItem(MenuContext(otherDisplays = 1, adjacentSupported = true)).map { it.action }
        assertTrue(actions.containsAll(listOf(MenuAction.Open, MenuAction.OpenOnDisplay, MenuAction.OpenBeside, MenuAction.AppInfo, MenuAction.AddToDock, MenuAction.AddToWorkspace, MenuAction.Uninstall)))
    }
    @Test fun unsupported_actions_are_hidden_not_disabled() {
        val actions = ContextMenus.forItem(MenuContext()).map { it.action }
        assertFalse(MenuAction.OpenOnDisplay in actions || MenuAction.OpenBeside in actions)
    }
}
