package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CFG-01 wave 5: the rules behind the rebind screen's swap, reset all and reserved chords. */
class ShortcutRebindW5Test {
    private val palette = KeyChord(76, ctrl = true)   // Ctrl+/
    private val drawer = KeyChord(32, ctrl = true)    // Ctrl+D

    @Test fun CFG_01_aSwapGivesTheOtherCommandTheChordTheFirstHad() {
        val reg = ShortcutRegistry(); val ed = ShortcutEditor(reg)
        assertEquals(ShortcutEditor.Outcome.Conflict(LauncherCommand.OpenPalette), ed.propose(LauncherCommand.OpenDrawer, palette))
        val out = ed.swap(LauncherCommand.OpenDrawer, palette)
        assertEquals(ShortcutEditor.Outcome.Swapped(LauncherCommand.OpenDrawer, palette, LauncherCommand.OpenPalette, drawer), out)
        assertEquals(palette, reg.binding(LauncherCommand.OpenDrawer))
        assertEquals(drawer, reg.binding(LauncherCommand.OpenPalette))
        // No chord is bound twice after a swap.
        val chords = reg.all().mapNotNull { it.second }
        assertEquals(chords.size, chords.toSet().size)
    }

    @Test fun CFG_01_aSwapWithACommandThatHadNoChordUnbindsTheOther() {
        val reg = ShortcutRegistry(); val ed = ShortcutEditor(reg)
        ed.unbind(LauncherCommand.OpenDrawer)
        val out = ed.swap(LauncherCommand.OpenDrawer, palette) as ShortcutEditor.Outcome.Swapped
        assertNull(out.withNow)
        assertNull(reg.binding(LauncherCommand.OpenPalette))
        assertEquals(palette, reg.binding(LauncherCommand.OpenDrawer))
    }

    @Test fun CFG_01_cancelIsNotCallingSwapAndLeavesBothBindings() {
        val reg = ShortcutRegistry(); val ed = ShortcutEditor(reg)
        ed.propose(LauncherCommand.OpenDrawer, palette) // a conflict: nothing is taken
        assertEquals(drawer, reg.binding(LauncherCommand.OpenDrawer))
        assertEquals(palette, reg.binding(LauncherCommand.OpenPalette))
    }

    @Test fun CFG_01_aSwapNeverOverridesARefusal() {
        val reg = ShortcutRegistry(); val ed = ShortcutEditor(reg)
        assertTrue(ed.swap(LauncherCommand.OpenDrawer, KeyChord(32, meta = true, ctrl = true)) is ShortcutEditor.Outcome.Refused)
        assertEquals(drawer, reg.binding(LauncherCommand.OpenDrawer))
    }

    @Test fun CFG_01_swapWithNoClashIsAPlainRebind() {
        val reg = ShortcutRegistry()
        val free = KeyChord(38, ctrl = true)
        assertEquals(ShortcutEditor.Outcome.Saved(LauncherCommand.OpenDrawer, free), ShortcutEditor(reg).swap(LauncherCommand.OpenDrawer, free))
    }

    @Test fun CFG_01_resetAllDropsEveryOverrideAndTheExportIsEmpty() {
        val reg = ShortcutRegistry(); val ed = ShortcutEditor(reg)
        ed.propose(LauncherCommand.OpenDrawer, KeyChord(38, ctrl = true)); ed.unbind(LauncherCommand.EditMode); ed.swap(LauncherCommand.OpenPuck, palette)
        assertTrue(ed.isChanged(LauncherCommand.EditMode))
        assertEquals(ShortcutEditor.Outcome.ResetAll, ed.resetAll())
        assertEquals(ShortcutRegistry().all(), reg.all())
        assertEquals("", reg.export())
        assertFalse(ed.isChanged(LauncherCommand.EditMode))
    }

    @Test fun CFG_01_perCommandResetRestoresTheDefaultOnly() {
        val reg = ShortcutRegistry(); val ed = ShortcutEditor(reg)
        ed.propose(LauncherCommand.OpenDrawer, KeyChord(38, ctrl = true)); ed.unbind(LauncherCommand.EditMode)
        ed.reset(LauncherCommand.EditMode)
        assertEquals(reg.defaultFor(LauncherCommand.EditMode), reg.binding(LauncherCommand.EditMode))
        assertEquals(KeyChord(38, ctrl = true), reg.binding(LauncherCommand.OpenDrawer))
    }

    @Test fun CFG_01_systemChordsAreRefusedWithTheirOwnReason() {
        val ed = ShortcutEditor(ShortcutRegistry())
        listOf(
            KeyChord(61, alt = true), KeyChord(61, ctrl = true), KeyChord(134, alt = true), KeyChord(111, alt = true),
            KeyChord(111, ctrl = true), KeyChord(112, ctrl = true, alt = true), KeyChord(62, ctrl = true),
        ).forEach { c ->
            assertEquals(ShortcutEditor.Outcome.Refused(ShortcutEditor.RESERVED_NOTE), ed.propose(LauncherCommand.OpenDrawer, c))
        }
        assertTrue(ed.propose(LauncherCommand.OpenDrawer, KeyChord(38, ctrl = true, alt = true)) is ShortcutEditor.Outcome.Saved)
    }

    @Test fun CFG_01_aSwapSurvivesARestartThroughTheSlot() {
        val slot = object : ShortcutStorage { var t: String? = null; override fun read() = t; override fun write(text: String?) { t = text } }
        val a = ShortcutRegistry(); ShortcutEditor(a).swap(LauncherCommand.OpenDrawer, palette)
        ShortcutPersistence.save(a, slot)
        val b = ShortcutRegistry(); ShortcutPersistence.load(b, slot)
        assertEquals(a.all(), b.all())
        ShortcutEditor(b).resetAll(); ShortcutPersistence.save(b, slot)
        assertNull(slot.t)
    }

    @Test fun CFG_01_resetNeverStealsAChordAnotherCommandNowHolds() {
        val reg = ShortcutRegistry(); val ed = ShortcutEditor(reg)
        ed.unbind(LauncherCommand.OpenDrawer)
        ed.propose(LauncherCommand.EditMode, drawer) // EditMode takes Ctrl+D, which is OpenDrawer's default
        assertEquals(ShortcutEditor.Outcome.Conflict(LauncherCommand.EditMode), ed.reset(LauncherCommand.OpenDrawer))
        assertNull(reg.binding(LauncherCommand.OpenDrawer))
        val chords = reg.all().mapNotNull { it.second }
        assertEquals(chords.size, chords.toSet().size)
    }
}
