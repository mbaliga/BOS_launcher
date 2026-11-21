package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 30 (radarDim, PAL-24, PAL-18): the maths of Radar's dimmed background, no Android needed. */
class RadarDimMathW30Test {
    @Test fun PAL_24_w30_theScrimLeavesAtMostAQuarterOfTheWallpaperBrightness() {
        assertTrue("scrim at least 75 percent", RadarDim.SCRIM >= 0.75f)
        assertEquals(0.25f, RadarDim.brightestAnywhere, 1e-6f)
        assertEquals(0.5f * 0.25f, RadarDim.after(0.5f, RadarDim.SCRIM), 1e-6f)
    }

    @Test fun PAL_24_w30_theWheelCapKeepsThePureWhiteWorstCaseUnderThreePercentOfGreyAndContrastHolds() {
        val worst = RadarDim.brightestBehindWheel
        assertTrue("worst pixel behind the wheel $worst", worst <= 0.025f)
        assertTrue(RadarDim.whiteOnGrey(RadarDim.SELECTED_LABEL_ALPHA, worst) >= RadarDim.SELECTED_MIN_CONTRAST)
        assertTrue(RadarDim.whiteOnGrey(RadarDim.NEIGHBOUR_LABEL_ALPHA, worst) >= RadarDim.NEIGHBOUR_MIN_CONTRAST)
    }

    @Test fun PAL_24_w30_withoutTheWheelCapTheFadedNeighboursWouldFailThreeToOne() {
        // That is why the wheel gets a cap of its own: the scrim alone is not enough for a bloom at full white.
        assertTrue(RadarDim.whiteOnGrey(RadarDim.NEIGHBOUR_LABEL_ALPHA, RadarDim.brightestAnywhere) < RadarDim.NEIGHBOUR_MIN_CONTRAST)
    }

    @Test fun PAL_24_w30_contrastMathMatchesKnownValues() {
        assertEquals(21f, RadarDim.contrast(1f, 0f), 1e-3f)
        assertEquals(1f, RadarDim.contrast(0.3f, 0.3f), 1e-6f)
        assertEquals(0f, RadarDim.greyLuminance(0f), 0f)
        assertEquals(1f, RadarDim.greyLuminance(1f), 1e-4f)
        assertEquals(21f, RadarDim.whiteOnGrey(1f, 0f), 1e-3f) // opaque white on black
    }

    @Test fun PAL_24_w30_theZoneIsTheWheelRowCappedAtTheLabelReachAndEveryLabelBoxIsFullyCapped() {
        val row = Rect(0f, 800f, 1080f, 1140f)
        val zone = RadarDim.wheelZone(row, density = 2.75f)
        assertEquals(540f, zone.center.x, 0.01f)
        assertEquals(2f * 190f * 2.75f, zone.width, 0.01f)
        assertEquals(row.top, zone.top, 0f); assertEquals(row.bottom, zone.bottom, 0f)
        assertTrue(RadarDim.fullyCapped(zone, Rect(540f, 800f, 1000f, 1140f))) // the wheel
        assertTrue(RadarDim.fullyCapped(zone, Rect(300f, 930f, 520f, 1010f))) // "SEARCH"
        assertFalse(RadarDim.fullyCapped(zone, Rect(0f, 0f, 1080f, 2000f))) // the whole screen is not
        val narrow = RadarDim.wheelZone(Rect(0f, 0f, 300f, 100f), 3f)
        assertEquals(300f, narrow.width, 0.01f)
    }
}
