package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayout

/**
 * HOM-12: edit mode over the live page arrangement. The session does not intercept edits; the
 * screen feeds it every layout the store publishes ([observe]) and every change the user makes
 * (drag, remove, add or delete a page, density) lands on the undo stack of the last 20 changes.
 * [undo] and [redo] return the layout to write back to the store.
 */
class LayoutEditSession(initial: HomeLayout) { // created when edit mode is entered, with the layout at that moment
    private val session = EditSession(initial).also { it.enter() }

    val active: Boolean get() = session.editing
    val current: HomeLayout get() = session.state
    val canUndo: Boolean get() = session.canUndo
    val canRedo: Boolean get() = session.canRedo
    val undoDepth: Int get() = session.undoDepth

    fun exit() = session.exit()

    /** Records [layout] when it differs from the session's state; false for an echo of our own write. */
    fun observe(layout: HomeLayout): Boolean = active && session.apply { layout }

    fun undo(): HomeLayout? = if (session.undo()) session.state else null
    fun redo(): HomeLayout? = if (session.redo()) session.state else null
}
