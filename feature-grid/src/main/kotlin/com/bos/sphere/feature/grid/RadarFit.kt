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

    fun boxW(icon: Float) = maxOf(icon + 12f, 56f)
    /** Icon, a 3 dp gap and one caption line (10 sp at the font scale and the pinch scale). */
    fun boxH(icon: Float, fontScale: Float, resultScale: Float = 1f) = icon + 3f + 10f * fontScale * resultScale * 1.3f

    /**
     * [w] x [h] is the region the Orbit may draw in, [cy] the y of the middle of the category wheel; the wheel and its caption keep a box of
     * [keepLeft] + [keepRight] x 2 [keepHalfH] around it clear. [count] results want a place; fewer are placed only when even the smallest
     * icon leaves no room for more.
     */
    fun fit(w: Float, h: Float, cy: Float, keepLeft: Float, keepRight: Float, keepHalfH: Float, count: Int, fontScale: Float, resultScale: Float = 1f): Fit {
        var best: Fit? = null
        val want = minOf(count, RadarRings.capacities.sum())
        var icon = MAX_ICON
        while (true) {
            // The elliptical rings first (the Orbit look); when they cannot hold every result at this size, a staggered fill of the same region may.
            for (f in listOf(place(w, h, cy, keepLeft, keepRight, keepHalfH, count, fontScale, resultScale, icon), packed(w, h, cy, keepLeft, keepRight, keepHalfH, count, fontScale, resultScale, icon))) {
                if (best == null || f.slots.size > best.slots.size) best = f
                if (f.slots.size >= want) return f
            }
            if (icon <= MIN_ICON) break
            icon = maxOf(MIN_ICON, icon - 2f)
        }
        return best!!
    }

    /**
     * The crowded case: an even grid over the whole region (every cell at least a tile box), minus what the wheel keeps, filled from the middle outward so the
     * best results are still the innermost. The ring ellipses are then the tiers' outlines.
     */
    private fun packed(w: Float, h: Float, cy: Float, keepLeft: Float, keepRight: Float, keepHalfH: Float, count: Int, fontScale: Float, resultScale: Float, icon: Float): Fit {
        val bw = boxW(icon); val bh = boxH(icon, fontScale, resultScale)
        val cx = w / 2f
        val left = bw / 2f + EDGE + BOB; val top = bh / 2f + BOB
        val availW = w - 2 * left; val availH = h - 2 * top
        if (availW < 0f || availH < 0f) return Fit(icon, bw, bh, emptyList(), emptyList(), 0)
        val nc = (availW / bw).toInt() + 1; val nr = (availH / bh).toInt() + 1
        val rxMax = maxOf(availW / 2f, 1f); val ryMax = maxOf(minOf(cy - top, h - cy - top), 1f)
        val cells = ArrayList<Pair<Slot, Float>>()
        for (j in 0 until nr) for (i in 0 until nc) {
            val x = if (nc == 1) cx else left + availW * i / (nc - 1)
            val y = if (nr == 1) h / 2f else top + availH * j / (nr - 1)
            if (y < top - 0.01f || y > h - top + 0.01f) continue
            val onWheel = x + bw / 2f > cx - keepLeft && x - bw / 2f < cx + keepRight && abs(y - cy) < keepHalfH + bh / 2f
            if (onWheel) continue
            val d = kotlin.math.hypot(((x - cx) / rxMax).toDouble(), ((y - cy) / ryMax).toDouble()).toFloat()
            cells += Slot(0, x, y) to d
        }
        val chosen = cells.sortedBy { it.second }.take(minOf(count, RadarRings.capacities.sum()))
        val ringOf = RadarRings.assign(chosen.size, RadarRings.capacities)
        val slots = chosen.mapIndexed { k, (s, _) -> Slot(ringOf[k], s.x, s.y) }
        val rings = (0 until RadarRings.MAX_RINGS).map { r ->
            val d = chosen.filterIndexed { k, _ -> ringOf[k] == r }.maxOfOrNull { it.second } ?: 0f
            floatArrayOf(cx, cy, rxMax * d, ryMax * d)
        }
        return Fit(icon, bw, bh, slots, rings, cells.size, guides = false) // a tight fill does not sit on ellipses, so no guide lines are drawn
    }

    private fun place(w: Float, h: Float, cy: Float, keepLeft: Float, keepRight: Float, keepHalfH: Float, count: Int, fontScale: Float, resultScale: Float, icon: Float): Fit {
        val bw = boxW(icon); val bh = boxH(icon, fontScale, resultScale)
        val cx = w / 2f
        val padX = bw / 2f + EDGE + BOB
        val padY = bh / 2f + BOB
        val rxMax = (w / 2f - padX).coerceAtLeast(0f)
        val ryMax = minOf(cy - padY, h - cy - padY).coerceAtLeast(0f)
        val rings = ArrayList<FloatArray>()
        val all = ArrayList<Slot>()
        val perRing = ArrayList<List<Slot>>()
        if (rxMax <= 0f || ryMax <= 0f) return Fit(icon, bw, bh, emptyList(), rings, 0)
        for (r in 0 until RadarRings.MAX_RINGS) {
            val f = 0.40f + 0.60f * r / (RadarRings.MAX_RINGS - 1)
            val rx = rxMax * f; val ry = ryMax * f
            rings += floatArrayOf(cx, cy, rx, ry)
            // Walk the ellipse in small steps from the top (alternate rings start half a step round), keep a point when its box is clear of every placed box and of the wheel.
            val cap = RadarRings.capacity(r)
            val steps = 360
            val mine = ArrayList<Slot>()
            val offset = if (r % 2 == 1) PI / cap else 0.0
            for (i in 0 until steps) {
                if (mine.size >= cap) break
                val a = -PI / 2 + offset + i * 2 * PI / steps
                val x = cx + rx * cos(a).toFloat(); val y = cy + ry * sin(a).toFloat()
                if (x < padX || x > w - padX || y < padY || y > h - padY) continue
                val onWheel = x + bw / 2f > cx - keepLeft && x - bw / 2f < cx + keepRight && abs(y - cy) < keepHalfH + bh / 2f
                if (onWheel) continue
                if (all.any { abs(it.x - x) < bw && abs(it.y - y) < bh }) continue
                val s = Slot(r, x, y); mine += s; all += s
            }
            perRing += mine
        }
        // Rank order: ring by ring; inside a ring the slots nearest the top first (the best result sits straight above the centre, the rest alternate outward).
        val ordered = ArrayList<Slot>()
        var left = minOf(count, RadarRings.capacities.sum())
        for (r in perRing.indices) {
            val mine = perRing[r]
            // A tier is its capacity (5, 9, 13, 17) of the best results: when a ring cannot hold the tier's share the later results have no place at this size.
            val need = minOf(left, RadarRings.capacity(r))
            val take = minOf(need, mine.size)
            if (take <= 0) break
            left -= take
            // Spread a part-filled ring over its slots instead of bunching it at the top.
            val spread = if (take == mine.size) mine else List(take) { j -> mine[(j * mine.size.toDouble() / take).toInt().coerceAtMost(mine.lastIndex)] }
            ordered += spread.sortedBy { abs(Math.atan2((it.x - cx) / rings[r][2].toDouble(), -(it.y - cy) / rings[r][3].toDouble())) }
            if (take < need) break
        }
        return Fit(icon, bw, bh, ordered, rings, perRing.sumOf { it.size })
    }
}
