package com.bos.sphere.feature.desktop

import kotlin.math.abs
import kotlin.math.hypot

/** Float rectangle for free-form placement math (HOM-13). */
data class FRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
    fun moved(dx: Float, dy: Float) = FRect(left + dx, top + dy, right + dx, bottom + dy)
    fun at(x: Float, y: Float) = FRect(x, y, x + width, y + height)
    /** True when the interiors overlap; touching edges do not count. */
    fun overlaps(o: FRect) = left < o.right - EPS && o.left < right - EPS && top < o.bottom - EPS && o.top < bottom - EPS
    fun inside(c: FRect) = left >= c.left - EPS && right <= c.right + EPS && top >= c.top - EPS && bottom <= c.bottom + EPS
    companion object { const val EPS = 0.01f }
}

/** A snap guide line: vertical lines have an x position, horizontal ones a y position. */
data class Guide(val vertical: Boolean, val pos: Float)

data class SnapResult(val rect: FRect, val guides: List<Guide>)

/**
 * Free-form placement on the desktop surface (HOM-13): edges and centres snap to the container and to other
 * items with visible guides; widgets never overlap each other but may sit anywhere in empty space.
 */
object FreeformPlacement {
    /** Snaps [moving] to container and [others] edges/centres within [threshold], clamped inside [container]. */
    fun snap(moving: FRect, others: List<FRect>, container: FRect, threshold: Float): SnapResult {
        val clamped = clamp(moving, container)
        val xLines = lines(container, others, vertical = true)
        val yLines = lines(container, others, vertical = false)
        val dx = bestOffset(listOf(clamped.left, clamped.cx, clamped.right), xLines, threshold)
        val dy = bestOffset(listOf(clamped.top, clamped.cy, clamped.bottom), yLines, threshold)
        val snapped = clamp(clamped.moved(dx, dy), container)
        return SnapResult(snapped, guidesFor(snapped, others, container))
    }

    /** The guide lines a rectangle currently sits on. */
    fun guidesFor(r: FRect, others: List<FRect>, container: FRect): List<Guide> {
        val out = mutableListOf<Guide>()
        val xs = lines(container, others, true)
        val ys = lines(container, others, false)
        for (a in listOf(r.left, r.cx, r.right)) xs.firstOrNull { abs(it - a) < 0.5f }?.let { out += Guide(true, it) }
        for (a in listOf(r.top, r.cy, r.bottom)) ys.firstOrNull { abs(it - a) < 0.5f }?.let { out += Guide(false, it) }
        return out.distinct()
    }

    /**
     * Places a widget: snaps, then if it would overlap another widget moves it to the nearest free position
     * (flush against a neighbour or the container). Returns null when the widget does not fit anywhere.
     */
    fun placeWidget(moving: FRect, widgets: List<FRect>, container: FRect, threshold: Float): SnapResult? {
        if (moving.width > container.width + FRect.EPS || moving.height > container.height + FRect.EPS) return null
        val snapped = snap(moving, widgets, container, threshold).rect
        val free = resolveOverlap(snapped, widgets, container) ?: return null
        return SnapResult(free, guidesFor(free, widgets, container))
    }

    /** Nearest position to [r] inside [container] that overlaps none of [widgets], or null. */
    fun resolveOverlap(r: FRect, widgets: List<FRect>, container: FRect): FRect? {
        val start = clamp(r, container)
        if (widgets.none { it.overlaps(start) }) return start
        val xs = mutableListOf(start.left, container.left, container.right - start.width)
        val ys = mutableListOf(start.top, container.top, container.bottom - start.height)
        for (w in widgets) {
            xs += w.right; xs += w.left - start.width
            ys += w.bottom; ys += w.top - start.height
        }
        var best: FRect? = null
        var bestD = Float.MAX_VALUE
        for (x in xs) for (y in ys) {
            val c = start.at(x, y)
            if (!c.inside(container) || widgets.any { it.overlaps(c) }) continue
            val d = hypot(x - start.left, y - start.top)
            if (d < bestD) { bestD = d; best = c }
        }
        return best
    }

    fun clamp(r: FRect, c: FRect): FRect {
        val x = r.left.coerceIn(c.left, maxOf(c.left, c.right - r.width))
        val y = r.top.coerceIn(c.top, maxOf(c.top, c.bottom - r.height))
        return r.at(x, y)
    }

    private fun lines(c: FRect, others: List<FRect>, vertical: Boolean): List<Float> =
        (listOf(c) + others).flatMap { if (vertical) listOf(it.left, it.cx, it.right) else listOf(it.top, it.cy, it.bottom) }

    private fun bestOffset(anchors: List<Float>, lines: List<Float>, threshold: Float): Float {
        var best = 0f
        var bestAbs = Float.MAX_VALUE
        for (a in anchors) for (l in lines) {
            val d = l - a
            if (abs(d) <= threshold && abs(d) < bestAbs) { bestAbs = abs(d); best = d }
        }
        return best
    }
}
