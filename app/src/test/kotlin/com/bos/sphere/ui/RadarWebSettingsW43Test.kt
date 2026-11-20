package com.bos.sphere.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 43 (radarWeb, PAL-06, NFR-16): the Radar group has the Web suggestions switch next to Web engine, and the manifest declares INTERNET once (source level, like the other wiring tests). */
class RadarWebSettingsW43Test {
    private fun text(path: String): String = listOf(File("."), File(".."), File("../..")).map { File(it, path) }.first { it.exists() }.readText()

    @Test fun PAL_06_theRadarGroupHasAWebSuggestionsToggleRightAfterWebResultsAndEngineChoiceOffersEveryEngine() {
        val s = text("app/src/main/kotlin/com/bos/sphere/ui/SettingsCategories.kt")
        val radar = s.substring(s.indexOf("\"Radar\",\n        Glyphs.Radar"))
        val engine = radar.indexOf("x(\"Web engine\")"); val results = radar.indexOf("x(\"Web results\")"); val sugg = radar.indexOf("x(\"Web suggestions\")")
        assertTrue(engine in 0 until results && results < sugg && sugg - engine < 2500)
        assertTrue(radar.contains("p.webSuggestions") && radar.contains("copy(webSuggestions = v)"))
        assertTrue("the hint names the engine and the Play Store", radar.contains("Sends what you type in the Web, Apps and Everything scopes to %s and the Play Store to show suggestions."))
        assertTrue(radar.contains("WebEngine.entries"))
    }

    @Test fun NFR_16_theManifestDeclaresInternetOnceAndNoLocation() {
        val m = text("app/src/main/AndroidManifest.xml")
        assertEquals(1, Regex("android.permission.INTERNET").findAll(m.lines().filter { it.contains("uses-permission") }.joinToString("\n")).count())
        assertTrue(!m.contains("ACCESS_COARSE_LOCATION") && !m.contains("ACCESS_FINE_LOCATION"))
    }
}
