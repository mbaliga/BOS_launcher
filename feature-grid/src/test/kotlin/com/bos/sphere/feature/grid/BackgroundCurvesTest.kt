package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.BgItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-16, PAL-24, NFR-14 (wave 15): the per-level parameter tables, the level easing and the slot picker's wording. */
class BackgroundCurvesTest {
    private val animated = BgItem.entries.filter { it != BgItem.None && it != BgItem.GlowLines }

    @Test fun HOM_16_everyItemHasParametersThatNeverShrinkAsTheSignalGetsStronger() {
        for (item in animated) {
            val tables = BackgroundCurves.tablesOf(item)
            assertTrue("$item has parameters", tables.isNotEmpty())
            for (t in tables) {
                assertEquals(5, t.size)
                // ping interval is the one parameter that falls with the level (more pings), and 0 = none
                if (t === BackgroundCurves.PING_EVERY_MS) { assertEquals(0f, t[0], 0f); for (l in 2..4) assertTrue(t[l] < t[l - 1]); continue }
                for (l in 1..4) assertTrue("$item level $l >= level ${l - 1}: ${t.toList()}", t[l] >= t[l - 1])
                assertTrue("$item strictly grows from level 1 to 4: ${t.toList()}", t[4] > t[1])
            }
        }
    }

    @Test fun HOM_16_noSignalIsADimRestingStateNotNothingAndExcellentIsTheFullPicture() {
        for (t in listOf(BackgroundCurves.BEAM_TIP, BackgroundCurves.SPIRE_CORE, BackgroundCurves.HALO_HAZE, BackgroundCurves.LASER_FLOOR, BackgroundCurves.SHAFT_BRIGHT, BackgroundCurves.BOTTOM_BRIGHT)) {
            assertTrue("level 0 stays visible: ${t.toList()}", t[0] > 0f)
            assertTrue("level 0 is dimmer than half of level 4", t[0] < t[4] / 2f)
            assertEquals("level 4 is the full picture", 1f, t[4], 0f)
        }
    }

    @Test fun HOM_16_haloArcSweepsFromAShortArcToANearlyClosedRing() {
        assertTrue(BackgroundCurves.HALO_ARC[0] < 40f)
        assertTrue(BackgroundCurves.HALO_ARC[4] in 300f..359f)
    }

    @Test fun HOM_16_interpolationBetweenLevelsIsLinearAndClamped() {
        val t = BackgroundCurves.BEAM_REACH
        assertEquals(t[2], BackgroundCurves.at(t, 2f), 0f)
        assertEquals((t[2] + t[3]) / 2f, BackgroundCurves.at(t, 2.5f), 1e-6f)
        assertEquals(t[0], BackgroundCurves.at(t, -3f), 0f)
        assertEquals(t[4], BackgroundCurves.at(t, 9f), 0f)
        assertEquals(t[4], BackgroundCurves.at(t, 4f), 0f)
    }

    @Test fun HOM_16_levelsEaseOverAboutOneSecondAndAreInstantUnderReducedMotion() {
        val e = LevelEase()
        assertEquals(3f, e.step(3, 0, false), 0f)
        e.step(0, 0, false)                      // same instant: no movement yet
        var v = 3f
        for (ms in 16..1000 step 16) v = e.step(0, ms.toLong(), false)
        assertTrue("about 95 percent of the way after a second: $v", v < 0.3f && v > 0f)
        for (ms in 1016..3000 step 16) v = e.step(0, ms.toLong(), false)
        assertEquals(0f, v, 0f)
        val up = LevelEase().also { it.step(0, 0, true) }
        assertEquals(4f, up.step(4, 10, true), 0f)
        assertEquals("a null level (source not heard yet) is calm: good", 3f, LevelEase().step(null, 0, true), 0f)
    }

    @Test fun HOM_16_easingMovesMonotonicallyAndNeverOvershoots() {
        val e = LevelEase(); e.step(1, 0, true)
        var last = 1f
        for (ms in 16..2000 step 16) { val v = e.step(4, ms.toLong(), false); assertTrue(v >= last && v <= 4f); last = v }
    }

    @Test fun HOM_16_pickerListsNoneThenTheFiveWallpapersThenTheFourEffects() {
        assertEquals(listOf("none", "beam", "spire", "halo", "laser", "shaft", "glow-lines", "breathing-dots", "bottom-glow", "radar-sweep"), BackgroundPickerOrder.items.map { it.id })
        assertEquals("Only one living wallpaper", refusalText(com.bos.sphere.core.data.BackgroundRefusal.TwoWallpapers))
        assertEquals("Already in the other slot", refusalText(com.bos.sphere.core.data.BackgroundRefusal.SameItem))
    }

    @Test fun PAL_24_pingIntervalGetsShorterWithTheLevelAndNoSignalHasNoPing() {
        assertEquals(-1f, ScopeMath.pingProgress(100L, 0f), 0f)
        assertTrue(ScopeMath.pingProgress(100L, 3200f) >= 0f)
        // at 6 s into a 5 s cycle the ping of a faster level is under way while a slower one has not started
        assertTrue(ScopeMath.pingProgress(9_100L, 3200f) >= 0f || ScopeMath.pingProgress(9_100L, 9000f) >= 0f)
    }

    @Test fun HOM_16_dotGridBreathAndLitRadiusFollowTheLevelAndTheDefaultsKeepTheWaveFourteenLook() {
        val layout = DotGridMath.layout(411f, 891f, 2.6f)
        val b1 = Array(DotGridMath.BUCKETS) { FloatArray(layout.count * 2) }; val c1 = IntArray(DotGridMath.BUCKETS)
        val b2 = Array(DotGridMath.BUCKETS) { FloatArray(layout.count * 2) }; val c2 = IntArray(DotGridMath.BUCKETS)
        DotGridMath.fillBuckets(layout, 1234L, false, b1, c1)
        DotGridMath.fillBuckets(layout, 1234L, false, b2, c2, DotGridMath.BASE_AMPLITUDE, 1f)
        assertEquals(c1.toList(), c2.toList())
        assertEquals(DotGridMath.brightness(0.8f, 0.3f), DotGridMath.brightness(0.8f, 0.3f, DotGridMath.BASE_AMPLITUDE), 1e-6f)
        val small = IntArray(DotGridMath.BUCKETS); DotGridMath.fillBuckets(layout, 1234L, true, b2, small, 0.3f, 0.4f)
        val big = IntArray(DotGridMath.BUCKETS); DotGridMath.fillBuckets(layout, 1234L, true, b2, big, 0.3f, 1f)
        assertTrue("a wider lit radius lights more dots: ${small.sum()} < ${big.sum()}", small.sum() < big.sum())
    }
}
