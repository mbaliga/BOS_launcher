package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 20 audit of HOM-23, HOM-22, HOM-16, PAL-24: rules the earlier tests only checked through the code under test. */
class GlanceAuditW20Test {
    // ---- hotspots by physical corner, explicit table (ROTATION_90: the physical top right shows at the screen top left)
    @Test fun HOM_23_physicalCornersLandOnTheScreenCornersTheTableSays() {
        // rotation -> (physical corner -> screen corner)
        val table = mapOf(
            0 to mapOf(Corner.TopLeft to Corner.TopLeft, Corner.TopRight to Corner.TopRight, Corner.BottomRight to Corner.BottomRight, Corner.BottomLeft to Corner.BottomLeft),
            1 to mapOf(Corner.TopLeft to Corner.BottomLeft, Corner.TopRight to Corner.TopLeft, Corner.BottomRight to Corner.TopRight, Corner.BottomLeft to Corner.BottomRight),
            2 to mapOf(Corner.TopLeft to Corner.BottomRight, Corner.TopRight to Corner.BottomLeft, Corner.BottomRight to Corner.TopLeft, Corner.BottomLeft to Corner.TopRight),
            3 to mapOf(Corner.TopLeft to Corner.TopRight, Corner.TopRight to Corner.BottomRight, Corner.BottomRight to Corner.BottomLeft, Corner.BottomLeft to Corner.TopLeft),
        )
        for ((rotation, map) in table) for ((physical, screen) in map) {
            assertEquals("rotation $rotation, physical $physical", setOf(screen), GlanceHotspots.enabledOnScreen(setOf(physical), rotation, true))
        }
    }

    @Test fun HOM_23_everyHotspotPresetDrawsTheRightCountInEveryRotation() {
        val counts = mapOf(HotspotPreset.Four to 4, HotspotPreset.Two to 2, HotspotPreset.One to 1, HotspotPreset.None to 0)
        for ((p, n) in counts) for (r in 0..3) assertEquals("$p at $r", n, GlanceHotspots.enabledOnScreen(GlanceHotspots.preset(p)!!, r, true).size)
        // the Two preset is the bottom edge of the natural portrait; turned a quarter (rotation 1) that edge is the screen's right edge
        assertEquals(setOf(Corner.TopRight, Corner.BottomRight), GlanceHotspots.enabledOnScreen(GlanceHotspots.preset(HotspotPreset.Two)!!, 1, true))
        assertEquals(setOf(Corner.BottomLeft, Corner.BottomRight), GlanceHotspots.enabledOnScreen(GlanceHotspots.preset(HotspotPreset.Two)!!, 0, true))
        assertEquals(setOf(Corner.TopLeft, Corner.TopRight), GlanceHotspots.enabledOnScreen(GlanceHotspots.preset(HotspotPreset.Two)!!, 2, true))
    }

    @Test fun HOM_23_aStoredHotspotListWithJunkKeepsTheValidCornersAndNothingElse() {
        assertEquals(setOf(Corner.TopLeft), GlanceHotspots.decode("TopLeft,Nope,,"))
        assertEquals(emptySet<Corner>(), GlanceHotspots.decode(""))
        assertEquals(GlanceHotspots.ALL, GlanceHotspots.decode(null))
    }

    // ---- the pop-in timer never gets stuck
    private val hide = 4000L

    @Test fun HOM_23_theControlsTimerInvariantsHoldForEveryShortSequenceOfEvents() {
        // 0 tap(playing) 1 tap(not playing) 2 interact 3 finger down 4 finger up 5 expire(at hideAt) 6 dismiss 7 expire(early)
        fun step(s: ControlsPop, op: Int, now: Long): ControlsPop = when (op) {
            0 -> s.tapSurface(now, true, hide); 1 -> s.tapSurface(now, false, hide); 2 -> s.interact(now, hide)
            3 -> s.fingerDown(); 4 -> s.fingerUp(now, hide)
            5 -> s.expired(if (s.hideAt == ControlsPop.NEVER) now else s.hideAt); 6 -> s.dismiss(); else -> s.expired(now)
        }
        fun walk(prefix: List<Int>, depth: Int) {
            var s = ControlsPop(); var now = 1000L
            for (op in prefix) { s = step(s, op, now); now += 300 }
            // invariants after every sequence
            if (!s.visible) { assertEquals(prefix.toString(), ControlsPop.NEVER, s.hideAt); assertFalse(prefix.toString(), s.fingerDown) }
            if (s.fingerDown) assertEquals(prefix.toString(), ControlsPop.NEVER, s.hideAt)
            // never stuck: a visible pill with no finger down always has a hide time, and firing it hides it
            if (s.visible && !s.fingerDown) {
                assertTrue(prefix.toString(), s.hideAt != ControlsPop.NEVER)
                assertFalse(prefix.toString(), s.expired(s.hideAt).visible)
            }
            // a finger down always holds it, whatever the clock says
            if (s.visible && s.fingerDown) assertTrue(prefix.toString(), s.expired(Long.MAX_VALUE - 1).visible)
            if (depth > 0) for (op in 0..7) walk(prefix + op, depth - 1)
        }
        walk(emptyList(), 5)
    }

    // ---- weather
    @Test fun HOM_23_noWeatherMeansNoLayerAndAnUnknownCodeIsClouds() {
        assertNull(GlanceWeather.effectOf(null, true))
        assertEquals(GlanceWeatherEffect.Clouds, GlanceWeather.effectOf(WeatherInfo(10f, 1234, 0L), true))
        assertEquals(GlanceWeatherEffect.ClearNight, GlanceWeather.effectOf(WeatherInfo(10f, 0, 0L), false))
        assertFalse(GlanceWeather.animates(reducedMotion = true, screenOn = true, surfaceVisible = true))
        assertFalse(GlanceWeather.animates(reducedMotion = false, screenOn = false, surfaceVisible = true))
        assertTrue(GlanceWeather.animates(reducedMotion = false, screenOn = true, surfaceVisible = true))
    }

    // ---- the Dock rail setting on Glance
    @Test fun HOM_23_theDockRailSettingForcesTheDockOnGlanceOnly() {
        val dockRail = ChromeSettings(rightSide = RightSide.DockRail)
        assertTrue(HomeChromeRules.forcesDock(HomeLanding.Glance, dockRail))
        assertTrue(HomeChromeRules.shows(HomeLanding.Glance, ChromeElement.Dock, dockRail))
        for (l in HomeLanding.entries.filter { it != HomeLanding.Glance }) assertFalse("$l keeps the dock setting", HomeChromeRules.forcesDock(l, dockRail))
        for (r in RightSide.entries.filter { it != RightSide.DockRail }) assertFalse(HomeChromeRules.forcesDock(HomeLanding.Glance, ChromeSettings(rightSide = r)))
    }

    // ---- the landing default and its marker
    @Test fun HOM_22_aStoredSelectedWithoutTheMarkerBecomesGlanceButAnyExplicitChoiceSticksAcrossSaves() {
        assertEquals(HomeLanding.Glance, LandingMigration.resolve("Selected", false))
        // the user picks Selected: the marker is written, and every later save keeps it
        var marker = LandingMigration.markerOnSave(HomeLanding.Selected, false)
        assertTrue(marker)
        assertEquals(HomeLanding.Selected, LandingMigration.resolve("Selected", marker))
        // then Glance again: explicit, still sticks as Glance, and a later Selected is read as chosen
        marker = LandingMigration.markerOnSave(HomeLanding.Glance, marker)
        assertTrue(marker)
        assertEquals(HomeLanding.Glance, LandingMigration.resolve("Minimal", marker))
        // a damaged value with the marker falls back to Glance, never to Selected
        assertEquals(HomeLanding.Glance, LandingMigration.resolve("???", true))
        assertEquals(HomeLanding.Glance, LandingMigration.resolve(null, true))
    }

    // ---- the two slots
    @Test fun HOM_16_bothSlotsNoneIsValidAndDrawsNothingAndTwoWallpapersNeverSurvive() {
        val none = BackgroundChoice(BgItem.None, BgItem.None)
        assertTrue(none.isValid); assertTrue(none.items.isEmpty()); assertNull(none.wallpaper)
        for (a in BgItem.entries) for (b in BgItem.entries) {
            val n = BackgroundChoice.normalised(a, b)
            assertTrue("$a $b", n.isValid)
            assertTrue("$a $b", n.items.count { it.living } <= 1)
            assertEquals("the first slot is kept", a, n.slot1)
        }
        // ids this build does not know fall back per slot, an old pair of wallpapers becomes one
        assertEquals(BackgroundChoice(BgItem.Beam, BgItem.None), BackgroundChoice.fromStored("beam", "halo"))
        assertEquals(BackgroundChoice(BgItem.Spire, BgItem.GlowLines), BackgroundChoice.fromStored("nope", "glow-lines"))
        assertEquals(BackgroundChoice.DEFAULT, BackgroundChoice.fromStored(null, null))
    }
}
