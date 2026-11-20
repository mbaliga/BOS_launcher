package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Radii of the shortcut ring and the Back hub, in dp from the wheel's centre. */
internal data class SectionRing(val backRadius: Float, val inner: Float, val outer: Float) {
    val thickness: Float get() = outer - inner
    val mid: Float get() = (inner + outer) / 2f
}

/** Axis-aligned box, in the same unit as the inputs. */
internal data class SectionBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * The window the disc is cut by, in dp: the wheel's centre ([cx], [cy]) inside a [width] x [height] window. Only what lies inside it is
 * visible, so only that part of a segment can be seen, drawn on or tapped.
 */
internal data class RingWindow(val cx: Float, val cy: Float, val width: Float, val height: Float) {
    fun contains(x: Float, y: Float) = x >= 0f && x <= width && y >= 0f && y <= height

    companion object {
        /** The window of a drawer in [anchor]'s corner: the centre sits [edge] dp in from the two screen edges (the wheel's inset). */
        fun of(anchor: Corner, width: Float, height: Float, edge: Float = PuckSectionsGeometry.EDGE_DP): RingWindow {
            val cx = if (anchor == Corner.TopRight || anchor == Corner.BottomRight) width - edge else edge
            val cy = if (anchor == Corner.BottomRight || anchor == Corner.BottomLeft) height - edge else edge
            return RingWindow(cx, cy, width, height)
        }
    }
}

/** Where the visible part of one segment is: the angle and arc length of its longest visible run at the mid radius, and its bounding box. */
internal data class VisibleSegment(val index: Int, val midAngleDeg: Float, val arcDp: Float, val bounds: SectionBox)

/**
 * Wave 38 (drawerRing): pure geometry of the shortcut ring around the all-apps Back hub. The ring is a full 360 degree annulus cut into N equal
 * segments (6, 7 or 8) by hairline dividers; the window clips it, so a quarter to a half of it is visible at once. It rotates: [p] is the ring's
 * position in segment units, "segment p is at the quadrant's bisector" (a whole number at rest, so a segment sits centred; the ring may be
 * between two while a finger is on it). Slot order runs away from the screen's horizontal edge (the thumb's side) in all four corners, mirrored
 * for the other hand's corner, exactly as the four wedges of wave 30 did.
 *
 * Touch size: the full arc of one segment is 2 pi r / N: at 8 segments and the phone's radii (hub 59, ring out to 119 dp) that is 46 dp at the
 * hub, 70 dp mid ring and 93 dp at the outer edge, so a whole segment is at least 44 dp across the arc everywhere (6 and 7 segments are wider);
 * radially it is [SectionRing.thickness] (about 60 dp). A segment the window cuts has less: [visibleSegment] measures what is left.
 */
internal object PuckSectionsGeometry {
    /** The 8-segment default (the store keeps 8 whatever the count). */
    const val COUNT = 8
    private const val QUADRANT = 90f
    /** How far in from the window's two edges the wheel's centre sits (dp); the same 63 dp `WheelDrawer` sets as the wheel's inset. */
    const val EDGE_DP = 63f
    /** The Back hub stops this short of the nearest window edge. */
    const val HUB_EDGE_GAP_DP = 4f
    /** Screen angle where [anchor]'s visible quadrant begins (the same rule as the wheel: TopLeft 0, TopRight 90, BottomRight 180, BottomLeft 270). */
    fun quadrantStart(anchor: Corner): Float = anchor.ordinal * QUADRANT
    private fun norm360(deg: Float): Float = ((deg % 360f) + 360f) % 360f
    private fun norm180(deg: Float): Float = norm360(deg + 180f) - 180f
    /** Width of the hairline divider drawn on each shared edge of two segments (wave 32). */
    const val DIVIDER_DP = 1.5f
    /** The radial depth a thumb-sized segment keeps whenever the window allows, and the arc width the segments keep across the ring. */
    const val MIN_TOUCH_DP = 48f
    const val MIN_ARC_DP = 44f
    /** An app icon in a segment is this share of the segment's smaller dimension. */
    const val APP_ICON_SHARE = 0.6f
    /** An action glyph never gets smaller than this, whatever the segment. */
    const val MIN_GLYPH_DP = 22f
    /** Least width, at the mid radius, that the visible part of a segment needs to count as visible (node, tap area). */
    const val MIN_VISIBLE_ARC_DP = 10f
    /** The group labels' baseline sits on the chip radius and their ink rises about 10 dp above it. */
    private const val LABEL_CLEAR = 7f
    /** Smallest hub (a small window); the hub otherwise is the distance to the nearest window edge less [HUB_EDGE_GAP_DP]. */
    const val BACK_MIN_DP = 44f
    /** The ring is at least this deep (radially) whatever the window. */
    const val MIN_RING_DEPTH_DP = 40f

    /**
     * Hub radius [hubDp] and label radius [chipDp] (both from `PuckLayout`) give the ring. The Back hub reaches (to within [HUB_EDGE_GAP_DP]) the
     * nearest window edge, [edgeDp] from the centre: 59 dp, clamped to half the label hub and at least [BACK_MIN_DP]. The ring wraps it with no gap
     * and runs out to just inside the group labels, at least [MIN_RING_DEPTH_DP] deep (`PuckLayout.hubRadius` keeps the labels clear of that).
     */
    fun ring(hubDp: Float, chipDp: Float, edgeDp: Float = EDGE_DP): SectionRing {
        val back = (edgeDp - HUB_EDGE_GAP_DP).coerceAtMost(hubDp * 0.5f).coerceAtLeast(BACK_MIN_DP)
        val outer = max(back + MIN_RING_DEPTH_DP, chipDp - LABEL_CLEAR)
        return SectionRing(back, back, outer)
    }

    /** True when the ring is deep enough for a [MIN_TOUCH_DP] touch across a segment (radially). */
    fun deepEnough(r: SectionRing): Boolean = r.thickness >= MIN_TOUCH_DP

    /** The segments' angular size for a ring of [n]. */
    fun segmentDeg(n: Int): Float = 360f / n

    /** True when the slot order runs against the wheel's clockwise angle for [anchor]: the horizontal edge is at the quadrant's far end. */
    fun reversed(anchor: Corner) = anchor == Corner.TopRight || anchor == Corner.BottomLeft
    private fun dir(anchor: Corner) = if (reversed(anchor)) -1f else 1f
    private fun bisector(anchor: Corner) = quadrantStart(anchor) + QUADRANT / 2f

    /** Screen angle of the centre of segment [i] of [n] when the ring is at position [p]. */
    fun centerAngle(anchor: Corner, n: Int, p: Float, i: Int): Float = norm360(bisector(anchor) + dir(anchor) * (i - p) * segmentDeg(n))

    /** Screen angle where segment [i] begins (the smaller one clockwise; [segmentDeg] later it ends). */
    fun startAngle(anchor: Corner, n: Int, p: Float, i: Int): Float = centerAngle(anchor, n, p, i) - segmentDeg(n) / 2f

    /** Screen angles of the n dividers: the edges between neighbouring segments. */
    fun dividerAngles(anchor: Corner, n: Int, p: Float): List<Float> = List(n) { norm360(centerAngle(anchor, n, p, it) + segmentDeg(n) / 2f) }

    /** The slot whose segment holds screen angle [angleDeg] at position [p]. */
    fun segmentAt(anchor: Corner, n: Int, p: Float, angleDeg: Float): Int {
        val rel = dir(anchor) * norm180(angleDeg - bisector(anchor)) / segmentDeg(n) + p
        return Math.floorMod(floor(rel + 0.5f).toInt(), n)
    }

    /** The slot sitting at the bisector when the ring rests at [p]. */
    fun frontSlot(n: Int, p: Float): Int = Math.floorMod(p.roundToInt(), n)

    /** The change of [p] when a finger moves by [fingerDeltaDeg] (screen degrees, clockwise positive) around the centre: the ring follows the finger. */
    fun positionDelta(anchor: Corner, n: Int, fingerDeltaDeg: Float): Float = -dir(anchor) * fingerDeltaDeg / segmentDeg(n)

    /** The same for a finger's angular velocity (deg/s): segments per second. */
    fun positionVelocity(anchor: Corner, n: Int, fingerOmegaDeg: Float): Float = positionDelta(anchor, n, fingerOmegaDeg)

    /**
     * Where the ring comes to rest: the whole segment position nearest to [p] shifted by what a fling carries it, [projectSec] seconds of
     * [velocity] (segments per second), limited to [maxSteps] segments so a hard fling cannot spin for long. Without inertia ([projectSec] 0) it is
     * the nearest whole position.
     */
    fun settleTarget(p: Float, velocity: Float, projectSec: Float = 0.18f, maxSteps: Int = 3): Float {
        val shift = (velocity * projectSec).coerceIn(-maxSteps.toFloat(), maxSteps.toFloat())
        return (p + shift).roundToInt().toFloat()
    }

    /** A position equivalent to [p] in 0 until [n], to keep the float small once the ring rests. */
    fun wrap(p: Float, n: Int): Float = ((p % n) + n) % n

    /** What a touch at ([dx], [dy]) from the wheel's centre lands on. */
    sealed interface Hit {
        data object None : Hit
        data object Back : Hit
        data class Slot(val index: Int) : Hit
    }

    /**
     * The tap target at ([dx], [dy]) from the wheel's centre (dp) for ring position [p]. The Back hub is the disc of [SectionRing.backRadius]; a
     * point between the hub and the ring's outer edge is the segment holding its angle; anything else is [Hit.None] (the outer wheel's). With a
     * [window], a segment that is not visible in it (a sliver by the hub that the screen edge hides the rest of) is not a target: [Hit.None].
     */
    fun hit(anchor: Corner, n: Int, p: Float, r: SectionRing, dx: Float, dy: Float, window: RingWindow? = null): Hit {
        val d = hypot(dx, dy)
        if (d <= r.backRadius) return Hit.Back
        if (d > r.outer) return Hit.None
        val i = segmentAt(anchor, n, p, Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
        if (window != null && visibleSegment(anchor, n, p, i, r, window) == null) return Hit.None
        return Hit.Slot(i)
    }

    /** True when a touch that starts at ([dx], [dy]) belongs to the ring (and its hub): it rotates the ring, the outer wheel keeps the rest. */
    fun ownsTouch(r: SectionRing, dx: Float, dy: Float): Boolean = hypot(dx, dy) <= r.outer

    /** Arc length (dp) of one whole segment at [radius]. */
    fun arcAt(n: Int, radius: Float): Float = (2.0 * PI * radius / n).toFloat()

    private const val STEP_DEG = 1f

    /**
     * What of segment [i] is inside [w] at position [p]: null when none of it shows at the mid radius (a sliver by the hub does not count). The glyph goes at [VisibleSegment.midAngleDeg] on the mid radius
     * (the middle of the segment's longest visible run there) and the node's box is [VisibleSegment.bounds], the box of the visible part.
     */
    fun visibleSegment(anchor: Corner, n: Int, p: Float, i: Int, r: SectionRing, w: RingWindow): VisibleSegment? {
        val seg = segmentDeg(n)
        val a0 = startAngle(anchor, n, p, i)
        val steps = (seg / STEP_DEG).toInt().coerceAtLeast(1)
        var bestStart = -1; var bestLen = 0; var runStart = -1; var runLen = 0
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var rt = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        var any = false
        for (k in 0..steps) {
            val a = Math.toRadians((a0 + seg * k / steps).toDouble())
            val c = cos(a).toFloat(); val s = sin(a).toFloat()
            val midVisible = w.contains(w.cx + r.mid * c, w.cy + r.mid * s)
            if (midVisible) { if (runStart < 0) { runStart = k; runLen = 0 }; runLen++; if (runLen > bestLen) { bestLen = runLen; bestStart = runStart } } else runStart = -1
            for (rad in floatArrayOf(r.inner, r.mid, r.outer)) {
                val x = w.cx + rad * c; val y = w.cy + rad * s
                if (w.contains(x, y)) { any = true; l = min(l, x); rt = max(rt, x); t = min(t, y); b = max(b, y) }
            }
        }
        // A segment counts as visible when a run of at least [MIN_VISIBLE_ARC_DP] of it shows at the mid radius; a sliver at the hub that the edge
        // hides the rest of, or a corner of it that only touches the edge, does not.
        if (!any || bestStart < 0) return null
        if (Math.toRadians(((bestLen - 1) * seg / steps).toDouble()) * r.mid < MIN_VISIBLE_ARC_DP) return null
        val bounds = SectionBox(l, t, rt, b)
        val runDeg = (bestLen - 1) * seg / steps
        val mid = a0 + seg * bestStart / steps + runDeg / 2f
        return VisibleSegment(i, norm360(mid), Math.toRadians(runDeg.toDouble()).toFloat() * r.mid, bounds)
    }

    /**
     * Where the glyph (a square of [boxDp]) of segment [i] goes, relative to the wheel's centre (dp), or null when it cannot be shown whole: the
     * point of the segment nearest the middle of its visible part (the mid radius at [VisibleSegment.midAngleDeg]) at which the whole square is inside
     * the window (2 dp clear of the edges) and clear of the segment's dividers. A segment the screen edge cuts therefore carries its glyph on the
     * part that shows; one with no room for it shows no glyph (it stays a tap area).
     */
    fun glyphPlacement(anchor: Corner, n: Int, p: Float, i: Int, r: SectionRing, w: RingWindow, boxDp: Float): Pair<Float, Float>? {
        val v = visibleSegment(anchor, n, p, i, r, w) ?: return null
        val seg = segmentDeg(n)
        val a0 = startAngle(anchor, n, p, i)
        val half = boxDp / 2f
        val target = glyphOffset(v.midAngleDeg, r)
        var best: Pair<Float, Float>? = null; var bestD = Float.MAX_VALUE
        var rad = r.inner + half * 0.9f
        while (rad <= r.outer - half * 0.9f + 0.01f) {
            var deg = 0f
            while (deg <= seg) {
                val a = Math.toRadians((a0 + deg).toDouble())
                val x = (rad * cos(a)).toFloat(); val y = (rad * sin(a)).toFloat()
                // clear of the two dividers: distance to each boundary ray
                val dl = rad * sin(Math.toRadians(deg.toDouble())).toFloat(); val dr = rad * sin(Math.toRadians((seg - deg).toDouble())).toFloat()
                if (dl >= half * 0.8f && dr >= half * 0.8f) {
                    val px = w.cx + x; val py = w.cy + y
                    if (px - half >= 2f && px + half <= w.width - 2f && py - half >= 2f && py + half <= w.height - 2f) {
                        val dd = (x - target.first) * (x - target.first) + (y - target.second) * (y - target.second)
                        if (dd < bestD) { bestD = dd; best = x to y }
                    }
                }
                deg += 1f
            }
            rad += 3f
        }
        return best
    }

    /** The visible segments for [p]: those with any part inside [w]. */
    fun visibleSegments(anchor: Corner, n: Int, p: Float, r: SectionRing, w: RingWindow): List<VisibleSegment> =
        (0 until n).mapNotNull { visibleSegment(anchor, n, p, it, r, w) }

    /** Edge, in dp, of the square glyph of a segment: its smaller dimension (whole arc at mid radius or ring depth) times [share]. */
    fun glyphSize(n: Int, r: SectionRing, share: Float): Float = share * min(arcAt(n, r.mid), r.thickness)

    /**
     * Wave 38: where the Back arrow goes relative to the hub's centre (dp): the middle of the part of the hub that is inside [w]. The hub reaches
     * the nearest edge to within [HUB_EDGE_GAP_DP], so on the drawer it is whole and the offset is (0, 0); a hub the window cuts is centred on what shows.
     */
    fun hubArrowOffset(r: SectionRing, w: RingWindow): Pair<Float, Float> {
        val l = max(w.cx - r.backRadius, 0f); val rt = min(w.cx + r.backRadius, w.width)
        val t = max(w.cy - r.backRadius, 0f); val b = min(w.cy + r.backRadius, w.height)
        return ((l + rt) / 2f - w.cx) to ((t + b) / 2f - w.cy)
    }

    /** Where a glyph at [angleDeg] on the mid radius is, relative to the wheel's centre (dp). */
    fun glyphOffset(angleDeg: Float, r: SectionRing): Pair<Float, Float> {
        val a = Math.toRadians(angleDeg.toDouble())
        return (r.mid * cos(a)).toFloat() to (r.mid * sin(a)).toFloat()
    }
}
