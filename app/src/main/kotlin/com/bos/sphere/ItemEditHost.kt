package com.bos.sphere

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.ClockTileOptions
import com.bos.sphere.feature.grid.HomeItemSelection
import com.bos.sphere.feature.grid.ItemActionLayer
import com.bos.sphere.feature.grid.ItemSheet
import com.bos.sphere.feature.grid.ItemSheetActions
import com.bos.sphere.feature.grid.ItemSheetModel
import com.bos.sphere.feature.grid.RemovedNotice
import com.bos.sphere.feature.grid.SheetCaps
import com.bos.sphere.feature.grid.SheetKind

/** Wave 45 (editSheet): the selection of the item the action bar and sheet are about, and whether its sheet is open. Written from event handlers only. */
class ItemEditState {
    val selection = HomeItemSelection()
    var sheetOpen by mutableStateOf(false)
    var notice by mutableStateOf<RemovedNotice?>(null)
    fun select(key: String, bounds: Rect?) { selection.select(key, bounds); sheetOpen = false }
    fun openSheet(key: String) { if (selection.selectedKey != key) selection.select(key, null); sheetOpen = true }
    fun clear() { selection.clear(); sheetOpen = false }
}

@Composable
fun rememberItemEditState() = remember { ItemEditState() }

/** What the item edit layer needs from the home; plain lambdas so it stays testable. */
class ItemEditOps(
    val appOf: (String) -> AppEntry?,
    val labelOf: (String) -> String,
    val hidden: Set<String>,
    val clock: ClockTileOptions,
    val onClock: (ClockTileOptions) -> Unit,
    val remove: (String) -> Unit,
    val undo: () -> Unit,
    val launch: (AppEntry) -> Unit,
    val appInfo: (AppEntry) -> Unit,
    val changeIcon: (AppEntry) -> Unit,
    val setHidden: (AppEntry, Boolean) -> Unit,
    val uninstall: (AppEntry) -> Unit,
    val openItem: (String) -> Unit,
    val startResize: (String) -> Unit,
    val canReconfigure: (String) -> Boolean,
    val reconfigure: (String) -> Unit,
    val resizeTo: (String, Int, Int) -> Unit,
    val spanOf: (String) -> Pair<Int, Int>,
    val grid: () -> Pair<Int, Int>,
    val dissolveFolder: (String) -> Unit,
    val editFolder: (String) -> Unit,
)

/** HOM-07 / HOM-12: the item action bar, its Undo snackbar and the item sheet, composed while home edit mode is on. */
@Composable
fun ItemEditHost(state: ItemEditState, ops: ItemEditOps, reserveTop: androidx.compose.ui.unit.Dp, reserveBottom: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    DisposableEffect(Unit) { onDispose { state.clear() } }
    ItemActionLayer(
        source = state.selection, notice = state.notice, onNoticeDone = { state.notice = null },
        onResize = { ops.startResize(it); state.clear() },
        onRemove = { key ->
            val name = ops.labelOf(key)
            ops.remove(key)
            state.clear()
            state.notice = RemovedNotice(L10n.tr("Removed %s", name), onUndo = { ops.undo() })
        },
        onSettings = { state.sheetOpen = true },
        label = ops.labelOf, modifier = modifier, reserveTop = reserveTop, reserveBottom = reserveBottom,
    )
    val key = state.selection.selectedKey
    if (state.sheetOpen && key != null) {
        val kind = ItemSheetModel.kindOf(key)
        val app = ops.appOf(key)
        val (gc, gr) = ops.grid()
        val span = ops.spanOf(key)
        val caps = SheetCaps(canReconfigure = kind == SheetKind.Widget && ops.canReconfigure(key))
        val dismiss = { state.sheetOpen = false }
        val actions = ItemSheetActions(
            onClock = ops.onClock,
            onOpen = { app?.let { ops.launch(it) } ?: ops.openItem(key); state.clear() },
            onAppInfo = { app?.let { ops.appInfo(it) } },
            onChangeIcon = { app?.let { ops.changeIcon(it) } },
            hidden = app != null && app.key in ops.hidden,
            onToggleHidden = { app?.let { ops.setHidden(it, it.key !in ops.hidden) }; state.clear() },
            onUninstall = { app?.let { ops.uninstall(it) } },
            onFolderEdit = { ops.editFolder(key) },
            onDissolve = { ops.dissolveFolder(key); state.clear() },
            onReconfigure = { ops.reconfigure(key) },
            widgetSpan = span, gridCols = gc, gridRows = gr,
            onWidgetSize = { c, r -> ops.resizeTo(key, c, r) },
        )
        ItemSheet(kind, app?.label ?: L10n.tr(if (kind == SheetKind.Clock) ItemSheetModel.title(kind) else ops.labelOf(key).ifBlank { ItemSheetModel.title(kind) }), ops.clock, caps, actions, dismiss)
    }
}
