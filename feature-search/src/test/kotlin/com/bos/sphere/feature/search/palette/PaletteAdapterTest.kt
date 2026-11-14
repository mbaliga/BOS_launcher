package com.bos.sphere.feature.search.palette

import com.bos.sphere.core.input.ContextMenus
import com.bos.sphere.core.input.DefaultShortcuts
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.core.input.MenuContext
import com.bos.sphere.core.input.ShortcutRegistry
import dev.aarso.search.palette.LauncherActions
import dev.aarso.search.palette.ResultActionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteAdapterTest {
    private val full = MenuContext(isApp = true, canUninstall = true, otherDisplays = 2, adjacentSupported = true)

    @Test fun PAL_08_appActionsComeFromTheOneMenuModel() {
        val ids = PaletteMenus.appActions(full).map { it.id }
        assertEquals(
            listOf(ResultActionId.Open, ResultActionId.OpenOnDisplay, ResultActionId.OpenBeside, ResultActionId.AppInfo, ResultActionId.AddToDock, ResultActionId.AddToWorkspace, ResultActionId.Uninstall),
            ids,
        )
    }

    @Test fun PAL_08_longPressRightClickAndPaletteListsAreIdentical() {
        for (c in listOf(full, MenuContext(), MenuContext(otherDisplays = 1, inDock = true), MenuContext(canUninstall = false, onHome = true))) {
            val menu = ContextMenus.onLongPress(c).map { it.action.name }
            assertEquals(menu, ContextMenus.onSecondaryClick(c).map { it.action.name })
            assertEquals(menu, PaletteMenus.appActions(c).map { it.id.name })
        }
    }

    @Test fun PAL_08_conditionalActionsAreOmittedWhenUnsupported() {
        val ids = PaletteMenus.appActions(MenuContext(canUninstall = false)).map { it.id }
        assertTrue(ResultActionId.OpenOnDisplay !in ids && ResultActionId.OpenBeside !in ids && ResultActionId.Uninstall !in ids)
    }

    @Test fun PAL_08_everyMenuActionMapsToAnEngineActionId() {
        MenuAction.entries.forEach { assertEquals(it.name, PaletteMenus.toId(it).name) }
    }

    private val reg = ShortcutRegistry()

    private fun key(code: Int, ch: Int, ctrl: Boolean = false, alt: Boolean = false) = TypeToSearch.onKey(code, ch, ctrl, alt, false, false, reg)

    @Test fun PAL_01_typingAPrintableKeyOpensThePaletteWithThatCharacter() {
        assertEquals(PaletteEntry.Open("g"), key(35, 'g'.code))
        assertEquals(PaletteEntry.Open("7"), key(14, '7'.code))
        assertEquals(PaletteEntry.Open("é"), key(33, 'é'.code))
    }

    @Test fun PAL_01_keyboardShortcutOpensWithEmptyQuery() {
        val chord = reg.binding(LauncherCommand.OpenPalette)!!
        assertEquals(PaletteEntry.Open(""), TypeToSearch.onKey(chord.keyCode, '/'.code, chord.ctrl, chord.alt, chord.shift, chord.meta, reg))
    }

    @Test fun PAL_01_shortcutFollowsRebinding() {
        val r = ShortcutRegistry()
        r.rebind(LauncherCommand.OpenPalette, com.bos.sphere.core.input.KeyChord(62, ctrl = true))
        assertEquals(PaletteEntry.Open(""), TypeToSearch.onKey(62, 0, true, false, false, false, r))
        assertNull(TypeToSearch.onKey(76, 0, true, false, false, false, r))
    }

    @Test fun PAL_01_commandKeysSpaceAndControlsDoNotOpenIt() {
        assertNull(key(32, 'd'.code, ctrl = true))       // Ctrl+D is Open drawer
        assertNull(key(62, ' '.code))
        assertNull(key(66, 10))
        assertNull(key(4, 0))
        assertNull(key(8, '1'.code, alt = true))          // Alt+1 is dock item 1
    }

    @Test fun PAL_01_neverStealsKeysFromAFocusedFieldOrAnOpenPalette() {
        assertNull(TypeToSearch.onKey(35, 'g'.code, false, false, false, false, reg, paletteOpen = true))
        assertNull(TypeToSearch.onKey(35, 'g'.code, false, false, false, false, reg, textFieldFocused = true))
    }

    @Test fun PAL_03_launcherActionCommandsExistInTheShortcutRegistry() {
        val names = LauncherCommand.entries.map { it.name }.toSet()
        LauncherActions.defaults.forEach { assertTrue("${it.command} is not a LauncherCommand", it.command in names) }
        assertNotNull(DefaultShortcuts.all.firstOrNull { it.command == LauncherCommand.EditMode })
    }
}
