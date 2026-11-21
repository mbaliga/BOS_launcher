package com.bos.sphere.core.catalog

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Wave 26 (HOM-01, HOM-07): the pure math of the SCROLL home (the Windows 8 start-screen look). One continuous surface scrolls
 * horizontally; the items of the pages after Glance fill it COLUMN BY COLUMN, top to bottom, then on into the next column. The number
 * of rows comes from the height of the occupy-able middle space (between the status bar / top hotspots and the bottom hotspots / gesture
 * area) and the cell size, so it is the same in portrait and landscape and on a foldable. Widgets span cells ([WidgetSpan]) and are
 * packed first-fit in column-major order, so a spanning widget never leaves the column order, only holes it can later be filled into.
 */
object ColumnFill {
    const val MIN_ROWS = 1
    const val MAX_ROWS = 10

    /** One placed item: its cell column and row (logical column 0 = the first column on the start side) and its span. */
    data class Cell(val key: String, val col: Int, val row: Int, val span: WidgetSpan)

    data class Layout(val cells: List<Cell>, val rows: Int) {
        /** Columns used (the right-most occupied column + 1); at least 0. */
        val columns: Int get() = cells.maxOfOrNull { it.col + it.span.cols } ?: 0
    }

    /** The vertical metrics of the scroll surface: how many rows fit, and the cell size once the rows fill the height exactly. */
    data class Metrics(val rows: Int, val cellPx: Float, val areaHeightPx: Float)

    /** Rows that fit [areaHeight] at a target cell size [targetCell] (any one unit): at least 1, at most [MAX_ROWS]. */
    fun rowsFor(areaHeight: Float, targetCell: Float): Int =
        floor(areaHeight / targetCell.coerceAtLeast(1f)).toInt().coerceIn(MIN_ROWS, MAX_ROWS)

    /**
     * The occupy-able height of a [height] tall window: minus the top [topBand] (status bar and top hotspots) and the bottom [bottomBand]
     * (bottom hotspots and gesture area). The cell is stretched so that [Metrics.rows] cells fill it exactly.
     */
    fun metrics(height: Float, topBand: Float, bottomBand: Float, targetCell: Float): Metrics {
        val area = (height - topBand - bottomBand).coerceAtLeast(targetCell)
        val rows = rowsFor(area, targetCell)
        return Metrics(rows, area / rows, area)
    }

    /** First-fit, column-major packing of [keys] into a surface [rows] tall and as wide as needed. A span taller than [rows] is cut to [rows]. */
    fun pack(keys: List<String>, spanOf: (String) -> WidgetSpan, rows: Int): Layout {
        val r = rows.coerceAtLeast(1)
        val used = ArrayList<BooleanArray>() // one BooleanArray(r) per column, grown on demand
        fun col(i: Int): BooleanArray { while (used.size <= i) used += BooleanArray(r); return used[i] }
        fun fits(c: Int, row: Int, s: WidgetSpan): Boolean {
            for (x in c until c + s.cols) { val a = col(x); for (y in row until row + s.rows) if (a[y]) return false }
            return true
        }
        val out = ArrayList<Cell>()
        var firstFree = 0 // columns before this one are completely full: skip them
        for (k in keys) {
            val s = spanOf(k).let { WidgetSpan(it.cols, min(it.rows, r)) }
            var c = firstFree
            search@ while (true) {
                for (row in 0..r - s.rows) if (fits(c, row, s)) {
                    for (x in c until c + s.cols) { val a = col(x); for (y in row until row + s.rows) a[y] = true }
                    out += Cell(k, c, row, s)
                    break@search
                }
                c++
            }
            while (firstFree < used.size && used[firstFree].all { it }) firstFree++
        }
        return Layout(out, r)
    }

    /** The content width (same unit as [cell]) of [layout] at cell size [cell] with [gap] between columns. */
    fun contentWidth(layout: Layout, cell: Float, gap: Float = 0f): Float = max(0, layout.columns).let { if (it == 0) 0f else it * cell + (it - 1) * gap }

    /** The key of the item covering logical cell ([col], [row]), or null. */
    fun keyAt(layout: Layout, col: Int, row: Int): String? =
        layout.cells.firstOrNull { col >= it.col && col < it.col + it.span.cols && row >= it.row && row < it.row + it.span.rows }?.key

    /** The column an item inserted at drop point (column [col], row [row]) takes in [keys] order: the index of the first item at or after that cell. */
    fun insertIndex(layout: Layout, col: Int, row: Int): Int {
        val i = layout.cells.indexOfFirst { it.col > col || (it.col == col && it.row >= row) }
        return if (i < 0) layout.cells.size else i
    }
}
