package com.bos.sphere

import com.bos.sphere.core.data.CalmStyle
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.wallpaper
import com.bos.sphere.core.data.withWallpaper
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

/** Wave 55 (calmStyles): the Indicator category has the Calm style cards, the regular-indicators switch, a Show row (Signal / Battery), intensity, speed, colours and a preview; no alert row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class CalmStyleRowsW55Test {
    private var prefs = LauncherPrefs()

    private fun items() = buildSettingsCategories(
        SettingsModel(
            prefs = prefs, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
        ),
    ).first { it.title == "Look" }.items // the Indicator settings sit under Look

    @Suppress("UNCHECKED_CAST")
    private fun <T> choice(title: String) = items().filter { it.title == title }.also { assertEquals("one $title row", 1, it.size) }.single() as SettingItem.Choice<T>

    @Test fun PAL_24_w55_theCalmStyleRowIsCardsOfOffRegularAndFourAmbientStylesAndWritesThePref() {
        val row = choice<CalmStyle>("Calm style")
        assertEquals(listOf("Off", "Regular", "Edge dots", "Corner bloom", "Bottom glow", "Edge beam"), row.options.map { row.label(it) })
        assertEquals("a Signal wallpaper with nothing chosen shows Edge dots", CalmStyle.EdgeDots, row.selected)
        assertNull("wave 58 (CFG-65): the cards show the styles; \"never cover your wallpaper\" is the Calm styles section note", row.hint)
        row.onSelect(CalmStyle.CornerBloom); assertEquals(CalmStyle.CornerBloom, prefs.calmStyle)
        row.onSelect(CalmStyle.Regular); assertEquals(CalmStyle.Regular, prefs.calmStyle)
        row.onSelect(CalmStyle.None); assertEquals(CalmStyle.None, prefs.calmStyle)
    }

    @Test fun PAL_24_w55_aPictureWallpaperWithNothingChosenShowsOffAndNoRegularSwitch() {
        prefs = prefs.withWallpaper(WallpaperSurface.Glance, prefs.wallpaper(WallpaperSurface.Glance).copy(kind = WallpaperKind.Picture))
        assertEquals(CalmStyle.None, choice<CalmStyle>("Calm style").selected)
        // Wave 58 (CFG-66): no ambient style: the switch stays, dimmed, with the reason (it was absent before).
        assertEquals("Calm style is Off", items().first { it.title == "Also show regular battery and signal" }.meta.disabledReason)
    }

    @Test fun PAL_24_w55_theRegularSwitchAppearsOnlyBesideAnAmbientStyle() {
        // Wave 58 (CFG-66): the switch is always listed; it can be used only beside an ambient style, otherwise it is dimmed with the reason.
        fun has() = items().first { it.title == "Also show regular battery and signal" }.meta.disabledReason == null
        prefs = prefs.copy(calmStyle = CalmStyle.None); assertTrue(!has())
        prefs = prefs.copy(calmStyle = CalmStyle.Regular); assertTrue("Regular alone is the regular indicators already", !has())
        for (s in CalmStyle.entries.filter { it.ambient }) {
            prefs = prefs.copy(calmStyle = s); assertTrue("$s: the switch is there", has())
        }
        val sw = items().first { it.title == "Also show regular battery and signal" } as SettingItem.Toggle
        assertTrue(!sw.checked); sw.onChange(true); assertTrue(prefs.calmAlsoRegular)
    }

    @Test fun PAL_24_w55_theAlertRowAndTheOldEffectNamesAreGone() {
        val titles = items().map { it.title }
        assertTrue("Alert effect" !in titles && "Calm effect" !in titles)
    }

    @Test fun PAL_24_w55_showRowIsSignalOrBatteryOnly() {
        val row = choice<IndicatorMetric>("Show")
        assertEquals(listOf("Signal", "Battery"), row.options.map { row.label(it) })
        row.onSelect(IndicatorMetric.Battery); assertEquals(IndicatorMetric.Battery, prefs.indicatorMetric)
    }

    @Test fun PAL_24_w55_theOldTogglesTheMeasurementListAndTheFlipRowAreGone() {
        val titles = items().map { it.title }
        for (gone in listOf("Glow: measurement", "Glow: Breathing dots", "Glow: Snake outline", "Glow: Edge meter", "Glow: Vignette", "Glow: Comic-book lines", "Glow: flip the ramp")) assertTrue("$gone is gone", gone !in titles)
        for (kept in listOf("Glow: intensity", "Glow: speed", "Glow: colours", "Glow: preview")) assertTrue("$kept stays", kept in titles)
    }
}
