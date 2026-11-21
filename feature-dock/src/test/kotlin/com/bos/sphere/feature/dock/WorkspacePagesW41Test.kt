package com.bos.sphere.feature.dock

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.data.HomeLayoutCodec
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.GroupLayout
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.feature.grid.OneUiDialogTags
import com.bos.sphere.feature.grid.OneUiMenuTags
import com.bos.sphere.feature.grid.OneUiPageFrame
import com.bos.sphere.feature.grid.OneUiTags
import com.bos.sphere.feature.grid.oneUiSegmentedTag
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * Wave 41 (settingsPopups), WSP-05, WSP-07, WSP-08, DCK-04: the Workspaces page, the App groups page and the Dock page in the One UI chrome:
 * the list with its active badge and three-dot menu, make active, rename, duplicate, delete with a confirm, add with "copy from", the calm
 * centre's radio dialog, the groups list, the dock card without a floating card, the dock panel's own navigation, and screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class WorkspacePagesW41Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); DockInput.dialogHook = {} }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); L10n.setLanguage("en") }

    private class Hooks : WorkspaceHooks {
        var home: String? = null; val applied = mutableListOf<String?>()
        override fun captureHome() = home
        override fun applyHome(arrangement: String?) { applied += arrangement }
    }

    private fun arrangement(pages: Int, dock: List<String> = emptyList()) =
        JSONObject().put("pages", HomeLayoutCodec.encode(List(pages) { HomePage() })).put("removed", JSONArray()).put("dock", JSONArray(dock)).toString()

    private val hooks = Hooks()
    private var seq = 0
    private val apps = listOf("Alpha", "Bravo", "Charlie").map { PanelApp("k$it", it, "p.$it", LaunchTarget("p.$it", null, 0)) }
    private val log = ArrayList<String>()

    private fun manager(groups: Boolean = false, extraWorkspace: Boolean = true): WorkspaceManager {
        hooks.home = arrangement(3, listOf("pin:app:a", "pin:app:b", "pin:app:c", "pin:app:d", "pin:app:e"))
        val m = WorkspaceManager(InMemoryWorkspaceStore(), { hooks }, newId = { "id${seq++}" })
        m.setPins(listOf("a", "b", "c", "d", "e").map(DockItemRef::app))
        if (extraWorkspace) {
            val w = m.create("Work")
            m.switchTo(w); hooks.home = arrangement(1, listOf("pin:app:x")); m.switchTo(DockWorkspaceState.DEFAULT_ID)
            hooks.home = arrangement(3, listOf("pin:app:a", "pin:app:b", "pin:app:c", "pin:app:d", "pin:app:e"))
        }
        if (groups) {
            m.saveGroup(AppGroup("Morning", listOf(LaunchRequest(apps[0].target), LaunchRequest(apps[1].target)), GroupLayout.Tile, "g1"))
            m.saveGroup(AppGroup("Evening", listOf(LaunchRequest(apps[2].target), LaunchRequest(apps[0].target), LaunchRequest(apps[1].target)), GroupLayout.Full, "g2"))
        }
        return m
    }

    private fun showWorkspaces(m: WorkspaceManager, light: Boolean = false, font: Float = 1f, actions: List<Pair<String, String>> = emptyList()) {
        if (light) HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, font)) {
                DockPagesScope {
                    val s by m.state.collectAsState()
                    OneUiPageFrame("Workspaces", {}, subtitle = WorkspaceSummary.activeLine(s), tag = DockPanelTags.PAGE) {
                        item { androidx.compose.foundation.layout.Column { WorkspacesContent(m, apps, actions) } }
                    }
                }
            }
        }
        settle()
    }

    private fun showGroups(m: WorkspaceManager, light: Boolean = false, font: Float = 1f) {
        if (light) HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, font)) {
                DockPagesScope {
                    OneUiPageFrame("App groups", {}, tag = DockPanelTags.PAGE) {
                        item { androidx.compose.foundation.layout.Column { AppGroupsContent(m, apps, emptyList(), { log += "run ${it.name}" }, { log += "restore" }, { _, l, _ -> log += "schedule $l" }, { 1L }, true, { log += "shelf $it" }) } }
                    }
                }
            }
        }
        settle()
    }

    private fun showDock(m: WorkspaceManager, light: Boolean = false, font: Float = 1f, nav: Boolean = false) {
        if (light) HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, font)) {
                DockPagesScope {
                    OneUiPageFrame("Dock", {}, tag = DockPanelTags.PAGE) {
                        item { androidx.compose.foundation.layout.Column { DockSettingsContent(m, Tier.T2, apps, onOpenWorkspaces = if (nav) ({}) else null, onOpenGroups = if (nav) ({}) else null) } }
                    }
                }
            }
        }
        settle()
    }

    private fun settle() { rule.mainClock.advanceTimeBy(400); rule.waitForIdle() }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 2000)
    }

    private fun openMenu(m: WorkspaceManager, id: String) {
        rule.onNodeWithTag(DockPageTags.workspaceMore(id)).performScrollTo().performClick(); settle()
    }

    // ---- Workspaces page ------------------------------------------------------------------------------------------------------

    @Test fun WSP_07_page_01_explainsWhatAWorkspaceIsAndShowsTheActiveOneInTheTitle() {
        val m = manager(); showWorkspaces(m)
        rule.onNodeWithTag(DockPageTags.EXPLAIN).assertIsDisplayed()
        val text = rule.onNodeWithTag(DockPageTags.EXPLAIN).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
        assertTrue(text, text.startsWith("Workspaces are separate home setups, like Work and Personal."))
        rule.onNodeWithTag(OneUiTags.SUBTITLE).assertIsDisplayed()
        rule.onNodeWithText("Active: Home").assertIsDisplayed()
        assertTrue(rule.onNodeWithTag(OneUiTags.TITLE_LARGE).fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
    }

    @Test fun WSP_07_page_02_eachWorkspaceIsACardWithASummaryAndTheActiveOneIsBadged() {
        val m = manager(); showWorkspaces(m)
        val home = DockWorkspaceState.DEFAULT_ID
        val work = m.current.workspaces.first { it.name == "Work" }.id
        rule.onNodeWithTag(DockPageTags.workspace(home)).assertIsDisplayed()
        rule.onNodeWithText("3 pages · 5 dock items").assertExists()
        rule.onNodeWithText("1 page · 1 dock item").assertExists()
        rule.onNodeWithTag(DockPageTags.workspaceBadge(home), useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag(DockPageTags.workspaceBadge(work), useUnmergedTree = true).assertCountEquals(0)
        assertEquals("In use", rule.onNodeWithTag(DockPageTags.workspace(home)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun WSP_07_page_03_makeActiveFromTheMenuMovesTheBadgeAndSwitches() {
        val m = manager(); showWorkspaces(m)
        val work = m.current.workspaces.first { it.name == "Work" }.id
        openMenu(m, work)
        rule.onNodeWithText("Make active").performClick(); settle()
        assertEquals(work, m.current.activeId)
        rule.onNodeWithTag(DockPageTags.workspaceBadge(work), useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Active: Work").assertExists()
    }

    @Test fun WSP_07_page_04_tappingAnInactiveCardSwitchesToIt() {
        val m = manager(); showWorkspaces(m)
        val work = m.current.workspaces.first { it.name == "Work" }.id
        rule.onNodeWithTag(DockPageTags.workspace(work)).performClick(); settle()
        assertEquals(work, m.current.activeId)
    }

    @Test fun WSP_07_page_05_renameOpensANameDialogWithTheNameAndRenames() {
        val m = manager(); showWorkspaces(m)
        val work = m.current.workspaces.first { it.name == "Work" }.id
        rule.mainClock.autoAdvance = false
        openMenu(m, work)
        rule.onNodeWithText("Rename").performClick(); settle()
        rule.onNodeWithText("Rename workspace").assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.FIELD).performTextInput("2"); settle()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick(); settle()
        // the dialog selects the old name, so typing replaces it
        assertEquals("2", m.current.workspaces.first { it.id == work }.name)
    }

    @Test fun WSP_07_page_06_aNameAlreadyUsedIsRefused() {
        val m = manager(); showWorkspaces(m)
        val work = m.current.workspaces.first { it.name == "Work" }.id
        rule.mainClock.autoAdvance = false
        openMenu(m, work)
        rule.onNodeWithText("Rename").performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.FIELD).performTextInput("home"); settle()
        rule.onNodeWithText("A workspace with this name already exists").assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsNotEnabled()
    }

    @Test fun WSP_07_page_07_duplicateMakesACopyWithItsArrangementAndAFreeName() {
        val m = manager(); showWorkspaces(m)
        openMenu(m, DockWorkspaceState.DEFAULT_ID)
        rule.onNodeWithText("Duplicate").performClick(); settle()
        val dup = m.current.workspaces.first { it.name == "Home copy" }
        assertEquals(hooks.home, dup.homeArrangement)
        rule.onNodeWithTag(DockPageTags.workspace(dup.id)).assertExists()
        rule.onAllNodesWithText("3 pages · 5 dock items", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test fun WSP_07_page_08_deleteAsksFirstAndCancelKeepsIt() {
        val m = manager(); showWorkspaces(m)
        val work = m.current.workspaces.first { it.name == "Work" }.id
        openMenu(m, work)
        rule.onNodeWithText("Delete").performClick(); settle()
        rule.onNodeWithText("Delete Work?").assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.CANCEL).performClick(); settle()
        assertEquals(2, m.current.workspaces.size)
        openMenu(m, work)
        rule.onNodeWithText("Delete").performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick(); settle()
        assertEquals(listOf("Home"), m.current.workspaces.map { it.name })
    }

    @Test fun WSP_07_page_09_theOnlyWorkspaceHasNoDeleteInItsMenu() {
        val m = manager(extraWorkspace = false); showWorkspaces(m)
        openMenu(m, DockWorkspaceState.DEFAULT_ID)
        rule.onAllNodesWithText("Delete").assertCountEquals(0)
        rule.onNodeWithText("Rename").assertIsDisplayed()
    }

    @Test fun WSP_07_page_10_addWorkspaceWithCopyFromTheCurrentOne() {
        val m = manager(); showWorkspaces(m)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(DockPageTags.ADD).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.TITLE).assertExists()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsNotEnabled() // a name is needed
        rule.onNodeWithTag(OneUiDialogTags.FIELD).performTextInput("Travel"); settle()
        rule.onNodeWithTag("ws.copy.current").performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick(); settle()
        val w = m.current.workspaces.first { it.name == "Travel" }
        assertEquals(listOf("a", "b", "c", "d", "e").map(DockItemRef::app), w.dockPins)
        assertEquals(hooks.home, w.homeArrangement)
        assertEquals(DockWorkspaceState.DEFAULT_ID, m.current.activeId)
    }

    @Test fun WSP_07_page_11_addWorkspaceEmptyIsTheDefault() {
        val m = manager(); showWorkspaces(m)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(DockPageTags.ADD).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.FIELD).performTextInput("Play"); settle()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick(); settle()
        val w = m.current.workspaces.first { it.name == "Play" }
        assertTrue(w.dockPins.isEmpty()); assertNull(w.homeArrangement)
    }

    // ---- the calm centre ---------------------------------------------------------------------------------------------------

    @Test fun WSP_08_page_12_theCalmCentreRowIsPlainLanguageAndOpensARadioDialog() {
        val m = manager(); showWorkspaces(m)
        rule.onNodeWithText("What the calm centre shows").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Calm centre shows all apps").assertCountEquals(0)
        rule.onAllNodesWithText("Choose").assertCountEquals(0)
        rule.onNodeWithTag(DockPageTags.CALM).performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.option(0)).assertIsDisplayed()
        assertEquals(true, rule.onNodeWithTag(OneUiDialogTags.option(0)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithText("Only apps I choose").assertIsDisplayed()
        rule.onAllNodesWithText("Apps of a group").assertCountEquals(0) // no group exists, so the choice is not offered
    }

    @Test fun WSP_08_page_13_chooseAppsStoresThePackagesAndAllAppsClearsThem() {
        val m = manager(); showWorkspaces(m)
        rule.onNodeWithTag(DockPageTags.CALM).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.option(1)).performClick(); settle()
        rule.onNodeWithText("Apps shown in the calm centre").assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.option(0)).performClick(); rule.onNodeWithTag(OneUiDialogTags.option(2)).performClick()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick(); settle()
        assertEquals(setOf("p.Alpha", "p.Charlie"), m.current.active.notificationApps)
        rule.onNodeWithText("2 chosen apps").assertIsDisplayed()
        rule.onNodeWithTag(DockPageTags.CALM).performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.option(0)).performClick(); settle()
        assertNull(m.current.active.notificationApps)
    }

    @Test fun WSP_08_page_14_aGroupCanSeedTheCalmCentre() {
        val m = manager(groups = true); showWorkspaces(m)
        rule.onNodeWithTag(DockPageTags.CALM).performScrollTo().performClick(); settle()
        rule.onNodeWithText("Apps of a group").performClick(); settle()
        rule.onNodeWithText("Morning · 2").performClick(); settle()
        assertEquals(setOf("p.Alpha", "p.Bravo"), m.current.active.notificationApps)
    }

    @Test fun WSP_07_page_15_radarActionsAreACheckDialogWhenTheHostOffersActions() {
        val m = manager(); showWorkspaces(m, actions = listOf("a1" to "Open Radar", "a2" to "Flashlight"))
        rule.onNodeWithTag(DockPageTags.RADAR_ACTIONS).performScrollTo().performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.option(1)).performClick()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick(); settle()
        assertEquals(listOf("a2"), m.current.active.paletteActions)
    }

    // ---- App groups page ---------------------------------------------------------------------------------------------------

    @Test fun WSP_05_page_20_theGroupsListShowsTheAppCountAndOpensOnTap() {
        val m = manager(groups = true); showGroups(m)
        rule.onNodeWithText("2 apps · Tiled").assertIsDisplayed()
        rule.onNodeWithText("3 apps · Full size").assertIsDisplayed()
        rule.onNodeWithTag(DockPageTags.group("g1")).performClick()
        assertEquals(listOf("run Morning"), log)
    }

    @Test fun WSP_05_page_21_aGroupsMenuHasOpenPinSaveSessionRemindAndDelete() {
        val m = manager(groups = true); showGroups(m)
        rule.onNodeWithTag(DockPageTags.groupMore("g1")).performClick(); settle()
        listOf("Open", "Pin to dock", "Save session", "Remind me", "Delete").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Pin to dock").performClick(); settle()
        assertTrue(DockItemRef.group("g1") in m.current.active.dockPins)
        rule.onNodeWithText("In the dock", substring = true).assertExists()
    }

    @Test fun WSP_05_page_22_deletingAGroupAsksFirst() {
        val m = manager(groups = true); showGroups(m)
        rule.onNodeWithTag(DockPageTags.groupMore("g2")).performClick(); settle()
        rule.onNodeWithText("Delete").performClick(); settle()
        rule.onNodeWithText("Delete Evening?").assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick(); settle()
        assertEquals(listOf("Morning"), m.current.groups.map { it.name })
    }

    @Test fun WSP_05_page_23_theShelfSwitchAndTheScheduleRowAreRows() {
        val m = manager(groups = true); showGroups(m)
        rule.onNodeWithTag(DockPageTags.GROUP_SHELF).performScrollTo().performClick(); settle()
        assertEquals(listOf("shelf false"), log.takeLast(1))
        rule.onNodeWithTag(DockPageTags.SCHEDULE).performScrollTo().performClick(); settle()
        rule.onNodeWithText("Which app?").assertIsDisplayed()
    }

    @Test fun WSP_05_page_24_newGroupNeedsANameAndTwoToFourApps() {
        val m = manager(); showGroups(m)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(DockPageTags.GROUP_NEW).performScrollTo().performClick(); settle()
        rule.onNodeWithText("New app group").assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsNotEnabled()
        rule.onNodeWithTag(OneUiDialogTags.FIELD).performTextInput("Focus"); settle()
        rule.onNodeWithText("Choose 2 to 4 apps").performClick(); settle()
        rule.onAllNodesWithTag(OneUiDialogTags.CONFIRM)[1].assertIsNotEnabled() // the picker is on top: below the minimum
        rule.onNodeWithTag(OneUiDialogTags.option(0)).performClick(); rule.onNodeWithTag(OneUiDialogTags.option(1)).performClick()
        rule.onAllNodesWithTag(OneUiDialogTags.CONFIRM)[1].performClick(); settle()
        rule.onNodeWithText("Alpha, Bravo").assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick(); settle()
        assertEquals(listOf("Focus"), m.current.groups.map { it.name })
    }

    // ---- Dock page, without a floating card --------------------------------------------------------------------------------

    @Test fun DCK_04_page_30_theDockPageHasRowsNotChipsAndNoPurpleLinks() {
        val m = manager(); showDock(m)
        rule.onNodeWithTag(DockPageTags.SHOW_DOCK).assertIsDisplayed()
        rule.onNodeWithTag(DockPageTags.MODE).assertIsDisplayed()
        rule.onNodeWithText("Pinned and recent").assertIsDisplayed() // the value of the Dock mode row, not a chip
        (0..2).forEach { rule.onNodeWithTag(oneUiSegmentedTag(it)).assertIsDisplayed() } // Bottom / Left / Right as one segmented control
        rule.onNodeWithTag(DockPageTags.ADD_APPS).assertIsDisplayed()
        assertTrue("the Add apps row is a 56 dp row", rule.onNodeWithTag(DockPageTags.ADD_APPS).getUnclippedBoundsInRoot().let { it.bottom - it.top } >= 56.dp)
        rule.onAllNodesWithText("Workspaces").assertCountEquals(0) // the old card's section heading is gone from this page
        rule.onAllNodesWithText("New workspace name").assertCountEquals(0)
    }

    @Test fun DCK_04_page_31_theSwitchTheModeDialogAndTheSegmentedPositionChangeTheDock() {
        val m = manager(); showDock(m)
        rule.onNodeWithTag(DockPageTags.SHOW_DOCK).performClick(); settle()
        assertEquals(false, m.current.dock.enabled)
        rule.onNodeWithTag(oneUiSegmentedTag(2)).performClick(); settle()
        assertEquals(DockEdge.Right, m.current.dock.edge)
        rule.onNodeWithTag(DockPageTags.MODE).performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.option(0)).performClick(); settle()
        assertEquals(DockMode.Collapsed, m.current.dock.mode)
        rule.onNodeWithText("Collapsed band").assertIsDisplayed()
        rule.onNodeWithTag("dock.useDefault").performClick(); settle()
        assertNull(m.current.dock.enabled)
    }

    @Test fun DCK_04_page_32_addAppsOpensTheCheckListAndPinsThePicked() {
        val m = manager(extraWorkspace = false); showDock(m)
        rule.onNodeWithTag(DockPageTags.ADD_APPS).performClick(); settle()
        rule.onNodeWithTag(OneUiDialogTags.option(0)).performClick()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick(); settle()
        assertTrue(DockItemRef.app("p.Alpha") in m.current.active.dockPins)
    }

    @Test fun DCK_04_page_33_theDockPanelIsAFullPageWithItsOwnNavigation() {
        val m = manager(groups = true)
        rule.setContent {
            DockPanel(m, Tier.T2, apps, emptyList(), {}, {}, { _, _, _ -> }, { log += "closed" })
        }
        settle()
        rule.onNodeWithTag(DockPanelTags.PAGE).assertIsDisplayed()
        rule.onNodeWithTag(OneUiTags.BACK).assertIsDisplayed()
        assertEquals("Dock", rule.onNodeWithTag(OneUiTags.TITLE_LARGE).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.single().text)
        rule.onNodeWithTag("dock.openWorkspaces").performScrollTo().performClick(); settle()
        rule.onNodeWithText("Active: Home").assertIsDisplayed()
        rule.onNodeWithTag(DockPageTags.ADD).assertExists()
        rule.onNodeWithTag(OneUiTags.BACK).performClick(); settle()
        rule.onNodeWithTag(DockPageTags.SHOW_DOCK).assertIsDisplayed()
        rule.onNodeWithTag("dock.openGroups").performScrollTo().performClick(); settle()
        rule.onNodeWithTag(DockPageTags.GROUP_NEW).assertExists()
        rule.onNodeWithTag(OneUiTags.BACK).performClick(); settle()
        rule.onNodeWithTag(OneUiTags.BACK).performClick(); settle()
        assertEquals(listOf("closed"), log)
    }

    // ---- font scale 2 and screenshots --------------------------------------------------------------------------------------

    @Test fun WSP_07_page_40_theListStillReadsAtFontScale2() {
        val m = manager(); showWorkspaces(m, font = 2f)
        rule.onNodeWithTag(DockPageTags.workspace(DockWorkspaceState.DEFAULT_ID)).assertExists()
        rule.onNodeWithTag(DockPageTags.workspaceMore(DockWorkspaceState.DEFAULT_ID)).performScrollTo().assertIsDisplayed()
        shoot("w41-workspaces-fontscale2-dark")
    }

    @Test fun WSP_07_page_50_screenshotWorkspacesDark() { val m = manager(); m.create("Travel"); showWorkspaces(m, actions = listOf("a1" to "Open Radar")); shoot("w41-workspaces-dark-portrait") }
    @Test fun WSP_07_page_51_screenshotWorkspacesLight() { val m = manager(); m.create("Travel"); showWorkspaces(m, light = true, actions = listOf("a1" to "Open Radar")); shoot("w41-workspaces-light-portrait") }
    @Test fun WSP_05_page_52_screenshotGroupsDark() { val m = manager(groups = true); showGroups(m); shoot("w41-groups-dark-portrait") }
    @Test fun WSP_05_page_53_screenshotGroupsLight() { val m = manager(groups = true); showGroups(m, light = true); shoot("w41-groups-light-portrait") }
    @Test fun DCK_04_page_54_screenshotDockDark() { val m = manager(); showDock(m, nav = true); shoot("w41-dock-dark-portrait") }
    @Test fun DCK_04_page_55_screenshotDockLight() { val m = manager(); showDock(m, light = true, nav = true); shoot("w41-dock-light-portrait") }
}
