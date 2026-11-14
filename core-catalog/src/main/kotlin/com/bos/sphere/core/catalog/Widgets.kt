package com.bos.sphere.core.catalog

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Axis-aligned float rectangle used by the layout math below. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun intersects(o: Box): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    fun contains(o: Box): Boolean = o.left >= left - EPS && o.top >= top - EPS && o.right <= right + EPS && o.bottom <= bottom + EPS
    companion object { const val EPS = 0.01f }
}

/** A widget (or tile) placed on the grid: top-left cell and its span. */
data class TilePlacement(val key: String, val col: Int, val row: Int, val span: WidgetSpan)

/**
 * HOM-08: the shared tile layout model of the grid surface. App tiles are 1x1; widgets take their
 * span. Items are placed in order, each in the first free spot (row-major), so a widget never
 * overlaps a tile. Items that no longer fit are returned in [Packed.overflow] (the page splits).
 */
object TilePacker {
    data class Packed(val placed: List<TilePlacement>, val overflow: List<String>)

    fun pack(keys: List<String>, spanOf: (String) -> WidgetSpan, cols: Int, rows: Int): Packed {
        val c = cols.coerceAtLeast(1)
        val r = rows.coerceAtLeast(1)
        val used = Array(r) { BooleanArray(c) }
        val placed = ArrayList<TilePlacement>()
        val overflow = ArrayList<String>()
        for (k in keys) {
            val s = spanOf(k).let { WidgetSpan(min(it.cols, c), min(it.rows, r)) }
            var done = false
            loop@ for (row in 0..r - s.rows) for (col in 0..c - s.cols) {
                if (fits(used, col, row, s)) {
                    for (y in row until row + s.rows) for (x in col until col + s.cols) used[y][x] = true
                    placed += TilePlacement(k, col, row, s)
                    done = true
                    break@loop
                }
            }
            if (!done) overflow += k
        }
        return Packed(placed, overflow)
    }

    private fun fits(used: Array<BooleanArray>, col: Int, row: Int, s: WidgetSpan): Boolean {
        for (y in row until row + s.rows) for (x in col until col + s.cols) if (used[y][x]) return false
        return true
    }

    /** Cell area free after packing: used to decide whether a widget can grow in place. */
    fun canGrow(placed: List<TilePlacement>, key: String, to: WidgetSpan, cols: Int, rows: Int): Boolean {
        val me = placed.firstOrNull { it.key == key } ?: return false
        if (me.col + to.cols > cols || me.row + to.rows > rows) return false
        return placed.filter { it.key != key }.none { o ->
            me.col < o.col + o.span.cols && o.col < me.col + to.cols && me.row < o.row + o.span.rows && o.row < me.row + to.rows
        }
    }
}

/** HOM-07: sizing a widget by provider hints, handles and keys. */
object WidgetSizing {
    /** Cells needed for a provider that asks for [minWidthDp] x [minHeightDp] at cell pitch [cellDp]. */
    fun spanFor(minWidthDp: Int, minHeightDp: Int, cellDp: Float): WidgetSpan =
        WidgetSpan(ceil(minWidthDp / cellDp).toInt().coerceAtLeast(1), ceil(minHeightDp / cellDp).toInt().coerceAtLeast(1))

    enum class Handle { East, South, SouthEast, West, North }

    /** The span change a handle drag of ([dxPx], [dyPx]) makes at the given cell pitch. West / North handles grow when dragged outward (negative delta). */
    fun deltaForDrag(handle: Handle, dxPx: Float, dyPx: Float, cellWpx: Float, cellHpx: Float): Pair<Int, Int> {
        val dc = (dxPx / cellWpx).roundToInt()
        val dr = (dyPx / cellHpx).roundToInt()
        return when (handle) {
            Handle.East -> dc to 0
            Handle.South -> 0 to dr
            Handle.SouthEast -> dc to dr
            Handle.West -> -dc to 0
            Handle.North -> 0 to -dr
        }
    }

    enum class Arrow { Left, Right, Up, Down }

    /** Keyboard resize: Shift+arrow grows right / down and shrinks left / up. */
    fun deltaForKey(a: Arrow): Pair<Int, Int> = when (a) {
        Arrow.Right -> 1 to 0
        Arrow.Left -> -1 to 0
        Arrow.Down -> 0 to 1
        Arrow.Up -> 0 to -1
    }
}

/**
 * HOM-08: on List, XMB and DSi, widgets live in a defined band beside or below the tiles. The band
 * is carved out of the app area first, so tiles and widgets can never overlap.
 */
object WidgetBand {
    enum class Edge { Bottom, End }

    data class Layout(val tiles: Box, val band: Box?, val widgets: List<Box>)

    fun layout(area: Box, widgetCount: Int, edge: Edge, bandFraction: Float = 0.3f, gap: Float = 8f): Layout {
        if (widgetCount <= 0) return Layout(area, null, emptyList())
        val f = bandFraction.coerceIn(0.15f, 0.5f)
        val (tiles, band) = when (edge) {
            Edge.Bottom -> Box(area.left, area.top, area.right, area.bottom - area.height * f) to Box(area.left, area.bottom - area.height * f, area.right, area.bottom)
            Edge.End -> Box(area.left, area.top, area.right - area.width * f, area.bottom) to Box(area.right - area.width * f, area.top, area.right, area.bottom)
        }
        val horizontal = edge == Edge.Bottom
        val n = widgetCount
        val total = (if (horizontal) band.width else band.height) - gap * (n - 1)
        val each = max(1f, total / n)
        val ws = (0 until n).map { i ->
            val off = i * (each + gap)
            if (horizontal) Box(band.left + off, band.top + gap, band.left + off + each, band.bottom - gap)
            else Box(band.left + gap, band.top + off, band.right - gap, band.top + off + each)
        }
        return Layout(tiles, band, ws)
    }
}

/** HOM-14: several widgets in one slot, switched by swipe, wheel or keys. */
data class WidgetStack(val keys: List<String>, val active: Int = 0) {
    val current: String? get() = keys.getOrNull(active)
    fun next(): WidgetStack = if (keys.isEmpty()) this else copy(active = (active + 1) % keys.size)
    fun previous(): WidgetStack = if (keys.isEmpty()) this else copy(active = (active - 1 + keys.size) % keys.size)
    fun select(i: Int): WidgetStack = if (i in keys.indices) copy(active = i) else this

    /** Wheel: positive scroll is "forward". Swipe up / left and Page Down / Right map to forward too (see [forKey]). */
    fun scroll(delta: Float): WidgetStack = when { delta > 0f -> next(); delta < 0f -> previous(); else -> this }

    fun add(key: String): WidgetStack = if (key in keys) this else copy(keys = keys + key, active = keys.size)

    fun remove(key: String): WidgetStack {
        val i = keys.indexOf(key)
        if (i < 0) return this
        val left = keys - key
        return WidgetStack(left, if (left.isEmpty()) 0 else min(active.let { if (i < it) it - 1 else it }, left.lastIndex))
    }

    /** Arrow keys and Page Up / Down on a focused stack. */
    fun forKey(a: WidgetSizing.Arrow): WidgetStack = when (a) {
        WidgetSizing.Arrow.Right, WidgetSizing.Arrow.Down -> next()
        WidgetSizing.Arrow.Left, WidgetSizing.Arrow.Up -> previous()
    }
}

/**
 * HOM-07: widgets restore after a reboot and after a provider update. The host keeps bound ids,
 * but an update can drop a provider or invalidate an id; this decides, per saved widget, what to do.
 */
data class SavedWidget(val key: String, val appWidgetId: Int, val provider: String)

sealed interface RestoreAction {
    val widget: SavedWidget
    /** Still bound to an installed provider: just host it. */
    data class Keep(override val widget: SavedWidget) : RestoreAction
    /** The id is gone but the provider exists: allocate and bind a new id (may need a configure step). */
    data class Rebind(override val widget: SavedWidget) : RestoreAction
    /** The provider is not installed (yet): show a placeholder and keep the slot. */
    data class Placeholder(override val widget: SavedWidget) : RestoreAction
}

object WidgetRestore {
    fun plan(saved: List<SavedWidget>, hostedIds: Set<Int>, installedProviders: Set<String>): List<RestoreAction> = saved.map { w ->
        when {
            w.provider !in installedProviders -> RestoreAction.Placeholder(w)
            w.appWidgetId in hostedIds -> RestoreAction.Keep(w)
            else -> RestoreAction.Rebind(w)
        }
    }
}

/** Searchable widget picker: matches app label and widget label. */
data class WidgetChoiceRef(val provider: String, val appLabel: String, val widgetLabel: String, val minWidthDp: Int = 0, val minHeightDp: Int = 0)

object WidgetPickerSearch {
    fun filter(all: List<WidgetChoiceRef>, query: String): List<WidgetChoiceRef> {
        val q = FolderSearch.normalize(query)
        if (q.isEmpty()) return all.sortedWith(compareBy({ FolderSearch.normalize(it.appLabel) }, { FolderSearch.normalize(it.widgetLabel) }))
        return all.mapNotNull { c ->
            val a = FolderSearch.normalize(c.appLabel); val w = FolderSearch.normalize(c.widgetLabel)
            val s = when { a.startsWith(q) || w.startsWith(q) -> 2; a.contains(q) || w.contains(q) -> 1; else -> return@mapNotNull null }
            c to s
        }.sortedWith(compareByDescending<Pair<WidgetChoiceRef, Int>> { it.second }.thenBy { FolderSearch.normalize(it.first.appLabel) }).map { it.first }
    }
}
