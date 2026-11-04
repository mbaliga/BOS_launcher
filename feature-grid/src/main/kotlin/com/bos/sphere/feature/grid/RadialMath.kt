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

/** One tile of a radial palette: an annular wedge in ring [ring]. Angles are screen degrees (0 = right, 90 = down). */
data class PaletteSlot(
    val index: Int,
    val ring: Int,
    val startDeg: Float,
    val sweepDeg: Float,
    val innerDp: Float,
    val outerDp: Float,
) {
    val midDeg: Float get() = startDeg + sweepDeg / 2f
    val midDp: Float get() = (innerDp + outerDp) / 2f
}

/**
 * Concepts-style radial palette: concentric rings of wedge tiles fanning into the screen from a
 * corner (up to [MAX_RINGS] rings, [CAPACITY] tiles each, inner rings first).
 */
object PaletteMath {
    const val MAX_RINGS = 5
    val CAPACITY = intArrayOf(4, 6, 8, 10, 12)
    const val HUB_DP = 84f
    const val RING_DP = 54f
    const val GAP_DP = 3f
    private const val EDGE_INSET_DEG = 3f
    private const val TILE_GAP_DEG = 1.6f

    val TOTAL: Int get() = CAPACITY.sum()

    /** Start angle (degrees) of the quadrant facing into the screen from [corner]. */
    fun quadrantStart(corner: Corner): Float = when (corner) {
        Corner.TopLeft -> 0f
        Corner.TopRight -> 90f
        Corner.BottomRight -> 180f
        Corner.BottomLeft -> 270f
    }

    fun ringInner(ring: Int): Float = HUB_DP + ring * (RING_DP + GAP_DP)
    fun ringOuter(ring: Int): Float = ringInner(ring) + RING_DP

    /** How many rings [count] items occupy. */
    fun ringsFor(count: Int): Int {
        var left = count.coerceAtMost(TOTAL)
        var r = 0
        while (left > 0) { left -= CAPACITY[r]; r++ }
        return r
    }

    /** Wedge tiles for [count] items around [corner]. Rings fill inner → outer, each evenly spread over the quadrant. */
    fun layout(count: Int, corner: Corner): List<PaletteSlot> {
        val out = ArrayList<PaletteSlot>()
        var placed = 0
        var ring = 0
        val total = count.coerceAtMost(TOTAL)
        while (placed < total) {
            val k = minOf(CAPACITY[ring], total - placed)
            // A partial outer ring still spans the quadrant with wider tiles, so it reads as a ring.
            val start = quadrantStart(corner) + EDGE_INSET_DEG
            val span = 90f - 2 * EDGE_INSET_DEG
            val w = span / k
            for (i in 0 until k) {
                out += PaletteSlot(placed + i, ring, start + i * w + TILE_GAP_DEG / 2, w - TILE_GAP_DEG, ringInner(ring), ringOuter(ring))
            }
            placed += k
            ring++
        }
        return out
    }

    /** Centre of [slot] as an offset (dp) from the anchor corner. */
    fun centre(slot: PaletteSlot): Pt {
        val a = slot.midDeg * PI.toFloat() / 180f
        return Pt(slot.midDp * cos(a), slot.midDp * sin(a))
    }

    /**
     * Index of the tile under [dx], [dy] (dp relative to the anchor), or -1. [slack] widens the
     * radial and angular tolerance (dp / degrees) so a thumb sliding between tiles keeps a selection.
     */
    fun hit(dx: Float, dy: Float, slots: List<PaletteSlot>, slackDp: Float = 0f): Int {
        val r = hypot(dx, dy)
        var deg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (deg < 0f) deg += 360f
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (s in slots) {
            val radial = when {
                r < s.innerDp -> s.innerDp - r
                r > s.outerDp -> r - s.outerDp
                else -> 0f
            }
            // Angular distance outside the wedge, in degrees, folded onto the circle.
            var d = ((deg - s.startDeg) % 360f + 360f) % 360f
            val outside = if (d <= s.sweepDeg) 0f else minOf(d - s.sweepDeg, 360f - d)
            val arcDp = Math.toRadians(outside.toDouble()).toFloat() * r.coerceAtLeast(1f)
            val score = radial + arcDp
            if (score <= slackDp && score < bestScore) { bestScore = score; best = s.index }
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
