package com.bos.sphere

import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bos.sphere.core.data.AddCarouselModel
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePageOps
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.feature.grid.EditCardGeometry
import com.bos.sphere.feature.grid.EditCardHost
import com.bos.sphere.feature.grid.GridHomeState
import com.bos.sphere.feature.grid.UndoNotice
import com.bos.sphere.feature.grid.rememberEditCardProgress
import com.bos.sphere.feature.grid.rememberEditCardTarget
import kotlinx.coroutines.launch

/**
 * HOM-12: the home as a card while it is edited. The progress and the target are read only inside the home layer's
 * graphicsLayer (see [com.bos.sphere.feature.grid.editCardLayer]), so the transition never recomposes the home.
 */
class EditCardState(val progress: State<Float>, val target: EditCardGeometry.Target, val reduced: Boolean) {
    fun frame(): EditCardGeometry.Frame = EditCardGeometry.frame(target, progress.value, reduced)
}

@Composable
fun rememberEditCardState(active: Boolean): EditCardState {
    val progress = rememberEditCardProgress(active)
    val target = rememberEditCardTarget()
    val reduced = rememberMotionPrefs().reduced
    return remember(progress, target, reduced) { EditCardState(progress, target, reduced) }
}

/** HOM-12 / HOM-01: the page carousel and the add carousel in the background of the edit card, wired to the live layout and its undo stack. */
@Composable
fun EditCardOverlay(
    card: EditCardState,
    homeEdit: HomeEditState,
    stores: LauncherStores,
    homeItems: HomeItems,
    pages: List<HomePage>,
    layout: HomeLayout,
    preserve: Set<String>,
    capacityOf: (PageLayout) -> Int,
    gridState: GridHomeState,
    appsByKey: Map<String, AppEntry>,
    apps: List<AppEntry>,
    folderKeys: Set<String>,
    prefs: LauncherPrefs,
    isLandscape: Boolean,
    focusAdd: Boolean,
    savePages: (List<HomePage>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<UndoNotice?>(null) }
    var goto by remember { mutableIntStateOf(-1) }
    LaunchedEffect(goto, pages.size) {
        if (goto >= 0) { val t = goto.coerceAtMost(pages.size - 1).coerceAtLeast(0); goto = -1; gridState.goToPage(t) }
    }
    fun apply(r: HomePageOps.Result) { savePages(r.pages); goto = r.focus }
    val me = remember { Process.myUserHandle() }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    fun deleteNow(i: Int) {
        val r = HomePageOps.delete(pages, i)
        val base = homeEdit.undoDepth
        val before = layout
        deletePageNow(i, pages, layout, preserve, appsByKey, homeItems, stores)
        goto = r.focus
        // The layout as this page edit left it: the Undo button only acts while it is still that, so a later edit is never undone by it.
        val after = stores.layout.layout.value
        notice = UndoNotice(L10n.tr(if (pages.size == 1) "Page emptied" else "Page deleted"), onUndo = {
            if (stores.layout.layout.value == after) {
                // One tap undoes the whole page edit, however many layout writes it took; it stops as soon as the page is back.
                var left = (homeEdit.undoDepth - base).coerceAtLeast(2)
                while (left-- > 0 && homeEdit.canUndo && stores.layout.layout.value != before) homeEdit.undo()
            }
        })
    }
    confirmDelete?.let { i ->
        PageDeleteConfirm(HomePageOps.itemCount(pages, i), onConfirm = { confirmDelete = null; deleteNow(i) }, onDismiss = { confirmDelete = null })
    }
    val onHome = remember(layout, folderKeys) { layout.pages.flatMapTo(HashSet()) { it.keys } + folderKeys }
    EditCardHost(
        progress = card.progress, reduced = card.reduced, target = card.target,
        pages = pages, current = { gridState.currentPage }, appsByKey = appsByKey, prefs = prefs, isLandscape = isLandscape,
        apps = apps, onHome = onHome, hasWork = remember(apps) { apps.any { it.user != me } }, isWork = { it.user != me },
        onSelect = { i -> scope.launch { gridState.goToPage(i) } },
        onDelete = { i -> if (HomePageOps.itemCount(pages, i) == 0) deleteNow(i) else confirmDelete = i },
        onMove = { i, d -> HomePageOps.move(pages, i, d)?.let(::apply) },
        onMoveTo = { i, t -> HomePageOps.moveTo(pages, i, t)?.let(::apply) },
        onAddPage = { apply(HomePageOps.add(pages, gridState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))) },
        onAddApp = { app ->
            // Wave 30 (pagesExplicit): the app goes on the page being edited; a full page says so and nothing else moves or opens.
            val placed = AddCarouselModel.place(pages, app.key, gridState.currentPage, capacityOf)
            if (placed == null) android.widget.Toast.makeText(homeItems.context(), L10n.tr("Page full. Add a page or remove an item."), android.widget.Toast.LENGTH_SHORT).show()
            else {
                stores.layout.replace(layout.copy(pages = HomeLayoutMath.toStored(placed, layout.pages, preserve), removed = layout.removed - app.key, seeded = true))
                stores.layout.markExplicit()
                goto = placed.indexOfFirst { app.key in it.keys }.coerceAtLeast(0)
            }
        },
        onWidget = { homeItems.widgetRequest++ },
        onFolder = { homeItems.folderOpen = true },
        onShortcut = { homeItems.shortcutOpen = true },
        onLink = { homeItems.linkOpen = true },
        onFile = { homeItems.fileRequest++ },
        onGroup = { homeItems.groupOpen = true },
        notice = notice, onNoticeDone = { notice = null },
        focusAdd = focusAdd, onExit = { homeEdit.exit() },
        modifier = modifier,
    )
}

/**
 * Wave 30 (pagesExplicit): deletes visual page [i] with everything placed on it. The items leave the home (apps stay in the drawer; folders,
 * widgets, shortcuts and links are removed the usual way, so edit mode's undo can bring them back), nothing moves to a neighbour page, and
 * nothing is placed again afterwards. The last page is emptied (Pages stays on with one empty page).
 */
fun deletePageNow(
    i: Int, pages: List<HomePage>, layout: HomeLayout, preserve: Set<String>,
    appsByKey: Map<String, AppEntry>, homeItems: HomeItems, stores: LauncherStores,
): HomePageOps.Result {
    val r = HomePageOps.delete(pages, i)
    com.bos.sphere.core.data.StateService.beforeDestructive(homeItems.context(), "delete page")
    r.taken.filter { it !in appsByKey }.forEach { homeItems.removeFromHome(it) }
    val gone = HomePageOps.groupOf(pages, i).map { pages[it].id }.toSet()
    val goneStored = layout.pages.filter { it.id in gone }.flatMapTo(HashSet()) { it.keys }
    val apps0 = r.taken.filter { it in appsByKey }.toSet()
    stores.layout.replace(layout.copy(pages = HomeLayoutMath.toStored(r.pages, layout.pages, preserve - r.taken - goneStored), removed = layout.removed + apps0, seeded = true))
    stores.layout.markExplicit()
    return r
}

/** "N items will be removed from this page; they stay in the drawer." before a page that holds something is deleted. */
@Composable
fun PageDeleteConfirm(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    com.bos.sphere.feature.grid.ConfirmDialog(
        title = L10n.tr("Delete this page?"),
        text = if (count == 1) L10n.tr("1 item will be removed from this page. It stays in the drawer.") else L10n.tr("%d items will be removed from this page. They stay in the drawer.", count),
        confirm = L10n.tr("Delete page"),
        dismiss = L10n.tr("Cancel"),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
