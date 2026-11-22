package com.bos.sphere

import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.ui.CommsOptions
import com.bos.sphere.ui.DefaultViewChoice
import com.bos.sphere.ui.HubView
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.WidgetsSlot
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wave 52 (panelWidgets, STR-15): the panel's settings category has the "Widgets section" switch (default off) and, only while it is on, the position choice. Found by row title, so a rename of the category does not matter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PanelWidgetsSettingsW52Test {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    @Before fun setUp() { ctx.getSharedPreferences("launchpad_hub", 0).edit().clear().commit(); CommsOptions.init(ctx); CommsOptions.setWidgetsSection(false); CommsOptions.setWidgetsSlot(WidgetsSlot.Last); CommsOptions.setDefaultView(HubView.All) }
    @After fun tearDown() { CommsOptions.setWidgetsSection(false); CommsOptions.setWidgetsSlot(WidgetsSlot.Last); ctx.getSharedPreferences("launchpad_hub", 0).edit().clear().commit() }

    private fun items(): List<SettingItem> = buildSettingsCategories(
        SettingsModel(
            prefs = LauncherPrefs(), updatePrefs = {}, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
        ),
    ).first { c -> c.items.any { it.title == "Widgets section" } }.items

    @Test fun STR_15_theSwitchIsThereDefaultsOffAndHidesThePositionChoice() {
        val sw = items().first { it.title == "Widgets section" } as SettingItem.Toggle
        assertFalse(sw.checked)
        assertFalse("no position row while it is off", items().any { it.title == "Widgets position" })
    }

    @Test fun STR_15_turningItOnStoresItAndOffersThePositionChoiceWhichStoresTheSlot() {
        (items().first { it.title == "Widgets section" } as SettingItem.Toggle).onChange(true)
        assertTrue(CommsOptions.widgetsSection.value)
        val pos = items().first { it.title == "Widgets position" } as SettingItem.Choice<*>
        assertNotNull(pos.preview)
        @Suppress("UNCHECKED_CAST") (pos as SettingItem.Choice<WidgetsSlot>).onSelect(WidgetsSlot.First)
        assertEquals(WidgetsSlot.First, CommsOptions.widgetsSlot.value)
        assertTrue("the Default view choice now offers Widgets", DefaultViewChoice(HubView.Widgets) in (items().first { it.title == "Default view" } as SettingItem.Choice<*>).options)
    }
}
