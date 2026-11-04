package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.PageLayout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** A point in px (or dp, by context). Pure so the math is JVM-testable. */
data class Pt(val x: Float, val y: Float) {
    fun dist(o: Pt): Float = hypot(x - o.x, y - o.y)
}

/**
 * Radial-palette placement: items fan out on concentric quarter-arcs facing into the screen
 * from a corner. Ring capacities grow outward (4, 6, 8) so spacing along each arc stays even.
 */
object PaletteMath {
    val RING_CAPACITY = intArrayOf(3, 5, 7)
    val RING_RADIUS_DP = floatArrayOf(108f, 186f, 264f)
    private const val EDGE_INSET_DEG = 11f

    /** Start angle (degrees, screen space: 0 = right, 90 = down) of the inward quadrant. */
    fun quadrantStart(corner: Corner): Float = when (corner) {
        Corner.TopLeft -> 0f
        Corner.TopRight -> 90f
        Corner.BottomRight -> 180f
        Corner.BottomLeft -> 270f
    }

    /** Offsets (dp, relative to the anchor) for [count] items around [corner]. */
    fun offsets(count: Int, corner: Corner): List<Pt> {
        val out = ArrayList<Pt>(count)
        var remaining = count
        var ring = 0
        while (remaining > 0) {
            val cap = RING_CAPACITY.getOrElse(ring) { RING_CAPACITY.last() + 2 * (ring - RING_CAPACITY.lastIndex) }
            val radius = RING_RADIUS_DP.getOrElse(ring) { RING_RADIUS_DP.last() + 78f * (ring - RING_RADIUS_DP.lastIndex) }
            val k = min(cap, remaining)
            val start = quadrantStart(corner) + EDGE_INSET_DEG
            val span = 90f - 2 * EDGE_INSET_DEG
            for (i in 0 until k) {
                val deg = if (k == 1) start + span / 2 else start + span * i / (k - 1)
                val rad = deg * PI.toFloat() / 180f
                out += Pt(radius * cos(rad), radius * sin(rad))
            }
            remaining -= k
            ring++
        }
        return out
    }

    /** Index of the item within [reach] of [p] (all in the same units), or -1. */
    fun hit(p: Pt, anchor: Pt, offsets: List<Pt>, scale: Float, reach: Float): Int {
        var best = -1
        var bestD = reach
        offsets.forEachIndexed { i, o ->
            val d = p.dist(Pt(anchor.x + o.x * scale, anchor.y + o.y * scale))
            if (d <= bestD) { bestD = d; best = i }
        }
        return best
    }
}

/**
 * Circular page geometry: a centre slot plus rings of 6·k slots, filling the largest circle that
 * fits [width]×[height]. Slot 0 is the centre; ring k starts at 12 o'clock and runs clockwise.
 */
data class CircleGeometry(val center: Pt, val ringStep: Float, val rings: Int) {
    val capacity: Int get() = PageLayout.circleCapacity(rings)

    /** Icon edge that keeps neighbours on the innermost ring (the tightest) from touching. */
    val iconSize: Float get() = ringStep * 0.74f

    fun slotCenter(index: Int): Pt {
        if (index <= 0) return center
        var i = index - 1
        var ring = 1
        while (i >= PageLayout.ringSlots(ring)) { i -= PageLayout.ringSlots(ring); ring++ }
        val n = PageLayout.ringSlots(ring)
        val stagger = if (ring % 2 == 0) 0.5 else 0.0
        val a = -PI / 2 + 2 * PI * (i + stagger) / n
        val r = ringStep * ring
        return Pt(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
    }

    /** Slot whose centre is within half a ring step of (x, y), or -1. */
    fun slotAt(x: Float, y: Float): Int {
        val i = nearestSlot(x, y)
        return if (slotCenter(i).dist(Pt(x, y)) <= ringStep * 0.5f) i else -1
    }

    fun nearestSlot(x: Float, y: Float): Int {
        val p = Pt(x, y)
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in 0 until capacity) {
            val d = slotCenter(i).dist(p)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    companion object {
        fun compute(width: Float, height: Float, rings: Int): CircleGeometry {
            val r = rings.coerceIn(PageLayout.MIN_RINGS, PageLayout.MAX_RINGS)
            val radius = min(width, height) / 2f
            return CircleGeometry(Pt(width / 2f, height / 2f), radius / (r + 0.55f), r)
        }
    }
}

/**
 * Flat coverflow strip (the reference's coverflow page): the focused app large in the centre,
 * neighbours shrinking and overlapping outward. [position] is the fractional focused index.
 */
data class CoverflowGeometry(val width: Float, val height: Float, val count: Int, val position: Float) {
    /** Edge of the focused icon. */
    val focusSize: Float = min(height * 0.5f, width * 0.3f).coerceAtLeast(1f)

    /** Pixels of finger travel per item step. */
    val stepPx: Float get() = focusSize * 0.6f

    fun offsetX(d: Float): Float {
        val a = kotlin.math.abs(d)
        val near = min(a, 1f)
        val far = (a - 1f).coerceIn(0f, 8f)
        return kotlin.math.sign(d) * (focusSize * 0.62f * near + focusSize * 0.36f * far)
    }

    fun scale(d: Float): Float {
        val a = kotlin.math.abs(d)
        return 1f - 0.16f * min(a, 1f) - 0.06f * (a - 1f).coerceIn(0f, 4f)
    }

    fun center(index: Int): Pt = Pt(width / 2f + offsetX(index - position), height / 2f)
    fun sizeAt(index: Int): Float = focusSize * scale(index - position)
    fun visible(index: Int): Boolean = kotlin.math.abs(index - position) <= 5.5f

    /** Front-most item whose square contains (x, y), or -1. */
    fun slotAt(x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0 until count) {
            if (!visible(i)) continue
            val c = center(i)
            val half = sizeAt(i) / 2f
            val d = kotlin.math.abs(i - position)
            if (kotlin.math.abs(x - c.x) <= half && kotlin.math.abs(y - c.y) <= half && d < bestD) { best = i; bestD = d }
        }
        return best
    }

    /** Insertion slot nearest to x (0..count). */
    fun nearest(x: Float): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in 0..count) {
            val d = kotlin.math.abs(center(i).x - x)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }
}
