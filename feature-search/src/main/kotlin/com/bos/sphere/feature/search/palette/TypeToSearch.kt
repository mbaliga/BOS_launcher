package com.bos.sphere.feature.search.palette

import com.bos.sphere.core.input.KeyChord
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.ShortcutRegistry

/** What the host should do with a key press on a launcher surface. */
sealed interface PaletteEntry {
    /** Open the palette with [initial] already typed ("" for the shortcut). */
    data class Open(val initial: String) : PaletteEntry
}

/**
 * PAL-01 hook for the input stream: call [onKey] from the home surface's key dispatch with the event's data. It returns
 * [PaletteEntry.Open] when the key is the palette shortcut (default Ctrl+/, rebindable) or a printable character typed
 * with no command modifier, and null otherwise so the key continues through normal dispatch.
 */
object TypeToSearch {
    fun onKey(
        keyCode: Int, unicodeChar: Int, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean,
        registry: ShortcutRegistry, paletteOpen: Boolean = false, textFieldFocused: Boolean = false,
    ): PaletteEntry? {
        if (paletteOpen || textFieldFocused) return null
        val chord = KeyChord(keyCode, ctrl, alt, shift, meta)
        if (registry.commandFor(chord) == LauncherCommand.OpenPalette) return PaletteEntry.Open("")
        if (ctrl || alt || meta) return null
        if (unicodeChar <= 0 || unicodeChar and COMBINING_ACCENT != 0) return null
        val s = String(Character.toChars(unicodeChar))
        // Only characters worth searching for: letters, digits, punctuation and symbols; not controls or spaces.
        if (s.isBlank() || Character.isISOControl(unicodeChar)) return null
        // A key bound to another launcher command (Dock digits use Alt, so unmodified ones never clash) wins.
        if (registry.commandFor(chord) != null) return null
        return PaletteEntry.Open(s)
    }

    private const val COMBINING_ACCENT = 0x80000000.toInt()
}
