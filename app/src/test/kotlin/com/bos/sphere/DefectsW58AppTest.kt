package com.bos.sphere

import android.content.Intent
import android.provider.Settings
import com.bos.sphere.core.data.ActionRunner
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.FreshHome
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Wave 58 (defects58) on the real Settings rows, the real action runner and the real home: the System clock format (CFG-61), the Radar and pinch rows
 * (CFG-62), the Do Not Disturb explanation and the one-time Glance hint (CFG-63).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class DefectsW58AppTest : TourBase() {
    private var prefs = LauncherPrefs() // a plain field: no snapshot state is written outside a Compose host
    private var gestures: Map<HomeGesture, CornerItem> = com.bos.sphere.core.data.DefaultGestures
    private var savedPinch: Map<HomeGesture, CornerItem?>? = null

    private fun rows(p: LauncherPrefs) = buildSettingsCategories(
        SettingsModel(
            prefs = p, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0,
            pages = listOf(HomePage(keys = emptyList(), id = 1L, layout = PageLayout())),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> },
            corners = emptyMap(), gestures = gestures, setGesture = { g, item -> gestures = if (item == null) gestures - g else gestures + (g to item) },
            about = emptyList(), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), savedPinch = { savedPinch }, savePinch = { savedPinch = it },
        ),
    ).flatMap { it.items }

    private fun row(p: LauncherPrefs, title: String) = rows(p).first { it.title == title }

    @Test fun CFG_61_theClockFormatRowOffersSystemFirstAndShowsItAsTheDefault() {
        @Suppress("UNCHECKED_CAST") val r = row(LauncherPrefs(), "Clock format") as SettingItem.Choice<ClockFormat>
        assertEquals(ClockFormat.entries, r.options)
        assertEquals(ClockFormat.System, r.selected)
        assertEquals("System (12/24 by device)", r.value)
    }

    @Test fun CFG_62_webSuggestionsSaysWhyItDoesNothingWhileWebResultsIsOff() {
        assertEquals("Web results is off", row(LauncherPrefs(searchWeb = false), "Web suggestions").meta.disabledReason)
        assertNull(row(LauncherPrefs(searchWeb = true), "Web suggestions").meta.disabledReason)
    }

    @Test fun CFG_62_pickingARadarLayoutLeavesKeepThisLayoutWithAKeyboardAlone() {
        prefs = LauncherPrefs(searchLayoutPinned = false)
        @Suppress("UNCHECKED_CAST") val r = row(prefs, "Radar layout") as SettingItem.Choice<com.bos.sphere.core.data.SearchLayout>
        val other = com.bos.sphere.core.data.SearchLayout.entries.first { it != prefs.searchLayout }
        r.onSelect(other)
        assertEquals(other, prefs.searchLayout)
        assertFalse("the pin is its own choice", prefs.searchLayoutPinned)
    }

    @Test fun CFG_62_turningPinchDensityOffPutsTheCustomPinchBindingsBack() {
        gestures = gestures + (HomeGesture.PinchIn to CornerItem.Action(CornerActionId.Camera)) + (HomeGesture.PinchOut to CornerItem.Action(CornerActionId.Flashlight))
        (row(LauncherPrefs(), "Pinch changes grid density") as SettingItem.Toggle).onChange(true)
        assertEquals(CornerItem.Action(CornerActionId.GridDenser), gestures[HomeGesture.PinchIn])
        (row(LauncherPrefs(), "Pinch changes grid density") as SettingItem.Toggle).onChange(false)
        assertEquals(CornerItem.Action(CornerActionId.Camera), gestures[HomeGesture.PinchIn])
        assertEquals(CornerItem.Action(CornerActionId.Flashlight), gestures[HomeGesture.PinchOut])
        assertNull(savedPinch)
    }

    @Test fun CFG_63_aSilentActionWithoutAccessAsksFirstAndNeverOpensTheSystemPageOnItsOwn() {
        val app = AppSmokeHarness.app
        var asked: (() -> Unit)? = null
        val runner = ActionRunner(app, onInternal = {}, onPolicyAccessNeeded = { open -> asked = open })
        runner.run(CornerActionId.RingerSilent)
        assertNotNull("the host was asked to explain", asked)
        assertNull("nothing opened before the user agreed", Shadows.shadowOf(app).nextStartedActivity)
        asked!!()
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, Shadows.shadowOf(app).nextStartedActivity?.action)
        // The background motion service has no explanation to show: it only says what is missing.
        ActionRunner(app, onInternal = {}).run(CornerActionId.DoNotDisturb)
        assertNull(Shadows.shadowOf(app).nextStartedActivity)
    }

    @Test fun CFG_63_glanceShowsLongPressForOptionsOnceOnAFreshInstall() {
        AppSmokeHarness.seedApps("Alpha", "Bravo")
        AppSmokeHarness.configureFresh() // past onboarding, nothing else touched: Glance on, Pages Off
        AppSmokeHarness.app.getSharedPreferences("liftoff_misc", android.content.Context.MODE_PRIVATE).edit().putBoolean("defaultHomeSeeded", true).commit()
        LauncherStores.get(AppSmokeHarness.app).layout.replace(FreshHome.emptyLayout())
        launch()
        assertTrue(glanceShown())
        assertTrue("the hint is on Glance", tagShown("glance.optionsHint"))
        sc.close()
        AppSmokeHarness.resetProcess()
        launch()
        assertFalse("only once", tagShown("glance.optionsHint"))
    }
}
