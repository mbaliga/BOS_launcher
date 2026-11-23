package com.bos.sphere

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.feature.grid.SetupState
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Wave 56 (settingsCore): Robolectric screenshots of the real Settings with the new rows, to app/build/screenshots/settings-w56-*.png (read by eye; not a device run). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2600dp-port-xhdpi")
class SettingsCoreW56ShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun model(p: LauncherPrefs, setup: SetupState) = SettingsModel(
        prefs = p, updatePrefs = {}, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L), HomePage(keys = emptyList(), id = 2L)),
        setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
        about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
        system = SystemLinks({}, {}, {}, {}), setup = setup,
    )

    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(1_200); rule.waitForIdle()
        val dir = File(System.getProperty("user.dir"), "build/screenshots").also { it.mkdirs() }
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir, "settings-w56-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dir, "settings-w56-$name.png").length() > 0)
    }

    private fun page(name: String, title: String?, p: LauncherPrefs, setup: SetupState = SetupState.Complete) {
        val cats = buildSettingsCategories(model(p, setup))
        val idx = if (title == null) -1 else cats.indexOfFirst { it.title == title }
        assertTrue("category $title exists", title == null || idx >= 0)
        rule.mainClock.autoAdvance = false
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = cats, initialCategory = idx, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) } }
        save(name)
    }

    @Test fun rootWithFinishSetup() = page("app-root-finish", null, LauncherPrefs(), SetupState(defaultHome = false, notificationAccess = false, usageAccess = true, dndAccess = false, dndActionBound = false))
    @Test fun homeScreenGlanceOnlyClockOff() = page("app-home-off", "Home screen", LauncherPrefs(homePages = HomePages.Off, clockStyle = ClockStyle.Off))
    @Test fun homeScreenPaginatedSlidersOffDefault() = page("app-home-paged", "Home screen", LauncherPrefs(homePages = HomePages.Paginated, iconScale = 0.65f, spacingV = -1f, iconSpacing = 0.3f, gridInsetX = 0.1f))
    @Test fun cornersHotCornersOff() = page("app-corners", "Corners", LauncherPrefs(hotCornersOn = false, cornerIconSize = 44f))
    @Test fun lookWithReset() = page("app-look-reset", "Look", LauncherPrefs(settingsDim = 0.4f))
}
