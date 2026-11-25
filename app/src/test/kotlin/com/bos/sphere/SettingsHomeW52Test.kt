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
import com.bos.sphere.feature.grid.SettingsTabTags
import com.bos.sphere.feature.grid.SettingsTabs
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
 * Wave 52 (settingsFont), CFG-21, updated in wave 57 (settingsTabs): the names the owner chose survive the move to six tabs: the right-hand panel
 * is "Side panel", not "Comms"; Workspaces is up front (the first row of Apps) and not under Desktop & dock; the Look tab has the font cards;
 * "comms" still finds the side panel.
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

    private fun show(c: List<SettingCategory> = cats()) {
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = c, initialCategory = CATEGORY_ROOT, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) } }
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
    }

    // Wave 57 (settingsTabs): the root list with its summary lines is gone (six tabs replace it), so the tests of the "Pages" row, the summary lines
    // and the Workspaces root row were replaced by these: the names the owner chose stay (Side panel, Workspaces up front in Apps, the font cards).

    @Test fun CFG_21_theRightHandPanelTabIsSidePanelNotComms() {
        val c = cats()
        val side = SettingsTestNav.tab(c, SettingsTabs.SIDE_PANEL)
        assertEquals("Side panel", side.title)
        assertTrue("no category is still called Comms", c.none { it.title == "Comms" })
        val items = side.items.map { it.title }
        assertTrue(items.any { it.startsWith("Comms ") }); assertTrue(items.contains("Default view"))
        show(c)
        rule.onNodeWithTag(SettingsTabTags.tab("Side panel"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun CFG_21_commsStillFindsTheSidePanelByASearchKeyword() {
        val c = cats()
        val index = SettingsIndex.fromCategories(c)
        val sideIdx = c.indexOfFirst { it.title == "Side panel" }
        assertTrue("every row of the side panel is a result for 'comms'", index.search("comms", 400).count { it.categoryIndex == sideIdx && it.paneEntryId == null } >= c[sideIdx].items.size)
        assertTrue("the old product word still leads there", index.search("launchpad", 50).any { it.categoryIndex == sideIdx })
        assertTrue("'widgets' finds it as well", index.search("widgets", 200).any { it.categoryIndex == sideIdx })
    }

    @Test fun CFG_21_workspacesLeadsTheAppsTabAndLeftTheDesktopCategory() {
        val c = cats()
        val apps = SettingsTestNav.tab(c, SettingsTabs.APPS)
        val ws = apps.items.first() as SettingItem.Pane
        assertEquals("Workspaces", ws.title)
        assertTrue(ws.bare)
        assertTrue("the old entry point under Desktop & dock is gone", desktopCategory().items.none { it.title == "Workspaces" })
        assertEquals("one Workspaces row in all of Settings", 1, c.flatMap { it.items }.count { it.title == "Workspaces" })
    }

    @Test fun CFG_21_theLookTabHasAFontRowWithAllCards() {
        val look = SettingsTestNav.tab(cats(), SettingsTabs.LOOK)
        val font = look.items.first { it.title == "Font" } as SettingItem.Choice<*>
        assertEquals(8, font.options.size)
        assertTrue("shown as cards, not a list", OneUiModel.presentation(font) == OneUiModel.Presentation.Rail)
    }
}
