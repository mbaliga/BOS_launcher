package com.bos.sphere.core.catalog

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Wave 45 (editHandles), HOM-07 / HOM-12: the pure maths behind selecting, resizing and moving an item of the home page in
 * edit mode: the provider's resize limits in cells, which handles a widget gets, the resize of a cell rectangle by a handle
 * (the top and left handles move the origin), the collision rules and the snapping of the ghost. No Android types.
 */

/** A rectangle of whole grid cells (logical columns: column 0 is the first column of the home order). */
data class CellRect(val col: Int, val row: Int, val cols: Int, val rows: Int) {
    val right: Int get() = col + cols
    val bottom: Int get() = row + rows
    val span: WidgetSpan get() = WidgetSpan(cols, rows)
    fun overlaps(o: CellRect): Boolean = col < o.right && o.col < right && row < o.bottom && o.row < bottom
    fun contains(c: Int, r: Int): Boolean = c in col until right && r in row until bottom
}

fun TilePlacement.rect(): CellRect = CellRect(col, row, span.cols, span.rows)

/** A provider's resize rules as the platform reports them, in dp (0 = not given). `AppWidgetProviderInfo` fields of the same names. */
data class ProviderLimits(
    val minResizeWidthDp: Float = 0f,
    val minResizeHeightDp: Float = 0f,
    val maxResizeWidthDp: Float = 0f,
    val maxResizeHeightDp: Float = 0f,
    /** `targetCellWidth` / `targetCellHeight` (Android 12): the size the provider would like when first placed (0 = none). */
    val targetCellWidth: Int = 0,
    val targetCellHeight: Int = 0,
    val horizontal: Boolean = true,
    val vertical: Boolean = true,
    /** The provider's own minimum (`minWidth` / `minHeight`), used when it names no `minResize*`. */
    val minWidthDp: Float = 0f,
    val minHeightDp: Float = 0f,
)

/** The limits in whole cells for one widget on one grid. */
data class CellLimits(val minCols: Int, val maxCols: Int, val minRows: Int, val maxRows: Int, val horizontal: Boolean, val vertical: Boolean) {
    val canWiden: Boolean get() = horizontal && maxCols > minCols
    val canHeighten: Boolean get() = vertical && maxRows > minRows

    companion object {
        fun free(gridCols: Int, gridRows: Int) = CellLimits(1, gridCols.coerceAtLeast(1), 1, gridRows.coerceAtLeast(1), true, true)
    }
}

object ProviderLimitsMath {
    /**
     * Converts the provider's dp limits to cells at a pitch of [cellDpX] x [cellDpY] on a [gridCols] x [gridRows] grid.
     * The minimum rounds up (the widget must get at least the dp it asked for), the maximum rounds down. A single-axis provider
     * keeps its [current] size on the other axis. A provider minimum above the current size never forces a shrink: min <= current.
     * `null` limits (no provider info, a stack) mean free resizing within the grid.
     */
    fun toCells(l: ProviderLimits?, cellDpX: Float, cellDpY: Float, gridCols: Int, gridRows: Int, current: WidgetSpan): CellLimits {
        val gc = gridCols.coerceAtLeast(1); val gr = gridRows.coerceAtLeast(1)
        if (l == null) return CellLimits.free(gc, gr)
        val cx = cellDpX.coerceAtLeast(1f); val cy = cellDpY.coerceAtLeast(1f)
        fun lo(minResize: Float, own: Float, c: Float, cur: Int): Int {
            val dp = if (minResize > 0f) minResize else own
            val cells = if (dp > 0f) ceil(dp / c - EPS).toInt() else 1
            return cells.coerceIn(1, cur.coerceAtLeast(1))
        }
        fun hi(maxResize: Float, c: Float, grid: Int, min: Int): Int {
            val cells = if (maxResize > 0f) floor(maxResize / c + EPS).toInt() else grid
            return cells.coerceIn(min, max(min, grid))
        }
        val minC = lo(l.minResizeWidthDp, l.minWidthDp, cx, current.cols)
        val minR = lo(l.minResizeHeightDp, l.minHeightDp, cy, current.rows)
        var maxC = hi(l.maxResizeWidthDp, cx, gc, minC)
        var maxR = hi(l.maxResizeHeightDp, cy, gr, minR)
        var mnC = minC; var mnR = minR
        if (!l.horizontal) { mnC = current.cols.coerceIn(1, gc); maxC = mnC }
        if (!l.vertical) { mnR = current.rows.coerceIn(1, gr); maxR = mnR }
        // A widget already larger than its max (the provider changed): never force it smaller than where it is.
        maxC = max(maxC, min(current.cols, gc)); maxR = max(maxR, min(current.rows, gr))
        return CellLimits(mnC, maxC, mnR, maxR, l.horizontal, l.vertical)
    }

    private const val EPS = 0.001f
}

/** The eight resize handles; `west`/`north` ones move the origin of the widget. */
enum class ResizeHandle(val west: Boolean, val east: Boolean, val north: Boolean, val south: Boolean) {
    NorthWest(true, false, true, false), North(false, false, true, false), NorthEast(false, true, true, false),
    West(true, false, false, false), East(false, true, false, false),
    SouthWest(true, false, false, true), South(false, false, false, true), SouthEast(false, true, false, true);

    val isCorner: Boolean get() = (west || east) && (north || south)
    val moves: Boolean get() = west || north
    /** The handle on the other side of the same corner or edge. */
    fun mirrored(horizontally: Boolean, vertically: Boolean): ResizeHandle {
        val w = if (horizontally) east else west
        val e = if (horizontally) west else east
        val n = if (vertically) south else north
        val s = if (vertically) north else south
        return entries.first { it.west == w && it.east == e && it.north == n && it.south == s }
    }
}

object HandleSet {
    /** Below this size (dp) in a dimension the edge handles of the other axis are dropped: their 48 dp hit areas would overlap the corners. */
    const val SMALL_DP = 96f

    /**
     * The handles to draw. Both axes: four corners and four edge midpoints. One axis: only the two edges on that axis (the others
     * are hidden, not greyed). None: no handles. [originMoves] false (surfaces whose items flow, such as the lists) offers only the
     * south and east handles, since the top-left cannot move there. Edge handles are dropped on a side shorter than [SMALL_DP].
     */
    fun of(limits: CellLimits, widthDp: Float, heightDp: Float, originMoves: Boolean = true): List<ResizeHandle> {
        val h = limits.canWiden
        val v = limits.canHeighten
        val all = when {
            h && v -> {
                val corners = listOf(ResizeHandle.NorthWest, ResizeHandle.NorthEast, ResizeHandle.SouthWest, ResizeHandle.SouthEast)
                val edges = buildList {
                    if (widthDp >= SMALL_DP) { add(ResizeHandle.North); add(ResizeHandle.South) }
                    if (heightDp >= SMALL_DP) { add(ResizeHandle.West); add(ResizeHandle.East) }
                }
                corners + edges
            }
            h -> listOf(ResizeHandle.West, ResizeHandle.East)
            v -> listOf(ResizeHandle.North, ResizeHandle.South)
            else -> emptyList()
        }
        return if (originMoves) all else all.filter { !it.moves }
    }
}

enum class LimitReason { AtMinimum, AtMaximum, GridEdge }

data class ResizeResult(val rect: CellRect, val limited: Boolean, val reason: LimitReason? = null)

object ResizeMath {
    /** Cells moved by a pixel drag: the nearest whole cell. */
    fun cells(px: Float, pitchPx: Float): Int = if (pitchPx <= 0f) 0 else (px / pitchPx).roundToInt()

    /**
     * [start] resized by dragging [handle] by ([dCol], [dRow]) cells. West and north handles move the left / top edge (the origin
     * moves, the right / bottom edge stays); the others move the right / bottom edge. Everything clamps to [limits] and the grid;
     * [ResizeResult.limited] tells that the clamp changed the wish, with the [LimitReason].
     */
    fun resize(start: CellRect, handle: ResizeHandle, dCol: Int, dRow: Int, limits: CellLimits, gridCols: Int, gridRows: Int): ResizeResult {
        var col = start.col; var cols = start.cols; var row = start.row; var rows = start.rows
        var reason: LimitReason? = null
        if (limits.horizontal && (handle.west || handle.east)) {
            val wish: Int
            val maxByGrid: Int
            if (handle.east) { wish = start.cols + dCol; maxByGrid = gridCols - start.col } else { wish = start.cols - dCol; maxByGrid = start.right }
            val maxCols = min(limits.maxCols, maxByGrid.coerceAtLeast(1))
            cols = wish.coerceIn(limits.minCols, max(limits.minCols, maxCols))
            if (cols != wish) reason = reasonOf(wish, limits.minCols, limits.maxCols, maxByGrid)
            if (handle.west) col = start.right - cols
        }
        if (limits.vertical && (handle.north || handle.south)) {
            val wish: Int
            val maxByGrid: Int
            if (handle.south) { wish = start.rows + dRow; maxByGrid = gridRows - start.row } else { wish = start.rows - dRow; maxByGrid = start.bottom }
            val maxRows = min(limits.maxRows, maxByGrid.coerceAtLeast(1))
            rows = wish.coerceIn(limits.minRows, max(limits.minRows, maxRows))
            if (rows != wish) reason = reason ?: reasonOf(wish, limits.minRows, limits.maxRows, maxByGrid)
            if (handle.north) row = start.bottom - rows
        }
        return ResizeResult(CellRect(col, row, cols, rows), reason != null, reason)
    }

    /** [resize] from a drag in pixels at the given cell pitch. */
    fun resizePx(start: CellRect, handle: ResizeHandle, dxPx: Float, dyPx: Float, pitchX: Float, pitchY: Float, limits: CellLimits, gridCols: Int, gridRows: Int): ResizeResult =
        resize(start, handle, cells(dxPx, pitchX), cells(dyPx, pitchY), limits, gridCols, gridRows)

    private fun reasonOf(wish: Int, min: Int, max: Int, gridMax: Int): LimitReason = when {
        wish < min -> LimitReason.AtMinimum
        gridMax < max -> LimitReason.GridEdge
        else -> LimitReason.AtMaximum
    }
}

/** What an edit would do to the items around it. */
sealed interface EditVerdict {
    /** Free cells: nothing else changes. */
    data object Free : EditVerdict
    /** A widget would overlap [by]: not allowed. */
    data class Blocked(val by: String) : EditVerdict
    /** Icons around it reflow (they are pushed aside). */
    data object Push : EditVerdict
    /** A folder is made with, or the item is filed into, [target]. */
    data class Folder(val target: String) : EditVerdict
}

object EditCollision {
    /** Widgets block: a resize that would cover any other item is refused (the others never move for it). */
    fun resizeVerdict(placed: List<TilePlacement>, key: String, rect: CellRect): EditVerdict {
        val hit = placed.firstOrNull { it.key != key && it.rect().overlaps(rect) } ?: return EditVerdict.Free
        return EditVerdict.Blocked(hit.key)
    }

    private fun foldable(kind: ItemKind) = kind == ItemKind.App || kind == ItemKind.Shortcut || kind == ItemKind.Link
    private fun accepts(kind: ItemKind) = foldable(kind) || kind == ItemKind.Folder

    /**
     * Moving [key] so that it would cover [target]. A widget blocks (and is blocked by) every other item. An icon dropped on a single
     * icon makes a folder (or joins one); dropped on cells of a widget, or on several cells, the icons around it are pushed aside.
     */
    fun moveVerdict(placed: List<TilePlacement>, key: String, target: CellRect): EditVerdict {
        val hits = placed.filter { it.key != key && it.rect().overlaps(target) }
        if (hits.isEmpty()) return EditVerdict.Free
        val me = ItemKeys.kind(key)
        if (me == ItemKind.Widget) return EditVerdict.Blocked(hits.first().key)
        val only = hits.singleOrNull()
        if (only != null && only.span.cols == 1 && only.span.rows == 1 && foldable(me) && accepts(ItemKeys.kind(only.key))) return EditVerdict.Folder(only.key)
        return EditVerdict.Push
    }
}

/** Where the dashed ghost sits: the pixel rectangle of a drag snapped to whole cells. */
object GhostSnap {
    /** The cell rectangle nearest to a pixel rectangle (left, top, width, height) in the grid's own coordinates, kept inside the grid. */
    fun snap(leftPx: Float, topPx: Float, widthPx: Float, heightPx: Float, pitchX: Float, pitchY: Float, gridCols: Int, gridRows: Int): CellRect {
        val cols = (widthPx / pitchX).roundToInt().coerceIn(1, gridCols.coerceAtLeast(1))
        val rows = (heightPx / pitchY).roundToInt().coerceIn(1, gridRows.coerceAtLeast(1))
        val col = (leftPx / pitchX).roundToInt().coerceIn(0, gridCols - cols)
        val row = (topPx / pitchY).roundToInt().coerceIn(0, gridRows - rows)
        return CellRect(col, row, cols, rows)
    }
}

/** A page's items in order: which index puts an item at a wanted cell (the packer fills the first free spot). */
object OriginFit {
    /**
     * The index in the page's key list (without [key]) at which the packer puts [key], sized [span], on or nearest to
     * ([col], [row]). Ties keep the earlier index. Items that no longer fit count as infinitely far.
     */
    fun indexFor(keys: List<String>, spanOf: (String) -> WidgetSpan, cols: Int, rows: Int, key: String, span: WidgetSpan, col: Int, row: Int): Int {
        val rest = keys.filter { it != key }
        val sized: (String) -> WidgetSpan = { if (it == key) span else spanOf(it) }
        var best = rest.size
        var bestScore = Int.MAX_VALUE
        for (i in 0..rest.size) {
            val order = rest.toMutableList().also { it.add(i, key) }
            val p = TilePacker.pack(order, sized, cols, rows).placed.firstOrNull { it.key == key } ?: continue
            val score = abs(p.col - col) + abs(p.row - row)
            if (score < bestScore) { bestScore = score; best = i }
            if (score == 0) break
        }
        return best
    }

    fun placeOf(keys: List<String>, spanOf: (String) -> WidgetSpan, cols: Int, rows: Int, key: String): TilePlacement? =
        TilePacker.pack(keys, spanOf, cols, rows).placed.firstOrNull { it.key == key }
}

/** The one-step edits the accessibility actions and the keyboard make, in cells, with the reason when nothing happens. */
object CellEdit {
    enum class Reason { Edge, Blocked, AtMinimum, AtMaximum, NotAllowed }

    /** A move: the new index in the page's key list and the cell the item really lands on (an icon may be placed after reflow). */
    data class MoveResult(val ok: Boolean, val index: Int, val rect: CellRect?, val reason: Reason? = null)
    data class StepResult(val ok: Boolean, val rect: CellRect?, val moveOrigin: Boolean = false, val reason: Reason? = null)

    fun move(keys: List<String>, spanOf: (String) -> WidgetSpan, cols: Int, rows: Int, key: String, dCol: Int, dRow: Int): MoveResult {
        val packed = TilePacker.pack(keys, spanOf, cols, rows)
        val me = packed.placed.firstOrNull { it.key == key } ?: return MoveResult(false, keys.indexOf(key), null, Reason.NotAllowed)
        val target = CellRect(me.col + dCol, me.row + dRow, me.span.cols, me.span.rows)
        if (target.col < 0 || target.row < 0 || target.right > cols || target.bottom > rows) return MoveResult(false, keys.indexOf(key), me.rect(), Reason.Edge)
        if (ItemKeys.kind(key) == ItemKind.Widget && EditCollision.moveVerdict(packed.placed, key, target) is EditVerdict.Blocked) {
            return MoveResult(false, keys.indexOf(key), me.rect(), Reason.Blocked)
        }
        val idx = OriginFit.indexFor(keys, spanOf, cols, rows, key, me.span, target.col, target.row)
        val order = keys.filter { it != key }.toMutableList().also { it.add(idx, key) }
        val landed = TilePacker.pack(order, spanOf, cols, rows).placed.firstOrNull { it.key == key }?.rect()
        if (landed == null || landed == me.rect()) return MoveResult(false, keys.indexOf(key), me.rect(), Reason.Blocked)
        return MoveResult(true, idx, landed)
    }

    /**
     * One cell wider / narrower / taller / shorter. Growing tries the east (south) edge first and then the west (north) one, so a widget
     * at the right edge still grows; shrinking takes the east (south) edge. A growth into another item is refused (widgets block).
     */
    fun resizeStep(
        keys: List<String>, spanOf: (String) -> WidgetSpan, cols: Int, rows: Int, key: String, limits: CellLimits,
        dCols: Int, dRows: Int, originMoves: Boolean = true,
    ): StepResult {
        val packed = TilePacker.pack(keys, spanOf, cols, rows)
        val me = packed.placed.firstOrNull { it.key == key } ?: return StepResult(false, null, reason = Reason.NotAllowed)
        val start = me.rect()
        if (dCols != 0 && !limits.horizontal || dRows != 0 && !limits.vertical) return StepResult(false, start, reason = Reason.NotAllowed)
        val grow = dCols > 0 || dRows > 0
        val handles = if (grow) listOf(ResizeHandle.SouthEast, ResizeHandle.NorthWest, ResizeHandle.NorthEast, ResizeHandle.SouthWest) else listOf(ResizeHandle.SouthEast)
        var firstFail: Reason? = null
        for (h in handles) {
            if (h.moves && !originMoves) continue
            val east = if (h.east) 1 else -1; val south = if (h.south) 1 else -1
            val r = ResizeMath.resize(start, h, dCols * east, dRows * south, limits, cols, rows)
            if (r.rect == start) {
                firstFail = firstFail ?: when (r.reason) { LimitReason.AtMinimum -> Reason.AtMinimum; null -> Reason.AtMinimum; else -> Reason.AtMaximum }
                continue
            }
            if (EditCollision.resizeVerdict(packed.placed, key, r.rect) is EditVerdict.Blocked) { firstFail = firstFail ?: Reason.Blocked; continue }
            return StepResult(true, r.rect, moveOrigin = r.rect.col != start.col || r.rect.row != start.row)
        }
        return StepResult(false, start, reason = firstFail ?: Reason.AtMaximum)
    }
}

/** Applying a resize result to the shared home state (the host's edit path). */
object EditHandlesApply {
    /**
     * Sets [key]'s span to [rect]'s size and, when [moveOrigin], re-orders it on its page so that the packer puts it on [rect]'s origin.
     * Returns [state] unchanged when [key] is no widget.
     */
    fun resizeTo(state: HomeState, key: String, rect: CellRect, moveOrigin: Boolean, gridCols: Int, gridRows: Int, cap: (com.bos.sphere.core.data.PageLayout) -> Int): HomeState {
        if (key !in state.widgets) return state
        val sized = state.copy(widgets = state.widgets + (key to rect.span))
        if (!moveOrigin) return sized
        val pageIdx = sized.pages.indexOfFirst { key in it.keys }
        if (pageIdx < 0) return sized
        val keys = sized.pages[pageIdx].keys
        val idx = OriginFit.indexFor(keys, sized::spanOf, gridCols, gridRows, key, rect.span, rect.col, rect.row)
        return if (idx == keys.indexOf(key)) sized else HomeEdit.reorderOnPage(sized, key, idx, cap)
    }
}
