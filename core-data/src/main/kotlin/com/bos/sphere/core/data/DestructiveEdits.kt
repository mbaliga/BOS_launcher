package com.bos.sphere.core.data

/**
 * CFG-04: one place the stores announce an edit that removes something the user arranged (a page, an app taken off home,
 * a hidden app, a cleared corner or gesture). The app installs a sink that takes a snapshot ([StateService.beforeDestructive])
 * before the edit is written. With no sink installed (unit tests, early start) the call does nothing.
 *
 * The sink is called on the thread of the edit, before the change is applied, and must not block: the app's sink reads the
 * state in memory and does the file work on a background thread. Snapshots are rate limited (5 s) so a burst of edits takes
 * one snapshot, of the state before the burst.
 */
object DestructiveEdits {
    @Volatile private var sink: ((String) -> Unit)? = null

    fun install(sink: ((String) -> Unit)?) { this.sink = sink }

    fun before(reason: String) { sink?.let { runCatching { it(reason) } } }
}
