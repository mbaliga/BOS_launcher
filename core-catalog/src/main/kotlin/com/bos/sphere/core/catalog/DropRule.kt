package com.bos.sphere.core.catalog

import kotlin.math.hypot

/**
 * HOM-04: the one rule that decides whether ending a drag on another item means "file into it" (drop onto) or "put it
 * next to it" (reorder), so the two never fight.
 *
 * A reorder preview moves the items under the finger, so a plain distance test flips between the two while the finger
 * rests near a cell centre. The rule has three parts, all pure and tested:
 *  - **Enter zone**: the finger must be inside [ENTER_ZONE] of the cell edge from the target's centre.
 *  - **Dwell**: it must stay there for [DWELL_MS] without leaving; passing over a centre while moving does not arm it.
 *  - **Hysteresis**: once armed it stays armed until the finger leaves the wider [EXIT_ZONE], so a small tremor does not
 *    flip the decision, and while armed the reorder preview is held still ([armedKey] non-null).
 */
class DropOntoTracker(
    private val enterZone: Float = ENTER_ZONE,
    private val exitZone: Float = EXIT_ZONE,
    private val dwellMs: Long = DWELL_MS,
) {
    private var candidate: String? = null
    private var enteredAt = -1L
    private var armed = false

    /** The item the drop would file into right now, or null when it means reorder. */
    val armedKey: String? get() = if (armed) candidate else null

    fun reset() { candidate = null; enteredAt = -1L; armed = false }

    /**
     * One pointer sample. [hitKey] is the item whose cell is under the finger (null over empty space), [dx] and [dy] the
     * finger's offset from that cell's centre, [cell] its shorter edge, [nowMs] a monotonic clock.
     */
    fun update(hitKey: String?, dx: Float, dy: Float, cell: Float, nowMs: Long) {
        if (hitKey == null || cell <= 0f) { reset(); return }
        val d = hypot(dx, dy)
        if (hitKey != candidate) {
            // A different item: nothing carries over. It only starts a dwell when the finger is already in its enter zone.
            reset()
            if (d <= cell * enterZone) { candidate = hitKey; enteredAt = nowMs }
            return
        }
        if (armed) {
            if (d > cell * exitZone) reset()
            return
        }
        when {
            d > cell * exitZone -> reset()
            d > cell * enterZone -> enteredAt = -1L // drifted out of the enter zone: the dwell starts over
            else -> {
                if (enteredAt < 0) enteredAt = nowMs
                if (nowMs - enteredAt >= dwellMs) armed = true
            }
        }
    }

    /** Re-checks the dwell at [nowMs] without a new sample (a finger resting still sends none). */
    fun tick(nowMs: Long) {
        if (!armed && candidate != null && enteredAt >= 0 && nowMs - enteredAt >= dwellMs) armed = true
    }

    companion object {
        const val ENTER_ZONE = 0.22f
        const val EXIT_ZONE = 0.36f
        const val DWELL_MS = 220L
    }
}

/** HOM-04: what a drag ends as. */
sealed interface DropDecision {
    data class Onto(val key: String) : DropDecision
    data object Reorder : DropDecision
}

object DropRule {
    /**
     * The decision when the finger lifts at [nowMs]: file into the armed item when [DropOntoIntent] accepts that pair
     * (never itself, only items that can receive a drop, and the edit must change something), otherwise reorder.
     */
    fun decide(tracker: DropOntoTracker, dragKey: String, cell: Float, state: HomeState, nowMs: Long): DropDecision {
        tracker.tick(nowMs)
        val key = tracker.armedKey ?: return DropDecision.Reorder
        val ok = DropOntoIntent.target(dragKey, key, 0f, 0f, cell, state)
        return if (ok != null) DropDecision.Onto(ok) else DropDecision.Reorder
    }
}
