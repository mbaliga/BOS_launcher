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
import org.junit.runner.RunWith

@RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
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

    @Test
    fun CFG_01_aRebindRoundTripsThroughAnExportedFileIntoASecondInstall() {
        val chord = KeyChord(keyCode = 47, ctrl = true, alt = true)
        val reg = ShortcutRegistry()
        ShortcutEditor(reg).propose(LauncherCommand.OpenPalette, chord)
        // Export side: the live registry is read through the same hook the app installs.
        val dir = java.nio.file.Files.createTempDirectory("cfg01").toFile()
        val extrasA = com.bos.sphere.core.data.StateExtrasStore(java.io.File(dir, "a.json"))
        val live = com.bos.sphere.core.data.LiveExtras(extrasA, emptyList(), keyboardLive = { com.bos.sphere.core.data.LiveText(reg.export()) })
        val parts = com.bos.sphere.core.data.StateParts(extras = live.read())
        val text = com.bos.sphere.core.data.StateCodec.export(parts, 1L, "t").encode()
        // Import side: decode, write the slot, load into a fresh registry (what reloadAfterStateChange does).
        val dec = com.bos.sphere.core.data.StateCodec.decode(com.bos.sphere.core.data.StateDocument.parse(text))
        assertTrue(com.bos.sphere.core.data.StateSchema.K_SHORTCUTS in dec.present)
        val extrasB = com.bos.sphere.core.data.StateExtrasStore(java.io.File(dir, "b.json"))
        extrasB.write(dec.parts.extras)
        val second = ShortcutRegistry()
        ShortcutPersistence.load(second, asStorage(com.bos.sphere.core.data.StateShortcutOverrides(extrasB)))
        assertEquals(chord, second.binding(LauncherCommand.OpenPalette))
        dir.deleteRecursively()
    }
}
