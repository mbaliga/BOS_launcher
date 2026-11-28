package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wave 49 (actionBubbles, PAL-03): where an app's shortcut bubbles sit in the Orbit. Each bubble is a satellite of its app: a [ShortcutForm.BUBBLE_W] wide box
 * (48 dp slot with the circle, then the label) placed next to the app's own box. The search is pure and deterministic (all numbers in dp): for the satellites in
 * order it tries distances from the app outward and, at each distance, angles starting at the preferred direction and alternating to both sides; the first spot
 * that is inside [bounds] and clear of the app, of every [obstacles] box and of the satellites already placed wins. When the area around the app is full, it
 * takes the nearest free spot anywhere in [bounds] (so every bubble still appears), and only when nothing is free at all the spot inside [bounds] that covers least.
 */
internal object ShortcutSatellites {
    /** Air kept between two boxes. */
    const val GAP = 4f
    private const val FIRST_DISTANCE = 66f
    private const val DISTANCE_STEP = 6f
    private const val DISTANCES = 40
    private const val ANGLE_STEP_DEG = 10

    class Spot(val box: Rect, val onRing: Boolean) {
        /** The middle of the bubble's circle (the line to the app ends here). */
        val circleCentre: Offset get() = Offset(box.center.x, box.top + ShortcutForm.SLOT_DP / 2f)
    }

    fun boxSize(fontScale: Float): Pair<Float, Float> = ShortcutForm.BUBBLE_W to ShortcutForm.bubbleHeight(fontScale)

    private fun grown(r: Rect, by: Float) = Rect(r.left - by, r.top - by, r.right + by, r.bottom + by)
    private fun inside(r: Rect, b: Rect) = r.left >= b.left && r.right <= b.right && r.top >= b.top && r.bottom <= b.bottom
    private fun clear(r: Rect, taken: List<Rect>) = taken.none { grown(it, GAP / 2f).overlaps(grown(r, GAP / 2f)) }

    /**
     * [app] is the app's own box (face and caption), [preferred] the direction (radians, screen coordinates: 0 = right, pi/2 = down) the fan opens in.
     * Returns one [Spot] per shortcut, in order. [rtl] mirrors the order the angles are tried on the two sides (the fan reads from the start side).
     */
    fun place(app: Rect, count: Int, fontScale: Float, bounds: Rect, obstacles: List<Rect>, preferred: Double, rtl: Boolean = false, soft: List<Rect> = emptyList()): List<Spot> {
        if (count <= 0) return emptyList()
        val (w, h) = boxSize(fontScale)
        val hard = ArrayList<Rect>(obstacles.size + count + 1).also { it += app; it += obstacles }
        val out = ArrayList<Spot>(count)
        val side = if (rtl) -1 else 1
        fun ring(taken: List<Rect>): Rect? {
            for (d in 0 until DISTANCES) {
                val dist = FIRST_DISTANCE + d * DISTANCE_STEP
                // 0, +10, -10, +20, -20 ... degrees from the preferred direction
                var k = 0
                while (k <= 360 / ANGLE_STEP_DEG) {
                    val off = ((k + 1) / 2) * ANGLE_STEP_DEG * (if (k % 2 == 1) side else -side)
                    k++
                    val a = preferred + Math.toRadians(off.toDouble())
                    val cx = app.center.x + dist * cos(a).toFloat()
                    val cy = app.center.y + dist * sin(a).toFloat()
                    val r = Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                    if (inside(r, bounds) && clear(r, taken)) return r
                }
            }
            return null
        }
        repeat(count) {
            // 1 next to the app, clear of everything; 2 anywhere in the area, clear of everything, nearest first; 3 nothing is free: the spot that covers least
            // (the app itself weighs ten times), so a bubble is never dropped.
            val strict = hard + soft
            var found = ring(strict)
            val onRing = found != null
            if (found == null) found = nearestFree(app, w, h, bounds, strict)
            if (found == null) found = leastCovering(app, w, h, bounds, strict)
            hard += found
            out += Spot(found, onRing)
        }
        return out
    }

    /**
     * The whole Orbit: [tileBoxes] are the boxes of every drawn result (an app's box at the index of that app), [counts] its shortcut count (0 for a result without),
     * [preferred] its fan direction, [fixed] boxes that are in the way too (the wheel's text). Apps are served in order (best match first), each one seeing the
     * bubbles already placed, so nothing overlaps. Result: the spots of each tile, in shortcut order.
     */
    fun placeAll(tileBoxes: List<Rect>, counts: List<Int>, preferred: List<Double>, fontScale: Float, bounds: Rect, fixed: List<Rect>, rtl: Boolean = false): List<List<Spot>> {
        val placed = ArrayList<Rect>()
        return tileBoxes.indices.map { i ->
            if (counts[i] <= 0) return@map emptyList<Spot>()
            place(tileBoxes[i], counts[i], fontScale, bounds, tileBoxes.filterIndexed { j, _ -> j != i } + placed, preferred[i], rtl, soft = fixed).also { sp -> sp.forEach { placed += it.box } }
        }
    }

    /** The free spot nearest the app anywhere in [bounds], on an 8 dp grid. */
    private fun nearestFree(app: Rect, w: Float, h: Float, bounds: Rect, taken: List<Rect>): Rect? {
        var best: Rect? = null
        var bestD = Float.MAX_VALUE
        var y = bounds.top
        while (y + h <= bounds.bottom + 0.01f) {
            var x = bounds.left
            while (x + w <= bounds.right + 0.01f) {
                val r = Rect(x, y, x + w, y + h)
                val dx = r.center.x - app.center.x; val dy = r.center.y - app.center.y
                val d = dx * dx + dy * dy
                if (d < bestD && clear(r, taken)) { best = r; bestD = d }
                x += 8f
            }
            y += 8f
        }
        return best
    }

    private fun overlapArea(a: Rect, b: Rect): Float {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left); val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return if (w > 0f && h > 0f) w * h else 0f
    }

    private fun leastCovering(app: Rect, w: Float, h: Float, bounds: Rect, taken: List<Rect>): Rect {
        var best = clampInto(app, w, h, bounds)
        var bestCost = Float.MAX_VALUE
        var y = bounds.top
        while (y + h <= bounds.bottom + 0.01f) {
            var x = bounds.left
            while (x + w <= bounds.right + 0.01f) {
                val r = Rect(x, y, x + w, y + h)
                var cost = 0f
                taken.forEach { cost += overlapArea(r, it) * (if (it == app) 10f else 1f) }
                val dx = r.center.x - app.center.x; val dy = r.center.y - app.center.y
                cost += (dx * dx + dy * dy) * 0.001f
                if (cost < bestCost) { best = r; bestCost = cost }
                x += 8f
            }
            y += 8f
        }
        return best
    }

    private fun clampInto(app: Rect, w: Float, h: Float, bounds: Rect): Rect {
        val x = (app.center.x - w / 2f).coerceIn(bounds.left, maxOf(bounds.left, bounds.right - w))
        val y = (app.center.y - h / 2f).coerceIn(bounds.top, maxOf(bounds.top, bounds.bottom - h))
        return Rect(x, y, x + w, y + h)
    }
}
