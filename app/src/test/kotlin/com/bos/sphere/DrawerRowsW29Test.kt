package com.bos.sphere

import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.WheelGrouping
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** HOM-01, CFG-15 (wave 29): the restored disc's settings are reachable (Wheel groups writes the pref) and the arc drawer rows are gone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class DrawerRowsW29Test {
    private var prefs = LauncherPrefs()

    private fun items() = buildSettingsCategories(
        SettingsModel(
            prefs = prefs, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            requestHealth = {}, about = listOf("Version" to "test"), healthAvailable = false, appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
        ),
    ).first { it.title == "App drawer" }.items

    @Test fun HOM_01_w29_theWheelGroupsRowOffersTheThreeGroupingsAndWritesThePref() {
        @Suppress("UNCHECKED_CAST")
        val row = items().filter { it.title == "Wheel groups" }.also { assertEquals("one Wheel groups row", 1, it.size) }.single() as SettingItem.Choice<WheelGrouping>
        assertEquals(listOf("A–Z", "Category", "Recent"), row.options.map { row.label(it) })
        row.onSelect(WheelGrouping.Recent); assertEquals(WheelGrouping.Recent, prefs.wheelGrouping)
    }

    @Test fun HOM_01_w29_noArcDrawerShortcutRowsRemain() {
        val titles = items().map { it.title }
        for (gone in listOf("Shortcuts", "Shortcuts are shown", "Choose shortcuts")) assertTrue("$gone is gone", gone !in titles)
    }
}
