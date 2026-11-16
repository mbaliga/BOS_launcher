package com.bos.sphere.core.input

/** Where the user's shortcut changes are kept between runs (the state file's keyboard slot, INP-10, INP-04). */
interface ShortcutStorage {
    fun read(): String?
    fun write(text: String?)
}

/** Saves and restores a [ShortcutRegistry]'s overrides through a [ShortcutStorage]. Pure, so it is JVM-testable. */
object ShortcutPersistence {
    /** Replaces the registry's overrides with the stored ones; an empty or missing slot means the defaults. */
    fun load(registry: ShortcutRegistry, storage: ShortcutStorage) {
        registry.import(storage.read().orEmpty())
    }

    /** Writes only the user's changes; with no changes the slot is cleared so the exported file carries nothing. */
    fun save(registry: ShortcutRegistry, storage: ShortcutStorage) {
        storage.write(registry.export().takeIf { it.isNotEmpty() })
    }
}

/**
 * The rules of rebinding, in one place (INP-10). A chord is refused when it uses Meta (the system owns those), when
 * it is a bare character key that would swallow typing in Radar and other text fields, or when another command
 * already uses it.
 */
class ShortcutEditor(private val registry: ShortcutRegistry) {
    sealed interface Outcome {
        data class Saved(val command: LauncherCommand, val chord: KeyChord) : Outcome
        data class Conflict(val with: LauncherCommand) : Outcome
        data class Refused(val reason: String) : Outcome
        data class Unbound(val command: LauncherCommand) : Outcome
        data class Reset(val command: LauncherCommand) : Outcome
        /** CFG-01: [command] took [chord] from [with], which got the chord [command] had (or none when it had none). */
        data class Swapped(val command: LauncherCommand, val chord: KeyChord, val with: LauncherCommand, val withNow: KeyChord?) : Outcome
        /** CFG-01: every override was dropped; all commands are back to their defaults. */
        data object ResetAll : Outcome
    }

    fun propose(command: LauncherCommand, chord: KeyChord): Outcome {
        refusal(chord)?.let { return Outcome.Refused(it) }
        return when (val r = registry.rebind(command, chord)) {
            ShortcutRegistry.Rebind.Ok -> Outcome.Saved(command, chord)
            is ShortcutRegistry.Rebind.Conflict -> Outcome.Conflict(r.with)
        }
    }

    /** Touch parity: a command can be unbound or reset to its default without pressing any key. */
    fun unbind(command: LauncherCommand): Outcome { registry.rebind(command, null); return Outcome.Unbound(command) }
    fun reset(command: LauncherCommand): Outcome { registry.reset(command); return Outcome.Reset(command) }

    /**
     * CFG-01: the answer to a [Outcome.Conflict] when the user chooses "swap". [command] takes [chord]; the command that held it
     * gets the chord [command] had before (none if it had none). Nothing is taken silently: the caller only calls this after
     * the user agreed, and "cancel" is simply not calling it. Refused chords stay refused, and a chord nobody holds is a plain rebind.
     */
    fun swap(command: LauncherCommand, chord: KeyChord): Outcome {
        refusal(chord)?.let { return Outcome.Refused(it) }
        val other = registry.conflictFor(chord, command) ?: return propose(command, chord)
        val mine = registry.binding(command)
        registry.rebind(other, null)
        registry.rebind(command, chord)
        registry.rebind(other, mine)
        return Outcome.Swapped(command, chord, other, mine)
    }

    /** CFG-01: forgets every override at once (touch parity with the per-command reset). */
    fun resetAll(): Outcome { LauncherCommand.entries.forEach { registry.reset(it) }; return Outcome.ResetAll }

    /** True when [command] differs from its default, so a per-command reset has something to do. */
    fun isChanged(command: LauncherCommand): Boolean = registry.isOverridden(command)

    companion object {
        private const val F1 = 131; private const val F12 = 142

        const val RESERVED_NOTE = "The system keeps this key combination (switching or closing windows), so it cannot be used"

        /** System chords a launcher must not claim: window switching and closing, the system's task and IME switchers. */
        fun isReserved(c: KeyChord): Boolean = when {
            c.alt && !c.ctrl && c.keyCode == Keys.TAB -> true          // Alt+Tab, Alt+Shift+Tab
            c.ctrl && !c.alt && c.keyCode == Keys.TAB -> true          // Ctrl+Tab, Ctrl+Shift+Tab (task or tab switch)
            c.alt && c.keyCode == 134 -> true                          // Alt+F4
            c.alt && c.keyCode == Keys.ESCAPE -> true                  // Alt+Esc
            c.ctrl && c.keyCode == Keys.ESCAPE -> true                 // Ctrl+Esc
            c.ctrl && c.alt && c.keyCode == 112 -> true                // Ctrl+Alt+Del
            c.ctrl && c.keyCode == 62 -> true                          // Ctrl+Space (input-method switch)
            else -> false
        }

        fun refusal(c: KeyChord): String? = when {
            Keys.isModifier(c.keyCode) -> "Press a key together with the modifiers"
            c.meta -> KeyLabels.META_NOTE
            isReserved(c) -> RESERVED_NOTE
            !c.ctrl && !c.alt && c.keyCode !in F1..F12 && c.keyCode != Keys.ESCAPE -> "Use Ctrl or Alt with the key, so typing is not taken over"
            else -> null
        }
    }
}
