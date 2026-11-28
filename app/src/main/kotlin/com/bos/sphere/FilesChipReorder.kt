package com.bos.sphere

/**
 * W54 (filesChips): the pure arithmetic behind dragging a chip of the Files source switcher. Everything is in LOGICAL coordinates: along the reading
 * direction, so in a right-to-left layout the first chip is at 0 and the row grows towards the left on screen. [starts] and [widths] list the chips in
 * order; [gap] is the space between neighbours.
 */
internal object FilesChipReorder {
    /** Auto-scroll starts this close (dp) to a viewport edge. */
    const val EDGE_ZONE_DP = 56f
    /** The fastest auto-scroll, in dp per frame (about 16 ms): 14 dp is roughly 850 dp a second. */
    const val MAX_STEP_DP = 14f

    /**
     * The index the dragged chip [from] would take if dropped now: the number of the OTHER chips whose centre lies before the dragged chip's centre
     * (which has moved by [delta]), kept inside [lo]..[hi] (the chips of its own kind).
     */
    fun targetIndex(starts: List<Float>, widths: List<Float>, from: Int, delta: Float, lo: Int, hi: Int): Int {
        if (from !in starts.indices || starts.size != widths.size) return from
        val centre = starts[from] + widths[from] / 2f + delta
        var n = 0
        for (k in starts.indices) if (k != from && starts[k] + widths[k] / 2f < centre) n++
        return n.coerceIn(lo, hi.coerceAtLeast(lo))
    }

    /** The drag distance, held so the dragged chip stays inside the block of its own kind ([lo]..[hi]). */
    fun clampDelta(starts: List<Float>, widths: List<Float>, from: Int, delta: Float, lo: Int, hi: Int): Float {
        if (from !in starts.indices) return delta
        val min = starts[lo] - starts[from]
        val max = starts[hi] + widths[hi] - widths[from] - starts[from]
        return if (max < min) 0f else delta.coerceIn(min, max)
    }

    /** How far chip [index] slides aside while chip [from] is being carried to [to] (the dragged chip itself is not moved by this). */
    fun slide(from: Int, to: Int, widths: List<Float>, gap: Float, index: Int): Float {
        if (from !in widths.indices || index == from) return 0f
        val step = widths[from] + gap
        return when {
            to > from && index in (from + 1)..to -> -step
            to < from && index in to until from -> step
            else -> 0f
        }
    }

    /**
     * How far to scroll this frame: negative towards the start, positive towards the end, 0 in the middle. Speed grows linearly from 0 at the edge
     * of the zone to [maxStep] at the viewport edge (and beyond it). [fingerX] is the finger's place in the viewport, logical.
     */
    fun autoScrollStep(fingerX: Float, viewportWidth: Float, zone: Float, maxStep: Float): Float {
        if (zone <= 0f || viewportWidth <= 0f) return 0f
        val z = minOf(zone, viewportWidth / 2f)
        return when {
            fingerX < z -> -maxStep * ((z - fingerX) / z).coerceIn(0f, 1f)
            fingerX > viewportWidth - z -> maxStep * ((fingerX - (viewportWidth - z)) / z).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    /** [items] with [id] moved to position [to] (clamped); same list when nothing changes. */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices) return items
        val j = to.coerceIn(0, items.lastIndex)
        if (j == from) return items
        val l = items.toMutableList(); l.add(j, l.removeAt(from)); return l
    }

    /** [items] arranged by [saved] keys first (in that order, unknown keys ignored), the rest after them in their own order. */
    fun <T> orderBy(items: List<T>, saved: List<String>, key: (T) -> String): List<T> {
        if (saved.isEmpty()) return items
        val rank = HashMap<String, Int>(); saved.forEachIndexed { i, k -> rank.putIfAbsent(k, i) }
        return items.withIndex().sortedWith(compareBy({ rank[key(it.value)] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
    }

    /** The `shown` list with the entries that [resolved] names rearranged to [newOrder]; entries not in [resolved] keep their slots. */
    fun replaceResolved(shown: List<String>, resolved: Set<String>, newOrder: List<String>): List<String> {
        val it = newOrder.iterator()
        return shown.map { s -> if (s in resolved && it.hasNext()) it.next() else s }
    }

    /**
     * What Alt+Left or Alt+Right does to the focused chip: -1 moves it earlier, 1 later, 0 nothing. [towardsLeft] is true for the Left arrow, false for
     * Right, null for any other key. The arrows follow the screen, so in a right-to-left row Alt+Left moves a chip later.
     */
    fun keyStep(alt: Boolean, towardsLeft: Boolean?, rtl: Boolean): Int {
        if (!alt || towardsLeft == null) return 0
        val screen = if (towardsLeft) -1 else 1
        return if (rtl) -screen else screen
    }
}
