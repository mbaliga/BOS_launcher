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

/**
 * W25 (owner item 3): the notch merges gently into the top edge. W60: the curve itself is traced from the owner's own design file
 * ("Music Controls.svg"), not a hand-derived shoulder+corner approximation, so these now exercise [NotchShape.path] directly rather
 * than the retired `shoulderPoints`/`shoulderAt` helpers.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class GlanceNotchShapeW25Test {
    private val f = GlanceNotch.FLARE_DP
    private val c = GlanceNotch.CORNER_DP

    @Test fun HOM_23_thePathIsOneClosedSmoothOutlineThatCoversTheTopEdge() {
        val w = 238f; val h = 48f
        val b = NotchShape.path(w, h, f, c).getBounds()
        assertTrue("drawn past the edge on top and both sides, so no seam", b.top < 0f && b.left < 0f && b.right > w)
        assertEquals("ends at the notch bottom", h, b.bottom, 0.01f)
    }

    @Test fun HOM_23_thePathAlwaysReachesExactlyHForAnyCutoutHeight() {
        // The traced curve spans the whole height by construction (every point's depth is a fraction of h), so there is never a
        // shorter-than-h shoulder left behind, whatever the cutout makes h: the W60 bug this guards against.
        for (h in listOf(48f, 60f, 80f, 120f)) {
            val b = NotchShape.path(w = 238f, h = h, f = f, c = c).getBounds()
            assertEquals("h=$h", h, b.bottom, 0.01f)
        }
    }
}
