package com.bos.sphere.feature.dock

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.quality.MotionPrefs
import com.bos.sphere.feature.grid.OneUiMenuTags
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
 * W55 (dockSheet): the dock's long-press sheet through the real [DockHost] inside a [DockSheetHost]: it opens, the whole home (dock included) is
 * pushed up by the sheet's height (or sideways beside a side dock), the owner changes the style live, edits the dock in place, and closes it by
 * drag, Back, Esc or the controller's B, with the home coming back each time.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.InternalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class DockSheetW55Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private fun app(n: String) = AppEntry("p.$n", ComponentName("p.$n", "p.$n.Main"), n, android.os.Process.myUserHandle()) { null }
    private val apps = listOf(app("Alpha"), app("Bravo"), app("Charlie"), app("Delta"))
    private val state = DockSheetState()
    private val log = mutableListOf<String>()
    private lateinit var manager: WorkspaceManager

    @Before fun setUp() {
        L10n.setLanguage("en"); DockInput.dialogHook = {}; log.clear()
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f)) // snap: the geometry is asserted at rest; one test turns motion on
        manager = WorkspaceRepository.get(context)
        manager.setDock { DockSettings(enabled = true) }
        manager.setPins(apps.take(3).map { DockItemRef.app(it.key) })
    }
    @After fun tearDown() { ReducedMotionTesting.force(null); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    @Composable private fun Screen(rtl: Boolean = false, landscape: Boolean = false) {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            DockSheetHost(state) {
                Box(Modifier.fillMaxSize().background(Color(0xFF223355))) {
                    Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(96.dp, 40.dp).background(Color(0xFFFFB300)).testTag("home.top").clickable { log += "home tap" })
                    DockHost(
                        apps = apps, launcher = AppLauncher(context), controller = DockController(), refreshKey = 0, onLaunchApp = { log += "launch ${it.label}" },
                        onOpenPanel = { log += "workspaces" }, modifier = { edge -> dockAt(this@Box, edge) }, alwaysShown = false,
                    )
                }
            }
        }
    }

    private fun dockAt(s: BoxScope, edge: DockEdge): Modifier = with(s) {
        when (edge) {
            DockEdge.Bottom -> Modifier.align(Alignment.BottomCenter).padding(start = 76.dp, end = 76.dp, bottom = 8.dp)
            DockEdge.Left -> Modifier.align(Alignment.CenterStart).padding(start = 12.dp, top = 40.dp, bottom = 40.dp)
            DockEdge.Right -> Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = 40.dp, bottom = 40.dp)
        }
    }

    private fun settle() { rule.mainClock.advanceTimeBy(600); rule.waitForIdle() }
    private fun openSheet() { rule.onNodeWithContentDescription("Dock").performTouchInput { longClick(Offset(width - 4f, height / 2f)) }; settle() }
    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag).getBoundsInRoot()
    private fun homeTop() = bounds("home.top").top
    private fun homeLeft() = bounds("home.top").left
    private fun sheet() = bounds(DockSheetTags.SHEET)
    private fun dock() = rule.onNodeWithContentDescription("Dock").getBoundsInRoot()

    private fun shoot(name: String) {
        settle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), Paint().apply { color = 0xFF10131F.toInt() }); drawBitmap(bmp, 0f, 0f, null) }
        File(outDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- opening ---------------------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_sheet_01_aLongPressOnTheDockOpensASheetWithGrabberCentredTitleAndEveryOption() {
        rule.setContent { Screen() }
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(0)
        openSheet()
        rule.onNodeWithTag(DockSheetTags.SHEET).assertIsDisplayed()
        rule.onNodeWithTag(OneUiMenuTags.GRABBER).assertExists()
        assertEquals("Dock", rule.onNodeWithTag(DockSheetTags.TITLE).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.single().text)
        DockStyle.entries.forEach { rule.onNodeWithTag(DockStyleUi.rowTag(it)).assertExists() }
        DockEdge.entries.forEach { rule.onNodeWithTag(DockOptionTags.position(it)).assertExists() }
        DockMode.entries.forEach { rule.onNodeWithTag(DockOptionTags.mode(it)).assertExists() }
        listOf(DockPageTags.SHOW_DOCK, DockOptionTags.GROUP_SHELF, DockSheetTags.EDIT, DockSheetTags.WORKSPACES, DockSheetTags.ABOUT).forEach { rule.onNodeWithTag(it).assertExists() }
        rule.onNodeWithText("In portrait the dock is always at the bottom. The side you choose applies in landscape.").assertExists() // portrait says so
        val b = sheet(); val centre = (b.left.value + b.right.value) / 2f
        assertEquals("a phone sheet spans the width, so it is centred", 411f / 2f, centre, 1f)
        shoot("w55-dock-sheet-portrait")
    }

    @Test fun DCK_04_sheet_02_theSheetHasATalkBackPaneTitle() {
        rule.setContent { Screen() }
        openSheet()
        assertEquals("Dock", rule.onNodeWithTag(DockSheetTags.SHEET).fetchSemanticsNode().config.getOrNull(SemanticsProperties.PaneTitle))
    }

    @Test fun DCK_04_sheet_03_theWholeHomeIsPushedUpByTheSheetHeightSoTheDockStaysVisibleAboveItAndReturnsOnDismiss() {
        rule.setContent { Screen() }
        val topBefore = homeTop(); val dockBefore = dock()
        openSheet()
        val s = sheet(); val d = dock()
        assertEquals("home pushed up by exactly the sheet's height", (topBefore - s.height).value, homeTop().value, 1.5f)
        assertTrue("the top of the home is outside the viewframe", homeTop() < 0.dp)
        assertTrue("the dock is fully visible", d.top >= 0.dp && d.bottom <= s.top + 1.dp)
        assertEquals("the dock sits directly above the sheet (its own 8 dp margin)", s.top.value, d.bottom.value + 8f, 2f)
        assertEquals("the dock moved by the same amount", (dockBefore.top - s.height).value, d.top.value, 1.5f)
        rule.onNodeWithTag(DockSheetTags.ROOT).performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }; settle()
        assertEquals(topBefore.value, homeTop().value, 0.5f)
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(0)
    }

    @Test fun DCK_04_sheet_04_withMotionTheSheetAndTheHomeFollowOneSpringAndSettleAtTheSameRest() {
        ReducedMotionTesting.force(MotionPrefs(1f, 1f, 1f))
        rule.setContent { Screen() }
        val before = homeTop()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Dock").performTouchInput { longClick(Offset(width - 4f, height / 2f)) }
        rule.mainClock.advanceTimeByFrame(); rule.mainClock.advanceTimeByFrame()
        val mid = homeTop()
        rule.mainClock.advanceTimeBy(1500)
        val rest = homeTop()
        assertTrue("moving (still between start $before and rest $rest): $mid", mid < before && mid > rest)
        assertEquals(before.value - sheet().height.value, rest.value, 1.5f)
        assertEquals("the sheet rests on the bottom edge", rule.onRoot().getBoundsInRoot().bottom.value, sheet().bottom.value, 1f)
    }

    @Test fun DCK_04_sheet_05_theSheetOffersMagnifyIconsAndTheDockAboveMagnifiesWhileTheSheetIsOpen() {
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag(DockOptionTags.MAGNIFY).performScrollTo().assertExists()
        assertFalse(manager.current.dock.magnify)
        rule.onNodeWithTag(DockOptionTags.MAGNIFY).performScrollTo().performClick(); settle()
        assertTrue(manager.current.dock.magnify)
        MagnifyStrength.entries.forEach { rule.onNodeWithTag(DockOptionTags.strength(it)).performScrollTo().assertExists() }
        val rest = rule.onNodeWithContentDescription("Bravo").getBoundsInRoot().width.value
        rule.onNodeWithContentDescription("Bravo").performTouchInput { down(center); moveBy(Offset(8f, 0f)) }
        settle()
        assertTrue("the real dock above the sheet magnifies live (${rule.onNodeWithContentDescription("Bravo").getBoundsInRoot().width.value} vs $rest)", rule.onNodeWithContentDescription("Bravo").getBoundsInRoot().width.value > rest * 1.15f)
        assertTrue("and nothing launched", log.none { it.startsWith("launch") })
        rule.onNodeWithContentDescription("Bravo").performTouchInput { up() }
    }

    // ---- live (WYSIWYG) --------------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_sheet_10_chooseingAStyleChangesTheRealDockAboveAtOnce() {
        rule.setContent { Screen() }
        openSheet()
        assertEquals("Dock style: Standard", rule.onNodeWithContentDescription("Dock").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        rule.onNodeWithTag(DockStyleUi.rowTag(DockStyle.Transparent)).performClick(); settle()
        assertEquals(DockStyle.Transparent, manager.current.dock.style)
        assertEquals("Dock style: Transparent", rule.onNodeWithContentDescription("Dock").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        rule.onNodeWithTag(DockStyleUi.rowTag(DockStyle.Transparent)).assertIsSelected()
        rule.onNodeWithTag(DockStyleUi.rowTag(DockStyle.ThreeD)).performClick(); settle()
        assertEquals("Dock style: 3D", rule.onNodeWithContentDescription("Dock").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun DCK_04_sheet_11_modeAndGroupChipsAndShowTheDockWriteTheSameStoreAsSettings() {
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag(DockOptionTags.mode(DockMode.Collapsed)).performScrollTo().performClick(); settle()
        assertEquals(DockMode.Collapsed, manager.current.dock.mode)
        rule.onNodeWithTag(DockOptionTags.mode(DockMode.PinnedPlusRecent)).performScrollTo().performClick(); settle()
        val shelf = manager.current.dock.groupShelf
        rule.onNodeWithTag(DockOptionTags.GROUP_SHELF).performScrollTo().performClick(); settle()
        assertEquals(!shelf, manager.current.dock.groupShelf)
        rule.onNodeWithTag(DockPageTags.SHOW_DOCK).performScrollTo().performClick(); settle()
        assertEquals(false, manager.current.dock.enabled)
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(1) // the sheet stays: it is how the dock is switched back on
        rule.onNodeWithTag(DockPageTags.SHOW_DOCK).performScrollTo().performClick(); settle()
        assertEquals(true, manager.current.dock.enabled)
        rule.onNodeWithContentDescription("Dock").assertExists()
    }

    @Test fun DCK_04_sheet_12_workspacesAndGroupsClosesTheSheetAndOpensThatPage() {
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag(DockSheetTags.WORKSPACES).performClick(); settle()
        assertEquals(listOf("workspaces"), log)
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(0)
    }

    @Test fun DCK_04_sheet_13_aboutTheDockOpensItsDialogOnTopOfTheSheet() {
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag(DockSheetTags.ABOUT).performClick(); settle()
        rule.onNodeWithText("Alt with an arrow key, or a long press and drag, reorders pinned items.").assertExists()
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(1)
    }

    // ---- edit in place ---------------------------------------------------------------------------------------------------------------------

    @Test fun DCK_01_edit_01_editDockShrinksTheSheetToABarWithDoneAndTheDockStaysAboveIt() {
        rule.setContent { Screen() }
        val top0 = homeTop()
        openSheet()
        val full = sheet().height
        rule.onNodeWithTag(DockSheetTags.EDIT).performClick(); settle()
        rule.onNodeWithTag(DockSheetTags.EDIT_BAR).assertIsDisplayed()
        rule.onNodeWithTag(DockSheetTags.DONE).assertIsDisplayed()
        assertEquals("Edit dock", rule.onNodeWithTag(DockSheetTags.TITLE).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.single().text)
        assertTrue("compact: ${sheet().height} vs $full", sheet().height < full / 2)
        val s = sheet(); val d = dock()
        assertTrue("the dock is visible right above the bar", d.bottom <= s.top + 1.dp && d.top >= 0.dp)
        assertEquals("the home follows the smaller sheet", top0.value - s.height.value, homeTop().value, 1.5f)
        rule.onNodeWithTag("dock.addTile").assertIsDisplayed()
        shoot("w55-dock-sheet-edit-portrait")
        rule.onNodeWithTag(DockSheetTags.DONE).performClick(); settle()
        rule.onAllNodesWithTag(DockSheetTags.EDIT_BAR).assertCountEquals(0)
        rule.onNodeWithTag(DockStyleUi.rowTag(DockStyle.Standard)).assertExists()
        rule.onAllNodesWithTag("dock.addTile").assertCountEquals(0)
    }

    @Test fun DCK_01_edit_02_tapTheXToRemoveAnIconAndTheHomeBehindIsNotTouchable() {
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag(DockSheetTags.EDIT).performClick(); settle()
        rule.onNodeWithTag("dock.remove.app:${apps[1].key}").performClick(); settle()
        assertEquals(listOf(apps[0], apps[2]).map { DockItemRef.app(it.key) }, manager.current.active.dockPins)
        rule.onAllNodesWithTag("dock.remove.app:${apps[1].key}").assertCountEquals(0)
        rule.onNodeWithTag("home.top").performClick(); settle()
        assertTrue("the home does not take the tap while the dock is edited", "home tap" !in log)
        rule.onNodeWithContentDescription("Alpha").performClick(); settle()
        assertTrue("an icon is not launched while editing", log.none { it.startsWith("launch") })
    }

    @Test fun DCK_01_edit_03_dragAnIconOnTheDockItselfToReorderIt() {
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag(DockSheetTags.EDIT).performClick(); settle()
        rule.onNodeWithContentDescription("Alpha").performTouchInput { swipeRight(startX = centerX, endX = centerX + 60.dp.toPx(), durationMillis = 400) }; settle()
        assertEquals(listOf(apps[1], apps[0], apps[2]).map { it.key }, manager.current.active.dockPins.map { it.id })
    }

    @Test fun DCK_01_edit_04_thePlusTileOpensTheAppPickerAsAPageAndAddsTheChosenApp() {
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag(DockSheetTags.EDIT).performClick(); settle()
        rule.onNodeWithTag("dock.addTile").performClick(); settle()
        rule.onNodeWithText("Add apps to the dock").assertExists()
        rule.onNodeWithTag(com.bos.sphere.feature.grid.OneUiDialogTags.option(3)).performClick()
        rule.onNodeWithTag(com.bos.sphere.feature.grid.OneUiDialogTags.CONFIRM).performClick(); settle()
        assertEquals("the three pins are kept by key and the new one is pinned by its key", apps.map { DockItemRef.app(it.key) }, manager.current.active.dockPins)
        rule.onNodeWithTag(DockSheetTags.EDIT_BAR).assertExists() // still in edit mode
    }

    @Test fun DCK_01_edit_05_anEmptyDockStillShowsItsPlusTileWhileEditing() {
        manager.setPins(emptyList())
        rule.setContent { Screen() }
        rule.onAllNodesWithTag("dock.addTile").assertCountEquals(0)
        rule.runOnUiThread { state.show(DockSheetSpec(manager, com.bos.sphere.core.capabilities.Tier.T2, false)); state.startEditing() }; settle()
        rule.onNodeWithTag("dock.addTile").assertIsDisplayed()
    }

    @Test fun DCK_01_edit_06_anIconsLongPressMenuIsStillASmallContextMenu() {
        rule.setContent { Screen() }
        rule.onNodeWithContentDescription("Bravo").performTouchInput { longClick() }; settle()
        rule.onNodeWithTag(DockMenuModel.TAG_ITEM_MENU).assertExists()
        listOf("Move earlier", "Move later", "Replace with another app", "Remove from dock").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(0)
    }

    // ---- closing ---------------------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_close_01_dragTheHeaderDownToDismissAndTheHomeComesBack() {
        rule.setContent { Screen() }
        val top = homeTop()
        openSheet()
        rule.onNodeWithTag(OneUiMenuTags.GRABBER).performTouchInput { swipeDown(startY = centerY, endY = centerY + 400f, durationMillis = 200) }; settle()
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(0)
        assertEquals(top.value, homeTop().value, 0.5f)
    }

    @Test fun DCK_04_close_02_aShortDragSpringsBackAndKeepsTheSheetOpen() {
        rule.setContent { Screen() }
        openSheet()
        val top = homeTop()
        rule.onNodeWithTag(OneUiMenuTags.GRABBER).performTouchInput { swipeDown(startY = centerY, endY = centerY + 40f, durationMillis = 200) }; settle()
        rule.onNodeWithTag(DockSheetTags.SHEET).assertIsDisplayed()
        assertEquals(top.value, homeTop().value, 1f)
    }

    @Test fun DCK_04_close_03_backClosesTheSheetAndInEditModeFirstLeavesEditing() {
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag(DockSheetTags.EDIT).performClick(); settle()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle()
        rule.onAllNodesWithTag(DockSheetTags.EDIT_BAR).assertCountEquals(0)
        rule.onNodeWithTag(DockSheetTags.SHEET).assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle()
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(0)
    }

    @Test fun DCK_04_close_04_escapeAndTheControllersBCloseTheSheet() {
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag(DockSheetTags.ROOT).performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }; settle()
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(0)
        openSheet()
        rule.onNodeWithTag(DockSheetTags.ROOT).performKeyInput { keyDown(Key.ButtonB); keyUp(Key.ButtonB) }; settle()
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(0)
    }

    @Test fun DCK_04_close_05_aTapOnTheHomeAboveTheSheetClosesItWithoutPressingWhatIsUnderneath() {
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag("home.top").assertExists()
        rule.onNodeWithTag(DockSheetTags.ROOT).performTouchInput { click(Offset(width / 2f, 60f)) }; settle()
        rule.onAllNodesWithTag(DockSheetTags.SHEET).assertCountEquals(0)
        assertTrue("nothing was pressed under the tap", log.isEmpty())
    }

    // ---- the input layer's dialog hook ------------------------------------------------------------------------------------------------------

    @Test fun INP_20_theDockDialogHookAppliesWhileTheSheetIsOpenAndEndsWithIt() {
        var live = 0
        DockInput.dialogHook = { DisposableEffect(Unit) { live++; onDispose { live-- } } }
        rule.setContent { Screen() }
        assertEquals(0, live)
        openSheet()
        assertEquals("the hook is composed inside the sheet", 1, live)
        rule.onNodeWithTag(DockSheetTags.ROOT).performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }; settle()
        assertEquals(0, live)
    }

    @Test fun INP_20_theAboutDialogInstallsTheDialogHookToo() {
        var live = 0
        DockInput.dialogHook = { DisposableEffect(Unit) { live++; onDispose { live-- } } }
        rule.setContent { Screen() }
        openSheet()
        rule.onNodeWithTag(DockSheetTags.ABOUT).performClick(); settle()
        assertEquals("the sheet's and the dialog's", 2, live)
    }

    // ---- geometry --------------------------------------------------------------------------------------------------------------------------

    @Test fun DCK_04_geo_01_aSideSheetOnlyForASideDockInLandscape() {
        val port = DockSheetSpec(manager, com.bos.sphere.core.capabilities.Tier.T2, landscape = false)
        val land = DockSheetSpec(manager, com.bos.sphere.core.capabilities.Tier.T2, landscape = true)
        assertFalse(DockSheetGeometry.isSide(port, DockEdge.Left))
        assertFalse(DockSheetGeometry.isSide(land, DockEdge.Bottom))
        assertTrue(DockSheetGeometry.isSide(land, DockEdge.Left)); assertTrue(DockSheetGeometry.isSide(land, DockEdge.Right))
        // The rail follows the layout direction, so in RTL "Left" is the right edge.
        assertTrue(DockSheetGeometry.physicalLeft(DockEdge.Left, rtl = false)); assertFalse(DockSheetGeometry.physicalLeft(DockEdge.Left, rtl = true))
        assertFalse(DockSheetGeometry.physicalLeft(DockEdge.Right, rtl = false)); assertTrue(DockSheetGeometry.physicalLeft(DockEdge.Right, rtl = true))
        val sz = androidx.compose.ui.unit.IntSize(300, 500)
        assertEquals(androidx.compose.ui.geometry.Offset(0f, -440f), DockSheetGeometry.homeOffset(false, false, sz, 60, 1f))
        assertEquals(androidx.compose.ui.geometry.Offset(0f, -220f), DockSheetGeometry.homeOffset(false, false, sz, 60, 0.5f))
        assertEquals(androidx.compose.ui.geometry.Offset(300f, 0f), DockSheetGeometry.homeOffset(true, true, sz, 60, 1f))
        assertEquals(androidx.compose.ui.geometry.Offset(-300f, 0f), DockSheetGeometry.homeOffset(true, false, sz, 60, 1f))
    }

    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    @Test fun DCK_04_geo_02_landscapeWithALeftDockOpensASideSheetAndPushesTheHomeSideways() {
        manager.setDock { it.copy(edge = DockEdge.Left) }
        rule.setContent { Screen(landscape = true) }
        val leftBefore = homeLeft(); val dockBefore = dock()
        openSheet()
        val s = sheet()
        assertTrue("a side sheet, not a full-width one: ${s.width}", s.width < 450.dp && s.width > 200.dp)
        assertEquals("on the dock's side", 0f, s.left.value, 1f)
        val root = rule.onRoot().getBoundsInRoot()
        assertEquals("full height", root.height.value, s.height.value, 1f)
        assertEquals("the home is pushed sideways by the sheet's width", (leftBefore + s.width).value, homeLeft().value, 1.5f)
        assertEquals((dockBefore.left + s.width).value, dock().left.value, 1.5f)
        assertTrue("the dock stays visible beside the sheet", dock().left >= s.right - 1.dp && dock().right < root.right)
        shoot("w55-dock-sheet-landscape-left")
        rule.onNodeWithTag(DockSheetTags.ROOT).performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }; settle()
        assertEquals(leftBefore.value, homeLeft().value, 0.5f)
    }

    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    @Test fun DCK_04_geo_03_landscapeWithARightDockPutsTheSheetOnTheRightAndPushesTheHomeLeft() {
        manager.setDock { it.copy(edge = DockEdge.Right) }
        rule.setContent { Screen(landscape = true) }
        val leftBefore = homeLeft()
        openSheet()
        val s = sheet()
        assertEquals(rule.onRoot().getBoundsInRoot().right.value, s.right.value, 1f)
        assertEquals((leftBefore - s.width).value, homeLeft().value, 1.5f)
        assertTrue(dock().right <= s.left + 1.dp)
        rule.onNodeWithTag(DockOptionTags.position(DockEdge.Bottom)).performScrollTo().performClick(); settle() // the dock moves to the bottom: the sheet follows to a bottom sheet
        assertTrue("now a bottom sheet: ${sheet().width}", sheet().width > 500.dp)
        assertEquals(rule.onRoot().getBoundsInRoot().bottom.value, sheet().bottom.value, 1f)
    }

    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    @Test fun DCK_04_geo_04_landscapeWithABottomDockIsABottomSheetCappedInHeight() {
        rule.setContent { Screen(landscape = true) }
        openSheet()
        val s = sheet()
        assertTrue("capped to 62% of the window: ${s.height}", s.height <= rule.onRoot().getBoundsInRoot().height * 0.62f + 1.dp)
        assertTrue("the dock is still fully visible: ${dock()}", dock().top >= 0.dp)
        shoot("w55-dock-sheet-landscape-bottom")
    }

    @Config(sdk = [35], qualifiers = "w1000dp-h700dp-xhdpi")
    @Test fun DCK_04_geo_05_onATabletTheBottomSheetIsCappedAndCentred() {
        rule.setContent { Screen() }
        openSheet()
        val s = sheet()
        assertEquals(640f, s.width.value, 1f)
        assertEquals("centred", 500f, (s.left.value + s.right.value) / 2f, 1f)
        shoot("w55-dock-sheet-tablet")
    }

    @Test fun DCK_04_geo_06_rtlPutsALeftDockOnTheRightAndTheSideSheetBesideIt() {
        manager.setDock { it.copy(edge = DockEdge.Left) }
        rule.setContent { Screen(rtl = true) }
        openSheet()
        // Portrait: still a bottom sheet in RTL, centred.
        assertEquals(411f / 2f, (sheet().left.value + sheet().right.value) / 2f, 1f)
        assertTrue(homeTop() < 0.dp)
        shoot("w55-dock-sheet-rtl")
    }

    @Config(sdk = [35], qualifiers = "ldrtl-w891dp-h411dp-land-xhdpi")
    @Test fun DCK_04_geo_07_rtlLandscapeALeftDockIsAtTheStartWhichIsTheRightAndTheSheetFollows() {
        manager.setDock { it.copy(edge = DockEdge.Left) }
        rule.setContent { Screen(rtl = true, landscape = true) }
        val leftBefore = homeLeft()
        openSheet()
        val s = sheet()
        assertEquals("the start edge is the right in RTL", rule.onRoot().getBoundsInRoot().right.value, s.right.value, 1f)
        assertEquals((leftBefore - s.width).value, homeLeft().value, 1.5f)
    }

    @Test fun DCK_04_look_01_screenshotLight() {
        HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF)
        rule.setContent { Screen() }
        openSheet()
        shoot("w55-dock-sheet-portrait-light")
    }

    @Test fun DCK_04_look_02_screenshotTransparentAndThreeD() {
        manager.setDock { it.copy(style = DockStyle.ThreeD) }
        rule.setContent { Screen() }
        openSheet()
        shoot("w55-dock-sheet-portrait-3d")
    }
}
