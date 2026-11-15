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

/**
 * HOM-19: the bottom of the home when the dock rail, the bottom clock and the page dots all want it. The clock and dots
 * stack up from the bottom edge (their height, [lowerStackDp], is what the clock fit reports); a rail on the bottom edge
 * sits above that stack instead of on top of it, and everything else that lives above the rail (the media strip, the group
 * shelf) rides on the rail's top. The app area gives up whatever the rail's top reaches beyond the frame band.
 */
object HomeBottomStack {
    /** Air between the lower stack and the rail, and between the rail and what sits above it. */
    const val GAP_DP = 4f
    /** Where the media strip sits with no rail (it clears the clock and dots in the band). */
    const val MEDIA_DEFAULT_DP = 66f
    const val SHELF_OFF_DP = 8f
    /** The group shelf's chip row: padding and one line of text. */
    const val SHELF_HEIGHT_DP = 40f

    data class Plan(
        /** Distance from the window's bottom edge to the rail's bottom (bottom rail only; the side rails use their own margins). */
        val railBottomDp: Float,
        /** Distance from the bottom edge to the top of the rail (bottom rail), or 0 when no bottom rail is drawn. */
        val railTopDp: Float,
        val reserve: DockFootprint.Reserve,
        val mediaBottomDp: Float,
        val shelfBottomDp: Float,
    )

    fun plan(enabled: Boolean, edge: DockEdge, collapsed: Boolean, bandDp: Float, lowerStackDp: Float, shelfShown: Boolean = false): Plan {
        val bottomRail = enabled && edge == DockEdge.Bottom
        val railBottom = if (bottomRail && lowerStackDp > 0f) maxOf(DockFootprint.BOTTOM_MARGIN_DP, lowerStackDp + GAP_DP) else DockFootprint.BOTTOM_MARGIN_DP
        val body = if (collapsed) DockFootprint.COLLAPSED_DP else DockFootprint.SLOT_DP + 2 * DockFootprint.PAD_DP
        val railTop = if (bottomRail) railBottom + body else 0f
        val reserve = when {
            !enabled -> DockFootprint.Reserve()
            edge == DockEdge.Bottom -> DockFootprint.Reserve(bottom = (railTop - bandDp).coerceAtLeast(0f))
            else -> DockFootprint.reserve(true, edge, collapsed, bandDp)
        }
        val shelfBottom = if (bottomRail) railTop + GAP_DP else maxOf(SHELF_OFF_DP, if (lowerStackDp > 0f) lowerStackDp + GAP_DP else 0f)
        val mediaBase = if (bottomRail) maxOf(MEDIA_DEFAULT_DP, railTop + GAP_DP) else MEDIA_DEFAULT_DP
        return Plan(
            railBottomDp = railBottom, railTopDp = railTop, reserve = reserve,
            // The media strip and the group shelf both ride on the rail: with a shelf on screen the strip sits above it.
            mediaBottomDp = if (shelfShown) maxOf(mediaBase, shelfBottom + SHELF_HEIGHT_DP + GAP_DP) else mediaBase,
            shelfBottomDp = shelfBottom,
        )
    }
}
