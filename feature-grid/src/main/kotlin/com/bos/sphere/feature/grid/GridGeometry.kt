package com.bos.sphere.feature.grid

import kotlin.math.floor
import kotlin.math.min

/**
 * Cell geometry for one grid page inside [width]×[height] px.
 *
 * At [spread] 0 the cells are squares of edge [base] (the largest that fits both axes) and the
 * block is placed by [verticalBias]. Towards 1 the cells stretch on the axis that has free space
 * until the block fills the whole area, so the gaps between icons grow without changing their size
 * ([base] stays the icon reference). Cells are therefore [pitchX] × [pitchY].
 */
data class GridGeometry(
    val columns: Int,
    val rows: Int,
    /** The square-cell edge that fits both axes; icon size is derived from it. */
    val base: Float,
    val pitchX: Float,
    val pitchY: Float,
    val originX: Float,
    val originY: Float,
) {
    val capacity: Int get() = columns * rows

    fun cellLeft(index: Int): Float = originX + (index % columns) * pitchX
    fun cellTop(index: Int): Float = originY + (index / columns) * pitchY
    fun centreX(index: Int): Float = cellLeft(index) + pitchX / 2f
    fun centreY(index: Int): Float = cellTop(index) + pitchY / 2f

    /** Cell index under (x, y), or -1 when outside the block. */
    fun cellAt(x: Float, y: Float): Int {
        val c = floor((x - originX) / pitchX).toInt()
        val r = floor((y - originY) / pitchY).toInt()
        return if (c in 0 until columns && r in 0 until rows) r * columns + c else -1
    }

    /** Nearest cell to (x, y), clamped into the block (for drop targets). */
    fun nearestCell(x: Float, y: Float): Int {
        val c = floor((x - originX) / pitchX).toInt().coerceIn(0, columns - 1)
        val r = floor((y - originY) / pitchY).toInt().coerceIn(0, rows - 1)
        return r * columns + c
    }

    companion object {
        fun compute(width: Float, height: Float, columns: Int, rows: Int, verticalBias: Float, spread: Float = 0f): GridGeometry {
            val cols = columns.coerceAtLeast(1)
            val rws = rows.coerceAtLeast(1)
            val base = min(width / cols, height / rws).coerceAtLeast(1f)
            val s = spread.coerceIn(0f, 1f)
            val px = base + s * (width / cols - base)
            val py = base + s * (height / rws - base)
            val originX = (width - px * cols) / 2f
            // The block's vertical slack shrinks as it spreads; the bias fades with it.
            val originY = (height - py * rws) * verticalBias.coerceIn(0f, 1f)
            return GridGeometry(cols, rws, base, px, py, originX, originY)
        }
    }
}
