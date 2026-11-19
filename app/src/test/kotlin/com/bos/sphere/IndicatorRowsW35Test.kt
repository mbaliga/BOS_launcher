package com.bos.sphere

import com.bos.sphere.core.data.AlertEffect
import com.bos.sphere.core.data.CalmEffect
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.IndicatorMetric
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wave 35 (signalEffects): the Indicator category has a calm row, an alert row, a Show row (Signal / Battery), intensity, speed, colours and a preview; nothing else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class IndicatorRowsW35Test {
    private var prefs = LauncherPrefs()

    private fun items() = buildSettingsCategories(
        SettingsModel(
            prefs = prefs, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            requestHealth = {}, about = listOf("Version" to "test"), healthAvailable = false, appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
        ),
    ).first { it.title == "Look" }.items // the Indicator settings sit under Look

    @Suppress("UNCHECKED_CAST")
    private fun <T> choice(title: String) = items().filter { it.title == title }.also { assertEquals("one $title row", 1, it.size) }.single() as SettingItem.Choice<T>

    @Test fun PAL_24_w35_calmRowOffersOffDotBloomAndAnimatedDotsAndWritesThePref() {
        val row = choice<CalmEffect?>("Calm effect")
        assertEquals(listOf("Off", "Dot bloom", "Animated dots"), row.options.map { row.label(it) })
        assertEquals(CalmEffect.AnimatedDots, row.selected)
        row.onSelect(CalmEffect.Bloom); assertEquals(CalmEffect.Bloom, prefs.indicatorCalm)
        row.onSelect(null); assertNull(prefs.indicatorCalm)
    }

    @Test fun PAL_24_w35_alertRowOffersOffColouredVignetteAndComicLinesWithTheUrgentHint() {
        val row = choice<AlertEffect?>("Alert effect")
        assertEquals(listOf("Off", "Coloured vignette", "Comic-book lines"), row.options.map { row.label(it) })
        assertNull(row.selected)
        assertTrue("the one-line hint names low and lost", row.hint!!.contains("low") && row.hint!!.contains("urgent") && row.hint!!.contains("lost"))
        row.onSelect(AlertEffect.ComicLines); assertEquals(AlertEffect.ComicLines, prefs.indicatorAlert)
    }

    @Test fun PAL_24_w35_theTwoGroupsAreIndependentSoOnePairCanBeOnTogetherAndEitherCanBeOff() {
        choice<CalmEffect?>("Calm effect").onSelect(CalmEffect.Bloom)
        choice<AlertEffect?>("Alert effect").onSelect(AlertEffect.Vignette)
        assertEquals(CalmEffect.Bloom, prefs.indicatorCalm); assertEquals(AlertEffect.Vignette, prefs.indicatorAlert)
        choice<CalmEffect?>("Calm effect").onSelect(null)
        assertNull(prefs.indicatorCalm); assertEquals(AlertEffect.Vignette, prefs.indicatorAlert)
    }

    @Test fun PAL_24_w35_showRowIsSignalOrBatteryOnly() {
        val row = choice<IndicatorMetric>("Show")
        assertEquals(listOf("Signal", "Battery"), row.options.map { row.label(it) })
        row.onSelect(IndicatorMetric.Battery); assertEquals(IndicatorMetric.Battery, prefs.indicatorMetric)
    }

    @Test fun PAL_24_w35_theOldTogglesTheMeasurementListAndTheFlipRowAreGone() {
        val titles = items().map { it.title }
        for (gone in listOf("Glow: measurement", "Glow: Breathing dots", "Glow: Snake outline", "Glow: Edge meter", "Glow: Vignette", "Glow: Comic-book lines", "Glow: flip the ramp")) assertTrue("$gone is gone", gone !in titles)
        for (kept in listOf("Glow: intensity", "Glow: speed", "Glow: colours", "Glow: preview")) assertTrue("$kept stays", kept in titles)
    }
}
