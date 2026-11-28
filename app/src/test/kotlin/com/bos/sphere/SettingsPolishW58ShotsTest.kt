package com.bos.sphere

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.feature.grid.SettingsTabTags
import com.bos.sphere.ui.HiddenAppsAccess
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 58 (settingsPolish): the six real tabs in dark on a tall screen, the long ones in several parts, written to
 * build/screenshots/w58-tab-*.png and read by eye (calm grey lines, section notes, dimmed rows, headers).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1400dp-port-mdpi")
class SettingsPolishW58ShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun cats(): List<SettingCategory> = buildSettingsCategories(
        SettingsModel(
            prefs = LauncherPrefs(homePages = HomePages.Paginated, settingsBackdrop = com.bos.sphere.core.data.SettingsBackdrop.Black, calmStyle = com.bos.sphere.core.data.CalmStyle.Regular),
            updatePrefs = {}, pageIndex = 0,
            pages = listOf(HomePage(keys = emptyList(), id = 1L), HomePage(keys = emptyList(), id = 2L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = true, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
            hiddenApps = HiddenAppsAccess(count = 2, hiddenKeys = kotlinx.coroutines.flow.MutableStateFlow(emptySet()), allApps = emptyList(), setHidden = { _, _ -> }),
        ),
    )

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 2000)
    }

    private fun tab(index: Int, name: String, parts: Int) {
        val c = cats()
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = c, initialCategory = index, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) } }
        rule.mainClock.advanceTimeBy(900); rule.waitForIdle()
        shoot("w58-tab-$name-dark-1")
        val list = rule.onNodeWithTag(SettingsTabTags.list(c[index].title))
        val items = com.bos.sphere.feature.grid.OneUiModel.blocks(c[index].items, c[index].headers, c[index].notes).size + 1 // the lazy items: blocks and the reset row
        for (p in 2..parts) {
            val at = ((p - 1) * items / parts).coerceAtLeast(1)
            list.performScrollToIndex(at); rule.mainClock.advanceTimeBy(400); rule.waitForIdle()
            shoot("w58-tab-$name-dark-$p")
        }
    }

    @Test fun CFG_69_screenshotHome() = tab(0, "home", 4)
    @Test fun CFG_69_screenshotLook() = tab(1, "look", 2)
    @Test fun CFG_69_screenshotApps() = tab(2, "apps", 1)
    @Test fun CFG_69_screenshotRadar() = tab(3, "radar", 2)
    @Test fun CFG_69_screenshotSidePanel() = tab(4, "sidepanel", 2)
    @Test fun CFG_69_screenshotSystem() = tab(5, "system", 2)
}
