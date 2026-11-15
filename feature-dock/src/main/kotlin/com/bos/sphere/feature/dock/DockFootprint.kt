package com.bos.sphere.feature.dock

/**
 * HOM-19: the space the dock rail takes on its edge, so the home can keep its app area clear of it.
 * The rail is [SLOT_DP] slots with [PAD_DP] padding on each side and a margin to the window edge; the
 * frame's margin band is about 15% of the short side. Where the rail is thicker than the band (any
 * window with a short side under about 540 dp) the app area gives up the difference.
 */
object DockFootprint {
    const val SLOT_DP = 52f
    const val PAD_DP = 10f
    const val BOTTOM_MARGIN_DP = 8f
    const val SIDE_MARGIN_DP = 12f
    const val COLLAPSED_DP = 18f

    /** How far the rail reaches in from its edge, in dp. */
    fun thicknessDp(edge: DockEdge, collapsed: Boolean): Float {
        val margin = if (edge == DockEdge.Bottom) BOTTOM_MARGIN_DP else SIDE_MARGIN_DP
        return margin + if (collapsed) COLLAPSED_DP else SLOT_DP + 2 * PAD_DP
    }

    data class Reserve(val bottom: Float = 0f, val start: Float = 0f, val end: Float = 0f) {
        val any: Boolean get() = bottom > 0f || start > 0f || end > 0f
    }

    /** What the app area gives up beyond the band: zero when the rail fits inside the band. [enabled] false means no rail. */
    fun reserve(enabled: Boolean, edge: DockEdge, collapsed: Boolean, bandDp: Float): Reserve {
        if (!enabled) return Reserve()
        val extra = (thicknessDp(edge, collapsed) - bandDp).coerceAtLeast(0f)
        return when (edge) {
            DockEdge.Bottom -> Reserve(bottom = extra)
            DockEdge.Left -> Reserve(start = extra)
            DockEdge.Right -> Reserve(end = extra)
        }
    }
}
