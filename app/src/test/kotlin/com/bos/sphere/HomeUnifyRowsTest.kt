package com.bos.sphere

import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.feature.grid.SettingItem
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

/** HOM-01, HOM-22 (wave 28): the Pages setting in Ground Control, Home screen: one row with exactly Off, Paginated, Continuous; Add page only in Paginated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class HomeUnifyRowsTest {
    private var prefs = LauncherPrefs() // a plain field: no snapshot state is written outside a Compose host

    private fun items(p: LauncherPrefs) = buildSettingsCategories(
        SettingsModel(
            prefs = p, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
        ),
    ).first { it.title == "Home screen" }.items

    @Test fun HOM_01_w28_groundControlHasOnePagesRowWithExactlyOffPaginatedContinuousAndItWritesThePref() {
        prefs = LauncherPrefs()
        @Suppress("UNCHECKED_CAST")
        val row = items(prefs).filter { it.title == "Pages" }.also { assertEquals("one Pages row", 1, it.size) }.single() as SettingItem.Choice<HomePages>
        assertEquals(listOf("Off", "Paginated", "Continuous"), row.options.map { row.label(it) })
        assertEquals(HomePages.Off, row.selected)
        row.onSelect(HomePages.Continuous); assertEquals(HomePages.Continuous, prefs.homePages)
        row.onSelect(HomePages.Paginated); assertEquals(HomePages.Paginated, prefs.homePages)
        assertTrue("the old Scroll or Paginated row is gone", items(prefs).none { it.title == "Pages after Glance" })
    }

    @Test fun HOM_01_w28_addPageIsOfferedOnlyForPaginatedAndEditHomeForEveryValue() {
        assertNotNull("Add page with Paginated", items(LauncherPrefs(homePages = HomePages.Paginated)).firstOrNull { it.title == "Add page" })
        for (m in listOf(HomePages.Off, HomePages.Continuous)) assertFalse("no Add page with $m: the Pages setting is the switch", items(LauncherPrefs(homePages = m)).any { it.title == "Add page" })
        for (m in HomePages.entries) {
            assertNotNull("Edit home with $m", items(LauncherPrefs(homePages = m)).firstOrNull { it.title == "Edit home" })
            assertFalse(items(LauncherPrefs(homePages = m)).any { it.title == "Edit home pages" })
        }
    }

    @Test fun HOM_22_w34_removedFromTheQuickSheetGroundControlHomeHasTheGlanceSwitchEditHomeAndTheItemPicker() {
        prefs = LauncherPrefs()
        val glance = items(prefs).filterIsInstance<SettingItem.Toggle>().single { it.title == "Calm home screen (Glance)" }
        glance.onChange(false); assertFalse(prefs.glanceEnabled)
        glance.onChange(true); assertTrue(prefs.glanceEnabled)
        var picker = 0
        val rows = buildSettingsCategories(
            SettingsModel(
                prefs = prefs, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
                setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
                about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
                pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
                system = SystemLinks({}, {}, {}, {}), lab = emptyList(), addHomeItems = { picker++ },
            ),
        ).first { it.title == "Home screen" }.items
        (rows.single { it.title == "Widgets, folders and icons" } as SettingItem.Action).onClick(); assertEquals(1, picker)
    }
}
