package com.bos.sphere.feature.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val show by rememberUpdatedState(showPage)
    val p by rememberUpdatedState(pages)
    val cap by rememberUpdatedState(capacityOf)
    val change by rememberUpdatedState(onPagesChange)
    val pr by rememberUpdatedState(prefs)
    DisposableEffect(Unit) {
        val hook: (FocusDir) -> Boolean = h@{ dir ->
            val id = LauncherInput.focus.currentId ?: return@h false
            if (!id.startsWith("grid.")) return@h false
            val key = id.removePrefix("grid.")
            val all = p
            val pageIdx = all.indexOfFirst { key in it.keys }
            if (pageIdx < 0) return@h false
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
        LauncherInput.moveHook = hook
        onDispose { if (LauncherInput.moveHook === hook) LauncherInput.moveHook = null }
    }
}
