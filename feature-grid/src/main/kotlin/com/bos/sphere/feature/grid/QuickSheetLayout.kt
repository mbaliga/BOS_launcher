package com.bos.sphere.feature.grid

/**
 * HOM-21: the long-press quick sheet used a fixed row of four tiles inside a column that could not
 * scroll, so on a narrow window, in landscape or at a large font size its tiles were squeezed and its
 * buttons fell off the bottom. These rules pick the tile columns from the window and font scale, and
 * keep the sheet within the window so the rest scrolls.
 */
object QuickSheetLayout {
    /** Narrowest a tile may get before the label no longer fits one line (dp at font scale 1). */
    const val MIN_TILE_DP = 72f
    const val SIDE_PADDING_DP = 18f
    const val TILE_GAP_DP = 10f

    /** How many tiles share a row: as many as keep each at least [MIN_TILE_DP] scaled by the font, 1 to [count]. */
    fun tileColumns(widthDp: Float, fontScale: Float, count: Int = 4): Int {
        if (count <= 1) return count.coerceAtLeast(1)
        val inner = (widthDp - 2 * SIDE_PADDING_DP).coerceAtLeast(0f)
        val minTile = MIN_TILE_DP * fontScale.coerceIn(0.8f, 2.0f)
        val fit = ((inner + TILE_GAP_DP) / (minTile + TILE_GAP_DP)).toInt()
        // Even splits only (4, 2, 1) so the rows stay balanced.
        return when {
            fit >= count -> count
            fit >= 2 -> if (count % 2 == 0) 2 else fit.coerceAtMost(count)
            else -> 1
        }
    }

    fun <T> rows(items: List<T>, columns: Int): List<List<T>> = items.chunked(columns.coerceAtLeast(1))

    /** The tallest the sheet's content may be: the window height minus the status bar and a grab margin. */
    fun maxContentHeightDp(windowHeightDp: Float, statusBarDp: Float = 24f, marginDp: Float = 56f): Float =
        (windowHeightDp - statusBarDp - marginDp).coerceAtLeast(120f)

    /** True when the content needs the scroll (taller than what the window leaves). */
    fun needsScroll(contentDp: Float, windowHeightDp: Float): Boolean = contentDp > maxContentHeightDp(windowHeightDp)
}
