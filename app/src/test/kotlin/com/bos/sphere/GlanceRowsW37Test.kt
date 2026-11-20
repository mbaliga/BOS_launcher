package com.bos.sphere

import com.bos.sphere.core.data.GlanceItem
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.ui.LabEntry
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HOM-23 (wave 37, specSync): the Time, Weather and Battery switches of the Glance home used to be tiles of the long-press Home sheet (removed in
 * wave 34). They stay reachable as plain rows of Ground Control > Home screen (shown while Glance is on, which is when they matter), and each
 * one really changes what the Glance shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlanceRowsW37Test {
    private var prefs = LauncherPrefs()

    private fun model(p: LauncherPrefs) = SettingsModel(
        prefs = p, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = List(1) { HomePage(keys = emptyList(), id = it + 1L) },
        setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
        requestHealth = {}, about = listOf("Version" to "test"), healthAvailable = false, appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
        system = SystemLinks({}, {}, {}, {}), lab = listOf(LabEntry("x", "Spike", "result", {})),
    )

    private fun homeItems(p: LauncherPrefs) = buildSettingsCategories(model(p)).first { it.title == "Home screen" }.items
    private fun toggle(p: LauncherPrefs, title: String) = homeItems(p).firstOrNull { it.title == title && it is SettingItem.Toggle } as? SettingItem.Toggle

    @Test fun HOM_23_w37_timeWeatherAndBatteryAreSwitchesInGroundControlHomeScreenWhileGlanceIsOn() {
        for (item in listOf(GlanceItem.Time, GlanceItem.Weather, GlanceItem.Battery)) {
            assertNotNull("${item.label} switch in Home screen", toggle(LauncherPrefs(glanceEnabled = true), item.label))
        }
        assertFalse("with Glance off its content rows are not offered", homeItems(LauncherPrefs(glanceEnabled = false)).any { it.title == "Battery" })
    }

    @Test fun HOM_23_w37_theBatterySwitchAddsAndRemovesBatteryOnTheGlance() {
        prefs = LauncherPrefs(glanceContent = setOf(GlanceItem.Time))
        assertFalse(toggle(prefs, "Battery")!!.checked)
        toggle(prefs, "Battery")!!.onChange(true)
        assertTrue(GlanceItem.Battery in prefs.glanceContent); assertTrue("the others stay", GlanceItem.Time in prefs.glanceContent)
        assertTrue(toggle(prefs, "Battery")!!.checked)
        toggle(prefs, "Battery")!!.onChange(false)
        assertEquals(setOf(GlanceItem.Time), prefs.glanceContent)
    }

    @Test fun HOM_23_w37_theWeatherAndTimeSwitchesChangeTheGlanceContentToo() {
        prefs = LauncherPrefs(glanceContent = setOf(GlanceItem.Time))
        toggle(prefs, "Weather")!!.onChange(true)
        assertTrue(GlanceItem.Weather in prefs.glanceContent)
        toggle(prefs, "Time")!!.onChange(false)
        assertFalse(GlanceItem.Time in prefs.glanceContent)
    }
}
