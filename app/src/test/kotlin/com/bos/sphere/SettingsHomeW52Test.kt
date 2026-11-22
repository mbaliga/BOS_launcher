package com.bos.sphere

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.grid.OneUiModel
import com.bos.sphere.feature.grid.OneUiTags
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SettingsIndex
import com.bos.sphere.feature.grid.SettingsSpace
import com.bos.sphere.ui.CATEGORY_ROOT
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import com.bos.sphere.ui.desktopCategory
import com.bos.sphere.ui.settingsSummaries
import com.bos.sphere.ui.sidePanelSummary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Wave 52 (settingsFont), CFG-21: the Settings home after the owner's feedback. The first row is "Pages" (summary "Page 1 of 3 · Grid"), not "Page 1";
 * the right-hand panel's row is "Side panel" (summary by what it holds), not "Comms"; Workspaces is its own top-level row near the top with a count;
 * the Look summary names the font; "comms" still finds the side panel. Screenshot of the root: build/screenshots/w52-settings-home.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class SettingsHomeW52Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private fun cats(prefs: LauncherPrefs = LauncherPrefs(homePages = HomePages.Paginated)): List<SettingCategory> = buildSettingsCategories(
        SettingsModel(
            prefs = prefs, updatePrefs = {}, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L), HomePage(keys = emptyList(), id = 2L), HomePage(keys = emptyList(), id = 3L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = listOf("Version" to "test"), appsByKey = emptyMap(), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = true, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(), editHomePages = {}, addHomeItems = {},
        ),
    )

    private fun summaries(c: List<SettingCategory>, workspaces: Int = 3, widgets: Int = 0) =
        settingsSummaries(HomePages.Paginated, 3, 0, 2, c, pageIndex = 0, workspaceCount = workspaces, sidePanelWidgets = widgets)

    private fun show(c: List<SettingCategory> = cats(), workspaces: Int = 3) {
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = c, initialCategory = CATEGORY_ROOT, sounds = null, onClose = {}, summaries = summaries(c, workspaces), modifier = Modifier.fillMaxSize()) } }
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun CFG_21_theFirstRowIsPagesNotPageOneAndItsSummarySaysWhichPageAndTheLayout() {
        val c = cats()
        val first = c[OneUiModel.groups(c).first().first()]
        assertEquals("Pages", first.title)
        assertEquals(OneUiModel.PAGE, OneUiModel.key(first.title))
        assertEquals("Page 1 of 3 · Grid", summaries(c)[first.title])
        assertEquals("Page 2 of 3 · Grid", settingsSummaries(HomePages.Paginated, 3, 0, 2, c, pageIndex = 1)[first.title])
        show(c)
        rule.onNodeWithTag(OneUiTags.row("Pages")).assertIsDisplayed()
        assertEquals("no row says Page 1", 0, rule.onAllNodesWithText("Page 1", substring = false).fetchSemanticsNodes().size)
        rule.onNodeWithText("Page 1 of 3 · Grid").assertIsDisplayed()
    }

    @Test fun CFG_21_theRightHandPanelRowIsSidePanelAndItsSummaryNamesWhatItHolds() {
        val c = cats()
        val title = c.first { OneUiModel.key(it.title) == OneUiModel.SIDE_PANEL }.title
        assertEquals("Side panel", title)
        assertTrue("no category is still called Comms", c.none { it.title == "Comms" })
        assertEquals("Comms · 100%", summaries(c)[title])
        assertEquals("Comms · 100%, Widgets · 2", summaries(c, widgets = 2)[title])
        assertEquals("widgets only", "Widgets · 2", sidePanelSummary(null, 2))
        assertEquals("Comms · 100%", sidePanelSummary("100%", 0))
        // the sections inside the panel keep their names
        val items = c.first { it.title == title }.items.map { it.title }
        assertTrue(items.any { it.startsWith("Comms ") }); assertTrue(items.contains("Default view"))
        show(c)
        rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag(OneUiTags.row("Side panel")))
        rule.onNodeWithTag(OneUiTags.row("Side panel")).assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Comms").fetchSemanticsNodes().size)
    }

    @Test fun CFG_21_commsStillFindsTheSidePanelByASearchKeyword() {
        val c = cats()
        val index = SettingsIndex.fromCategories(c)
        val sideIdx = c.indexOfFirst { it.title == "Side panel" }
        assertTrue("every row of the side panel is a result for 'comms'", index.search("comms", 200).count { it.categoryIndex == sideIdx } >= c[sideIdx].items.size)
        assertTrue("the old product word still leads there", index.search("launchpad", 50).any { it.categoryIndex == sideIdx })
        assertTrue("'widgets' finds it as well", index.search("widgets", 200).any { it.categoryIndex == sideIdx })
    }

    @Test fun CFG_21_workspacesIsATopLevelRowNearTheTopWithACountAndLeavesTheDesktopCategory() {
        val c = cats()
        val ws = c.indexOfFirst { OneUiModel.key(it.title) == "Workspaces" }
        assertTrue("a top-level Workspaces category exists", ws >= 0)
        val groups = OneUiModel.groups(c)
        assertEquals("it sits in the first card, with the pages and the home screen", 0, groups.indexOfFirst { ws in it })
        assertEquals("3 workspaces", summaries(c, workspaces = 3)[c[ws].title])
        assertEquals("1 workspace", summaries(c, workspaces = 1)[c[ws].title])
        assertTrue("the old entry point under Desktop & dock is gone", desktopCategory().items.none { it.title == "Workspaces" })
        assertTrue("App groups stay where they were", desktopCategory().items.any { it.title == "App groups" })
        val only = c[ws].items.single() as SettingItem.Pane
        assertTrue(only.bare)
        show(c)
        rule.onNodeWithTag(OneUiTags.row("Workspaces")).assertIsDisplayed() // on the first screen, no scrolling
        rule.onNodeWithText("3 workspaces").assertIsDisplayed()
        val y = { t: String -> rule.onNodeWithTag(OneUiTags.row(t)).getUnclippedBoundsInRoot().top }
        assertTrue("above Look", y("Workspaces") < y("Look"))
    }

    @Test fun CFG_21_theLookSummaryNamesTheFontAndTheLookPageHasAFontRowWithAllCards() {
        val c = cats()
        val look = c.first { OneUiModel.key(it.title) == "Look" }
        assertEquals("Dark · Rounded icons · Space Grotesk", summaries(c)[look.title])
        val chosen = cats(LauncherPrefs(homePages = HomePages.Paginated, uiFont = "jetbrains_mono"))
        assertTrue(summaries(chosen)["Look"]!!.endsWith("JetBrains Mono"))
        val font = look.items.first { it.title == "Font" } as SettingItem.Choice<*>
        assertEquals(8, font.options.size)
        assertTrue("shown as cards, not a list", OneUiModel.presentation(font) == OneUiModel.Presentation.Rail)
    }

    @Test fun CFG_21_screenshotOfTheSettingsHome() {
        show()
        shoot("w52-settings-home")
        assertTrue(File(outDir, "w52-settings-home.png").length() > 1000)
    }
}
