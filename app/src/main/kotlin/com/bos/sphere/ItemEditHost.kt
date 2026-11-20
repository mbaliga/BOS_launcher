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
import com.bos.sphere.feature.grid.EditSelectionState
import com.bos.sphere.feature.grid.ItemActionLayer
import com.bos.sphere.feature.grid.ItemSheet
import com.bos.sphere.feature.grid.ItemSheetActions
import com.bos.sphere.feature.grid.ItemSheetModel
import com.bos.sphere.feature.grid.RemovedNotice
import com.bos.sphere.feature.grid.SheetCaps
import com.bos.sphere.feature.grid.SheetKind

/**
 * Wave 45 (editSheet), joined in wave 47: whether the sheet of the selected item is open, and the Undo notice of a removal. The selection itself is the
 * home's one [EditSelectionState] (the same object the handles, the outline and the keyboard use). Written from event handlers only.
 */
class ItemEditState(val selection: EditSelectionState) {
    var sheetOpen by mutableStateOf(false)
    var notice by mutableStateOf<RemovedNotice?>(null)
    /** A long press on a surface that has no selection of its own (scroll homes, tiles): selects through the one state, the touch's rect as first bounds. */
    fun select(key: String, bounds: Rect?) { selection.select(key); bounds?.let { selection.report(key, it) }; sheetOpen = false }
    fun openSheet(key: String) { if (selection.selectedKey != key) selection.select(key); sheetOpen = true }
    fun clear() { selection.clear(); sheetOpen = false }
}

@Composable
fun rememberItemEditState(selection: EditSelectionState) = remember(selection) { ItemEditState(selection) }

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
    val widgetLimits: (String) -> com.bos.sphere.core.catalog.CellLimits?,
    val spanOf: (String) -> Pair<Int, Int>,
    val grid: () -> Pair<Int, Int>,
    val dissolveFolder: (String) -> Unit,
    val editFolder: (String) -> Unit,
)

/** HOM-07 / HOM-12: the item action bar, its Undo snackbar and the item sheet, composed while home edit mode is on. */
@Composable
fun ItemEditHost(state: ItemEditState, ops: ItemEditOps, reserveTop: androidx.compose.ui.unit.Dp, reserveBottom: androidx.compose.ui.unit.Dp, avoid: List<Rect> = emptyList(), modifier: Modifier = Modifier) {
    DisposableEffect(Unit) { onDispose { state.clear() } }
    ItemActionLayer(
        source = state.selection, notice = state.notice, onNoticeDone = { state.notice = null },
        onResize = { ops.startResize(it) }, // keeps the selection: the handles of the selected widget are the resize
        avoid = avoid, showOutline = false, // the handles stream draws the one outline
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
            widgetLimits = if (kind == SheetKind.Widget) ops.widgetLimits(key) else null,
        )
        ItemSheet(kind, app?.label ?: L10n.tr(if (kind == SheetKind.Clock) ItemSheetModel.title(kind) else ops.labelOf(key).ifBlank { ItemSheetModel.title(kind) }), ops.clock, caps, actions, dismiss)
    }
}
