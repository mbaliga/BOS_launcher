package com.bos.sphere

import android.os.Process
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.bos.sphere.feature.grid.AddCarouselDrag
import com.bos.sphere.feature.grid.EditCardGeometry
import com.bos.sphere.feature.grid.PageEditorGeometry
import com.bos.sphere.feature.grid.PageScrollRequest
import com.bos.sphere.feature.grid.PageEditorHost
import com.bos.sphere.feature.grid.PageEditorMenu
import com.bos.sphere.feature.grid.rememberPageEditorLayout
import com.bos.sphere.feature.grid.GridHomeState
import com.bos.sphere.feature.grid.UndoNotice
import com.bos.sphere.feature.grid.rememberEditCardProgress
import kotlinx.coroutines.launch

/**
 * HOM-12: the home as a card while it is edited. The progress and the target are read only inside the home layer's
 * graphicsLayer (see [com.bos.sphere.feature.grid.editCardLayer]), so the transition never recomposes the home.
 */
class EditCardState(val progress: State<Float>, val layout: PageEditorGeometry.Layout, val reduced: Boolean, val continuous: Boolean) {
    val target: EditCardGeometry.Target get() = layout.target
    /** Wave 36 (pageEditor): the swing of the live page into the focus slot (0 = at the tapped card, 1 = at rest) and where the tapped card was, in px from the slot. */
    val swingProgress = mutableFloatStateOf(1f)
    val swingDx = mutableFloatStateOf(0f)
    val swingDy = mutableFloatStateOf(0f)
    /** True while the Glance card is the focus: the live page is out of the way and the editor draws the Glance card in the slot. */
    val glanceFocus = mutableStateOf(false)
    /** The app being dragged out of the Apps tab (the home draws its ghost above everything: [com.bos.sphere.feature.grid.PageEditorGhost]). */
    val drag = AddCarouselDrag()
    /** Wave 37 (editorDebt): Continuous has no pager, so the editor keeps the selected page; the scroll surface follows it (and it follows the surface when the user scrolls). */
    val contSel = mutableIntStateOf(0)
    /** The latest request to bring a page's first column to the start edge of the Continuous surface. */
    val pageScroll = mutableStateOf<PageScrollRequest?>(null)
    /** The overlay registers the one add-an-empty-page path here, so the edit bar's Add page button takes the same path as the strip's "+" card. */
    var addPageAction: (() -> Unit)? = null
    fun addPage() { addPageAction?.invoke() }

    fun swing(): PageEditorGeometry.Swing = PageEditorGeometry.swing(swingProgress.floatValue, swingDx.floatValue, swingDy.floatValue, reduced)

    /** The card frame of the live page, with the swing added. Read only inside the home layer's graphicsLayer. */
    fun frame(): EditCardGeometry.Frame {
        val f = EditCardGeometry.frame(target, progress.value, reduced)
        val s = swing()
        val away = if (glanceFocus.value) HIDDEN_PX else 0f
        if (s.atRest && away == 0f) return f
        return f.copy(
            scale = f.scale * s.scale, translationX = f.translationX + s.translationX + away, translationY = f.translationY + s.translationY,
            rotationX = s.rotationX, rotationY = s.rotationY, layerAlpha = s.alpha,
        )
    }

    private companion object { const val HIDDEN_PX = 100_000f }
}

@Composable
fun rememberEditCardState(active: Boolean, continuous: Boolean = false, orderRtl: Boolean = false): EditCardState {
    val progress = rememberEditCardProgress(active)
    val layout = rememberPageEditorLayout(continuous, orderRtl)
    val reduced = rememberMotionPrefs().reduced
    return remember(progress, layout, reduced, continuous) { EditCardState(progress, layout, reduced, continuous) }
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
    // Paginated: the page being edited is the home pager's page. Continuous has no pager, so the editor keeps the selected page itself.
    val current = (if (card.continuous) card.contSel.intValue else gridState.currentPage).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    // Continuous: the selected page changes and the scroll surface moves to that page's first column (the swing is drawn over it).
    fun showPage(i: Int) { if (card.continuous) { card.contSel.intValue = i; card.pageScroll.value = PageScrollRequest(i) } else scope.launch { gridState.goToPage(i, animate = false) } }
    LaunchedEffect(goto, pages.size) {
        if (goto >= 0) { val t = goto.coerceAtMost(pages.size - 1).coerceAtLeast(0); goto = -1; card.glanceFocus.value = false; showPage(t) }
    }
    LaunchedEffect(homeEdit.active) { if (!homeEdit.active) { card.glanceFocus.value = false; card.swingProgress.floatValue = 1f } }
    // The swing: the live page starts where the tapped card was and tilts into the focus slot (about 350 ms; a quick cross-fade with reduced motion). Nothing runs when idle.
    val swingAnim = remember { Animatable(1f) }
    fun startSwing(dx: Float, dy: Float) {
        card.swingDx.floatValue = dx; card.swingDy.floatValue = dy; card.swingProgress.floatValue = 0f
        scope.launch {
            swingAnim.snapTo(0f)
            swingAnim.animateTo(1f, tween(if (card.reduced) PageEditorGeometry.SWING_REDUCED_MS else PageEditorGeometry.SWING_MS, easing = FastOutSlowInEasing)) { card.swingProgress.floatValue = value }
            card.swingProgress.floatValue = 1f
        }
    }
    fun apply(r: HomePageOps.Result) { savePages(r.pages); goto = r.focus }
    // The one add-an-empty-page path: after the last page, focus the new page. The strip's "+" card passes where it really is; the edit bar has no card, so the swing starts from where the card is when the strip is not scrolled.
    fun addOnePage(dx: Float, dy: Float) { startSwing(dx, dy); apply(HomePageOps.add(pages, pages.size - 1)) }
    val latestAdd by androidx.compose.runtime.rememberUpdatedState {
        val entries = PageEditorGeometry.entries(prefs.glanceEnabled, pages.size)
        val focus = PageEditorGeometry.focusPosition(prefs.glanceEnabled, card.glanceFocus.value, current)
        val (dx, dy) = PageEditorGeometry.offsetOf(entries.lastIndex, focus, card.layout)
        addOnePage(dx, dy)
    }
    androidx.compose.runtime.DisposableEffect(card) { card.addPageAction = { latestAdd() }; onDispose { card.addPageAction = null } }
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
    // Wave 30 (pagesExplicit): the app goes on the page asked for; a full page says so and nothing else moves or opens.
    fun addAppTo(app: AppEntry, page: Int): Boolean {
        val placed = AddCarouselModel.place(pages, app.key, page, capacityOf)
        if (placed == null) { android.widget.Toast.makeText(homeItems.context(), L10n.tr("Page full. Add a page or remove an item."), android.widget.Toast.LENGTH_SHORT).show(); return false }
        stores.layout.replace(layout.copy(pages = HomeLayoutMath.toStored(placed, layout.pages, preserve), removed = layout.removed - app.key, seeded = true))
        stores.layout.markExplicit()
        return true
    }
    PageEditorHost(
        progress = card.progress, reduced = card.reduced, layout = card.layout, continuous = card.continuous,
        pages = pages, current = current, glanceOn = prefs.glanceEnabled, glanceFocus = card.glanceFocus.value, swing = { card.swing() },
        appsByKey = appsByKey, prefs = prefs, isLandscape = isLandscape,
        apps = apps, onHome = onHome, hasWork = remember(apps) { apps.any { it.user != me } }, isWork = { it.user != me },
        drag = card.drag,
        onSelectPage = { i, dx, dy -> if (card.glanceFocus.value || i != current) { startSwing(dx, dy); card.glanceFocus.value = false; showPage(i) } },
        onSelectGlance = { dx, dy -> if (!card.glanceFocus.value) { startSwing(dx, dy); card.glanceFocus.value = true } },
        onAddPage = { dx, dy -> addOnePage(dx, dy) },
        onDelete = { i -> if (HomePageOps.itemCount(pages, i) == 0) deleteNow(i) else confirmDelete = i },
        onMove = { i, d -> HomePageOps.move(pages, i, d)?.let(::apply) },
        onAddApp = { app -> if (addAppTo(app, current)) goto = current },
        onDropApp = { app, page ->
            if (addAppTo(app, page) && page != current) android.widget.Toast.makeText(homeItems.context(), L10n.tr("Added to page %d", page + 1), android.widget.Toast.LENGTH_SHORT).show()
        },
        onWidget = { homeItems.widgetRequest++ },
        onFolder = { homeItems.folderOpen = true },
        onShortcut = { homeItems.shortcutOpen = true },
        onLink = { homeItems.linkOpen = true },
        onFile = { homeItems.fileRequest++ },
        onGroup = { homeItems.groupOpen = true },
        onPrefs = { f -> stores.prefs.update(f) },
        notice = notice, onNoticeDone = { notice = null },
        focusAdd = focusAdd, onExit = { homeEdit.exit() },
        modifier = modifier,
        menu = PageEditorMenu(
            canUndo = homeEdit.canUndo, canRedo = homeEdit.canRedo, onUndo = { homeEdit.undo() }, onRedo = { homeEdit.redo() },
            onAddPage = { card.addPage() }, onDone = { homeEdit.exit() },
        ),
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
