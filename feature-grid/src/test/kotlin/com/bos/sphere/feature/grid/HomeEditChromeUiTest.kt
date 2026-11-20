package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.view.KeyEvent as AKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.click
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import androidx.compose.ui.input.key.Key
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * HOM-07, HOM-12 (wave 45, editChrome): the single-page home edit mode's chrome in isolation (top bar, hint, tray, Cancel rule, fixed-chrome shield) over a
 * stand-in page. Robolectric: no real touch pipeline, no compositor, no device; the real HomeScreen wiring is in the app module's EditChromeW45AppTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class HomeEditChromeUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @After fun reset() = ReducedMotionTesting.force(null)

    private class Calls {
        var undo = 0; var redo = 0; var done = 0; var cancel = 0; var apps = 0; var widgets = 0; var folders = 0; var page = 0; var editor = 0
        val said = ArrayList<String>()
    }
    private var calls = Calls()
    private var rtl = false
    private var hasChanges by mutableStateOf(false)
    private var showHint by mutableStateOf(true)
    private var showAddPage by mutableStateOf(true)
    private var undoCount by mutableIntStateOf(0)
    private var redoCount by mutableIntStateOf(0)
    private var touch by mutableIntStateOf(0)
    private var pageIndex by mutableIntStateOf(1)
    private var behindClicks = 0
    private var progressValue = 1f

    private fun content(active: Boolean = true, progress: State<Float>? = null, keyboard: Boolean = false) {
        calls = Calls(); behindClicks = 0
        rule.setContent {
            if (keyboard) { val im = androidx.compose.ui.platform.LocalInputModeManager.current; androidx.compose.runtime.SideEffect { im.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) } }
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                val page = rememberEditChromePage(size)
                val prog = progress ?: remember { mutableStateOf(1f) }
                val chromeState = remember { HomeEditChromeState() }
                Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }) {
                    EditChromeBackdrop(prog, page)
                    Box(Modifier.fillMaxSize().editCardLayer { EditChromeGeometry.frame(page, prog.value, ReducedMotionTesting.forced?.reduced == true) }.testTag("page")) { StandInPage() }
                    // a clickable at the page's top-left corner stands for a hotspot
                    Box(Modifier.fillMaxSize().padding0(page)) { Box(Modifier.size(40.dp).testTag("hotspot").clickable { behindClicks++ }) }
                    Box(Modifier.align(Alignment.Center).size(60.dp).testTag("item").clickable { behindClicks += 100 })
                    EditFixedChromeShield(page)
                    HomeEditChrome(
                        visible = active, progress = prog, page = page, pageIndex = pageIndex, pageCount = 4,
                        undoCount = undoCount, redoCount = redoCount, canUndo = undoCount > 0, canRedo = redoCount > 0,
                        hasChanges = hasChanges, showHint = showHint, showAddPage = showAddPage, trayBottomDp = 8f, state = chromeState,
                        onUndo = { calls.undo++ }, onRedo = { calls.redo++ }, onDone = { calls.done++ }, onCancel = { calls.cancel++ },
                        onApps = { calls.apps++ }, onWidgets = { calls.widgets++ }, onFolders = { calls.folders++ }, onAddPage = { calls.page++ },
                        onOpenPageEditor = { calls.editor++ }, touchSignal = touch, announce = { calls.said += it },
                    )
                }
            }
        }
    }

    private fun Modifier.padding0(page: EditChromeGeometry.PageFrame): Modifier = this.absoluteOffset { androidx.compose.ui.unit.IntOffset(page.rect.left.toInt(), page.rect.top.toInt()) }

    @Composable private fun StandInPage() {
        val g = GridGeometry.compute(1000f, 2000f, 4, 6, 0.5f, insetX = 0.05f, insetTop = 0.12f, insetBottom = 0.22f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0B10), Color(0xFF1B2347))))) {
            EditGridDotsLayer(g)
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 1000f
                for (i in 0 until 9) {
                    val col = i % 4; val row = i / 4
                    val cx = (g.originX + col * g.pitchX + g.pitchX / 2) * s; val cy = (g.originY + row * g.pitchY + g.pitchY / 2) * s
                    drawRoundRect(listOf(Color(0xFFE8564A), Color(0xFF4A90E2), Color(0xFF50C878), Color(0xFFF5A623))[i % 4], Offset(cx - 70 * s, cy - 70 * s), androidx.compose.ui.geometry.Size(140 * s, 140 * s), androidx.compose.ui.geometry.CornerRadius(32 * s))
                }
            }
        }
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(AColor.BLACK); view.draw(it) } }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    // ---- bar

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_theTopBarHasCancelTitleUndoRedoAndDoneAt48dp() {
        undoCount = 3; redoCount = 0; showHint = false
        content()
        rule.onNodeWithTag(EditChromeTags.BAR).assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        rule.onNodeWithTag(EditChromeTags.CANCEL).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).assertHasClickAction()
        rule.onNodeWithTag(EditChromeTags.DONE).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).assertHasClickAction()
        rule.onNodeWithTag(EditChromeTags.UNDO).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag(EditChromeTags.REDO).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithText("Editing page 2 of 4").assertIsDisplayed()
        val bar = rule.onNodeWithTag(EditChromeTags.BAR).getBoundsInRoot()
        assertEquals(56f, bar.height.value, 0.5f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_thereIsExactlyOneDone() {
        content()
        rule.onAllNodesWithText("Done").assertCountEquals(1)
        rule.onAllNodesWithText("Editing page 2 of 4").assertCountEquals(1)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_theTitleIsAHeadingAndTheBarIsATraversalGroupWithLabelledButtons() {
        content()
        val title = rule.onNodeWithTag(EditChromeTags.TITLE).fetchSemanticsNode()
        assertTrue("heading", title.config.contains(SemanticsProperties.Heading))
        val bar = rule.onNodeWithTag(EditChromeTags.BAR).fetchSemanticsNode()
        assertEquals("Edit toolbar", bar.config.getOrNull(SemanticsProperties.PaneTitle))
        assertTrue(bar.config.getOrNull(SemanticsProperties.IsTraversalGroup) == true)
        // TalkBack focus starts on the title: it has the lowest traversal index in the toolbar
        assertTrue((title.config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f) < (bar.config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f))
        rule.onNodeWithText("Cancel").assertHasClickAction(); rule.onNodeWithText("Done").assertHasClickAction()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_undoAndRedoShowTheirCountsAndTheDisabledOnesAreDimmedAndInert() {
        undoCount = 3; redoCount = 0
        content()
        rule.onNodeWithContentDescription("Undo (3)").assertIsEnabled().performClick()
        rule.onNodeWithContentDescription("Redo").assertIsNotEnabled()
        assertEquals(1, calls.undo); assertEquals(0, calls.redo)
        redoCount = 2; rule.waitForIdle()
        rule.onNodeWithContentDescription("Redo (2)").assertIsEnabled().performClick()
        assertEquals(1, calls.redo)
        assertEquals(0.38f, EditChromeGeometry.DISABLED_ALPHA, 0f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_doneCommitsAndSaysSaved() {
        content()
        rule.onNodeWithText("Done").performClick()
        assertEquals(1, calls.done); assertEquals(0, calls.cancel)
        assertTrue(calls.said.contains("Saved"))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_entryIsAnnouncedWithThePageAndTheCount() {
        content()
        rule.waitForIdle()
        assertEquals(listOf("Editing home page 2 of 4"), calls.said)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_enterOnDoneCommits() {
        content(keyboard = true)
        rule.onNodeWithTag(EditChromeTags.DONE).requestFocus()
        rule.onNodeWithTag(EditChromeTags.DONE).performKeyInput { pressKey(Key.Enter) }
        assertTrue("Enter on Done commits", calls.done >= 1)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_theTitleOpensThePageEditor() {
        content()
        rule.onNodeWithTag(EditChromeTags.TITLE).performClick()
        assertEquals(1, calls.editor)
    }

    // ---- hint

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_theHintIsOneLineUnderTheBarAndFadesAfterFourSeconds() {
        showHint = true
        content()
        rule.onNodeWithText("Drag to move. Tap an item to resize or change it.").assertIsDisplayed()
        rule.onAllNodesWithText("Drag to move. Tap an item to resize or change it.").assertCountEquals(1)
        val bar = rule.onNodeWithTag(EditChromeTags.BAR).getBoundsInRoot(); val hint = rule.onNodeWithTag(EditChromeTags.HINT).getBoundsInRoot()
        assertTrue("under the bar", hint.top >= bar.bottom - 1.dp)
        rule.mainClock.advanceTimeBy(3500); rule.onNodeWithText("Drag to move. Tap an item to resize or change it.").assertExists()
        rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        rule.onNodeWithTag(EditChromeTags.HINT).assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_theHintGoesOnTheFirstTouch() {
        showHint = true
        content()
        rule.onNodeWithTag(EditChromeTags.HINT).assertExists()
        touch++; rule.mainClock.advanceTimeBy(400); rule.waitForIdle()
        rule.onNodeWithTag(EditChromeTags.HINT).assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_noHintAfterTheFirstThreeEntries() {
        showHint = false
        content()
        rule.onNodeWithTag(EditChromeTags.HINT).assertDoesNotExist()
        rule.onAllNodesWithText("Drag to move. Tap an item to resize or change it.").assertCountEquals(0)
    }

    // ---- tray

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_theTrayChipsOpenTheAppsWidgetsAndFoldersPickersAndAddAPage() {
        content()
        rule.onNodeWithTag(EditChromeTags.TRAY).assertIsDisplayed().assertHeightIsAtLeast(72.dp)
        listOf(EditChromeTags.CHIP_APPS, EditChromeTags.CHIP_WIDGETS, EditChromeTags.CHIP_FOLDERS, EditChromeTags.CHIP_PAGE).forEach { rule.onNodeWithTag(it).assertHeightIsAtLeast(48.dp).performClick() }
        assertEquals(1, calls.apps); assertEquals(1, calls.widgets); assertEquals(1, calls.folders); assertEquals(1, calls.page)
        rule.onNodeWithContentDescription("Add page").assertHasClickAction()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_thePageChipIsOnlyThereWhenPagesCanBeAdded() {
        showAddPage = false
        content()
        rule.onNodeWithTag(EditChromeTags.CHIP_PAGE).assertDoesNotExist()
        rule.onNodeWithTag(EditChromeTags.CHIP_APPS).assertExists()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_noBottomPillsOfTheOldBarRemain() {
        content()
        rule.onAllNodesWithText("Add page").assertCountEquals(0) // the visible chip says "+ Page"; the old pill said "Add page"
        rule.onAllNodesWithText("Undo").assertCountEquals(0); rule.onAllNodesWithText("Redo").assertCountEquals(0)
        rule.onAllNodesWithText("Editing home: drag items to rearrange, long-press for more").assertCountEquals(0)
    }

    // ---- Cancel

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_cancelWithNoChangesLeavesAtOnceWithoutAsking() {
        hasChanges = false
        content()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(1, calls.cancel)
        rule.onAllNodesWithText("Discard changes?").assertCountEquals(0)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_cancelAfterChangesAsksFirstAndKeepEditingChangesNothing() {
        hasChanges = true
        content()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertEquals(0, calls.cancel)
        rule.onNodeWithText("Keep editing").performClick(); rule.waitForIdle()
        assertEquals(0, calls.cancel)
        rule.onAllNodesWithText("Discard changes?").assertCountEquals(0)
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Discard").performClick(); rule.waitForIdle()
        assertEquals(1, calls.cancel)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_systemBackIsCancelWithTheSameRule() {
        hasChanges = false
        content()
        back()
        assertEquals("nothing changed: Back leaves at once", 1, calls.cancel)
        hasChanges = true; rule.waitForIdle()
        back()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertEquals("changed: Back asks first", 1, calls.cancel)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_escapeIsCancelWithTheSameRule() {
        hasChanges = false
        content(keyboard = true)
        rule.onNodeWithTag(EditChromeTags.CANCEL).requestFocus() // keys go to the focused control and bubble up to the chrome
        rule.onNodeWithTag(EditChromeTags.CANCEL).performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, calls.cancel)
    }

    // ---- fixed chrome

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_aHotspotAtThePageCornerCannotBeTappedWhileEditingButTheRestOfThePageStaysLive() {
        showHint = false
        content()
        rule.onNodeWithTag("hotspot").performTouchInput { click(Offset(10f, 10f)) }
        assertEquals("the shield swallows the corner", 0, behindClicks)
        rule.onNodeWithTag("item").performClick()
        assertEquals("an item in the middle of the page is still live", 100, behindClicks)
        assertEquals(0.40f, EditChromeGeometry.FIXED_DIM, 0f); assertEquals(0.70f, EditChromeGeometry.DOCK_DIM, 0f)
    }

    // ---- entry and exit

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_thePageEndsAtEightyEightPercentOfTheWindow() {
        content()
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val page = rule.onNodeWithTag("page").getBoundsInRoot(); val root = rule.onRoot().getBoundsInRoot()
        assertEquals(root.width.value * 0.88f, page.width.value, 1.5f)
        assertEquals(root.height.value * 0.88f, page.height.value, 1.5f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_theEntryAnimatesFromFullSizeAndReachesTheEndState() {
        rule.mainClock.autoAdvance = false
        var active by mutableStateOf(false)
        rule.setContent {
            val prog = rememberEditChromeProgress(active)
            var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            val page = rememberEditChromePage(size)
            Box(Modifier.fillMaxSize().onSizeChanged { size = it }.editCardLayer { EditChromeGeometry.frame(page, prog.value, false) }.testTag("page")) { Box(Modifier.fillMaxSize().background(Color.DarkGray)) }
        }
        rule.mainClock.advanceTimeBy(50)
        assertEquals(411f, rule.onNodeWithTag("page").getBoundsInRoot().width.value, 1.5f)
        active = true; rule.waitForIdle(); rule.mainClock.advanceTimeBy(150)
        val mid = rule.onNodeWithTag("page").getBoundsInRoot().width.value
        assertTrue("part way after 150 ms: $mid", mid < 411f - 2f && mid > 411f * 0.88f + 2f)
        rule.mainClock.advanceTimeBy(400)
        assertEquals(411f * 0.88f, rule.onNodeWithTag("page").getBoundsInRoot().width.value, 1.5f)
        active = false; rule.waitForIdle(); rule.mainClock.advanceTimeBy(600)
        assertEquals("Done animates back to scale 1.0", 411f, rule.onNodeWithTag("page").getBoundsInRoot().width.value, 1.5f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_withReducedMotionThePageIsAtRestAfterTheShortFade() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        rule.mainClock.autoAdvance = false
        var active by mutableStateOf(false)
        rule.setContent {
            val prog = rememberEditChromeProgress(active)
            var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            val page = rememberEditChromePage(size)
            Box(Modifier.fillMaxSize().onSizeChanged { size = it }.editCardLayer { EditChromeGeometry.frame(page, prog.value, true) }.testTag("page")) { Box(Modifier.fillMaxSize().background(Color.DarkGray)) }
        }
        active = true; rule.waitForIdle(); rule.mainClock.advanceTimeBy(60)
        assertEquals("no scale animation: the page is at rest from the first frame", 411f * 0.88f, rule.onNodeWithTag("page").getBoundsInRoot().width.value, 1.5f)
        assertEquals(120, EditChromeGeometry.REDUCED_MS)
    }

    // ---- RTL, landscape, screenshots

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_inRightToLeftCancelIsOnTheRightAndDoneOnTheLeft() {
        rtl = true; showHint = false
        content()
        val cancel = rule.onNodeWithTag(EditChromeTags.CANCEL).getBoundsInRoot(); val done = rule.onNodeWithTag(EditChromeTags.DONE).getBoundsInRoot()
        assertTrue("Cancel at the start (right)", cancel.left > done.left)
        rtl = false
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_12_landscapeKeepsTheBarAndTheTrayOnScreenAndTheTitleReadable() {
        showHint = false
        content()
        rule.onNodeWithTag(EditChromeTags.BAR).assertIsDisplayed(); rule.onNodeWithTag(EditChromeTags.TRAY).assertIsDisplayed()
        rule.onNodeWithText("Editing page 2 of 4").assertIsDisplayed()
        val tray = rule.onNodeWithTag(EditChromeTags.TRAY).getBoundsInRoot()
        assertTrue("tray inside the window", tray.bottom.value <= 411.5f)
        rule.mainClock.advanceTimeBy(500)
        val page = rule.onNodeWithTag("page").getBoundsInRoot()
        assertTrue("landscape page is smaller than 0.88 of the height: ${page.height}", page.height.value < 411f * 0.88f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_screenshotPortraitEntryWithHint() {
        undoCount = 3; hasChanges = true; showHint = true; rtl = false
        content()
        shoot("home_edit_chrome_portrait_hint")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_12_screenshotPortraitEntryNoHint() {
        undoCount = 0; hasChanges = false; showHint = false; rtl = false
        content()
        shoot("home_edit_chrome_portrait")
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_12_screenshotLandscapeEntryWithHint() {
        undoCount = 1; redoCount = 1; hasChanges = true; showHint = true; rtl = false
        content()
        shoot("home_edit_chrome_landscape_hint")
    }
}
