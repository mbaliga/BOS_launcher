package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.MemoryShortcutOverrides
import com.bos.sphere.core.data.ShortcutOverrideStore
import com.bos.sphere.core.input.KeyChord
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.ShortcutEditor
import com.bos.sphere.core.input.ShortcutPersistence
import com.bos.sphere.core.input.ShortcutRegistry
import com.bos.sphere.core.input.ShortcutStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CFG-01: the rebind sheet's registry persists through the core-data override store, and a second registry reads it back. */
class ShortcutOverrideStoreTest {
    private fun asStorage(s: ShortcutOverrideStore) = object : ShortcutStorage {
        override fun read() = s.read()
        override fun write(text: String?) = s.write(text)
    }

    @Test
    fun CFG_01_aReboundShortcutSurvivesThroughTheOverrideStoreIntoAFreshRegistry() {
        val store = MemoryShortcutOverrides()
        val first = ShortcutRegistry()
        val chord = KeyChord(keyCode = 47, ctrl = true, alt = true) // Ctrl+Alt+S
        assertTrue(ShortcutEditor(first).propose(LauncherCommand.OpenPalette, chord) is ShortcutEditor.Outcome.Saved)
        ShortcutPersistence.save(first, asStorage(store))
        assertTrue(store.hasOverrides())

        val second = ShortcutRegistry()
        ShortcutPersistence.load(second, asStorage(store))
        assertEquals(chord, second.binding(LauncherCommand.OpenPalette))
        assertEquals(ShortcutRegistry().binding(LauncherCommand.OpenDrawer), second.binding(LauncherCommand.OpenDrawer))
    }

    @Test
    fun CFG_01_resetToDefaultsClearsTheStoredSlotSoTheExportCarriesNothing() {
        val store = MemoryShortcutOverrides()
        val r = ShortcutRegistry()
        ShortcutEditor(r).propose(LauncherCommand.OpenDrawer, KeyChord(keyCode = 47, ctrl = true, alt = true))
        ShortcutPersistence.save(r, asStorage(store))
        ShortcutEditor(r).reset(LauncherCommand.OpenDrawer)
        ShortcutPersistence.save(r, asStorage(store))
        assertFalse(store.hasOverrides())
    }
}
