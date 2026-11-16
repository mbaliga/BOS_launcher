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
    val onHome = remember(layout, folderKeys) { layout.pages.flatMapTo(HashSet()) { it.keys } + folderKeys }
    EditCardHost(
        progress = card.progress, reduced = card.reduced, target = card.target,
        pages = pages, current = { gridState.currentPage }, appsByKey = appsByKey, prefs = prefs, isLandscape = isLandscape,
        apps = apps, onHome = onHome, hasWork = remember(apps) { apps.any { it.user != me } }, isWork = { it.user != me },
        onSelect = { i -> scope.launch { gridState.goToPage(i) } },
        onDelete = { i ->
            val r = HomePageOps.delete(pages, i, capacityOf)
            val base = homeEdit.undoDepth
            if (r.taken.isEmpty()) {
                com.bos.sphere.core.data.StateService.beforeDestructive(homeItems.context(), "delete page")
                apply(r)
            } else {
                // The last page cannot vanish: it is emptied. Apps go off the home (they stay in the drawer); folders, widgets and shortcuts are removed the usual way.
                r.taken.filter { it !in appsByKey }.forEach { homeItems.removeFromHome(it) }
                val apps0 = r.taken.filter { it in appsByKey }.toSet()
                stores.layout.replace(layout.copy(pages = HomeLayoutMath.toStored(r.pages, layout.pages, preserve - r.taken), removed = layout.removed + apps0, seeded = true))
                goto = 0
            }
            notice = UndoNotice(L10n.tr(if (r.taken.isEmpty()) "Page deleted" else "Page emptied"), onUndo = {
                // One tap undoes the whole page edit, however many layout writes it took.
                repeat((homeEdit.undoDepth - base).coerceAtLeast(1)) { homeEdit.undo() }
            })
        },
        onMove = { i, d -> HomePageOps.move(pages, i, d)?.let(::apply) },
        onMoveTo = { i, t -> HomePageOps.moveTo(pages, i, t)?.let(::apply) },
        onAddPage = { apply(HomePageOps.add(pages, gridState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))) },
        onAddApp = { app ->
            val placed = AddCarouselModel.place(pages, app.key, gridState.currentPage, capacityOf)
            stores.layout.replace(layout.copy(pages = HomeLayoutMath.toStored(placed, layout.pages, preserve), removed = layout.removed - app.key, seeded = true))
            goto = placed.indexOfFirst { app.key in it.keys }.coerceAtLeast(0)
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
