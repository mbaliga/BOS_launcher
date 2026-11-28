package com.bos.sphere

import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.PrefFilesSource
import com.bos.sphere.core.data.StateSchema
import com.bos.sphere.core.data.StateService
import com.bos.sphere.core.design.PaletteSource
import com.bos.sphere.core.design.ThemingStore
import com.bos.sphere.ui.CommsOptions
import com.bos.sphere.ui.HubPrefs
import com.bos.sphere.ui.HubView
import com.bos.sphere.ui.PanelWidget
import com.bos.sphere.ui.WidgetsSlot
import com.bos.sphere.ui.reloadAfterStateChange
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wave 58 (defects58, CFG-60): the settings a user chooses on screen survive Export, a fresh install and Import, through the real stores, the real
 * StateService and the reload after an import: Comms Default view, Clear from the shade, the Widgets section switch and place, per-app icon colours and
 * the theming file (palette source, interface density, text size). The Comms widget ids stay on the old device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripW58Test {
    private val app get() = AppSmokeHarness.app

    private fun freshThemingStore() {
        ThemingStore::class.java.getDeclaredField("sp").apply { isAccessible = true }.set(ThemingStore, null)
        ThemingStore.init(app)
    }

    private fun newProcess() { AppSmokeHarness.resetProcess(); freshThemingStore(); CommsOptions.init(app) }

    @Before fun setUp() = newProcess()
    @After fun tearDown() { ThemingStore.update { com.bos.sphere.core.design.ThemingSettings() }; CommsOptions.setDefaultView(HubView.All); CommsOptions.setClearFromShade(true); CommsOptions.setWidgetsSection(false); CommsOptions.setWidgetsSlot(WidgetsSlot.Last) }

    @Test fun CFG_60_everyUserVisibleSettingRoundTripsExportFreshInstallImport() {
        CommsOptions.setDefaultView(HubView.Unread)
        CommsOptions.setClearFromShade(false)
        CommsOptions.setWidgetsSection(true)
        CommsOptions.setWidgetsSlot(WidgetsSlot.First)
        HubPrefs.of(app).widgetList = listOf(PanelWidget(4242, "Weather"))
        LauncherStores.get(app).iconColors.set("com.example/.Main#0", 0xFF00AA00L)
        ThemingStore.update { it.copy(source = PaletteSource.Wallpaper, density = 0.9f, type = 1.15f) }
        LauncherStores.get(app).prefs.update { it.copy(clockFormat = ClockFormat.Hour12) }
        val text = StateService.get(app).exportText()
        assertFalse("the Comms widget id stays on this device", text.contains("4242"))

        // A fresh install: every file empty, every in-memory copy rebuilt.
        (PrefFilesSource.ALLOWED + listOf("launcher_prefs", "home_layout", "shortcuts", "launchpad_rules", "hidden_apps", StateSchema.COMMS_WIDGETS_FILE))
            .forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        newProcess()
        assertEquals(HubView.All, CommsOptions.defaultView.value)
        assertEquals(PaletteSource.Fixed, ThemingStore.settings.value.source)
        assertTrue(LauncherStores.get(app).iconColors.colors.value.isEmpty())

        StateService.get(app).import(text, emptySet())
        reloadAfterStateChange(app)
        assertEquals(HubView.Unread, CommsOptions.defaultView.value)
        assertFalse(CommsOptions.clearFromShade.value)
        assertTrue(CommsOptions.widgetsSection.value)
        assertEquals(WidgetsSlot.First, CommsOptions.widgetsSlot.value)
        assertEquals(0xFF00AA00L, LauncherStores.get(app).iconColors.colors.value["com.example/.Main#0"])
        assertEquals(PaletteSource.Wallpaper, ThemingStore.settings.value.source)
        assertEquals(0.9f, ThemingStore.settings.value.density, 0.001f)
        assertEquals(1.15f, ThemingStore.settings.value.type, 0.001f)
        assertEquals(ClockFormat.Hour12, LauncherStores.get(app).prefs.prefs.value.clockFormat)
        assertTrue("the new device starts with no Comms widgets", HubPrefs.of(app).widgetList.isEmpty())
    }

    @Test fun CFG_60_anOlderInstallsCommsWidgetListMovesToTheDeviceLocalFileOnce() {
        app.getSharedPreferences("launchpad_hub", 0).edit().putString("widgets", "9,Clock").putString("defaultView", "unread").commit()
        val prefs = HubPrefs.of(app)
        assertFalse(app.getSharedPreferences("launchpad_hub", 0).contains("widgets"))
        assertTrue(app.getSharedPreferences(StateSchema.COMMS_WIDGETS_FILE, 0).contains("widgets"))
        assertEquals(HubView.Unread, prefs.defaultView)
        assertEquals(listOf(9), prefs.widgetList.map { it.appWidgetId })
    }
}
