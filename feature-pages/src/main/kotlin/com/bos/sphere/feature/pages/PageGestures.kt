package com.bos.sphere.feature.pages

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

/** A pointer position in px. */
data class Pt(val x: Float, val y: Float) {
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
}

/** The axis pages move along: sideways (modes 1 and 2) or stacked below home (mode 3). */
enum class PageAxis { Horizontal, Vertical }

/** What a page-gesture classifier has decided so far for the gesture in progress. */
sealed interface Verdict {
    /** Not yet: the content keeps receiving the touch. */
    data object Pending : Verdict

    /** Not a page gesture: leave the rest of it alone. */
    data object Declined : Verdict

    /** A page gesture: the launcher takes the rest of it. [direction] +1 = next page, −1 = previous. */
    data class Claimed(val direction: Int) : Verdict
}

/**
 * Mode 1 (and two-finger vertical in mode 3): two pointers travelling the same way along [axis], each
 * past touch slop, while the distance between them stays roughly constant. A change in that distance
 * beyond [spanTolerance] (or the slop, whichever is larger) first is a pinch or spread, which keeps its
 * meaning (the coverflow's globe / settings, a website's zoom). Feed it the pressed pointers on every
 * event, in arrival order. Measurements start when the second pointer lands; a third pointer, or a
 * pointer lifting before the claim, declines. Pure.
 */
class TwoFingerSwipe(
    private val axis: PageAxis,
    private val slopPx: Float,
    private val spanTolerance: Float = SPAN_TOLERANCE,
) {
    var verdict: Verdict = Verdict.Pending
        private set

    private var ids: Pair<Long, Long>? = null
    private var startA = Pt(0f, 0f)
    private var startB = Pt(0f, 0f)
    private var span0 = 0f

    fun update(pointers: List<Pair<Long, Pt>>): Verdict {
        if (verdict != Verdict.Pending) return verdict
        val started = ids
        when {
            pointers.size >= 3 -> verdict = Verdict.Declined
            pointers.size == 2 -> {
                val (a, b) = pointers
                if (started == null) {
                    ids = a.first to b.first
                    startA = a.second
                    startB = b.second
                    span0 = dist(a.second, b.second)
                } else if (started != a.first to b.first) {
                    verdict = Verdict.Declined
                } else {
                    verdict = evaluate(a.second, b.second)
                }
            }
            else -> if (started != null) verdict = Verdict.Declined
        }
        return verdict
    }

    private var lastDrag = 0f

    /**
     * Centroid travel along the axis since the second pointer landed (px); negative = toward next.
     * Fingers never lift in the same event (Android sends POINTER_UP, then UP), so once one of the
     * two has lifted this keeps returning the last two-finger value instead of falling back to 0.
     */
    fun drag(pointers: List<Pair<Long, Pt>>): Float = liveDrag(pointers) ?: lastDrag

    /** [drag] while both measured pointers are still down (looked up by id); null once one has lifted. */
    fun liveDrag(pointers: List<Pair<Long, Pt>>): Float? {
        val (idA, idB) = ids ?: return null
        val a = pointers.firstOrNull { it.first == idA }?.second ?: return null
        val b = pointers.firstOrNull { it.first == idB }?.second ?: return null
        val c = Pt((a.x + b.x - startA.x - startB.x) / 2f, (a.y + b.y - startA.y - startB.y) / 2f)
        return along(c).also { lastDrag = it }
    }

    private fun evaluate(a: Pt, b: Pt): Verdict {
        val spanChange = abs(dist(a, b) - span0)
        if (spanChange > maxOf(slopPx, span0 * spanTolerance)) return Verdict.Declined
        val dA = a - startA
        val dB = b - startB
        val c = Pt((dA.x + dB.x) / 2f, (dA.y + dB.y) / 2f)
        val alongA = along(dA)
        val alongB = along(dB)
        if (abs(alongA) > slopPx && abs(alongB) > slopPx && sign(alongA) == sign(alongB)) {
            return if (abs(along(c)) > abs(across(c))) {
                Verdict.Claimed(if (along(c) < 0f) 1 else -1)
            } else {
                Verdict.Declined
            }
        }
        // Both fingers clearly travelling across the axis (a two-finger scroll): the content's.
        if (abs(across(c)) > 2f * slopPx && abs(across(c)) > abs(along(c))) return Verdict.Declined
        return Verdict.Pending
    }

    private fun along(d: Pt) = if (axis == PageAxis.Horizontal) d.x else d.y
    private fun across(d: Pt) = if (axis == PageAxis.Horizontal) d.y else d.x

    companion object {
        /** Relative change in finger distance that makes it a pinch / spread instead (the coverflow's own
         *  pinch fires at 22 % in and 28 % out, so this always decides first). */
        const val SPAN_TOLERANCE = 0.18f

        private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
    }
}

/**
 * Mode 2 on full pages: one finger starting in an edge strip and moving inward turns the page (left
 * strip → previous, right strip → next). The strips sit just inside the system back-gesture zones
 * ([sideAt]), so the system's back swipe from the very edge is untouched. Taps and vertical drags in
 * the strip are declined and reach the content.
 */
class EdgeSwipe(private val side: Int, private val start: Pt, private val slopPx: Float) {
    var verdict: Verdict = if (side == 0) Verdict.Declined else Verdict.Pending
        private set
    private var id: Long? = null

    fun update(pointers: List<Pair<Long, Pt>>): Verdict {
        if (verdict != Verdict.Pending) return verdict
        if (pointers.size != 1) return Verdict.Declined.also { verdict = it }
        val (pid, p) = pointers[0]
        if (id == null) id = pid else if (id != pid) return Verdict.Declined.also { verdict = it }
        val d = p - start
        if (abs(d.x) <= slopPx && abs(d.y) <= slopPx) return verdict
        val inward = if (side < 0) d.x > 0f else d.x < 0f
        verdict = if (abs(d.x) > abs(d.y) && inward) Verdict.Claimed(side) else Verdict.Declined
        return verdict
    }

    fun drag(p: Pt): Float = p.x - start.x

    /** [drag] for the finger that started in the strip (by id), or null once it has lifted. */
    fun liveDrag(pointers: List<Pair<Long, Pt>>): Float? =
        pointers.firstOrNull { it.first == id }?.second?.let { drag(it) }

    companion object {
        /** Width of each strip, in dp. */
        const val STRIP_DP = 24f

        /**
         * Which strip [x] is in: −1 left, +1 right, 0 none. [insetLeftPx] / [insetRightPx] are the
         * system-gesture insets (the back-gesture zones); each strip starts where they end.
         */
        fun sideAt(x: Float, widthPx: Float, insetLeftPx: Float, insetRightPx: Float, stripPx: Float): Int = when {
            x >= insetLeftPx && x < insetLeftPx + stripPx -> -1
            x <= widthPx - insetRightPx && x > widthPx - insetRightPx - stripPx -> 1
            else -> 0
        }
    }
}

/**
 * Mode 3 on home: which vertical drags mean "page" and which mean "hub". With the hub and globe
 * closed, a one-finger drag that starts inside the equator surface and above the bottom
 * system-gesture inset is decided exactly where the surface decides it (the first move past touch
 * slop): mostly sideways → the surface's scrub (declined); mostly vertical and **up** → the next page
 * (claimed); mostly vertical and **down** → the surface's hub pull (declined). With the hub open or
 * opening, every vertical drag stays the hub's (open, close), so swiping up on an open hub closes it
 * as before. A second finger declines.
 */
class HomeSwipeUp(private val start: Pt, private val slopPx: Float) {
    var verdict: Verdict = Verdict.Pending
        private set
    private var id: Long? = null

    fun update(pointers: List<Pair<Long, Pt>>): Verdict {
        if (verdict != Verdict.Pending) return verdict
        if (pointers.size != 1) return Verdict.Declined.also { verdict = it }
        val (pid, p) = pointers[0]
        if (id == null) id = pid else if (id != pid) return Verdict.Declined.also { verdict = it }
        val d = p - start
        if (abs(d.x) <= slopPx && abs(d.y) <= slopPx) return verdict
        // Same tie-break as the surface: |dx| > |dy| is a scrub, anything else vertical.
        verdict = if (abs(d.x) > abs(d.y) || d.y >= 0f) Verdict.Declined else Verdict.Claimed(1)
        return verdict
    }

    fun drag(p: Pt): Float = p.y - start.y

    /** [drag] for the finger that started the swipe (by id), or null once it has lifted. */
    fun liveDrag(pointers: List<Pair<Long, Pt>>): Float? =
        pointers.firstOrNull { it.first == id }?.second?.let { drag(it) }

    companion object {
        /** Hub / globe fraction under which home counts as "at rest" for page gestures. */
        const val REST = 0.01f

        fun eligible(
            start: Pt,
            heightPx: Float,
            bottomGestureInsetPx: Float,
            hubFraction: Float,
            zoom: Float,
        ): Boolean = hubFraction < REST && zoom < REST && start.y < heightPx - bottomGestureInsetPx
    }
}

/**
 * Turns a claimed page gesture's events into the drag samples the pager records (its position and its
 * velocity tracker). Only events where every claimed finger is still down drive the drag; once one
 * lifts (the POINTER_UP that always comes before the last UP), nothing new is recorded, so the
 * release reads the velocity from before the first lift and the page doesn't snap back. If the fingers
 * rested longer than [stopMillis] before lifting, one stationary sample is recorded at the lift, so a
 * swipe that paused before release isn't read as a fling. Pure.
 */
class DragFeed(private val stopMillis: Long = STOP_MILLIS) {
    private var last: Float? = null
    private var lastTime = 0L
    private var stopped = false

    /** For an event at [timeMillis] whose live drag is [liveDrag] (null = a claimed finger lifted): the sample to record, or null. */
    fun feed(timeMillis: Long, liveDrag: Float?): Float? {
        if (liveDrag != null) {
            last = liveDrag
            lastTime = timeMillis
            stopped = false
            return liveDrag
        }
        val l = last ?: return null
        if (stopped || timeMillis - lastTime <= stopMillis) return null
        stopped = true
        return l
    }

    companion object {
        /** The gap after which Compose's velocity tracker assumes the pointer stopped. */
        const val STOP_MILLIS = 40L
    }
}

/** Page positions and the settle decision for a page drag. Positions are in pages (0 = home). */
object PageTurn {
    /** Fraction of the page a drag must cover to turn without a fling. */
    const val DISTANCE_FRACTION = 0.22f

    /** How far past the first / last page a drag may pull (rubber band), in pages. */
    const val MAX_OVERDRAG = 0.12f

    /**
     * Position while dragging from [startIndex] by [dragPx] along the axis (negative = toward the next
     * page) over a page [extentPx] long, with a rubber band beyond the first and last page.
     */
    fun dragPosition(startIndex: Int, dragPx: Float, extentPx: Float, count: Int): Float {
        if (extentPx <= 0f || count <= 0) return startIndex.toFloat()
        val raw = startIndex - dragPx / extentPx
        val last = (count - 1).toFloat()
        return when {
            raw < 0f -> -minOf(-raw * 0.25f, MAX_OVERDRAG)
            raw > last -> last + minOf((raw - last) * 0.25f, MAX_OVERDRAG)
            else -> raw
        }
    }

    /**
     * Where to settle after a drag released at [position], from [startIndex]: one page on if the drag
     * covered [DISTANCE_FRACTION] of a page, or the finger was flung past [flingPx] per second
     * ([velocityPx] along the axis, negative = toward next); a fling decides over distance. Never more
     * than one page per gesture, never past the ends.
     */
    fun settleTarget(startIndex: Int, position: Float, velocityPx: Float, flingPx: Float, count: Int): Int {
        if (count <= 0) return 0
        val delta = position - startIndex
        val dir = when {
            abs(velocityPx) > flingPx -> if (velocityPx < 0f) 1 else -1
            delta > DISTANCE_FRACTION -> 1
            delta < -DISTANCE_FRACTION -> -1
            else -> 0
        }
        return (startIndex + dir).coerceIn(0, count - 1)
    }

    /** The page one step [direction] from [index], or null at the ends (no wrap-around). */
    fun neighbour(index: Int, direction: Int, count: Int): Int? =
        (index + direction.coerceIn(-1, 1)).takeIf { it in 0 until count && direction != 0 }

    /** The page the user is on at [position]. */
    fun current(position: Float, count: Int): Int = position.roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))

    /**
     * Pages whose views stay alive at [position]: the current page and its two neighbours (which
     * always include both pages visible mid-turn). Every other page's WebView is destroyed.
     */
    fun liveWindow(position: Float, count: Int): IntRange {
        if (count <= 0) return IntRange.EMPTY
        val c = current(position, count)
        return (c - 1).coerceAtLeast(0)..(c + 1).coerceAtMost(count - 1)
    }

    /** True while [position] sits exactly on a page (no turn in progress). */
    fun atRest(position: Float): Boolean = abs(position - floor(position + 0.5f)) < 0.001f
}

/**
 * Mode 2 on home: scrubbing past the coverflow's first or last item turns the page, but only beyond a
 * resistance threshold. The surface reports how far the finger went past the ends, in columns (the
 * coverflow's rubber band stops drawing at 0.6); ordinary scrubbing, even into the rubber band, never
 * reaches [THRESHOLD_COLUMNS].
 */
object PastEnd {
    /** ≈ 160 dp of finger travel past the end at the default spacing (≈ 114 dp per column). */
    const val THRESHOLD_COLUMNS = 1.4f

    /** From here a hint names the page that a further pull opens. */
    const val HINT_FROM_COLUMNS = 0.7f

    enum class Stage { None, Approaching, Ready }

    /** +1 (past the last item → next page), −1 (past the first → previous), 0 (not far enough). */
    fun direction(pastEndColumns: Float, threshold: Float = THRESHOLD_COLUMNS): Int = when {
        pastEndColumns >= threshold -> 1
        pastEndColumns <= -threshold -> -1
        else -> 0
    }

    fun stage(pastEndColumns: Float): Stage = when {
        abs(pastEndColumns) >= THRESHOLD_COLUMNS -> Stage.Ready
        abs(pastEndColumns) >= HINT_FROM_COLUMNS -> Stage.Approaching
        else -> Stage.None
    }
}

/** Reduced motion: Android's "Remove animations" sets the animator duration scale to 0. */
object MotionPolicy {
    fun reduceMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f
}
