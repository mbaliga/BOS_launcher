package com.bos.sphere.core.catalog

/**
 * HOM-12: bounded undo for edit mode. Holds the last [limit] states from before each change.
 * A new change clears the redo side.
 */
class UndoStack<T>(private val limit: Int = DEFAULT_LIMIT) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    val size: Int get() = past.size
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** Records [before] as the state to return to; call before applying a change. */
    fun record(before: T) {
        past.addLast(before)
        while (past.size > limit) past.removeFirst()
        future.clear()
    }

    /** The state before the last change, or null. [current] becomes redoable. */
    fun undo(current: T): T? {
        val prev = past.removeLastOrNull() ?: return null
        future.addLast(current)
        return prev
    }

    fun redo(current: T): T? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(current)
        return next
    }

    fun clear() { past.clear(); future.clear() }

    companion object { const val DEFAULT_LIMIT = 20 }
}

/** Edit mode session: the live state plus its undo history. */
class EditSession<T>(initial: T, limit: Int = UndoStack.DEFAULT_LIMIT) {
    private val stack = UndoStack<T>(limit)
    var state: T = initial
        private set
    var editing: Boolean = false
        private set

    val canUndo: Boolean get() = stack.canUndo
    val canRedo: Boolean get() = stack.canRedo

    fun enter() { editing = true; stack.clear() }
    fun exit() { editing = false; stack.clear() }

    /** Applies [change]; a no-op change is not recorded. Returns whether anything changed. */
    fun apply(change: (T) -> T): Boolean {
        val next = change(state)
        if (next == state) return false
        stack.record(state)
        state = next
        return true
    }

    fun undo(): Boolean { state = stack.undo(state) ?: return false; return true }
    fun redo(): Boolean { state = stack.redo(state) ?: return false; return true }
}
