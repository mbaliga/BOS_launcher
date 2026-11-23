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
import com.bos.sphere.ui.settingsSummaries
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
 * Wave 40 (settingsOneUI), CFG-21: the One UI style settings over the real categories: the root lists every category with its live
 * summary, the deep links (the Home sheet's Ground Control button, "Edit home", the App drawer, Hidden apps) land where they did, every
 * section title still names a row that exists, and screenshots of the real content.
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

    private fun summaries(c: List<SettingCategory>, pages: HomePages = HomePages.Paginated) = settingsSummaries(pages, 3, 0, 2, c)

    private fun show(initial: Int, c: List<SettingCategory> = cats()) {
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = c, initialCategory = initial, sounds = null, onClose = {}, summaries = summaries(c), modifier = Modifier.fillMaxSize()) } }
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun CFG_21_theRealCategoriesAllLandInTheCardsOfTheRootList() {
        val c = cats()
        val groups = OneUiModel.groups(c)
        assertEquals(c.indices.toList(), groups.flatten().sorted())
        val unknown = c.filter { OneUiModel.key(it.title) == null }.map { it.title }
        assertTrue("a category the grouping does not know: $unknown", unknown.isEmpty())
    }

    @Test fun CFG_21_theRootShowsEveryRealCategoryUpFront() {
        val c = cats()
        show(CATEGORY_ROOT)
        // the first cards are on screen; the rest are one scroll away in the same plain list (no strip, no tabs)
        for (t in listOf("Look", "Home screen", "App drawer")) rule.onNodeWithTag(OneUiTags.row(t)).assertIsDisplayed()
        for (t in c.map { OneUiModel.displayTitle(it.title) }.filter { !it.startsWith("Page") }) {
            rule.onAllNodes(hasScrollAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag(OneUiTags.row(t)))
            rule.onNodeWithTag(OneUiTags.row(t)).assertIsDisplayed()
        }
    }

    @Test fun CFG_21_theSummariesAreLiveFromTheRowsAndTheModel() {
        val c = cats()
        val s = summaries(c)
        assertEquals("Dark · Rounded icons · Space Grotesk", s["Look"]) // wave 52: the font is part of the Look line
        assertEquals("Paginated · 3 pages", s["Home screen"])
        assertEquals("2 hidden", s[c.first { OneUiModel.key(it.title) == "Hidden apps" }.title])
        assertEquals("No gestures assigned", s["Gestures"])
        assertEquals("Glance only", settingsSummaries(HomePages.Off, 3, 0, 2, c)["Home screen"])
        assertEquals("Nothing hidden", settingsSummaries(HomePages.Paginated, 3, 0, 0, c)[c.first { OneUiModel.key(it.title) == "Hidden apps" }.title])
        assertEquals("1 page", settingsSummaries(HomePages.Paginated, 1, 0, 0, c)["Home screen"]?.substringAfter(" · "))
        assertEquals("3 gestures assigned", settingsSummaries(HomePages.Paginated, 3, 3, 0, c)["Gestures"])
        assertTrue("every category has a line", c.all { !s[it.title].isNullOrBlank() })
        assertTrue("Radar shows its layout and engine: ${s["Radar"]}", s["Radar"]!!.contains(" · "))
    }

    @Test fun CFG_21_everyRowNamedInTheSectionTableStillExistsInTheRealCategories() {
        val c = cats()
        val missing = ArrayList<String>()
        for ((cat, rows) in OneUiSections.TABLE) {
            val category = c.firstOrNull { OneUiModel.key(it.title) == cat }
            if (category == null) { missing += "category $cat"; continue }
            for ((row, _) in rows) if (OneUiSections.find(category.items, row) < 0) missing += "$cat / $row" // wave 56: by row id, else by title
        }
        assertTrue("section table rows that no longer exist: $missing", missing.isEmpty())
    }

    @Test fun CFG_21_theBigCategoriesAreCutIntoSectionsWithHeaders() {
        val c = cats()
        val home = c.first { it.title == "Home screen" }
        val headers = OneUiSections.headers(home.title, home.items)
        assertTrue("Home screen has several sections: $headers", headers.size >= 8)
        assertEquals("Pages", headers[0])
    }

    @Test fun CFG_21_theHomeSheetsGroundControlButtonOpensTheRoot() {
        val c = cats()
        assertEquals(-1, settingsCategoryIndex(c, CATEGORY_ROOT))
        show(settingsCategoryIndex(c, CATEGORY_ROOT))
        rule.onNodeWithTag(OneUiTags.ROOT).assertIsDisplayed()
    }

    @Test fun CFG_21_theOtherDeepLinksStillOpenTheirCategoryPage() {
        val c = cats()
        val home = settingsCategoryIndex(c, CATEGORY_HOME)
        assertEquals("Home screen", c[home].title)
        assertEquals("App drawer", c[settingsCategoryIndex(c, CATEGORY_APP_DRAWER)].title)
        show(home)
        rule.onNodeWithTag(OneUiTags.CATEGORY_PAGE).assertIsDisplayed()
    }

    @Test fun CFG_21_editHomeAndTheItemPickerStillRunThroughTheirRows() {
        val c = cats()
        show(settingsCategoryIndex(c, CATEGORY_HOME))
        rule.onNodeWithText("Edit home").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Widgets, folders and icons").performClick(); rule.waitForIdle()
        assertEquals(listOf("editHome", "addItems"), log)
    }

    @Test fun CFG_21_hiddenAppsOpensFromTheRootAndShowsItsCount() {
        val c = cats()
        show(CATEGORY_ROOT, c)
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag(OneUiTags.row("Hidden apps")))
        rule.onNodeWithText("2 hidden").assertIsDisplayed()
        rule.onNodeWithTag(OneUiTags.row("Hidden apps")).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.CATEGORY_PAGE).assertIsDisplayed()
        rule.onNodeWithText("No apps are hidden", substring = true).assertIsDisplayed()
    }

    @Test fun CFG_21_aSearchResultOpensTheRealRowOnItsPage() {
        val c = cats()
        show(CATEGORY_ROOT, c)
        rule.onNodeWithTag(OneUiTags.SEARCH).performTextInputCompat("hotspot")
        rule.waitForIdle()
        rule.onAllNodes(hasText("Top left hotspot", substring = true))[0].performClick(); rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.CATEGORY_PAGE).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performTextInputCompat(t: String) = performTextInput(t)

    @Test fun CFG_21_screenshotRealRootDark() { show(CATEGORY_ROOT); shoot("cfg21-real-root-dark") }
    @Test fun CFG_21_screenshotRealRootLight() { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF); show(CATEGORY_ROOT); shoot("cfg21-real-root-light") }
    @Test fun CFG_21_screenshotRealHomeScreenPageDark() { val c = cats(); show(settingsCategoryIndex(c, CATEGORY_HOME)); shoot("cfg21-real-home-dark") }
    @Test fun CFG_21_screenshotRealHomeScreenPageScrolledLight() {
        HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF)
        val c = cats(); show(settingsCategoryIndex(c, CATEGORY_HOME))
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Default density", substring = true)); rule.waitForIdle()
        shoot("cfg21-real-home-scrolled-light")
    }
    @Test fun CFG_21_screenshotRealLookPageDark() { val c = cats(); show(c.indexOfFirst { it.title == "Look" }); shoot("cfg21-real-look-dark") }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_21_screenshotRealTwoPaneDark() { val c = cats(); show(c.indexOfFirst { it.title == "Look" }); shoot("cfg21-real-twopane-dark") }
}
