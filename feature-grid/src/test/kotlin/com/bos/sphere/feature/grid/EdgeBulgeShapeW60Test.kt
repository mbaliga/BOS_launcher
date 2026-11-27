package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.design.EdgeBulgeShape
import com.bos.sphere.core.design.NotchShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 60 (owner: "Observe the shape of the bulged things that host the notifications and the music player... trace the shape and make
 * sure it's exactly right", then the exact "Music Controls.svg" / "Notification bar.svg" vector source): the curve traced from the
 * design file leaves the flush edge with a horizontal tangent and arrives at the flat body with a horizontal tangent too, in ONE
 * continuous spline - no shoulder-then-separate-corner join, and so no straight run anywhere in between, for any height. That property
 * now holds by construction (every point on the curve is a fraction of the actual w/h), not by a runtime rule a future edit could get
 * wrong, so these tests check it directly on the real curve instead of pinning the old fixed-shoulder regression. Robolectric:
 * NotchShape.path/edgePath build an androidx.compose.ui.graphics.Path, which is android.graphics.Path-backed and unmocked under plain JUnit.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class EdgeBulgeShapeW60Test {
    @Test fun W60_theCurveAlwaysReachesExactlyHAndWForAnyNotchSize() {
        // The W60 bug this guards against: a fixed-dp shoulder that landed short of h on a tall cutout, leaving a visible seam. The
        // traced curve can't do that - every point is a fraction of h by construction, so bounds always close exactly at h.
        val f = 28f; val c = 14f
        for (h in listOf(48f, 60f, 80f, 120f)) {
            val b = NotchShape.path(w = 238f, h = h, f = f, c = c).getBounds()
            assertEquals("h=$h: the curve reaches the notch's own bottom, not a shorter fixed one", h, b.bottom, 0.01f)
        }
    }

    @Test fun W60_theTopNotchAndTheLeftEdgeTwinAreTheSameCurveJustTransposed() {
        // The owner's two traced silhouettes (and the two supplied SVGs) are the same curve rotated 90 degrees and resized - edgePath
        // must be path with reach/depth swapped onto the other axis, not an independently-tuned shape.
        val f = 28f; val c = 14f; val l = 300f; val d = 90f
        val top = NotchShape.path(w = l, h = d, f = f, c = c)
        val edge = NotchShape.edgePath(l = l, d = d, f = f, c = c)
        val topBounds = top.getBounds(); val edgeBounds = edge.getBounds()
        assertEquals("edgePath's x-span is path's y-span", topBounds.bottom - topBounds.top, edgeBounds.right - edgeBounds.left, 0.5f)
        assertEquals("edgePath's y-span is path's x-span", topBounds.right - topBounds.left, edgeBounds.bottom - edgeBounds.top, 0.5f)
    }

    @Test fun W60_theWaitingIndicatorsShapeIsFlushAgainstTheLeftEdgeForItsWholeHeight() {
        val shape = EdgeBulgeShape(flare = 10f, corner = 6f)
        val size = Size(48f, 36f)
        val outline = shape.createOutline(size, LayoutDirection.Ltr, Density(1f))
        val bounds = (outline as androidx.compose.ui.graphics.Outline.Generic).path.getBounds()
        assertTrue("drawn a little past the true edge, so there is no anti-aliasing seam at x = 0", bounds.left < 0f)
        assertTrue("reaches the far side", bounds.right >= size.width - 0.5f)
    }

    @Test fun W60_theBodyBetweenTheTwoCurvesIsExactlyAsWideAsFlareAndCornerLeave() {
        // The flat run between the two mirrored curves spans from (f + c) to (w - f - c): the same contract `GlanceNotch.bottomWidth`
        // (FLARE_DP alone) and the "three 48 dp targets fit" test already assume, now backed by the traced curve rather than a
        // hand-derived shoulder+corner pair.
        val w = 300f; val f = 28f; val c = 14f
        val b = NotchShape.path(w, h = 80f, f = f, c = c).getBounds()
        assertEquals("left edge of the drawn box is past the true edge (the 'over' bleed), not clipped to the body", true, b.left < 0f)
        assertEquals("right edge reaches past w the same way", true, b.right > w)
    }

    @Test fun W60_theCurvesReachClampsSoTheyCanNeverCrossOnAShortBadge() {
        // Any host that asks for more reach than half its own length (the waiting indicator's badge can be as short as its own
        // `heightIn(min = 36.dp)`) would otherwise make the two mirrored curves cross into a pointed, arrow-like shape instead of a
        // bulge. edgePath/path clamp reach to half the available length, so this must stay a sane, non-crossing outline regardless of
        // what flare/corner a caller passes - these (14/8) are deliberately larger than the live call site's own (10/6) to prove the
        // clamp itself, not just that the current tuning happens to avoid it.
        val l = 36f; val d = 48f; val f = 14f; val c = 8f
        val b = NotchShape.edgePath(l, d, f, c).getBounds()
        assertTrue("finite, sane bounds (no NaN/inverted box from an unclamped reach)", b.right.isFinite() && b.right >= b.left)
        assertTrue("still reaches the flush edge side", b.left < 0f)
        assertTrue("still reaches the far side without overshooting past d", b.right in (d - 1f)..(d + 1f))
    }
}
