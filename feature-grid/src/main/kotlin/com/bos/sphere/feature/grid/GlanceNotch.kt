package com.bos.sphere.feature.grid

/** HOM-23 (wave 22): the numbers of the controls notch (pure, in dp). */
object GlanceNotch {
    /** The notch is this share of the clock-area width, within [MIN_WIDTH_DP] and [MAX_WIDTH_DP], never wider than the window. */
    const val WIDTH_SHARE = 0.58f
    const val MIN_WIDTH_DP = 220f
    const val MAX_WIDTH_DP = 320f
    /** The drawn height below the display cutout (the cutout itself is also covered: the shape starts at y = 0). */
    /** Wave 23: as tall as the 48 dp targets, so the drawn notch and the touch area are the same (no invisible hit area below it). */
    const val BODY_HEIGHT_DP = 48f
    /** Concave flare of each top shoulder, and the convex radius of each bottom corner. */
    const val FLARE_DP = 28f
    /**
     * Legacy fixed shoulder height, kept only for the geometry tests that exercise [NotchShape.shoulderAt] in isolation. The live notch
     * (GlanceHome.kt) no longer passes this: it lets [NotchShape.path]'s `hs` default to `h - corner`, so the shoulder curve always lands
     * exactly where the bottom corner's straight run would otherwise start. A fixed shoulder shorter than `h - corner` left a straight
     * vertical seam between the two curves whenever the cutout made the notch taller than shoulder + corner, which read as a kink rather
     * than the single smooth bulge the shape is meant to be.
     */
    const val SHOULDER_DP = 26f
    const val CORNER_DP = 14f
    const val TARGET_DP = 48f
    const val ICON_DP = 22f
    /** The icon centre sits this far below the top of the controls row (cutout bottom). */
    const val ICON_TOP_DP = 13f
    /**
     * How long a tapped play/pause glyph stays flipped while the player has not confirmed; a command the player ignored shows the old
     * glyph again. W60: sized to outlast [com.bos.sphere.core.data.MediaCommands]'s own confirm-or-escalate ladder (up to 3 routes x
     * [com.bos.sphere.core.data.MediaCommands.CONFIRM_MS] = 2100 ms in the worst case), so a command that is slow but genuinely confirmed
     * does not revert the glyph only to flip it forward again a moment later; was 1500 ms before that ladder could retry.
     */
    const val PENDING_MS = 2200L
    /** W25: the title is pushed down by exactly the notch body while the controls are in, so it never touches the notch. */
    const val TITLE_PUSH_DP = BODY_HEIGHT_DP
    /** The title line's gap under the status area when the controls are away. */
    const val TITLE_TOP_DP = 14f
    /** Room kept free on each side of the title slot (the corner hotspots). */
    const val TITLE_SIDE_DP = 64f

    fun widthDp(areaWidthDp: Float): Float = (areaWidthDp * WIDTH_SHARE).coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP).coerceAtMost(areaWidthDp)
    /** The straight-sided body is this wide: the top is wider by a flare on each side. */
    fun bottomWidth(topWidth: Float, flare: Float = FLARE_DP): Float = topWidth - 2f * flare
    fun heightDp(cutoutTopDp: Float): Float = cutoutTopDp.coerceAtLeast(0f) + BODY_HEIGHT_DP
}

// W25/W60: the shape itself (com.bos.sphere.core.design.NotchShape) now lives in core-design, shared with the Launchpad waiting
// indicator's EdgeBulgeShape (its left-edge twin, com.bos.sphere.core.design.EdgeBulgeShape) so the two curves can never drift apart.
// GlanceNotch still passes its own FLARE_DP/CORNER_DP defaults at each call site in GlanceHome.kt, same as before this moved.
