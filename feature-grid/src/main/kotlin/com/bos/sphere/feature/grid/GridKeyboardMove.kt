package com.bos.sphere.feature.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.input.FocusDir
import com.bos.sphere.core.input.KeyboardMove
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CROSS_PAGE_REFOCUS_MS = 400L

/**
 * INP-09 hook for the paged home: while keyboard edit mode is on, the arrows move the focused item through the
 * layout (across pages at the edges) instead of moving focus. The drag path keeps working beside it.
 */
@Composable
fun GridKeyboardMove(
    pages: List<HomePage>,
    prefs: LauncherPrefs,
    isLandscape: Boolean,
    capacityOf: (PageLayout) -> Int,
    onPagesChange: (List<HomePage>) -> Unit,
    /** Brings a page into view; after a move across pages the item is on another page and must be composed to take focus back. */
    showPage: (Int) -> Unit = {},
    /** The live non-app items (folders, widgets); with them Shift + arrow resizes a widget or files an item into its neighbour. */
    extras: HomeExtras? = null,
) {
    val ex by rememberUpdatedState(extras)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val show by rememberUpdatedState(showPage)
    val p by rememberUpdatedState(pages)
    val cap by rememberUpdatedState(capacityOf)
    val change by rememberUpdatedState(onPagesChange)
    val pr by rememberUpdatedState(prefs)
    DisposableEffect(Unit) {
        // Wave 45: the selected item is the one the keys act on; without a selection it is the focused one.
        fun targetKey(): String? = ex?.selection?.selectedKey ?: LauncherInput.focus.currentId?.takeIf { it.startsWith("grid.") }?.removePrefix("grid.")
        fun widgetEditor(key: String, all: List<HomePage>, pageIdx: Int): ItemEditor? {
            val extras = ex ?: return null
            if (ItemKeys.kind(key) != ItemKind.Widget) return null
            val page = all[pageIdx]
            if (page.layout.shape != PageShape.Grid) return null
            val columns = page.layout.gridFor(isLandscape, pr).columns.coerceAtLeast(1)
            val rows = (cap(page.layout) / columns).coerceAtLeast(1)
            return ItemEditor(key, extras, page.keys, pageIdx, columns, rows, pr.homeOrder.rtl, true) { extras.selection.onRemove(key) }
        }
        val hook: (FocusDir) -> Boolean = h@{ dir ->
            val key = targetKey() ?: return@h false
            val id = "grid.$key"
            val all = p
            val pageIdx = all.indexOfFirst { key in it.keys }
            if (pageIdx < 0) return@h false
            // A widget moves by one CELL (blocked by what is in the way), not by one place in the list.
            widgetEditor(key, all, pageIdx)?.let { e ->
                when (dir) { FocusDir.Left -> e.move(-1, 0); FocusDir.Right -> e.move(1, 0); FocusDir.Up -> e.move(0, -1); FocusDir.Down -> e.move(0, 1) }
                return@h true
            }
            val page = all[pageIdx]
            val columns = if (page.layout.shape == PageShape.Grid) page.layout.gridFor(isLandscape, pr).columns else 1
            val index = page.keys.indexOf(key)
            val t = KeyboardMove.target(index, page.keys.size, columns, dir, pageIdx > 0, pageIdx < all.lastIndex) ?: return@h true
            val toPage = pageIdx + t.pageDelta
            val toIndex = if (t.index == KeyboardMove.END) all[toPage].keys.size else t.index
            change(HomeLayoutMath.move(all, key, toPage, toIndex, cap))
            if (t.pageDelta != 0) {
                // The item left the composition with its old page: show the new page and give it focus back so the next arrow keeps moving it.
                show(toPage.coerceAtMost((all.size - 1).coerceAtLeast(0)))
                scope.launch { delay(CROSS_PAGE_REFOCUS_MS); LauncherInput.focus.request(id) }
            }
            true
        }
        val shiftHook: (FocusDir) -> Boolean = h@{ dir ->
            val extras = ex ?: return@h false
            val key = targetKey() ?: return@h false
            val all = p
            val pageIdx = all.indexOfFirst { key in it.keys }
            if (pageIdx < 0) return@h false
            // A widget resizes by one cell within its provider's limits (and announces why when it cannot).
            widgetEditor(key, all, pageIdx)?.let { e ->
                val (dc, dr) = when (dir) { FocusDir.Left -> -1 to 0; FocusDir.Right -> 1 to 0; FocusDir.Up -> 0 to -1; FocusDir.Down -> 0 to 1 }
                e.resize(dc, dr)
                return@h true
            }
            val page = all[pageIdx]
            val columns = if (page.layout.shape == PageShape.Grid) page.layout.gridFor(isLandscape, pr).columns else 1
            val rows = (cap(page.layout) / columns.coerceAtLeast(1)).coerceAtLeast(1)
            val state = extras.state.copy(pages = all)
            val action = KeyboardEditOps.resolveOnPage(state, pageIdx, key, dir, shift = true, columns = columns, rows = rows)
            if (action != null) extras.onAction(action)
            true
        }
        val deleteHook: () -> Boolean = d@{
            val sel = ex?.selection ?: return@d false
            val key = sel.selectedKey ?: return@d false
            ItemEditor(key, ex ?: return@d false, emptyList(), 0, 1, 1) { sel.onRemove(key) }.remove()
            true
        }
        LauncherInput.moveHook = hook
        LauncherInput.shiftMoveHook = shiftHook
        LauncherInput.deleteHook = deleteHook
        onDispose {
            if (LauncherInput.moveHook === hook) LauncherInput.moveHook = null
            if (LauncherInput.shiftMoveHook === shiftHook) LauncherInput.shiftMoveHook = null
            if (LauncherInput.deleteHook === deleteHook) LauncherInput.deleteHook = null
        }
    }
}
