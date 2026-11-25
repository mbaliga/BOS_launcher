package com.bos.sphere

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.UiLanguage
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.ui.LanguagePane
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

/** Wave 54 (settingsVisual): Robolectric screenshots of real Settings category pages, to app/build/screenshots/settings-w54-*.png (read by eye; not a device run). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2300dp-port-xhdpi")
class SettingsVisualW54ShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun model(p: LauncherPrefs) = SettingsModel(
        prefs = p, updatePrefs = {}, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L), HomePage(keys = emptyList(), id = 2L)),
        setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
        about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
        pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
        system = SystemLinks({}, {}, {}, {}),
    )

    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(1_200); rule.waitForIdle()
        val dir = File(System.getProperty("user.dir"), "build/screenshots").also { it.mkdirs() }
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir, "settings-w54-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dir, "settings-w54-$name.png").length() > 0)
    }

    private fun page(name: String, title: String, p: LauncherPrefs = LauncherPrefs(homeStyle = HomeStyle.Grid, homePages = HomePages.Paginated), light: Boolean = false) {
        HyleTheme.apply(if (light) ThemeVariant.Light else ThemeVariant.Dark, 0xFF8E7BFF)
        val cats = buildSettingsCategories(model(p))
        val idx = cats.indexOfFirst { it.title == SettingsTestNav.tabOf(title) } // wave 57: the old page is a section of a tab
        assertTrue("category $title exists in ${cats.map { it.title }}", idx >= 0)
        rule.mainClock.autoAdvance = false
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = cats, initialCategory = idx, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) } }
        save(name)
    }

    @Test fun homeScreen() = page("home-screen", "Home screen")
    @Test fun homeScreenLight() = page("home-screen-light", "Home screen", light = true)
    @Test fun look() = page("look", "Look")
    @Test fun corners() = page("corners", "Corners")
    @Test fun radar() = page("radar", "Radar")
    @Test fun appDrawer() = page("app-drawer", "App drawer")
    @Test fun sidePanel() = page("side-panel", "Side panel")
    @Test fun indicator() = page("indicator", "Look")

    @Test fun languagePage() {
        rule.mainClock.autoAdvance = false
        rule.setContent { SphereTheme { Column(Modifier.fillMaxSize().background(HyleColors.Background).padding(16.dp)) { LanguagePane(UiLanguage.German) {} } } }
        save("language")
    }

    @Test @Config(qualifiers = "w411dp-h2300dp-port-xhdpi", fontScale = 2f) fun homeScreenFont2() = page("home-screen-font2", "Home screen")
}
