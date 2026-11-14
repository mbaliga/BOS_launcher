package com.bos.sphere.core.input

/**
 * Moving an item with the arrow keys in edit mode (INP-05, INP-09). Pure: the home converts the answer into a layout change.
 * [index] is the item's place on its page, [count] how many items the page holds, [columns] the grid width.
 */
object KeyboardMove {
    /** Where the item goes: [pageDelta] pages over and to [index] on that page; [END] means the last place. */
    data class Target(val pageDelta: Int, val index: Int)

    const val END = Int.MAX_VALUE

    fun target(index: Int, count: Int, columns: Int, dir: FocusDir, hasPrevPage: Boolean, hasNextPage: Boolean): Target? {
        val cols = columns.coerceAtLeast(1)
        return when (dir) {
            FocusDir.Left -> if (index > 0) Target(0, index - 1) else if (hasPrevPage) Target(-1, END) else null
            FocusDir.Right -> if (index < count - 1) Target(0, index + 1) else if (hasNextPage) Target(1, 0) else null
            FocusDir.Up -> if (index - cols >= 0) Target(0, index - cols) else null
            FocusDir.Down -> when {
                index + cols < count -> Target(0, index + cols)
                index / cols < (count - 1) / cols -> Target(0, count - 1) // a shorter last row: land on its last item
                else -> null
            }
        }
    }
}

/** Edit mode by keyboard: Ctrl+E (or its command) turns it on, arrows then move the focused item, Esc leaves. */
object EditModeState {
    fun next(on: Boolean, command: LauncherCommand): Boolean = when (command) {
        LauncherCommand.EditMode -> !on
        LauncherCommand.Dismiss -> false
        else -> on
    }
}
