package com.bos.sphere.feature.grid

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
