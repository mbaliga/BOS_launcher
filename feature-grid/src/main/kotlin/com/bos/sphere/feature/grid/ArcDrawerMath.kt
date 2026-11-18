package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * W26 (arcDrawer): the pure geometry of the corner arc drawer, JVM-testable (see ArcDrawerMathTest).
 *
 * Angles are *u* degrees measured from the screen's horizontal edge that the corner sits on, up
 * to the vertical edge (0..90). That makes the layout the same for every corner: the left-handed
 * (mirrored) corner is the same numbers with the x direction flipped.
 */
internal object ArcMath {
    const val QUADRANT = 90f

    /** Sections form a ring: [i] wrapped into 0 until [n] (0 for no or one section). */
    fun wrap(i: Int, n: Int): Int = if (n <= 1) 0 else Math.floorMod(i, n)

    /** One step (or [delta] steps) around the ring of [n] sections, past the last into the first and back. */
    fun step(index: Int, delta: Int, n: Int): Int = wrap(index + delta, n)

    /**
     * The wedges the quarter-ring switcher shows: the selected section first, then the next ones, wrapping past the last
     * into the first, so the first section is shown again after the last. At most [maxVisible] and never more than [n].
     */
    fun wedgeSections(selected: Int, n: Int, maxVisible: Int = WEDGES): List<Int> {
        if (n <= 0) return emptyList()
        val s = wrap(selected, n)
        return List(min(n, maxVisible)) { wrap(s + it, n) }
    }

    const val WEDGES = 3

    /** Wedge position (0-based) at [u] degrees, or -1 outside the quarter. */
    fun wedgeAt(u: Float, count: Int): Int {
        if (count <= 0 || u < 0f || u > QUADRANT) return -1
        return min(count - 1, floor(u / (QUADRANT / WEDGES)).toInt())
    }

    // ---- corner mapping

    /** +1 when u grows clockwise on screen (TL, BR), -1 otherwise (TR, BL). */
    fun sign(anchor: Corner): Int = when (anchor) {
        Corner.TopLeft, Corner.BottomRight -> 1
        Corner.TopRight, Corner.BottomLeft -> -1
    }

    /** Screen angle (Compose convention, 0 = right, clockwise) for [u] at [anchor]. */
    fun screenAngle(anchor: Corner, u: Float): Float = when (anchor) {
        Corner.TopLeft -> u
        Corner.TopRight -> 180f - u
        Corner.BottomRight -> 180f + u
        Corner.BottomLeft -> 360f - u
    }

    /** True when the corner is on the right edge: content mirrors horizontally. */
    fun onRight(anchor: Corner) = anchor == Corner.TopRight || anchor == Corner.BottomRight
    fun onBottom(anchor: Corner) = anchor == Corner.BottomLeft || anchor == Corner.BottomRight

    fun pointX(anchor: Corner, cx: Float, radius: Float, u: Float): Float {
        val dx = radius * cos(Math.toRadians(u.toDouble())).toFloat()
        return if (onRight(anchor)) cx - dx else cx + dx
    }

    fun pointY(anchor: Corner, cy: Float, radius: Float, u: Float): Float {
        val dy = radius * sin(Math.toRadians(u.toDouble())).toFloat()
        return if (onBottom(anchor)) cy - dy else cy + dy
    }

    /** u of the screen point ([x], [y]) around the corner, in degrees; the result can be outside 0..90 for a point off the quarter. */
    fun uOf(anchor: Corner, cx: Float, cy: Float, x: Float, y: Float): Float {
        val dx = if (onRight(anchor)) cx - x else x - cx
        val dy = if (onBottom(anchor)) cy - y else y - cy
        return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    /** rotationZ for a cell at u whose own up points away from the corner (never more than 90 either way, so text never reads upside down). */
    fun cellRotation(anchor: Corner, u: Float): Float {
        // Up on screen is -90 degrees (Compose angle); the radial direction is screenAngle; the rotation is their difference.
        val a = screenAngle(anchor, u)
        var r = (a + 90f) % 360f
        if (r > 180f) r -= 360f
        if (r < -180f) r += 360f
        return r
    }

    // ---- rings

    /** One section ring: [radius] of its cell centres, [pitchDeg] between neighbours, and the [lo]..[hi] u window that stays on screen. */
    class Ring(val index: Int, val radius: Float, val pitchDeg: Float, val lo: Float, val hi: Float) {
        val window: Float get() = hi - lo
        /** Cells visible at once. */
        val capacity: Int get() = floor(window / pitchDeg + 1e-4f).toInt()
    }

    /** Angular distance between neighbouring cell centres on the ring at [radius] so that square cells of side [cell] never touch (gap [gap]). */
    fun pitchDeg(radius: Float, cell: Float, gap: Float): Float {
        val inner = max(1f, radius - cell / 2f)
        val s = ((cell + gap) / (2f * inner)).coerceAtMost(1f)
        return Math.toDegrees(2.0 * asin(s.toDouble())).toFloat()
    }

    /** The u window at [radius] in which a cell centre stays at least [margin] inside a [w]x[h] screen. */
    fun window(anchor: Corner, cx: Float, cy: Float, w: Float, h: Float, radius: Float, margin: Float): Pair<Float, Float> {
        val dx = (if (onRight(anchor)) cx else w - cx) - margin
        val dy = (if (onBottom(anchor)) cy else h - cy) - margin
        if (dx <= 0f || dy <= 0f) return 0f to 0f
        val lo = if (radius > dx) Math.toDegrees(acos((dx / radius).toDouble())).toFloat() else 0f
        val hi = if (radius > dy) Math.toDegrees(asin((dy / radius).toDouble())).toFloat() else QUADRANT
        return lo to hi
    }

    /**
     * The rings outward from [hubOuter]: ring k has its cell centres at `hubOuter + gap + cell/2 + k*(cell+gap)`. A ring exists
     * while at least one cell fits in its on-screen window; there are at most [maxRings]. Outer rings hold more cells (a longer arc).
     */
    fun rings(anchor: Corner, cx: Float, cy: Float, w: Float, h: Float, hubOuter: Float, cell: Float, gap: Float, maxRings: Int): List<Ring> {
        val margin = cell * 0.7072f + 2f // half the diagonal: the whole rotated square stays on screen
        val out = ArrayList<Ring>()
        var k = 0
        while (k < maxRings) {
            val r = hubOuter + gap + cell / 2f + k * (cell + gap)
            val (lo, hi) = window(anchor, cx, cy, w, h, r, margin)
            val ring = Ring(k, r, pitchDeg(r, cell, gap), lo, hi)
            if (ring.capacity < 1) break
            out += ring
            k++
        }
        return out
    }

    /** How far the ring's content may be turned: none when [count] cells fit, otherwise from the first cell at the leading edge to the last at the far edge. */
    fun offsetBounds(ring: Ring, count: Int): ClosedFloatingPointRange<Float> {
        val span = count * ring.pitchDeg
        return if (span <= ring.window) 0f..0f else (ring.window - span)..0f
    }

    fun clampOffset(ring: Ring, count: Int, offset: Float): Float = offset.coerceIn(offsetBounds(ring, count))

    /** u of cell [j]'s centre at content [offset]. */
    fun cellU(ring: Ring, j: Int, offset: Float): Float = ring.lo + (j + 0.5f) * ring.pitchDeg + offset

    /** Cells of [count] that are at least half in the window at [offset] (the range to compose). */
    fun visibleCells(ring: Ring, count: Int, offset: Float): IntRange {
        if (count <= 0) return IntRange.EMPTY
        val half = ring.pitchDeg / 2f
        val first = max(0, kotlin.math.ceil((-half - offset) / ring.pitchDeg - 0.5f).toInt())
        val last = min(count - 1, floor((ring.window + half - offset) / ring.pitchDeg - 0.5f).toInt())
        return if (first > last) IntRange.EMPTY else first..last
    }

    /** The offset that brings cell [j] fully inside the window, moving as little as possible. */
    fun offsetToShow(ring: Ring, count: Int, offset: Float, j: Int): Float {
        val start = ring.lo + j * ring.pitchDeg + offset
        val end = start + ring.pitchDeg
        val moved = when {
            start < ring.lo -> offset + (ring.lo - start)
            end > ring.hi -> offset - (end - ring.hi)
            else -> offset
        }
        return clampOffset(ring, count, moved)
    }

    /** The cell nearest to a finger at [u] (null when none). */
    fun cellAt(ring: Ring, count: Int, offset: Float, u: Float): Int? {
        val j = floor((u - ring.lo - offset) / ring.pitchDeg).toInt()
        return if (j in 0 until count) j else null
    }

    /** Which ring a finger at [radius] is on (the nearest ring within half a pitch), or -1. */
    fun ringAt(rings: List<Ring>, radius: Float, cell: Float, gap: Float): Int {
        for (r in rings) if (kotlin.math.abs(radius - r.radius) <= (cell + gap) / 2f) return r.index
        return -1
    }

    /** The corners of the square cell at ([x], [y]) with side [cell] rotated by [rotationDeg]: x0,y0,x1,y1,... */
    fun cellCorners(x: Float, y: Float, cell: Float, rotationDeg: Float): FloatArray {
        val a = Math.toRadians(rotationDeg.toDouble())
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        val h = cell / 2f
        val local = floatArrayOf(-h, -h, h, -h, h, h, -h, h)
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val lx = local[2 * i]; val ly = local[2 * i + 1]
            out[2 * i] = x + lx * c - ly * s
            out[2 * i + 1] = y + lx * s + ly * c
        }
        return out
    }
}
