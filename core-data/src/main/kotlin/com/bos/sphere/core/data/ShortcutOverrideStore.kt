package com.bos.sphere.core.data

/**
 * CFG-01: where the user's keyboard shortcut overrides live as part of the setup. The text is the registry's own export
 * (`ShortcutRegistry.export()`: only changes, `COMMAND=chord;...`), kept in the shortcuts section of the state file under
 * `keyboard`. Any module may read or write it through this interface; the input stream's registry and the rebind sheet
 * persist through the same slot (feature-grid `StateShortcutStorage` implements it too), so there is one copy.
 */
interface ShortcutOverrideStore {
    /** The saved overrides, or null when the user changed nothing. */
    fun read(): String?

    /** Replaces the saved overrides; null or blank clears them so an export carries nothing. */
    fun write(text: String?)

    /** True when at least one override is saved. */
    fun hasOverrides(): Boolean = !read().isNullOrBlank()

    /** Forgets every override (all shortcuts return to their defaults). */
    fun clear() = write(null)
}

/** The live implementation, over the state extras file. */
class StateShortcutOverrides(private val extras: StateExtrasStore) : ShortcutOverrideStore {
    override fun read(): String? = extras.read().keyboardShortcuts
    override fun write(text: String?) { extras.write(extras.read().copy(keyboardShortcuts = text?.takeIf { it.isNotBlank() })) }
}

/** Memory only, for tests and tools. */
class MemoryShortcutOverrides(private var text: String? = null) : ShortcutOverrideStore {
    override fun read(): String? = text
    override fun write(text: String?) { this.text = text?.takeIf { it.isNotBlank() } }
}
