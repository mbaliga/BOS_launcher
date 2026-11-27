package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.NotchShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-23 (wave 22): the pure numbers of the controls notch. */
class GlanceNotchGeometryTest {
    @Test fun HOM_23_theNotchIsAboutFiftyEightPercentOfTheAreaWithinTheCaps() {
        assertEquals(411f * 0.58f, GlanceNotch.widthDp(411f), 0.01f)
        assertEquals("capped on a wide window", GlanceNotch.MAX_WIDTH_DP, GlanceNotch.widthDp(891f), 0.01f)
        assertEquals("never wider than a tiny window", 200f, GlanceNotch.widthDp(200f), 0.01f)
        assertEquals("a floor that keeps three targets", GlanceNotch.MIN_WIDTH_DP, GlanceNotch.widthDp(320f), 0.01f)
    }

    @Test fun HOM_23_theTopIsWiderThanTheBodyAndTheThreeTargetsFitTheBody() {
        val top = GlanceNotch.widthDp(411f)
        val body = GlanceNotch.bottomWidth(top)
        assertTrue("flared: the top is wider than the bottom", top > body)
        assertEquals(2f * GlanceNotch.FLARE_DP, top - body, 0.01f)
        assertTrue("three 48 dp targets fit the body", body >= 3 * GlanceNotch.TARGET_DP)
    }

    @Test fun HOM_23_theHeightIsTheBodyBelowTheCutoutAndNeverLessThanTheBody() {
        assertEquals(48f, GlanceNotch.heightDp(0f), 0f)
        assertEquals(88f, GlanceNotch.heightDp(40f), 0f)
        assertEquals(48f, GlanceNotch.heightDp(-5f), 0f)
        assertEquals("wave 23: the drawn body is as tall as the 48 dp targets", GlanceNotch.TARGET_DP, GlanceNotch.BODY_HEIGHT_DP, 0f)
    }
}

class GlanceNotchHitAreaW23Test {
    @Test fun HOM_23_theIconIsCentredInTheTargetAndTheTargetFillsTheBody() {
        assertEquals((GlanceNotch.TARGET_DP - GlanceNotch.ICON_DP) / 2f, GlanceNotch.ICON_TOP_DP, 0.6f)
        assertEquals(GlanceNotch.TARGET_DP, GlanceNotch.BODY_HEIGHT_DP, 0f)
    }
}

/** W25 (owner item 3): the notch merges gently into the top edge. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class GlanceNotchShapeW25Test {
    private val f = GlanceNotch.FLARE_DP
    private val hs = GlanceNotch.SHOULDER_DP

    @Test fun HOM_23_theShoulderStartsOnTheTopEdgeHorizontallyAndEndsVertically() {
        val p = NotchShape.shoulderPoints(f, hs)
        assertEquals("starts at the screen edge, y = 0", 0f, p[0].second, 0f)
        assertEquals("the first control keeps y = 0: the tangent runs along the top edge (no corner)", 0f, p[1].second, 0f)
        assertTrue("and leads away from the corner", p[1].first > 0f)
        assertEquals("the last control is straight above the end: it arrives vertically", p[3].first, p[2].first, 0f)
        assertTrue("a long flare, not a pasted box corner", f >= 24f && hs >= 24f)
    }

    @Test fun HOM_23_theShoulderIsConcaveMonotoneAndHasNoVerticalSegmentAtTheTopEdge() {
        var prev = NotchShape.shoulderAt(f, hs, 0f)
        for (i in 1..40) {
            val q = NotchShape.shoulderAt(f, hs, i / 40f)
            assertTrue("x never goes back", q.first >= prev.first)
            assertTrue("y never goes back", q.second >= prev.second)
            val chordY = hs * q.first / f
            assertTrue("concave: the curve stays on the black side of the chord (y=${q.second}, chord=$chordY)", q.second <= chordY + 0.001f)
            prev = q
        }
        val early = NotchShape.shoulderAt(f, hs, 0.05f)
        assertTrue("near the edge it runs sideways, not down (dy < dx)", early.second < early.first * 0.2f)
    }

    @Test fun HOM_23_thePathIsOneClosedSmoothOutlineThatCoversTheTopEdge() {
        val w = 238f; val h = 48f
        val b = NotchShape.path(w, h, f, GlanceNotch.CORNER_DP, hs).getBounds()
        assertTrue("drawn past the edge on top and both sides, so no seam", b.top < 0f && b.left < 0f && b.right > w)
        assertEquals("ends at the notch bottom", h, b.bottom, 0.01f)
    }
}
