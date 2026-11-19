package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Radii of the section ring and the Back hub, in dp from the wheel's centre. */
internal data class SectionRing(val backRadius: Float, val inner: Float, val outer: Float) {
    val thickness: Float get() = outer - inner
    val mid: Float get() = (inner + outer) / 2f
}

/** One section: [startDeg]..[endDeg] are screen angles (Compose convention, 0 = right, clockwise); the wedges touch (wave 32). */
internal data class SectionWedge(val slot: Int, val startDeg: Float, val endDeg: Float) {
    val midDeg: Float get() = (startDeg + endDeg) / 2f
    val sweep: Float get() = endDeg - startDeg
}

/** Axis-aligned box, in the same unit as the inputs. */
internal data class SectionBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Wave 30 (puckSections): pure geometry of the four sections around the all-apps hub, like the wedge sections of the Concepts quick switcher:
 * contiguous, touching wedges of the visible quadrant (wave 32: a solid ring around the hub, hairline dividers), between a round Back hub and the group labels. Slot 0 is the wedge next to the
 * screen's horizontal edge (the thumb's side), slot 3 next to the vertical edge, for all four corners (the right-hand corner is the exact mirror
 * of the left-hand one). Everything is in dp or degrees; the composable scales by density.
 *
 * Touch size: a wedge is [SectionRing.thickness] deep (at least [MIN_TOUCH_DP] whenever the window allows) but only a quarter-turn over four
 * wedges wide, so across the arc it is narrower than 48 dp on a phone (about 25 dp at mid radius). The whole wedge, is the tap area ([slotAt]), and nothing else shares it.
 */
internal object PuckSectionsGeometry {
    const val COUNT = 4
    private const val QUADRANT = 90f
    /** Screen angle where [anchor]'s visible quadrant begins (the same rule as the wheel: TopLeft 0, TopRight 90, BottomRight 180, BottomLeft 270). */
    fun quadrantStart(anchor: Corner): Float = anchor.ordinal * QUADRANT
    private fun norm360(deg: Float): Float = ((deg % 360f) + 360f) % 360f
    /** Width of the black hairline divider drawn on each shared edge of two wedges (wave 32): the wedges themselves touch, there is no gap. */
    const val DIVIDER_DP = 1.5f
    const val MIN_TOUCH_DP = 48f
    /** An app icon in a wedge is this share of the wedge's smaller dimension. */
    const val APP_ICON_SHARE = 0.6f
    /** An action glyph never gets smaller than this, whatever the wedge. */
    const val MIN_GLYPH_DP = 22f
    /** The group labels' baseline sits on the chip radius and their ink rises about 10 dp above it. */
    private const val LABEL_CLEAR = 7f

    /**
     * Hub radius [hubDp] and label radius [chipDp] (both from `PuckLayout`) give the ring: a hub just big enough for a 48 dp Back button, and a
     * ring wrapped directly around it (no gap, [SectionRing.inner] equals [SectionRing.backRadius]) out to just inside the labels. The reference
     * ring is about 0.9 x the hub radius deep; ours is deeper on purpose (the space out to the labels), because a deeper ring puts the wedges at
     * a larger radius and so makes them wider across the arc, closer to a 48 dp tap target.
     */
    fun ring(hubDp: Float, chipDp: Float): SectionRing {
        val back = (hubDp * 0.24f).coerceIn(24f, 34f)
        val inner = back
        val outer = max(inner + 20f, chipDp - LABEL_CLEAR)
        return SectionRing(back, inner, outer)
    }

    /** True when the ring is deep enough for a [MIN_TOUCH_DP] touch across a wedge (radially). */
    fun deepEnough(r: SectionRing): Boolean = r.thickness >= MIN_TOUCH_DP

    /** True when the slot order runs against the wheel's clockwise angle for [anchor]: the horizontal edge is at the quadrant's far end. */
    private fun reversed(anchor: Corner) = anchor == Corner.TopRight || anchor == Corner.BottomLeft

    /** Wheel angle (0..90 clockwise from the quadrant start) that slot [slot] begins at, in the full, untrimmed quadrant. */
    private fun cell(anchor: Corner, slot: Int): Int = if (reversed(anchor)) COUNT - 1 - slot else slot

    /** The four wedges for [anchor]: equal, touching cells of the quadrant (the hairline dividers are drawn over the shared edges). */
    @Suppress("UNUSED_PARAMETER")
    fun wedges(anchor: Corner, r: SectionRing, count: Int = COUNT): List<SectionWedge> {
        val q = quadrantStart(anchor)
        val cellDeg = QUADRANT / count
        return List(count) { slot ->
            val c = if (reversed(anchor)) count - 1 - slot else slot
            SectionWedge(slot, q + c * cellDeg, q + (c + 1) * cellDeg)
        }
    }

    /** Screen angles of the dividers: the edges the wedges share (three of them; the quadrant's two screen-edge radii get none). */
    fun dividerAngles(anchor: Corner, count: Int = COUNT): List<Float> =
        List(count - 1) { quadrantStart(anchor) + (it + 1) * (QUADRANT / count) }

    /** Edge, in dp, of the square glyph of [w]: the wedge's smaller dimension (arc width at mid radius or ring depth) times [share]. */
    fun glyphSize(w: SectionWedge, r: SectionRing, share: Float): Float = share * min(arcWidth(w, r), r.thickness)

    /** Where the glyph of [w] is centred, relative to the wheel's centre (dp). */
    fun centroid(w: SectionWedge, r: SectionRing): Pair<Float, Float> {
        val a = Math.toRadians(w.midDeg.toDouble())
        return (r.mid * cos(a)).toFloat() to (r.mid * sin(a)).toFloat()
    }

    /** The bounding box of [w] relative to the wheel's centre (same unit as [r]). */
    fun bounds(w: SectionWedge, r: SectionRing): SectionBox {
        val angles = ArrayList<Float>()
        angles += w.startDeg; angles += w.endDeg
        var k = Math.ceil((w.startDeg / 90f).toDouble()).toInt()
        while (k * 90f < w.endDeg) { if (k * 90f > w.startDeg) angles += k * 90f; k++ }
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var rt = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (a in angles) for (rad in floatArrayOf(r.inner, r.outer)) {
            val x = (rad * cos(Math.toRadians(a.toDouble()))).toFloat(); val y = (rad * sin(Math.toRadians(a.toDouble()))).toFloat()
            l = min(l, x); rt = max(rt, x); t = min(t, y); b = max(b, y)
        }
        return SectionBox(l, t, rt, b)
    }

    /** Arc length of the wedge along its mid radius (dp), the width of the touch area across the arc. */
    fun arcWidth(w: SectionWedge, r: SectionRing): Float = Math.toRadians(w.sweep.toDouble()).toFloat() * r.mid

    /** What a touch at ([dx], [dy]) from the wheel's centre lands on. */
    sealed interface Hit {
        data object None : Hit
        data object Back : Hit
        data class Slot(val index: Int) : Hit
    }

    /**
     * The tap target at ([dx], [dy]) from the wheel's centre (dp). The Back hub is the disc of [SectionRing.backRadius]; a point in the ring and
     * inside the quadrant is the slot whose cell (cell) holds its angle; anything else is [Hit.None].
     */
    fun hit(anchor: Corner, r: SectionRing, dx: Float, dy: Float, count: Int = COUNT): Hit {
        val d = hypot(dx, dy)
        if (d <= r.backRadius) return Hit.Back
        if (d < r.inner || d > r.outer) return Hit.None
        val rel = norm360(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() - quadrantStart(anchor))
        if (rel > QUADRANT) return Hit.None
        val c = min((rel / (QUADRANT / count)).toInt(), count - 1)
        return Hit.Slot(if (reversed(anchor)) count - 1 - c else c)
    }
}
