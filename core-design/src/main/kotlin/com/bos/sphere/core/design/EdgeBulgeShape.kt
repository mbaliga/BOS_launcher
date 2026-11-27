package com.bos.sphere.core.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * W25/W60 (owner item 3, then "trace the shape and make sure it's exactly right", with the exact "Music Controls.svg" / "Notification
 * bar.svg" vector source supplied): a bulge that melts into a screen edge as ONE smooth path, shared by every host that needs it (the
 * Glance music-controls notch on the top edge, the Launchpad "waiting" indicator on the left edge). The flush edge is drawn a few pixels
 * past itself (no anti-aliasing seam); the curve itself ([FLARE_SPLINE]) leaves the flush edge with a horizontal tangent and arrives at
 * the flat far side with a horizontal tangent too (no shoulder-then-separate-corner join, no straight run anywhere in between) - the
 * exact curve traced from the owner's own design file, not a hand-derived approximation. [flare] + [corner] together are the curve's
 * total reach into the flat body (kept at the existing dp budget so the "three 48 dp targets fit the body" contract is unaffected); the
 * far side's total length is [corner]'s old name only by convention now - the curve no longer has a separate circular corner piece.
 */
class NotchShape(
    private val flare: Float = CORNER_DEFAULTS.first, private val corner: Float = CORNER_DEFAULTS.second,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = density.density
        return Outline.Generic(path(size.width, size.height, flare * d, corner * d))
    }

    companion object {
        private val CORNER_DEFAULTS = 28f to 14f

        /**
         * The curve's control polygon, normalized to a unit square: reach (how far it has eaten into the flat body, 0 at the flush edge
         * to 1 at the flat body's own edge) on one axis, depth (0 at the flush edge to 1 at the far flat side) on the other. Traced
         * directly from "Music Controls.svg"'s path data (8 cubic segments from the top-right corner down to where it meets the flat
         * bottom), each entry (c1r, c1d, c2r, c2d, er, ed). The first segment's first control point shares the start's depth (0), and the
         * last segment's second control point shares the end's depth (1): the curve leaves and arrives horizontally by construction, not
         * by a separate rule, exactly like the source file.
         */
        private val FLARE_SPLINE = floatArrayOf(
            0.08193f, 0.0f, 0.14439f, 0.00258f, 0.18292f, 0.01613f,
            0.26805f, 0.04548f, 0.30491f, 0.07645f, 0.33426f, 0.11355f,
            0.36027f, 0.14677f, 0.38392f, 0.18871f, 0.40075f, 0.23226f,
            0.42468f, 0.29387f, 0.44318f, 0.35419f, 0.47308f, 0.45839f,
            0.50619f, 0.57355f, 0.53651f, 0.65935f, 0.56573f, 0.72097f,
            0.58701f, 0.76581f, 0.62276f, 0.82839f, 0.65892f, 0.86935f,
            0.68341f, 0.89677f, 0.71067f, 0.91871f, 0.74725f, 0.94032f,
            0.81708f, 0.98161f, 0.89512f, 1.0f, 1.0f, 1.0f,
        )

        /**
         * Appends the flare curve starting from the path's current point (assumed to already be at the flush-edge end, reach = 0, depth
         * = 0 in local terms), tracing to reach = 1, depth = 1 (the flat body's own corner). [ox]/[oy] place reach = 0, depth = 0 in the
         * path's real coordinates; [reachX]/[reachY] and [depthX]/[depthY] are how much real x/y change per unit of reach/depth (each
         * pair has exactly one non-zero component - this is how the same spline serves both the horizontal top edge and, transposed, the
         * vertical left edge, with reach and depth swapped onto the other axis). [reverse] walks the spline backwards (reach 1 to 0),
         * for the mirrored half that must arrive back at a flush edge rather than leave one.
         */
        private fun Path.flareSpline(
            ox: Float, oy: Float, reachX: Float, reachY: Float, depthX: Float, depthY: Float, reverse: Boolean,
        ) {
            fun point(r: Float, d: Float) = (ox + r * reachX + d * depthX) to (oy + r * reachY + d * depthY)
            val segs = FLARE_SPLINE.size / 6
            val order = if (reverse) (segs - 1) downTo 0 else 0 until segs
            for (i in order) {
                val b = i * 6
                val c1r = FLARE_SPLINE[b]; val c1d = FLARE_SPLINE[b + 1]
                val c2r = FLARE_SPLINE[b + 2]; val c2d = FLARE_SPLINE[b + 3]
                val er = FLARE_SPLINE[b + 4]; val ed = FLARE_SPLINE[b + 5]
                val (c1, c2, end) = if (!reverse) Triple(point(c1r, c1d), point(c2r, c2d), point(er, ed))
                else Triple(point(c2r, c2d), point(c1r, c1d), point(FLARE_SPLINE.getOrElse(b - 6 + 4) { 0f }, FLARE_SPLINE.getOrElse(b - 6 + 5) { 0f }))
                cubicTo(c1.first, c1.second, c2.first, c2.second, end.first, end.second)
            }
        }

        /**
         * The bulge hanging from a horizontal top edge, width [w], height [h]: [f] + [c] is the curve's reach into the flat body,
         * clamped to at most half of [w] so the two mirrored curves can never cross (a body narrower than `2 * (f + c)` would
         * otherwise self-intersect into a pointed, arrow-like shape instead of a bulge).
         */
        fun path(w: Float, h: Float, f: Float, c: Float, over: Float = 3f): Path = Path().apply {
            val reach = minOf(f + c, w / 2f)
            moveTo(-over, -over); lineTo(w + over, -over); lineTo(w + over, 0f); lineTo(w, 0f)
            // Right half: reach grows leftward from the right edge, depth grows downward.
            flareSpline(ox = w, oy = 0f, reachX = -reach, reachY = 0f, depthX = 0f, depthY = h, reverse = false)
            lineTo(reach, h) // the flat run between where each half's curve meets the body's own corner
            // Left half (mirrored): walked backwards, from the flat body's own corner back up to the flush edge at x = 0.
            flareSpline(ox = 0f, oy = 0f, reachX = reach, reachY = 0f, depthX = 0f, depthY = h, reverse = true)
            lineTo(-over, 0f)
            close()
        }

        /**
         * The same bulge, transposed 90° for a vertical edge: flush against x = 0 for the whole [l]ength, flaring at each end to meet
         * that edge, bulging out to a flat far side at depth [d] in between. It is [path]'s body with reach/depth swapped onto the other
         * axis (x and y exchanged), so the two stay the exact same curve by construction and can never drift apart.
         */
        fun edgePath(l: Float, d: Float, f: Float, c: Float, over: Float = 3f): Path = Path().apply {
            val reach = minOf(f + c, l / 2f)
            moveTo(-over, -over); lineTo(-over, l + over); lineTo(0f, l + over); lineTo(0f, l)
            // Top half: reach grows upward from the bottom... no - this traces the END of the length (the "bottom" of the vertical bar)
            // back toward the flush edge, mirroring path()'s right-half-then-left-half order exactly (transposed: y plays reach's role).
            flareSpline(ox = 0f, oy = l, reachX = 0f, reachY = -reach, depthX = d, depthY = 0f, reverse = false)
            lineTo(d, reach) // the flat run between where each half's curve meets the body's own corner
            flareSpline(ox = 0f, oy = 0f, reachX = 0f, reachY = reach, depthX = d, depthY = 0f, reverse = true)
            lineTo(0f, -over)
            close()
        }
    }
}

/** The vertical-edge twin of [NotchShape]: same curve, transposed via [NotchShape.edgePath] so it always matches the top notch exactly. */
class EdgeBulgeShape(
    private val flare: Float = 28f, private val corner: Float = 14f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = density.density
        return Outline.Generic(NotchShape.edgePath(size.height, size.width, flare * d, corner * d))
    }
}
