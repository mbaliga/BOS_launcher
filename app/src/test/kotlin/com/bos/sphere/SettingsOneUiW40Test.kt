package com.bos.sphere

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.grid.OneUiModel
import com.bos.sphere.feature.grid.OneUiSections
import com.bos.sphere.feature.grid.OneUiTags
import com.bos.sphere.feature.grid.SettingsTabTags
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.ui.CATEGORY_APP_DRAWER
import com.bos.sphere.ui.CATEGORY_HOME
import com.bos.sphere.ui.CATEGORY_ROOT
import com.bos.sphere.ui.HiddenAppsAccess
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import com.bos.sphere.ui.settingsCategoryIndex
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Wave 40 (settingsOneUI), CFG-21, over the real rows; wave 57 (settingsTabs): the six tabs replace the root list, so these check that the deep links
 * (the Home sheet's Settings button, "Edit home", the App drawer, Hidden apps) still land where they should, in their tab.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class SettingsOneUiW40Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val log = ArrayList<String>()

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun hidden(n: Int) = HiddenAppsAccess(count = n, hiddenKeys = kotlinx.coroutines.flow.MutableStateFlow(emptySet()), allApps = emptyList(), setHidden = { _, _ -> })

    private fun cats(pages: HomePages = HomePages.Paginated, hiddenCount: Int? = 2): List<SettingCategory> = buildSettingsCategories(
        SettingsModel(
            prefs = LauncherPrefs(homePages = pages), updatePrefs = {}, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L), HomePage(keys = emptyList(), id = 2L), HomePage(keys = emptyList(), id = 3L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = true, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
            editHomePages = { log += "editHome" }, addHomeItems = { log += "addItems" },
            hiddenApps = hiddenCount?.let { hidden(it) },
        ),
    )

    private fun show(initial: Int, c: List<SettingCategory> = cats()) {
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = c, initialCategory = initial, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) } }
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Wave 57 (settingsTabs): the root list of categories, its summary lines and the category pages are gone (six tabs replace them). The tests of
    // the cards, the summaries, the section table and the root screenshots were replaced by SettingsTabsW57ModelTest and SettingsTabsW57ShotsTest;
    // what still holds of wave 40 over the real rows is below.

    @Test fun CFG_21_everyTabIsInTheTabRowUpFront() {
        val c = cats()
        show(CATEGORY_ROOT)
        for (t in c.map { it.title }) rule.onNodeWithTag(SettingsTabTags.tab(t), useUnmergedTree = true).assertExists()
    }

    @Test fun CFG_21_theBigTabsAreCutIntoSectionsWithHeaders() {
        val home = SettingsTestNav.tab(cats(), "Home")
        val headers = OneUiSections.headers(home)
        assertTrue("Home has several sections: $headers", headers.size >= 7) // wave 58: Dock and External displays are one section
        assertEquals("Glance", headers[0])
    }

    @Test fun CFG_21_theHomeSheetsSettingsButtonOpensSettingsHome() {
        val c = cats()
        assertEquals(-1, settingsCategoryIndex(c, CATEGORY_ROOT))
        show(settingsCategoryIndex(c, CATEGORY_ROOT))
        rule.onNodeWithTag(OneUiTags.ROOT).assertIsDisplayed()
        rule.onNodeWithTag(SettingsTabTags.ROW).assertIsDisplayed()
    }

    @Test fun CFG_21_theOtherDeepLinksOpenTheirTab() {
        val c = cats()
        val home = settingsCategoryIndex(c, CATEGORY_HOME)
        assertEquals("Home", c[home].title)
        assertEquals("Apps", c[settingsCategoryIndex(c, CATEGORY_APP_DRAWER)].title)
        show(settingsCategoryIndex(c, CATEGORY_APP_DRAWER))
        assertTrue(SettingsTestNav.tabShown(rule, "Apps"))
    }

    @Test fun CFG_21_editHomeAndTheItemPickerStillRunThroughTheirRows() {
        val c = cats()
        show(settingsCategoryIndex(c, CATEGORY_HOME))
        SettingsTestNav.goTo(rule, "Home", "Edit home")
        rule.onNodeWithText("Edit home").performClick(); rule.waitForIdle()
        SettingsTestNav.goTo(rule, "Home", "Widgets, folders and icons")
        rule.onNodeWithText("Widgets, folders and icons").performClick(); rule.waitForIdle()
        assertEquals(listOf("editHome", "addItems"), log)
    }

    @Test fun CFG_21_hiddenAppsOpensFromTheAppsTabAndShowsItsCount() {
        val c = cats()
        show(CATEGORY_ROOT, c)
        SettingsTestNav.goTo(rule, "Apps", "Hidden apps")
        rule.onNodeWithText("2 hidden").assertIsDisplayed()
        rule.onNodeWithText("2 hidden").performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.SUB_PAGE).assertIsDisplayed()
        rule.onNodeWithText("No apps are hidden", substring = true).assertIsDisplayed()
    }

    @Test fun CFG_21_aSearchResultOpensTheRealRowInItsTab() {
        val c = cats()
        show(CATEGORY_ROOT, c)
        rule.onNodeWithTag(SettingsTabTags.tab("Look"), useUnmergedTree = true).performClick(); rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.SEARCH).performTextInput("hotspot")
        rule.waitForIdle()
        rule.onAllNodes(hasText("Top left hotspot", substring = true))[0].performClick(); rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
        assertTrue("the hit switched to the Home tab", SettingsTestNav.tabShown(rule, "Home"))
        rule.onNodeWithText("Top left hotspot").assertIsDisplayed()
    }
}
