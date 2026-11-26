package com.bos.sphere

import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.HiddenAppRow
import com.bos.sphere.core.data.HiddenAppsStore
import com.bos.sphere.core.data.HideSource
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.Rule
import com.bos.sphere.core.data.RuleAction
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.ui.HiddenAppsAccess
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import com.bos.sphere.ui.unhideEverywhere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wave 38 (popupsHidden): Ground Control lists Hidden apps as its own row with a count, and unhiding clears the hidden set and the Comms rule. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class HiddenAppsSettingsW38Test {
    private fun cats(access: HiddenAppsAccess?) = buildSettingsCategories(
        SettingsModel(
            prefs = LauncherPrefs(), updatePrefs = {}, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(), hiddenApps = access,
        ),
    )

    private fun access(count: Int, store: HiddenAppsStore = HiddenAppsStore(ApplicationProvider.getApplicationContext())) =
        HiddenAppsAccess(count, store.hidden, emptyList(), store::setHidden)

    @Test fun HOM_05_w38_hiddenAppsIsItsOwnRowRightAfterTheAppDrawerWithTheCountOnTheTab() {
        val c = cats(access(2))
        // wave 57: a row of its own in the Apps tab, its section right after the App drawer, the count in its value
        val apps = c.first { it.title == "Apps" }
        val i = apps.items.indexOfFirst { it.title == "Hidden apps" }
        assertTrue("a Hidden apps row: ${apps.items.map { it.title }}", i > 0)
        assertEquals("Hidden and default apps", apps.headers[i]) // wave 58 (CFG-69): the header no longer repeats the row title
        val row = apps.items[i] as SettingItem.Pane
        assertEquals("2 hidden", row.value)
    }

    @Test fun HOM_05_w38_withNothingHiddenTheRowStillExistsWithoutABadge() {
        val c = cats(access(0))
        val row = c.first { it.title == "Apps" }.items.first { it.title == "Hidden apps" } // wave 57: a row of the Apps tab
        assertEquals("None", (row as SettingItem.Pane).value)
    }

    @Test fun HOM_05_w38_noAccessNoRow() = assertFalse(cats(null).flatMap { it.items }.any { it.title.startsWith("Hidden apps") })

    @Test fun HOM_05_w38_unhideEverywhereClearsTheHiddenSetAndEveryHideRuleOfThePackage() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        LaunchpadSettings.init(ctx)
        val store = HiddenAppsStore(ctx)
        store.setHidden("wa#0", true)
        LaunchpadSettings.addRule(Rule.create(RuleAction.Hide, packageName = "com.whatsapp"))
        LaunchpadSettings.addRule(Rule.create(RuleAction.Hide, packageName = "com.maps"))
        val row = HiddenAppRow("wa#0", "com.whatsapp", "WhatsApp", setOf(HideSource.Launcher, HideSource.CommsRule), emptyList())
        unhideEverywhere(row, access(1, store))
        assertFalse(store.isHidden("wa#0"))
        assertEquals("only the other app's rule is left", listOf("com.maps"), LaunchpadSettings.rules.value.mapNotNull { it.packageName })
    }
}
