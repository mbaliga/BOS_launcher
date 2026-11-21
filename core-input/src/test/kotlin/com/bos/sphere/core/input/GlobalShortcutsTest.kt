package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalShortcutsTest {
    private class Fake(override var active: Boolean) : GlobalShortcutProvider {
        var got: Map<KeyChord, LauncherCommand>? = null
        override fun register(chords: Map<KeyChord, LauncherCommand>, onCommand: (LauncherCommand) -> Unit): Boolean { got = chords; return true }
        override fun unregister() { got = null }
    }

    @Test fun INP_14_the_core_build_has_no_provider_and_says_so() {
        assertFalse(NoGlobalShortcuts.active)
        assertFalse(NoGlobalShortcuts.register(emptyMap()) { })
        val a = GlobalShortcuts.availability(NoGlobalShortcuts, built = false)
        assertEquals(GlobalShortcuts.Availability.NotInThisBuild, a)
        assertTrue(GlobalShortcuts.explain(a).contains("while Liftoff is open"))
    }

    @Test fun INP_14_an_elevated_provider_is_used_only_when_switched_on() {
        assertEquals(GlobalShortcuts.Availability.SwitchedOff, GlobalShortcuts.availability(Fake(false), built = true))
        assertEquals(GlobalShortcuts.Availability.Available, GlobalShortcuts.availability(Fake(true), built = true))
    }

    @Test fun INP_14_only_launcher_level_commands_are_offered_and_meta_chords_never() {
        val reg = ShortcutRegistry()
        ShortcutEditor(reg).propose(LauncherCommand.OpenDrawer, KeyChord(32, ctrl = true, alt = true))
        reg.rebind(LauncherCommand.OpenSettings, KeyChord(55, ctrl = true, meta = true))
        val chords = GlobalShortcuts.chords(reg, LauncherCommand.entries.toSet())
        assertEquals(LauncherCommand.OpenDrawer, chords[KeyChord(32, ctrl = true, alt = true)])
        assertFalse(LauncherCommand.Dismiss in chords.values || LauncherCommand.EditMode in chords.values)
        assertFalse(LauncherCommand.OpenSettings in chords.values)
        assertTrue(chords.keys.none { it.meta })
    }
}
