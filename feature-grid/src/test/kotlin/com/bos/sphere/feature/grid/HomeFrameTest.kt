package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFrameTest {
    private val sizes = listOf(360f to 640f, 411f to 891f, 600f to 960f, 800f to 1280f, 1024f to 768f, 1920f to 1080f)

    @Test
    fun HOM_19_bandIsAboutFifteenPercentOfShortSideInBothOrientations() {
        for ((w, h) in sizes) {
            val portrait = HomeFrame.band(minOf(w, h), maxOf(w, h))
            val landscape = HomeFrame.band(maxOf(w, h), minOf(w, h))
            assertEquals(portrait, landscape, 0.001f)
            assertEquals(0.15f, portrait / minOf(w, h), 0.005f)
        }
    }

    @Test
    fun HOM_19_appAreaIsTheSameRegionRotatedAndEveryStyleUsesIt() {
        for ((w, h) in sizes) {
            val p = HomeFrame.appArea(minOf(w, h), maxOf(w, h))
            val l = HomeFrame.appArea(maxOf(w, h), minOf(w, h))
            // Same inset, so the landscape area is the portrait one turned on its side.
            assertEquals(p.width, l.height, 0.001f)
            assertEquals(p.height, l.width, 0.001f)
            assertEquals(p.left, l.top, 0.001f)
        }
        // The frame does not depend on the home style: every style reads the same function.
        for (style in com.bos.sphere.core.data.HomeStyle.entries) {
            assertEquals(HomeFrame.appArea(1080f, 2400f), HomeFrame.appArea(1080f, 2400f))
            assertTrue(style.name.isNotEmpty())
        }
    }

    @Test
    fun HOM_19_cornersClockDotsAndRailSitInTheBandNeverOnTheAppArea() {
        for ((w, h) in sizes) {
            val area = HomeFrame.appArea(w, h)
            val strips = HomeFrame.bandStrips(w, h)
            strips.forEach { assertFalse(it.intersects(area)) }
            // The strips plus the area cover the whole window exactly.
            val cover = strips.sumOf { (it.width * it.height).toDouble() } + area.width * area.height
            assertEquals((w * h).toDouble(), cover, 1.0)
        }
    }

    @Test
    fun HOM_20_clockAndDotsFitInBottomBandAtEverySupportedSize() {
        // Supported: short side from 320 dp, clock sizes up to 56, with or without a weather line.
        for (short in 320..1200 step 20) {
            val band = HomeFrame.band(short.toFloat(), short * 2f)
            for (clockSize in 16..56 step 4) {
                val noWeather = clockSize * 1.22f
                val full = noWeather + (clockSize * 0.42f).coerceIn(14f, 28f) + 2f
                val fit = ClockBandFit.compute(band, full, noWeather)
                assertTrue("short=$short clock=$clockSize used=${fit.usedDp} band=$band", fit.fits)
                assertTrue(fit.usedDp <= band + 0.01f)
                assertTrue(fit.clockScale >= ClockBandFit.MIN_CLOCK_SCALE)
            }
        }
    }

    @Test
    fun HOM_20_weatherLineGoesBeforeTheClockIsScaled() {
        val band = 61f
        val roomy = ClockBandFit.compute(band, 40f, 25f)
        assertTrue(roomy.showWeather && roomy.clockScale == 1f)
        val tight = ClockBandFit.compute(band, 56f, 38f)
        assertFalse(tight.showWeather)
        assertEquals(1f, tight.clockScale, 0f)
        val tighter = ClockBandFit.compute(band, 80f, 60f)
        assertFalse(tighter.showWeather)
        assertTrue(tighter.clockScale < 1f)
    }

    @Test
    fun HOM_20_dotsOnTheStartEdgeLeaveTheWholeBottomBandToTheClock() {
        val withDots = ClockBandFit.compute(50f, 60f, 40f, dotsAtBottom = true)
        val without = ClockBandFit.compute(50f, 60f, 40f, dotsAtBottom = false)
        assertTrue(without.clockScale >= withDots.clockScale)
    }

    @Test
    fun HOM_20_reportsWhenNothingCanFit() {
        assertFalse(ClockBandFit.compute(20f, 80f, 70f).fits)
    }
}

class ClockBandReserveTest {
    @org.junit.Test
    fun HOM_20_placementUsesTheSameReserveAsTheFit() {
        // The dots row sits EDGE above the band's bottom, the clock sits right above the dots row: the three add up to the fit's reserve.
        assertEquals(ClockBandFit.EDGE_DP + ClockBandFit.DOTS_DP + ClockBandFit.GAP_DP, ClockBandFit.clockBottomDp(true), 0f)
        assertEquals(ClockBandFit.DOTS_BOTTOM_DP, ClockBandFit.EDGE_DP, 0f)
        assertEquals(ClockBandFit.EDGE_DP, ClockBandFit.clockBottomDp(false), 0f)
        for (short in 320..1200 step 40) {
            val band = HomeFrame.band(short.toFloat(), short * 2f)
            val fit = ClockBandFit.compute(band, 60f, 40f)
            assertTrue(ClockBandFit.clockBottomDp(true) + (fit.usedDp - ClockBandFit.clockBottomDp(true)) <= band + 0.01f)
        }
    }
}
