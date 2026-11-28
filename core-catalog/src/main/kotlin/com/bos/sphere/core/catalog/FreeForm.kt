package com.bos.sphere.core.catalog

import kotlin.math.abs

/**
 * HOM-13: free-form placement on desktop surfaces: items sit off-grid, snap to the edges and
 * centres of the area and of their neighbours when within [threshold], and widgets may overlap
 * empty space but never each other. Model and math only; the desktop surface hosts it.
 */
object FreeForm {

    data class Guide(val vertical: Boolean, val at: Float)
    data class Snapped(val box: Box, val guides: List<Guide>)

    /**
     * Snaps [moving] (the box being dragged, already at its dragged position) to the area and
     * [others]. Each axis snaps independently to the nearest candidate line within [threshold].
     */
    fun snap(moving: Box, area: Box, others: List<Box>, threshold: Float = 8f): Snapped {
        val xs = ArrayList<Float>().apply { add(area.left); add((area.left + area.right) / 2f); add(area.right); others.forEach { add(it.left); add((it.left + it.right) / 2f); add(it.right) } }
        val ys = ArrayList<Float>().apply { add(area.top); add((area.top + area.bottom) / 2f); add(area.bottom); others.forEach { add(it.top); add((it.top + it.bottom) / 2f); add(it.bottom) } }
        val (dx, gx) = best(listOf(moving.left, (moving.left + moving.right) / 2f, moving.right), xs, threshold)
        val (dy, gy) = best(listOf(moving.top, (moving.top + moving.bottom) / 2f, moving.bottom), ys, threshold)
        val box = Box(moving.left + dx, moving.top + dy, moving.right + dx, moving.bottom + dy)
        val guides = listOfNotNull(gx?.let { Guide(true, it) }, gy?.let { Guide(false, it) })
        return Snapped(box, guides)
    }

    private fun best(edges: List<Float>, candidates: List<Float>, threshold: Float): Pair<Float, Float?> {
        var bestD = Float.MAX_VALUE
        var bestShift = 0f
        var line: Float? = null
        for (e in edges) for (c in candidates) {
            val d = abs(c - e)
            if (d <= threshold && d < bestD) { bestD = d; bestShift = c - e; line = c }
        }
        return bestShift to line
    }

    /** Whether a widget at [box] may be placed: inside [area] and not overlapping another widget. Plain items may overlap empty space only (checked by the caller against icons). */
    fun canPlaceWidget(box: Box, area: Box, otherWidgets: List<Box>): Boolean =
        area.contains(box) && otherWidgets.none { it.intersects(box) }

    /** Pushes [box] inside [area]. */
    fun clamp(box: Box, area: Box): Box {
        val w = box.width.coerceAtMost(area.width); val h = box.height.coerceAtMost(area.height)
        val l = box.left.coerceIn(area.left, area.right - w); val t = box.top.coerceIn(area.top, area.bottom - h)
        return Box(l, t, l + w, t + h)
    }
}
