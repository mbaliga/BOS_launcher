package com.bos.sphere.feature.grid

import android.content.Context
import com.bos.sphere.core.data.StateService
import com.bos.sphere.core.input.ShortcutPersistence
import com.bos.sphere.core.input.ShortcutStorage
import java.util.concurrent.Executors

/**
 * The keyboard slot of the state file (`extras.json`, key "keyboard") as a [ShortcutStorage]. Import and restore
 * write the same slot, so [reload] after one of them picks the imported bindings up (INP-10, CFG-02).
 */
class StateShortcutStorage(context: Context) : ShortcutStorage {
    private val extras = StateService.get(context.applicationContext).extras
    override fun read(): String? = extras.read().keyboardShortcuts
    override fun write(text: String?) { extras.write(extras.read().copy(keyboardShortcuts = text)) }
}

/** Loads the saved bindings into [LauncherInput.registry] off the main thread, and saves changes the same way. */
object LauncherShortcuts {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "shortcuts-io").apply { isDaemon = true } }

    fun reload(context: Context) {
        val app = context.applicationContext
        io.execute { runCatching { ShortcutPersistence.load(LauncherInput.registry, StateShortcutStorage(app)) } }
    }

    fun save(context: Context) {
        val app = context.applicationContext
        io.execute { runCatching { ShortcutPersistence.save(LauncherInput.registry, StateShortcutStorage(app)) } }
    }
}
