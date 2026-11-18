package com.bos.sphere.feature.grid

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wave 25 (owner, item 5): "Don't let icons exit the display area. I should be able to see all recents at once." The Orbit's rings are
 * fitted to the region it really has (between the top bar and the mic bar, above the keyboard): each ring is an ellipse that is scaled to the
 * region (not a circle that runs off the screen), a tile is only ever placed where its whole box (icon and caption) lies inside the region, and when
 * the region is small the icons shrink (down to [MIN_ICON]) before any result is dropped. All numbers are dp. Pure Kotlin: the bounds matrix test
 * (`RadarFitW25Test`) runs it for every window size, font scale and keyboard height without a screen.
 */
internal object OrbitFit {
    const val MAX_ICON = 54f
    const val MIN_ICON = 32f
    /** Keep clear of the system's edge-swipe zones along both sides. */
    const val EDGE = 12f
    /** Room kept for the gentle bob that moving rings do (a tile drifts at most this far from its slot). */
    const val BOB = 3f

    /** A placed tile: [ring] (0 innermost) and the centre of its box in dp, from the top-left of the region. */
    data class Slot(val ring: Int, val x: Float, val y: Float)

    /** The result: [icon] dp, the tile box [boxW] x [boxH] dp, the placed [slots] best first, and the ring ellipses to draw. */
    class Fit(val icon: Float, val boxW: Float, val boxH: Float, val slots: List<Slot>, val rings: List<FloatArray> /* cx, cy, rx, ry */, val capacity: Int, val guides: Boolean = true)

    /**
     * The room the Orbit's wheel may take out of a region [availDp] high: half of it (plus its 8 dp of air), so the rings keep the other half for tiles
     * (the wheel's own fit then picks 1, 3 or 5 rows of that).
     */
    fun wheelRoom(availDp: Float): Float = availDp * 0.5f + 8f

    fun boxW(icon: Float, rowW: Float = 0f) = maxOf(icon + 8f, 46f, rowW)
    /** Icon, a 3 dp gap and one caption line (10 sp at the font scale and the pinch scale). */
    /** Wave 25: [rowH] is the row of shortcut circles under the caption (0 when no tile has shortcuts). */
    fun boxH(icon: Float, fontScale: Float, resultScale: Float = 1f, rowH: Float = 0f) = icon + 3f + 10f * fontScale * resultScale * 1.3f + rowH

    /**
     * [w] x [h] is the region the Orbit may draw in, [cy] the y of the middle of the category wheel; the wheel and its caption keep a box of
     * [keepLeft] + [keepRight] x 2 [keepHalfH] around it clear. [count] results want a place; fewer are placed only when even the smallest
     * icon leaves no room for more.
     */
    fun fit(w: Float, h: Float, cy: Float, keepLeft: Float, keepRight: Float, keepHalfH: Float, count: Int, fontScale: Float, resultScale: Float = 1f, rowW: Float = 0f, rowH: Float = 0f): Fit {
        var best: Fit? = null
        val want = minOf(count, RadarRings.capacities.sum())
        var icon = MAX_ICON
        while (true) {
            // Only the elliptical rings (the Orbit look). Wave 25 had a staggered column fill here for crowded regions; the owner asked for it to go: stacked columns of icons belong to the home's scroll pages, not to Radar.
            for (f in listOf(place(w, h, cy, keepLeft, keepRight, keepHalfH, count, fontScale, resultScale, icon, rowW, rowH))) {
                if (best == null || f.slots.size > best.slots.size) best = f
                if (f.slots.size >= want) return f
            }
            if (icon <= MIN_ICON) break
            icon = maxOf(MIN_ICON, icon - 2f)
        }
        return best!!
    }

    private fun place(w: Float, h: Float, cy: Float, keepLeft: Float, keepRight: Float, keepHalfH: Float, count: Int, fontScale: Float, resultScale: Float, icon: Float, rowW: Float, rowH: Float): Fit {
        val bw = boxW(icon, rowW); val bh = boxH(icon, fontScale, resultScale, rowH)
        val cx = w / 2f
        val padX = bw / 2f + EDGE + BOB
        val padY = bh / 2f + BOB
        val rxMax = (w / 2f - padX).coerceAtLeast(0f)
        val ryMax = minOf(cy - padY, h - cy - padY).coerceAtLeast(0f)
        val rings = ArrayList<FloatArray>()
        if (rxMax <= 0f || ryMax <= 0f) return Fit(icon, bw, bh, emptyList(), rings, 0)
        val all = ArrayList<Slot>()
        val ordered = ArrayList<Slot>()
        var left = minOf(count, RadarRings.capacities.sum())
        var total = 0
        var prevF = 0.40f
        for (r in 0 until RadarRings.MAX_RINGS) {
            // Rings are ellipses scaled to the region, the inner one just outside the wheel's keep-out, the outer one at the region's edge. A ring that cannot place a tile at its
            // nominal size (the wheel is in the way, the region is short) is pushed outward until it can, never inward and never past the region.
            val nominal = maxOf(0.52f + 0.48f * r / (RadarRings.MAX_RINGS - 1), prevF + 0.06f)
            val cap = RadarRings.capacity(r)
            val offset = if (r % 2 == 1) PI / cap else 0.0
            var mine: List<Slot> = emptyList()
            var ringF = nominal.coerceAtMost(1f)
            // First at the nominal size (the most tiles that fit evenly spaced), and only when not one fits there is the ring pushed outward.
            fun tryRing(f: Float, kk: Int, turn: Double = 0.0): List<Slot>? {
                val rx = rxMax * f; val ry = ryMax * f
                val cand = List(kk) { j ->
                    val a = -PI / 2 + offset + turn + j * 2 * PI / kk
                    Slot(r, cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
                }
                val ok = cand.withIndex().all { (i, c) ->
                    c.x >= padX && c.x <= w - padX && c.y >= padY && c.y <= h - padY &&
                        !(c.x + bw / 2f > cx - keepLeft && c.x - bw / 2f < cx + keepRight && abs(c.y - cy) < keepHalfH + bh / 2f) &&
                        all.none { abs(it.x - c.x) < bw && abs(it.y - c.y) < bh } &&
                        cand.withIndex().none { (j, o) -> j < i && abs(o.x - c.x) < bw && abs(o.y - c.y) < bh }
                }
                return if (ok) cand else null
            }
            val want = minOf(left, cap)
            var k = want
            while (k > 0 && mine.isEmpty()) {
                // The top first; when the region is short and the wheel is in the way a part-filled ring may start at its sides (a quarter turn either way).
                for (turn in listOf(0.0, PI / 2, -PI / 2)) { if (mine.isEmpty()) tryRing(nominal, k, turn)?.let { mine = it; ringF = nominal } }
                k--
            }
            if (mine.isEmpty()) {
                var f = nominal + 0.06f
                while (f <= 1.0001f && mine.isEmpty()) { for (turn in listOf(0.0, PI / 2, -PI / 2)) { if (mine.isEmpty()) tryRing(f, 1, turn)?.let { mine = it; ringF = f } }; f += 0.06f }
            }
            val rx = rxMax * ringF; val ry = ryMax * ringF
            rings += floatArrayOf(cx, cy, rx, ry)
            prevF = ringF
            all += mine; total += mine.size; left -= mine.size
            // Rank order inside a ring: the slot nearest the top first, the rest alternating outward.
            ordered += mine.sortedBy { abs(Math.atan2((it.x - cx) / rx.toDouble(), -(it.y - cy) / ry.toDouble())) }
            if (left <= 0) break
        }
        return Fit(icon, bw, bh, ordered, rings, total)
    }
}
