package com.bos.sphere.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 30 (radarDim): the Radar background setting is a choice row in Ground Control's Radar group and Radar draws through the one layer (source level, like the other wiring tests). */
class RadarDimWiringW30Test {
    private fun text(path: String): String = listOf(File("."), File(".."), File("../..")).map { File(it, path) }.first { it.exists() }.readText()

    @Test fun PAL_24_w30_theRadarGroupHasABackgroundChoiceRowWithBothOptions() {
        val s = text("app/src/main/kotlin/com/bos/sphere/ui/SettingsCategories.kt")
        val radar = s.substring(s.indexOf("\"Radar\",\n        Glyphs.Radar"))
        assertTrue(radar.contains("x(\"Radar background\")") && radar.contains("RadarBackground.entries") && radar.contains("copy(radarBackground = v)"))
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/HomeModel.kt").contains("Dimmed(\"Dimmed wallpaper\"), TrueBlack(\"True black\")"))
    }

    @Test fun PAL_24_w30_radarDrawsItsBackgroundOnlyThroughTheRadarLayer() {
        val o = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/SearchOverlay.kt")
        assertTrue(o.contains("RadarBackgroundLayer(prefs"))
        assertTrue("no direct SignalBackground in Radar", !o.contains("SignalBackground("))
    }
}
