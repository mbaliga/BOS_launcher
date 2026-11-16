package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-22: the "Cycle home landing" command lives in the one registry with a default shortcut that clashes with nothing. */
class HomeLandingCommandTest {
    @Test fun HOM_22_cycleLandingHasADefaultChordNoOtherCommandUses() {
        val reg = ShortcutRegistry()
        val chord = reg.binding(LauncherCommand.CycleLanding)
        assertNotNull(chord)
        assertEquals(LauncherCommand.CycleLanding, reg.commandFor(chord!!))
        assertEquals(1, reg.all().count { it.second == chord })
        assertTrue(chord.ctrl && chord.shift)
    }

    @Test fun HOM_22_theChordCanBeRebindAndResetLikeAnyOther() {
        val reg = ShortcutRegistry()
        val free = KeyChord(50, ctrl = true, alt = true)
        assertEquals(ShortcutRegistry.Rebind.Ok, reg.rebind(LauncherCommand.CycleLanding, free))
        assertTrue(reg.export().contains("CycleLanding="))
        reg.reset(LauncherCommand.CycleLanding)
        assertEquals(reg.defaultFor(LauncherCommand.CycleLanding), reg.binding(LauncherCommand.CycleLanding))
    }

    @Test fun HOM_22_theCommandStaysInsideTheLauncherAndIsNotAGlobalShortcut() {
        assertTrue(LauncherCommand.CycleLanding !in GlobalShortcuts.eligible)
    }
}
