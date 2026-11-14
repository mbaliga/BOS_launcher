package com.bos.sphere.feature.grid

import com.bos.sphere.core.catalog.AdaptiveGrid
import com.bos.sphere.core.catalog.Box
import com.bos.sphere.core.catalog.HomeEdit
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.TilePacker
import com.bos.sphere.core.catalog.WidgetBand
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.input.InputMode

/*
 * HOM-01: the home surface contract. A surface renders the one shared [HomeState] for a window and
 * an input mode, reports where focus can go, and hands every item action back to the shared edit
 * functions. Grid, List, XMB and DSi are adapters of this contract, so switching surface changes
 * only the plan, never the data.
 */

enum class Dir { Left, Right, Up, Down }

/** A placed item. Cells are in surface units (grid cells; list rows; XMB / band slots). */
data class SurfaceCell(val key: String, val col: Int, val row: Int, val colSpan: Int = 1, val rowSpan: Int = 1)

data class FocusTarget(val key: String, val to: Map<Dir, String>)

data class SurfaceRequest(
    val state: HomeState,
    val windowWidthDp: Int,
    val windowHeightDp: Int,
    val input: InputMode = InputMode.Touch,
    /** Keys of the items shown (apps resolved by the caller, plus everything in [state]). Empty = the state's top-level keys. */
    val keys: List<String> = emptyList(),
    val density: Float = 1f,
)

data class SurfacePlan(
    val style: HomeStyle,
    val cells: List<SurfaceCell>,
    /** Widgets that live in the widget band (List, XMB, DSi); on the grid they are in [cells]. */
    val bandWidgets: List<String>,
    val focus: List<FocusTarget>,
    val focusVisible: Boolean,
    val overflow: List<String> = emptyList(),
) {
    /** Every key the plan accounts for. */
    fun allKeys(): Set<String> = cells.map { it.key }.toSet() + bandWidgets + overflow
}

sealed interface ItemAction {
    val key: String
    data class Open(override val key: String) : ItemAction
    data class Menu(override val key: String) : ItemAction
    data class Move(override val key: String, val page: Int, val index: Int) : ItemAction
    data class Resize(override val key: String, val dCols: Int, val dRows: Int, val max: WidgetSpan = WidgetSpan(8, 8)) : ItemAction
    data class FileInto(override val key: String, val targetKey: String, val newFolderId: String = "", val defaultName: String = "") : ItemAction
    data class Remove(override val key: String) : ItemAction
}

interface HomeSurfaceContract {
    val style: HomeStyle

    /** Render: where every item goes for this window and input mode. Pure; never edits the state. */
    fun plan(req: SurfaceRequest): SurfacePlan

    /** Accept an item action: returns the next shared state. Same result on every surface. */
    fun accept(action: ItemAction, state: HomeState, cap: (PageLayout) -> Int = { 12 }): HomeState = SharedItemActions.apply(action, state, cap)

    companion object {
        fun forStyle(style: HomeStyle): HomeSurfaceContract = when (style) {
            HomeStyle.Grid -> GridSurface
            HomeStyle.List -> ListSurface
            HomeStyle.Psp -> XmbSurface
            HomeStyle.Dsi -> DsiSurface
        }
    }
}

object SharedItemActions {
    fun apply(a: ItemAction, s: HomeState, cap: (PageLayout) -> Int): HomeState = when (a) {
        is ItemAction.Move -> HomeEdit.move(s, a.key, a.page, a.index, cap)
        is ItemAction.Resize -> HomeEdit.resizeWidget(s, a.key, a.dCols, a.dRows, max = a.max, gridCols = a.max.cols, gridRows = a.max.rows)
        is ItemAction.FileInto -> HomeEdit.dropOnto(s, a.key, a.targetKey, a.newFolderId.ifEmpty { "f" + s.folders.size + s.topLevelKeys().size }, a.defaultName.ifEmpty { "Folder" }, cap)
        is ItemAction.Remove -> HomeEdit.remove(s, a.key, cap)
        // Opening and menus are handled by the host UI; they do not change the arrangement.
        is ItemAction.Open, is ItemAction.Menu -> s
    }
}

private fun visibleKeys(r: SurfaceRequest): List<String> = r.keys.ifEmpty { r.state.topLevelKeys() }

private fun splitWidgets(keys: List<String>): Pair<List<String>, List<String>> = keys.partition { ItemKeys.kind(it) != ItemKind.Widget }

/** Focus graph of a row-major grid of [cols] columns: arrows move to the adjacent cell, rows wrap nowhere. */
internal fun gridFocus(cells: List<SurfaceCell>): List<FocusTarget> {
    val at = HashMap<Pair<Int, Int>, String>()
    for (c in cells) for (y in c.row until c.row + c.rowSpan) for (x in c.col until c.col + c.colSpan) at[x to y] = c.key
    return cells.map { c ->
        val m = LinkedHashMap<Dir, String>()
        at[c.col - 1 to c.row]?.takeIf { it != c.key }?.let { m[Dir.Left] = it }
        at[c.col + c.colSpan to c.row]?.takeIf { it != c.key }?.let { m[Dir.Right] = it }
        at[c.col to c.row - 1]?.takeIf { it != c.key }?.let { m[Dir.Up] = it }
        at[c.col to c.row + c.rowSpan]?.takeIf { it != c.key }?.let { m[Dir.Down] = it }
        FocusTarget(c.key, m)
    }
}

private fun chainFocus(keys: List<String>, prev: Dir, next: Dir): List<FocusTarget> = keys.mapIndexed { i, k ->
    val m = LinkedHashMap<Dir, String>()
    keys.getOrNull(i - 1)?.let { m[prev] = it }
    keys.getOrNull(i + 1)?.let { m[next] = it }
    FocusTarget(k, m)
}

object GridSurface : HomeSurfaceContract {
    override val style = HomeStyle.Grid

    override fun plan(req: SurfaceRequest): SurfacePlan {
        val area = HomeFrame.appArea(req.windowWidthDp.toFloat(), req.windowHeightDp.toFloat())
        val dims = AdaptiveGrid.dims(area.width, area.height, req.density)
        // On the grid, widgets share the tile layout model with app tiles (HOM-08).
        val packed = TilePacker.pack(visibleKeys(req), { req.state.spanOf(it) }, dims.columns, dims.rows)
        val cells = packed.placed.map { SurfaceCell(it.key, it.col, it.row, it.span.cols, it.span.rows) }
        return SurfacePlan(style, cells, emptyList(), gridFocus(cells), req.input != InputMode.Touch, packed.overflow)
    }
}

object ListSurface : HomeSurfaceContract {
    override val style = HomeStyle.List

    override fun plan(req: SurfaceRequest): SurfacePlan {
        val (rows, widgets) = splitWidgets(visibleKeys(req))
        val cells = rows.mapIndexed { i, k -> SurfaceCell(k, 0, i) }
        return SurfacePlan(style, cells, widgets, chainFocus(rows, Dir.Up, Dir.Down), req.input != InputMode.Touch)
    }
}

object XmbSurface : HomeSurfaceContract {
    override val style = HomeStyle.Psp

    override fun plan(req: SurfaceRequest): SurfacePlan {
        val (bar, widgets) = splitWidgets(visibleKeys(req))
        val cells = bar.mapIndexed { i, k -> SurfaceCell(k, i, 0) }
        return SurfacePlan(style, cells, widgets, chainFocus(bar, Dir.Left, Dir.Right), req.input != InputMode.Touch)
    }
}

object DsiSurface : HomeSurfaceContract {
    override val style = HomeStyle.Dsi
    private const val COLUMNS = 4

    override fun plan(req: SurfaceRequest): SurfacePlan {
        val (tiles, widgets) = splitWidgets(visibleKeys(req))
        val cells = tiles.mapIndexed { i, k -> SurfaceCell(k, i % COLUMNS, i / COLUMNS) }
        return SurfacePlan(style, cells, widgets, gridFocus(cells), req.input != InputMode.Touch)
    }
}

/** Where the widget band sits for a window: below tiles in portrait, at the end in landscape. */
fun widgetBandEdge(widthDp: Int, heightDp: Int): WidgetBand.Edge = if (widthDp > heightDp) WidgetBand.Edge.End else WidgetBand.Edge.Bottom

/** Tile area and band for the non-grid surfaces, inside the frame's app area. */
fun widgetBandLayout(widthDp: Int, heightDp: Int, widgetCount: Int): WidgetBand.Layout {
    val a = HomeFrame.appArea(widthDp.toFloat(), heightDp.toFloat())
    return WidgetBand.layout(Box(a.left, a.top, a.right, a.bottom), widgetCount, widgetBandEdge(widthDp, heightDp))
}
