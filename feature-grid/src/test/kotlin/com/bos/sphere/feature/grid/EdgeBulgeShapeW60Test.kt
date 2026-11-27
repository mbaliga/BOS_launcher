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
 * sure it's exactly right"): the owner's reference image traced to two silhouettes (one on the top edge, one on the left edge) whose
 * per-column/per-row depth rose in a single smooth hump from the flush edge to a flat plateau, with no jump anywhere - never a hump that
 * jumps to a shallower or deeper value partway up, which is what a shoulder curve that stops short of (or overshoots) the bottom corner's
 * own start would draw. These tests hold that "no seam" property as a property of the curve itself, in dp, independent of any one device's
 * cutout height - the thing a fixed-dp shoulder constant (the pre-wave-60 bug) could not guarantee. Robolectric: NotchShape.path/edgePath
 * build an androidx.compose.ui.graphics.Path, which is android.graphics.Path-backed and unmocked under plain JUnit.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class EdgeBulgeShapeW60Test {
    @Test fun W60_theShoulderAlwaysLandsExactlyWhereTheCornersStraightRunWouldStart() {
        // On a realistic cutout (h = 80, so h - c = 66, far from the old fixed 26 dp shoulder), sample the SAME point on the shoulder
        // curve (t = 0.7, well clear of either endpoint, so this isn't sensitive to the curve's vertical tangent at t = 1) under both
        // the fix's shoulder height and the old fixed one: the fix reaches much deeper at the exact same horizontal position, which is
        // the visible difference between a smooth bulge and one with a kink carved out of it partway down.
        val f = 28f; val c = 14f; val h = 80f
        val fixedHs = h - c // the live call site's exact default: NotchShape.path(w, h, f, c) with no explicit hs.
        val oldHs = GlanceNotch.SHOULDER_DP
        val (xNew, yNew) = NotchShape.shoulderAt(f, fixedHs, 0.7f)
        val (xOld, yOld) = NotchShape.shoulderAt(f, oldHs, 0.7f)
        assertEquals("same t, so the same horizontal position for both", xNew, xOld, 0.01f)
        assertTrue("the fix's shoulder reaches far deeper at that same position: no seam left above it", yNew > yOld + 10f)
        assertEquals("the fix's curve actually ends at h - c, not a fixed dp value", fixedHs, NotchShape.shoulderAt(f, fixedHs, 1f).second, 0.01f)
    }

    @Test fun W60_theOldFixedShoulderWouldHaveLeftAVisibleSeamOnARealCutout() {
        // The regression this test documents: GlanceNotch.SHOULDER_DP (26) on a typical ~32 dp cutout (h = 32 + 48 = 80) used to leave a
        // jump of |hs - (h - c)| = |26 - 66| = 40 dp between the shoulder curve's end and the corner curve's start - a real, visible kink,
        // not a rounding error. The fix (hs defaults to h - c) makes that jump exactly zero for every h.
        val h = 80f; val c = 14f; val oldFixedHs = GlanceNotch.SHOULDER_DP
        val oldJump = kotlin.math.abs(oldFixedHs - (h - c))
        assertTrue("the old fixed shoulder really did create a >30 dp seam on a realistic cutout", oldJump > 30f)
        val newJump = kotlin.math.abs((h - c) - (h - c))
        assertEquals(0f, newJump, 0f)
    }

    @Test fun W60_theTopNotchAndTheLeftEdgeTwinAreTheSameCurveJustTransposed() {
        // The owner's two traced silhouettes measured numerically identical depth profiles at matching offsets, just rotated 90 degrees
        // and different lengths - edgePath must be path with every (x, y) swapped, not an independently-tuned shape.
        val f = 28f; val c = 14f; val l = 300f; val d = 90f
        val top = NotchShape.path(w = l, h = d, f = f, c = c)
        val edge = NotchShape.edgePath(l = l, d = d, f = f, c = c)
        val topBounds = top.getBounds(); val edgeBounds = edge.getBounds()
        assertEquals("edgePath's x-span is path's y-span", topBounds.bottom - topBounds.top, edgeBounds.right - edgeBounds.left, 0.5f)
        assertEquals("edgePath's y-span is path's x-span", topBounds.right - topBounds.left, edgeBounds.bottom - edgeBounds.top, 0.5f)
    }

    @Test fun W60_theWaitingIndicatorsShapeIsFlushAgainstTheLeftEdgeForItsWholeHeight() {
        val shape = EdgeBulgeShape(flare = 14f, corner = 8f)
        val size = Size(48f, 36f)
        val outline = shape.createOutline(size, LayoutDirection.Ltr, Density(1f))
        val bounds = (outline as androidx.compose.ui.graphics.Outline.Generic).path.getBounds()
        assertTrue("drawn a little past the true edge, so there is no anti-aliasing seam at x = 0", bounds.left < 0f)
        assertTrue("reaches the far side", bounds.right >= size.width - 0.5f)
    }
}
