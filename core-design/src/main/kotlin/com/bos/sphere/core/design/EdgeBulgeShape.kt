package com.bos.sphere.core.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * W25/W60 (owner item 3, then "trace the shape and make sure it's exactly right"): a bulge that melts into a screen edge as ONE smooth
 * path, shared by every host that needs it (the Glance music-controls notch on the top edge, the Launchpad "waiting" indicator on the
 * left edge). The flush edge is drawn a few pixels past itself (no anti-aliasing seam); each shoulder is a long concave cubic that leaves
 * the flush edge tangentially (no corner, no vertical/horizontal segment there) and arrives at the straight side after [shoulder] dp; the
 * far corners are convex cubics. [flare] is the reach of a shoulder along the flush edge, [shoulder] its depth, [corner] the far radius.
 */
class NotchShape(
    private val flare: Float = CORNER_DEFAULTS.first, private val corner: Float = CORNER_DEFAULTS.second,
    private val shoulder: Float? = null,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = density.density
        val hs = (shoulder ?: (size.height / d - corner)) * d
        return Outline.Generic(path(size.width, size.height, flare * d, corner * d, hs))
    }

    companion object {
        private val CORNER_DEFAULTS = 28f to 14f

        /** Bezier control polygon of the LEFT shoulder, from the flush edge (0, 0) to the side (f, hs). The right one is its mirror image. */
        fun shoulderPoints(f: Float, hs: Float): List<Pair<Float, Float>> =
            listOf(0f to 0f, (f * 0.62f) to 0f, f to (hs * 0.38f), f to hs)

        /** The Bezier point at [t] of [shoulderPoints]. */
        fun shoulderAt(f: Float, hs: Float, t: Float): Pair<Float, Float> {
            val p = shoulderPoints(f, hs); val u = 1f - t
            val x = u * u * u * p[0].first + 3 * u * u * t * p[1].first + 3 * u * t * t * p[2].first + t * t * t * p[3].first
            val y = u * u * u * p[0].second + 3 * u * u * t * p[1].second + 3 * u * t * t * p[2].second + t * t * t * p[3].second
            return x to y
        }

        /**
         * The bulge hanging from a horizontal top edge, width [w], height [h]. [hs] defaults to `h - c`: the shoulder curve then lands
         * exactly where the far corner's straight run would start, so the two curves join with no vertical seam, for any [h]. Pass [hs]
         * explicitly only to reproduce a fixed-shoulder shape (e.g. in a test that exercises one shoulder height directly).
         */
        fun path(w: Float, h: Float, f: Float, c: Float, hs: Float = h - c, over: Float = 3f): Path = Path().apply {
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

        /**
         * The same bulge, transposed 90° for a vertical edge: flush against x = 0 for the whole [l]ength, flaring at each end to meet
         * that edge, bulging out to a rounded far side at depth [d] in between. It is [path]'s body with every (x, y) swapped, so the two
         * stay the exact same curve by construction and can never drift apart.
         */
        fun edgePath(l: Float, d: Float, f: Float, c: Float, hs: Float = d - c, over: Float = 3f): Path = Path().apply {
            val k = 0.5523f * c
            moveTo(-over, -over); lineTo(-over, l + over); lineTo(0f, l + over); lineTo(0f, l)
            cubicTo(0f, l - f * 0.62f, hs * 0.38f, l - f, hs, l - f)
            lineTo(d - c, l - f)
            cubicTo(d - c + k, l - f, d, l - f - c + k, d, l - f - c)
            lineTo(d, f + c)
            cubicTo(d, f + c - k, d - c + k, f, d - c, f)
            lineTo(hs, f)
            cubicTo(hs * 0.38f, f, 0f, f * 0.62f, 0f, 0f)
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
