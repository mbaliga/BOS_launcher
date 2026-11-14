package com.bos.sphere.feature.grid

import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetSizing
import com.bos.sphere.core.input.FocusDir
import com.bos.sphere.core.input.KeyboardMove

/**
 * INP-09: what an arrow key means in keyboard edit mode, as the shared [ItemAction]s the drag path also produces, so a
 * keyboard edit and a drag edit end in the same [SharedItemActions.apply].
 *
 *  - Arrow on any item: move it one place (across pages at the edges), [KeyboardMove].
 *  - Shift + arrow on a widget: resize it one cell (right and down grow, left and up shrink), the same step as the resize frame.
 *  - Shift + arrow on an app, shortcut or link: file it into the neighbour in that direction (a folder takes it in, anything
 *    else makes a new folder holding both), the same step as dropping one item on another.
 */
object KeyboardEditOps {
    fun resolve(state: HomeState, plan: SurfacePlan, key: String, dir: FocusDir, shift: Boolean, columns: Int): ItemAction? {
        val kind = ItemKeys.kind(key)
        if (shift) return when (kind) {
            ItemKind.Widget -> {
                val a = when (dir) { FocusDir.Left -> WidgetSizing.Arrow.Left; FocusDir.Right -> WidgetSizing.Arrow.Right; FocusDir.Up -> WidgetSizing.Arrow.Up; FocusDir.Down -> WidgetSizing.Arrow.Down }
                val (dc, dr) = WidgetSizing.deltaForKey(a)
                ItemAction.Resize(key, dc, dr)
            }
            ItemKind.App, ItemKind.Shortcut, ItemKind.Link -> {
                val d = when (dir) { FocusDir.Left -> Dir.Left; FocusDir.Right -> Dir.Right; FocusDir.Up -> Dir.Up; FocusDir.Down -> Dir.Down }
                val neighbour = plan.focus.firstOrNull { it.key == key }?.to?.get(d) ?: return null
                // Only things an item can be dropped on: not a widget.
                if (ItemKeys.kind(neighbour) == ItemKind.Widget || ItemKeys.kind(neighbour) == ItemKind.Tile) null
                else ItemAction.FileInto(key, neighbour)
            }
            else -> null
        }
        val pageIdx = state.pages.indexOfFirst { key in it.keys }
        if (pageIdx < 0) return null
        val page = state.pages[pageIdx]
        val t = KeyboardMove.target(page.keys.indexOf(key), page.keys.size, columns, dir, pageIdx > 0, pageIdx < state.pages.lastIndex) ?: return null
        val toPage = pageIdx + t.pageDelta
        val toIndex = if (t.index == KeyboardMove.END) state.pages[toPage].keys.size else t.index
        return ItemAction.Move(key, toPage, toIndex)
    }
}
