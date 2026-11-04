package com.bos.sphere.feature.grid

import kotlin.math.floor
import kotlin.math.min

/**
 * Pure cell geometry for one grid page inside an area of [width]×[height] px. Cells are square
 * with edge [pitch] = the largest that fits both axes; the block is centred horizontally and
 * placed vertically by [verticalBias] (0 = top, 0.5 = centre).
 */
data class GridGeometry(
    val columns: Int,
    val rows: Int,
    val pitch: Float,
    val originX: Float,
    val originY: Float,
) {
    val capacity: Int get() = columns * rows

    fun cellLeft(index: Int): Float = originX + (index % columns) * pitch
    fun cellTop(index: Int): Float = originY + (index / columns) * pitch

    /** Cell index under (x, y), or -1 when outside the block. */
    fun cellAt(x: Float, y: Float): Int {
        val c = floor((x - originX) / pitch).toInt()
        val r = floor((y - originY) / pitch).toInt()
        return if (c in 0 until columns && r in 0 until rows) r * columns + c else -1
    }

    /** Nearest cell to (x, y), clamped into the block (for drop targets). */
    fun nearestCell(x: Float, y: Float): Int {
        val c = floor((x - originX) / pitch).toInt().coerceIn(0, columns - 1)
        val r = floor((y - originY) / pitch).toInt().coerceIn(0, rows - 1)
        return r * columns + c
    }

    companion object {
        fun compute(width: Float, height: Float, columns: Int, rows: Int, verticalBias: Float): GridGeometry {
            val cols = columns.coerceAtLeast(1)
            val rws = rows.coerceAtLeast(1)
            val pitch = min(width / cols, height / rws).coerceAtLeast(1f)
            val originX = (width - pitch * cols) / 2f
            val originY = (height - pitch * rws) * verticalBias.coerceIn(0f, 1f)
            return GridGeometry(cols, rws, pitch, originX, originY)
        }
    }
}
