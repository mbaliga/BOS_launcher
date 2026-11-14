package com.bos.sphere.core.input

/** Screen corners as the pointer sees them (names match the launcher's Corner enum). */
enum class HotCornerId { TopLeft, TopRight, BottomRight, BottomLeft }

/**
 * Hot corners (INP-16). With a pointer, resting in a corner zone for [dwellMs] reveals that corner's
 * action; a click then activates it. The reveal never happens during a drag. [dwellMs] of 0 or less
 * means the feature is off. Time is passed in, so the tracker is plain JVM and fully testable.
 */
class HotCornerTracker(dwellMs: Long = DEFAULT_DWELL_MS, val zoneDp: Float = DEFAULT_ZONE_DP) {
    var dwellMs: Long = dwellMs
    private var inside: HotCornerId? = null
    private var since: Long = 0L
    var revealed: HotCornerId? = null
        private set

    val enabled get() = dwellMs > 0

    /** A pointer move or hover. [corner] is the zone under the pointer, or null. */
    fun onPointer(corner: HotCornerId?, nowMs: Long, dragging: Boolean) {
        if (!enabled || dragging) { reset(); return }
        if (corner != inside) {
            inside = corner
            since = nowMs
            revealed = null
        }
        tick(nowMs)
    }

    /** Called by a timer: reveals once the pointer has rested long enough. */
    fun tick(nowMs: Long) {
        val c = inside
        if (enabled && c != null && revealed == null && nowMs - since >= dwellMs) revealed = c
    }

    /** Milliseconds until the pending reveal, or null when nothing is pending. */
    fun pendingMs(nowMs: Long): Long? = inside?.takeIf { revealed == null && enabled }?.let { (dwellMs - (nowMs - since)).coerceAtLeast(0) }

    /** The pointer left the window, a drag started or a button went down elsewhere. */
    fun reset() { inside = null; revealed = null }

    /** A click: returns the corner to activate, only if it is revealed. */
    fun onClick(): HotCornerId? = revealed

    companion object {
        const val DEFAULT_DWELL_MS = 150L
        const val DEFAULT_ZONE_DP = 28f
        const val MAX_DWELL_MS = 1000L

        /** The corner zone containing the point, or null. Sizes in the same unit as [zone]. */
        fun zoneAt(x: Float, y: Float, width: Float, height: Float, zone: Float): HotCornerId? {
            val left = x <= zone; val right = x >= width - zone
            val top = y <= zone; val bottom = y >= height - zone
            return when {
                left && top -> HotCornerId.TopLeft
                right && top -> HotCornerId.TopRight
                right && bottom -> HotCornerId.BottomRight
                left && bottom -> HotCornerId.BottomLeft
                else -> null
            }
        }
    }
}
