package com.bos.sphere.feature.desktop

/**
 * What the phone panel's arrow, Tab, Enter and backspace keys do inside the desktop surface's app drawer (EXT-10). Pure, so the
 * behaviour is tested without a window. Keys do nothing while the drawer is closed: the surface has no other text or list focus.
 */
object DrawerKeys {
    /** The highlighted index after [key], kept inside 0 until [count]; vertical keys move by a whole row of [columns]. */
    fun step(selected: Int, count: Int, key: PanelKey, columns: Int): Int {
        if (count <= 0) return 0
        val cols = columns.coerceAtLeast(1)
        val next = when (key) {
            PanelKey.Right, PanelKey.Tab -> selected + 1
            PanelKey.Left -> selected - 1
            PanelKey.Down -> selected + cols
            PanelKey.Up -> selected - cols
            else -> selected
        }
        return next.coerceIn(0, count - 1)
    }

    /** Columns of an adaptive grid [widthDp] wide whose cells are at least [cellDp] wide. */
    fun columns(widthDp: Float, cellDp: Float): Int = if (cellDp <= 0f) 1 else (widthDp / cellDp).toInt().coerceAtLeast(1)

    /** Removes the last character the user typed, never half of a surrogate pair (emoji and other astral characters). */
    fun backspace(text: String): String {
        if (text.isEmpty()) return text
        val cut = text.offsetByCodePoints(text.length, -1)
        return text.substring(0, cut)
    }
}
