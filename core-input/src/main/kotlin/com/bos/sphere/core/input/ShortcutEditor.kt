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

    companion object {
        private const val F1 = 131; private const val F12 = 142

        fun refusal(c: KeyChord): String? = when {
            Keys.isModifier(c.keyCode) -> "Press a key together with the modifiers"
            c.meta -> KeyLabels.META_NOTE
            !c.ctrl && !c.alt && c.keyCode !in F1..F12 && c.keyCode != Keys.ESCAPE -> "Use Ctrl or Alt with the key, so typing is not taken over"
            else -> null
        }
    }
}
