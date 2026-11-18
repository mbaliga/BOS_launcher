package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * W28 (arcShortcuts): the pure geometry of the corner alphabet wheel and its app fan, JVM-testable (ArcDrawerMathTest).
 *
 * Angles are *u* degrees measured from the screen's horizontal edge that the corner sits on, up to the vertical edge (0..90).
 * That makes the layout the same for every corner: the right-handed (mirrored) corner is the same numbers with x flipped.
 * Everything is in one unit (dp or px); the callers pass the same unit throughout.
 */
internal object ArcMath {
    const val QUADRANT = 90f

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

    /** u of the screen point ([x], [y]) around the corner, in degrees; outside 0..90 for a point off the quarter. */
    fun uOf(anchor: Corner, cx: Float, cy: Float, x: Float, y: Float): Float {
        val dx = if (onRight(anchor)) cx - x else x - cx
        val dy = if (onBottom(anchor)) cy - y else y - cy
        return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    /** rotationZ for a glyph at u whose own up points away from the corner (within +-180, so it reads along the arc). */
    fun radialRotation(anchor: Corner, u: Float): Float {
        var r = (screenAngle(anchor, u) + 90f) % 360f
        if (r > 180f) r -= 360f
        if (r < -180f) r += 360f
        return r
    }
}

/** The looped alphabet ring: A to Z, then "#" for everything else, then A again. */
internal object AlphabetRing {
    val LETTERS: List<String> = ('A'..'Z').map { it.toString() } + "#"
    val COUNT: Int = LETTERS.size

    /** Degrees between neighbouring letters on the ring. */
    const val STEP_DEG = 8f

    /** The u at which the selected letter sits (the middle of the quarter). */
    const val SELECT_U = 45f

    fun wrap(i: Int): Int = Math.floorMod(i, COUNT)
    fun step(index: Int, delta: Int): Int = wrap(index + delta)

    /** The ring index of a letter label ("A".."Z", "#"), -1 when unknown. */
    fun indexOf(letter: String): Int = LETTERS.indexOf(letter)

    /** The ring index a typed character jumps to: its letter, "#" for a digit or "#"; -1 otherwise. */
    fun indexOfChar(c: Char): Int = when {
        c == '#' || c.isDigit() -> COUNT - 1
        c.isLetter() && c.uppercaseChar() in 'A'..'Z' -> c.uppercaseChar() - 'A'
        else -> -1
    }

    /** Signed distance in letters from the rotation to [index] along the shortest way round, in [-COUNT/2, COUNT/2). */
    fun offsetOf(rotation: Float, index: Int): Float {
        var d = (index - rotation) % COUNT
        if (d < 0f) d += COUNT
        if (d >= COUNT / 2f) d -= COUNT
        return d
    }

    /** u of letter [index] when the ring is turned to [rotation] (the alphabet runs clockwise round the corner, so u falls with the index). */
    fun letterU(rotation: Float, index: Int): Float = SELECT_U - offsetOf(rotation, index) * STEP_DEG

    /** The letter at the selected angle. */
    fun selected(rotation: Float): Int = wrap(rotation.roundToInt())

    /** The letter under a finger at [u] (within half a step of its centre), -1 elsewhere. */
    fun letterAt(rotation: Float, u: Float): Int {
        val d = (SELECT_U - u) / STEP_DEG // letters from the rotation
        val idx = wrap(Math.round(rotation + d))
        return if (abs(letterU(rotation, idx) - u) <= STEP_DEG / 2f + 1e-3f) idx else -1
    }

    /** The rotation to animate to so [index] becomes selected, taking the shortest way round. */
    fun rotationFor(current: Float, index: Int): Float = current + offsetOf(current, index)

    /** Turn by [du] degrees of finger travel along the ring. */
    fun rotateBy(rotation: Float, du: Float): Float = rotation + du / STEP_DEG
}

/** One app slot of the fan: its centre and where it came from. */
internal class FanSlot(val x: Float, val y: Float, val chain: Int, val k: Int)

internal class FRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    fun intersects(o: FRect) = l < o.r && o.l < r && t < o.b && o.t < b
    fun contains(x: Float, y: Float) = x in l..r && y in t..b
}

/** The apps of the selected letter fanned out from the wheel along radial chains, nearest slot first. */
internal object ArcFan {
    /** The chains' u angles, from the left edge round to the bottom edge. The bottom chain (0) is the shortcut row when shortcuts show. */
    val CHAINS = floatArrayOf(90f, 75f, 60f, 45f, 30f, 15f, 0f)

    /**
     * The slots: along each chain, from just outside the ring ([ringOuter] + half an icon + [gap]) every [pitch], kept while the
     * whole icon (size [icon]) is inside the [w] x [h] screen by [margin] and clear of every [reserved] rect; a slot too close to a nearer one
     * (on both axes less than [icon] + half a [gap] apart) is dropped, so chains that start close together never overlap. Sorted nearest to farthest
     * (ties: chain order); [useBottomChain] false leaves chain 0 free for the shortcut row.
     */
    fun slots(
        anchor: Corner, cx: Float, cy: Float, w: Float, h: Float,
        ringOuter: Float, icon: Float, pitch: Float, gap: Float, margin: Float,
        reserved: List<FRect> = emptyList(), useBottomChain: Boolean = true,
    ): List<FanSlot> {
        val half = icon / 2f
        val r0 = ringOuter + half + gap
        val cand = ArrayList<FanSlot>()
        for ((ci, u) in CHAINS.withIndex()) {
            if (u == 0f && !useBottomChain) continue
            // Square icons along a slanted chain would touch at the plain pitch; widen it so the neighbours are [gap] apart on their wider axis.
            val rad = Math.toRadians(u.toDouble())
            val step = pitch * (1f / maxOf(cos(rad), sin(rad)).toFloat().coerceAtLeast(0.7f)).coerceAtLeast(1f)
            var k = 0
            while (true) {
                val r = r0 + k * step
                val x = ArcMath.pointX(anchor, cx, r, u); val y = ArcMath.pointY(anchor, cy, r, u)
                val outside = x - half < margin || y - half < margin || x + half > w - margin || y + half > h - margin
                if (hypot(x - cx, y - cy) > hypot(w, h)) break
                if (!outside) cand += FanSlot(x, y, ci, k)
                k++
                if (k > 60) break
            }
        }
        cand.sortWith(compareBy<FanSlot>({ it.k }, { it.chain }))
        val out = ArrayList<FanSlot>()
        val minD = icon + gap / 2f
        for (s in cand) {
            val box = FRect(s.x - half, s.y - half, s.x + half, s.y + half)
            if (reserved.any { it.intersects(box) }) continue
            if (out.any { abs(it.x - s.x) < minD && abs(it.y - s.y) < minD }) continue
            out += s
        }
        // Nearest first by true distance, so apps fill from the wheel outwards.
        out.sortWith(compareBy<FanSlot>({ Math.round(hypot(it.x - cx, it.y - cy) / 2f) }, { it.chain }))
        return out
    }

    /** The first app index shown, kept in range: with [count] apps and [capacity] slots the list scrolls by whole slots. */
    fun clampStart(start: Int, count: Int, capacity: Int): Int = start.coerceIn(0, (count - capacity).coerceAtLeast(0))

    /** The apps shown for a scroll position: those from [start] that fit in [capacity] slots. */
    fun shown(count: Int, capacity: Int, start: Int): IntRange {
        val s = clampStart(start, count, capacity)
        return if (count <= 0 || capacity <= 0) IntRange.EMPTY else s until minOf(count, s + capacity)
    }

    /** Scroll steps for a drag of [travel] (positive = away from the corner) at [pitch] per step; the remainder is carried by the caller. */
    fun stepsFor(travel: Float, pitch: Float): Int = if (pitch <= 0f) 0 else (-travel / pitch).toInt()
}

/** Shortcut placement: the bottom row of the open wheel and the group on the home at rest. */
internal object ShortcutPlacement {
    /** The centres of [count] icons along the bottom edge from just outside the ring (the wheel's bottom chain). */
    fun wheelRow(anchor: Corner, cx: Float, cy: Float, ringOuter: Float, icon: Float, pitch: Float, gap: Float, count: Int): List<Pair<Float, Float>> =
        List(count) { i ->
            val r = ringOuter + icon / 2f + gap + i * pitch
            ArcMath.pointX(anchor, cx, r, 0f) to ArcMath.pointY(anchor, cy, r, 0f)
        }

    /**
     * Where the group of [count] shortcuts sits on the home at rest, next to the four-squares glyph in [anchor]'s corner (a [glyph] square):
     * a row along the bottom edge when it clears the page indicator zone in the middle ([keepMid] half-width) and the other
     * corners, else a column up the side edge. [lift] raises it above anything reserved along the bottom (the dock).
     * Returns the icon centres in order, in [w] x [h].
     */
    fun atRest(anchor: Corner, w: Float, h: Float, count: Int, icon: Float, gap: Float, glyph: Float, keepMid: Float, lift: Float): List<Pair<Float, Float>> {
        if (count <= 0) return emptyList()
        val pitch = icon + gap
        val bottom = ArcMath.onBottom(anchor)
        val right = ArcMath.onRight(anchor)
        val edgeY = if (bottom) h - lift - gap - icon / 2f else gap + icon / 2f
        val rowEnd = glyph + count * pitch // distance from the side edge to the far end of the row
        val rowFits = rowEnd <= w / 2f - keepMid
        return List(count) { i ->
            if (rowFits) {
                val d = glyph + gap + icon / 2f + i * pitch - gap
                (if (right) w - d else d) to edgeY
            } else {
                val d = glyph + gap + icon / 2f + i * pitch - gap
                val x = if (right) w - gap - icon / 2f else gap + icon / 2f
                x to (if (bottom) h - lift - d else d)
            }
        }
    }
}
