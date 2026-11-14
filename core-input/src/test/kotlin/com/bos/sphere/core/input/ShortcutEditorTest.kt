package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutEditorTest {
    private class Mem(var text: String? = null) : ShortcutStorage {
        override fun read() = text
        override fun write(text: String?) { this.text = text }
    }

    private val ctrlJ = KeyChord(38, ctrl = true)

    @Test fun INP_10_a_new_binding_is_saved_and_the_old_default_is_gone() {
        val reg = ShortcutRegistry()
        val out = ShortcutEditor(reg).propose(LauncherCommand.OpenDrawer, ctrlJ)
        assertEquals(ShortcutEditor.Outcome.Saved(LauncherCommand.OpenDrawer, ctrlJ), out)
        assertEquals(ctrlJ, reg.binding(LauncherCommand.OpenDrawer))
        assertNull(reg.commandFor(KeyChord(32, ctrl = true)))
    }

    @Test fun INP_10_a_clash_is_named_and_nothing_changes() {
        val reg = ShortcutRegistry()
        val palette = reg.binding(LauncherCommand.OpenPalette)!!
        assertEquals(ShortcutEditor.Outcome.Conflict(LauncherCommand.OpenPalette), ShortcutEditor(reg).propose(LauncherCommand.OpenDrawer, palette))
        assertEquals(KeyChord(32, ctrl = true), reg.binding(LauncherCommand.OpenDrawer))
    }

    @Test fun INP_10_meta_and_bare_letters_are_refused() {
        val reg = ShortcutRegistry(); val ed = ShortcutEditor(reg)
        assertTrue(ed.propose(LauncherCommand.OpenDrawer, KeyChord(32, meta = true, ctrl = true)) is ShortcutEditor.Outcome.Refused)
        assertTrue(ed.propose(LauncherCommand.OpenDrawer, KeyChord(32)) is ShortcutEditor.Outcome.Refused)
        assertTrue(ed.propose(LauncherCommand.OpenDrawer, KeyChord(59, ctrl = true)) is ShortcutEditor.Outcome.Refused)
        assertTrue(ed.propose(LauncherCommand.OpenDrawer, KeyChord(133)) is ShortcutEditor.Outcome.Saved) // F3
    }

    @Test fun INP_10_unbind_and_reset_need_no_key() {
        val reg = ShortcutRegistry(); val ed = ShortcutEditor(reg)
        ed.unbind(LauncherCommand.EditMode)
        assertNull(reg.binding(LauncherCommand.EditMode))
        ed.reset(LauncherCommand.EditMode)
        assertEquals(KeyChord(33, ctrl = true), reg.binding(LauncherCommand.EditMode))
    }

    @Test fun INP_04_changes_survive_a_restart_through_the_storage_slot() {
        val store = Mem()
        val a = ShortcutRegistry()
        ShortcutEditor(a).propose(LauncherCommand.OpenDrawer, ctrlJ)
        ShortcutEditor(a).unbind(LauncherCommand.EditMode)
        ShortcutPersistence.save(a, store)
        val b = ShortcutRegistry()
        ShortcutPersistence.load(b, store)
        assertEquals(a.all(), b.all())
    }

    @Test fun INP_04_no_changes_leave_the_slot_empty_and_a_cleared_slot_means_defaults() {
        val store = Mem("OpenDrawer=C:38")
        val reg = ShortcutRegistry()
        ShortcutPersistence.load(reg, store)
        assertEquals(ctrlJ, reg.binding(LauncherCommand.OpenDrawer))
        ShortcutEditor(reg).reset(LauncherCommand.OpenDrawer)
        ShortcutPersistence.save(reg, store)
        assertNull(store.text)
        store.text = null
        ShortcutPersistence.load(reg, store)
        assertEquals(KeyChord(32, ctrl = true), reg.binding(LauncherCommand.OpenDrawer))
    }

    @Test fun INP_10_a_garbled_slot_is_ignored_not_fatal() {
        val reg = ShortcutRegistry()
        ShortcutPersistence.load(reg, Mem("nonsense;OpenDrawer=;Foo=C:1;OpenSettings=C:xx"))
        assertEquals(ShortcutRegistry().all(), reg.all())
    }
}
