package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Wave 6 audit of INP-10: an imported file can never leave two commands on one chord. */
class W6ImportAuditTest {
    private val e = KeyChord(33, ctrl = true) // EditMode default
    private val x = KeyChord(52, ctrl = true)

    @Test fun INP_10_an_import_that_binds_a_chord_another_command_keeps_is_dropped() {
        val r = ShortcutRegistry()
        r.import("OpenDrawer=${e.encode()}")
        assertEquals("the drawer keeps its own key", KeyChord(32, ctrl = true), r.binding(LauncherCommand.OpenDrawer))
        assertEquals(LauncherCommand.EditMode, r.commandFor(e))
    }

    @Test fun INP_10_an_import_that_binds_one_new_chord_twice_keeps_the_first_only() {
        val r = ShortcutRegistry()
        r.import("OpenDrawer=${x.encode()};OpenPuck=${x.encode()}")
        assertEquals(x, r.binding(LauncherCommand.OpenDrawer))
        assertEquals(KeyChord(44, ctrl = true), r.binding(LauncherCommand.OpenPuck))
        assertEquals(LauncherCommand.OpenDrawer, r.commandFor(x))
    }

    @Test fun INP_10_a_swap_between_two_commands_is_a_valid_import_and_round_trips() {
        val a = ShortcutRegistry()
        assertEquals(ShortcutRegistry.Rebind.Ok, a.rebind(LauncherCommand.OpenDrawer, null))
        a.rebind(LauncherCommand.EditMode, KeyChord(32, ctrl = true))
        a.rebind(LauncherCommand.OpenDrawer, e)
        val b = ShortcutRegistry(); b.import(a.export())
        assertEquals(e, b.binding(LauncherCommand.OpenDrawer))
        assertEquals(KeyChord(32, ctrl = true), b.binding(LauncherCommand.EditMode))
        assertNull(b.commandFor(KeyChord(1)))
    }
}
