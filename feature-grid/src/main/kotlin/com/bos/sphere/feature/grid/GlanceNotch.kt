package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

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
    /** Height of the upper shoulders: long and shallow, so the notch melts into the top edge. */
    const val SHOULDER_DP = 26f
    const val CORNER_DP = 14f
    const val TARGET_DP = 48f
    const val ICON_DP = 22f
    /** The icon centre sits this far below the top of the controls row (cutout bottom). */
    const val ICON_TOP_DP = 13f
    /** How long a tapped play/pause glyph stays flipped while the player has not confirmed; a command the player ignored shows the old glyph again. */
    const val PENDING_MS = 1500L
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

/**
 * W25 (owner item 3, "it needs to gently merge into the top edge"): the notch outline as ONE smooth path. The top edge is flush at y = 0 and
 * is drawn a few pixels past the screen edge (so no anti-aliasing seam), each upper shoulder is a long concave cubic that leaves the top edge
 * horizontally (tangent along the edge, no corner, no vertical segment there) and arrives vertically at the straight side after [shoulder] dp,
 * the bottom corners are convex cubics. [flare] is the horizontal reach of a shoulder, [shoulder] its height, [corner] the bottom radius; all in dp.
 */
class NotchShape(
    private val flare: Float = GlanceNotch.FLARE_DP, private val corner: Float = GlanceNotch.CORNER_DP,
    private val shoulder: Float = GlanceNotch.SHOULDER_DP,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = density.density
        return Outline.Generic(path(size.width, size.height, flare * d, corner * d, shoulder * d))
    }

    companion object {
        /** Bezier control polygon of the LEFT shoulder, from the top edge (0, 0) to the side (f, hs). The right one is its mirror image. */
        fun shoulderPoints(f: Float, hs: Float): List<Pair<Float, Float>> =
            listOf(0f to 0f, (f * 0.62f) to 0f, f to (hs * 0.38f), f to hs)

        /** The Bezier point at [t] of [shoulderPoints]. */
        fun shoulderAt(f: Float, hs: Float, t: Float): Pair<Float, Float> {
            val p = shoulderPoints(f, hs); val u = 1f - t
            val x = u * u * u * p[0].first + 3 * u * u * t * p[1].first + 3 * u * t * t * p[2].first + t * t * t * p[3].first
            val y = u * u * u * p[0].second + 3 * u * u * t * p[1].second + 3 * u * t * t * p[2].second + t * t * t * p[3].second
            return x to y
        }

        fun path(w: Float, h: Float, f: Float, c: Float, hs: Float = f, over: Float = 3f): Path = Path().apply {
            val k = 0.5523f * c // circle-like convex corner as a cubic
            moveTo(-over, -over); lineTo(w + over, -over); lineTo(w + over, 0f); lineTo(w, 0f)
            cubicTo(w - f * 0.62f, 0f, w - f, hs * 0.38f, w - f, hs)
            lineTo(w - f, h - c)
            cubicTo(w - f, h - c + k, w - f - c + k, h, w - f - c, h)
            lineTo(f + c, h)
            cubicTo(f + c - k, h, f, h - c + k, f, h - c)
            lineTo(f, hs)
            cubicTo(f, hs * 0.38f, f * 0.62f, 0f, 0f, 0f)
            lineTo(-over, 0f)
            close()
        }
    }
}
