package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ThemeVariant
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
 * CFG-21 (wave 40), wave 57 (settingsTabs): the settings in the real host: the tabs with every section up front, search across the tabs, a
 * section with switch, choice, slider and stepper rows, back navigation, deep links, the landscape rail, right-to-left, the screen reader's
 * headings, lists and tabs, the collapsing title. The tab behaviour itself (swipe, indicator, memory, keys) is SettingsTabsW57UiTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class SettingsOneUiUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private var dark by mutableStateOf(true)
    private val log = ArrayList<String>()
    private var closed = 0
    private var inputMode: androidx.compose.ui.input.InputModeManager? = null
    private var style by mutableStateOf("Rounded")
    private var plate by mutableStateOf(true)
    private var size by mutableStateOf(0.5f)
    private var count by mutableStateOf(3)

    private fun titles() = listOf("Home screen", "Look", "App drawer", "Radar", "Side panel", "System & sound", "Hidden apps", "About")

    private fun categories(): List<SettingCategory> = listOf(
        SettingCategory(
            "Home screen", Glyphs.Apps,
            (0 until 14).map { i -> SettingItem.Toggle("Home option $i", Glyphs.Apps, i % 2 == 0, "Hint $i") { v -> log += "home$i=$v" } } +
                SettingItem.Action("Edit home", Glyphs.Apps, "Add icons") { log += "edit" },
        ),
        SettingCategory(
            "Look", Glyphs.Palette,
            listOf(
                SettingItem.Choice("Icon shape", Glyphs.Apps, listOf("Rounded", "Square", "Circle"), style, { it }, hint = "The shape of every icon") { style = it; log += "shape=$it" },
                SettingItem.Toggle("Icon plate", Glyphs.Apps, plate) { plate = it; log += "plate=$it" },
                SettingItem.Slider("Glow intensity", Glyphs.Sliders, size, 0f..1f, "${kotlin.math.round(size * 100).toInt()}%") { size = it; log += "glow=$it" },
                SettingItem.Stepper("Recent apps shown", Glyphs.Apps, count, 1..9) { count = it; log += "recents=$it" },
                SettingItem.Action("Reset look", Glyphs.Close, "", confirm = "Reset?", confirmLabel = "Reset") { log += "reset" },
                SettingItem.Pane("Themes", Glyphs.Palette, "Import, export") { androidx.compose.material3.Text("THEMES PANE BODY") },
            ),
        ),
        SettingCategory("App drawer", Glyphs.Widget, listOf(SettingItem.Choice("Sort by", Glyphs.Apps, listOf("A-Z", "Recent"), "A-Z", { it }) { log += "sort=$it" })),
        SettingCategory("Radar", Glyphs.Radar, listOf(SettingItem.Toggle("Web results", Glyphs.Globe, true) {}, SettingItem.Toggle("Deep results", Glyphs.Search, true) {})),
        SettingCategory("Side panel", Glyphs.Bell, listOf(SettingItem.Toggle("Unread strip", Glyphs.Bell, true) {})),
        SettingCategory("System & sound", Glyphs.Gear, listOf(SettingItem.Toggle("Interface sounds", Glyphs.Sliders, true) {})),
        SettingCategory("Hidden apps", Glyphs.Apps, listOf(SettingItem.Pane("Hidden apps", Glyphs.Apps, "2 hidden") { androidx.compose.material3.Text("HIDDEN BODY") })),
        SettingCategory("About", Glyphs.Info, listOf(SettingItem.Action("Version", Glyphs.Info, "1.0") {})),
    )


    private fun show(initial: Int = -1, rtl: Boolean = false, font: Float = 1f, light: Boolean = false, cats: List<SettingCategory>? = null) {
        if (light) HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF)
        rule.setContent {
            inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
            val d = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, androidx.compose.ui.platform.LocalDensity provides Density(d.density, font)) {
                SettingsSpace(visible = true, categories = cats ?: categories(), initialCategory = initial, sounds = null, onClose = { closed++ }, modifier = Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.mainClock.advanceTimeBy(500); rule.waitForIdle() }

    private fun shoot(name: String): Bitmap {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    // ---- the root --------------------------------------------------------------------------------------------------------------

    private fun tab(t: String) { rule.onNodeWithTag(SettingsTabTags.tab(t), useUnmergedTree = true).performClick(); rule.mainClock.advanceTimeBy(700); rule.waitForIdle() }
    private fun listOf(t: String) = rule.onNodeWithTag(SettingsTabTags.list(t))

    @Test fun CFG_40_settingsHomeIsTheTitleTheSearchTheTabsAndTheFirstSection() {
        show()
        for (t in titles()) rule.onNodeWithTag(SettingsTabTags.tab(t), useUnmergedTree = true).assertExists()
        assertEquals("one tab per section", titles().size, rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("tab") { it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Tab }).fetchSemanticsNodes().size)
        rule.onNodeWithTag(OneUiTags.TITLE_LARGE).assertIsDisplayed()
        rule.onAllNodesWithTag(OneUiTags.TITLE_SMALL).assertCountEquals(0) // the small title only exists once the large one starts to collapse
        rule.onNodeWithTag(OneUiTags.SEARCH).assertIsDisplayed()
        rule.onNodeWithText("Home option 0").assertIsDisplayed() // the first section's rows, no tap needed
        rule.onAllNodesWithText("Dark · Rounded icons").assertCountEquals(0) // wave 57: no root list of categories with summary lines
    }

    @Test fun CFG_40_tappingATabShowsItsRowsAndBackClosesSettings() {
        show()
        tab("Look")
        rule.onNodeWithText("Icon shape").assertIsDisplayed()
        rule.onAllNodesWithText("Home option 0").assertCountEquals(0)
        back()
        assertEquals("from a tab, Back closes Settings", 1, closed)
    }

    @Test fun CFG_21_theSystemBackStepsOutOneLevelAtATimeAndThenClosesSettings() {
        show()
        tab("Look")
        rule.onNodeWithText("Themes").performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithText("THEMES PANE BODY").assertIsDisplayed()
        back(); rule.onAllNodesWithTag(OneUiTags.SUB_PAGE).assertCountEquals(0); rule.onNodeWithText("Icon shape").assertIsDisplayed()
        assertEquals(0, closed)
        back(); assertEquals(1, closed)
    }

    @Test fun CFG_21_theRootBackArrowClosesSettings() {
        show()
        rule.onNodeWithContentDescription("Close settings").performClick()
        assertEquals(1, closed)
    }

    // ---- search ----------------------------------------------------------------------------------------------------------------

    @Test fun CFG_41_searchFindsRowsAcrossTabsAndAHitSwitchesToItsTabOnTheRow() {
        show()
        rule.onNodeWithTag(OneUiTags.SEARCH).performTextInput("results")
        rule.waitForIdle()
        rule.onNodeWithText("Web results").assertIsDisplayed()
        rule.onNodeWithText("Deep results").assertIsDisplayed()
        rule.onAllNodesWithTag(SettingsTabTags.ROW).assertCountEquals(0) // the tabs give way to the results
        rule.onNodeWithText("Deep results").performClick(); rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        listOf("Radar").assertIsDisplayed()
        rule.onNode(isSelected() and hasText("Deep results", substring = true)).assertIsDisplayed()
        assertEquals(true, rule.onNodeWithTag(SettingsTabTags.tab("Radar"), useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    @Test fun CFG_21_searchWithNoMatchSaysSo() {
        show()
        rule.onNodeWithTag(OneUiTags.SEARCH).performTextInput("zzzz")
        rule.waitForIdle()
        rule.onNodeWithText("No settings match “zzzz”").assertIsDisplayed()
    }

    @Test fun CFG_21_searchFindsByTheHintToo() {
        show()
        rule.onNodeWithTag(OneUiTags.SEARCH).performTextInput("shape of every")
        rule.waitForIdle()
        rule.onNodeWithText("Icon shape").assertIsDisplayed()
    }

    // ---- a category page -------------------------------------------------------------------------------------------------------

    @Test fun CFG_21_aSwitchRowFlipsThroughItsHandlerAndIsAnnouncedWithItsState() {
        show(initial = 1)
        val row = rule.onNode(hasContentDescription("Icon plate, On"))
        row.assertIsDisplayed()
        assertEquals(androidx.compose.ui.state.ToggleableState.On, row.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState))
        assertEquals(androidx.compose.ui.semantics.Role.Switch, row.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        row.performClick(); rule.waitForIdle()
        assertTrue("plate=false" in log)
        rule.onNode(hasContentDescription("Icon plate, Off")).assertIsDisplayed()
    }

    @Test fun CFG_21_aChoiceRowShowsItsValueAndOpensARadioListThatAppliesAndReturns() {
        show(initial = 1)
        rule.onNode(hasContentDescription("Icon shape, Rounded")).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.SUB_PAGE).assertIsDisplayed()
        rule.onNodeWithText("The shape of every icon").assertIsDisplayed()
        val rounded = rule.onNode(hasContentDescription("Rounded") and androidx.compose.ui.test.hasClickAction())
        assertEquals(true, rounded.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNode(hasContentDescription("Square") and androidx.compose.ui.test.hasClickAction()).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        assertTrue("shape=Square" in log)
        rule.onAllNodesWithTag(OneUiTags.SUB_PAGE).assertCountEquals(0)
        rule.onNode(hasContentDescription("Icon shape, Square")).assertIsDisplayed()
    }

    @Test fun CFG_21_aSliderRowMovesThroughSetProgressAndShowsItsValue() {
        show(initial = 1)
        rule.onNodeWithText("50%").assertIsDisplayed()
        rule.onNode(hasContentDescription("Glow intensity, 50%")).performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
        rule.waitForIdle()
        assertTrue("slider reported 0.8: $log", log.any { it.startsWith("glow=") && kotlin.math.abs(it.removePrefix("glow=").toFloat() - 0.8f) < 0.001f })
        rule.onNodeWithText("80%").assertIsDisplayed()
    }

    @Test fun CFG_21_aStepperRowStepsWithItsButtonsAndThroughSetProgress() {
        show(initial = 1)
        rule.onNodeWithContentDescription("Next Recent apps shown").performClick(); rule.waitForIdle()
        assertEquals(4, count)
        rule.onNodeWithContentDescription("Previous Recent apps shown").performClick(); rule.onNodeWithContentDescription("Previous Recent apps shown").performClick(); rule.waitForIdle()
        assertEquals(2, count)
    }

    @Test fun CFG_21_aDestructiveActionAsksFirstAndBackAnswersNo() {
        show(initial = 1)
        rule.onNodeWithText("Reset look").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("gc.confirm.text").assertIsDisplayed()
        assertFalse("reset" in log)
        back()
        assertFalse("reset" in log)
        rule.onNodeWithText("Reset look").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Reset").performClick(); rule.waitForIdle()
        assertTrue("reset" in log)
    }

    @Test fun CFG_21_aPaneRowOpensItsContentOnAPageWithTheSameChrome() {
        show(initial = 1)
        rule.onNodeWithText("Themes").performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithText("THEMES PANE BODY").assertIsDisplayed()
        rule.onNodeWithTag(OneUiTags.BACK).assertIsDisplayed()
    }

    @Test fun CFG_41_aDeepLinkOpensItsTabAndBackClosesSettings() {
        show(initial = 6)
        listOf("Hidden apps").assertIsDisplayed()
        assertEquals(true, rule.onNodeWithTag(SettingsTabTags.tab("Hidden apps"), useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        back()
        assertEquals(1, closed)
    }

    @Test fun CFG_21_aRadarResultLandsOnItsRowAndLightsIt() {
        SettingsFocus.request(9)
        show(initial = 0)
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        rule.onNode(isSelected() and hasText("Home option 9", substring = true)).assertIsDisplayed()
    }

    @Test fun CFG_41_aDragUpTakesTheTitleAwayAndPinsTheTabsUnderTheBar() {
        show(initial = 0)
        rule.onNodeWithTag(OneUiTags.TITLE_LARGE).assertIsDisplayed()
        repeat(3) { listOf("Home screen").performTouchInput { swipeUp() }; rule.mainClock.advanceTimeBy(400) }
        rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.TITLE_LARGE).assertIsNotDisplayed()
        rule.onNodeWithTag(OneUiTags.TITLE_SMALL).assertIsDisplayed()
        val bar = rule.onNodeWithTag(OneUiTags.BACK).getUnclippedBoundsInRoot()
        val tabs = rule.onNodeWithTag(SettingsTabTags.ROW).getUnclippedBoundsInRoot()
        assertTrue("the tab row is pinned right under the bar: ${tabs.top} vs ${bar.bottom}", tabs.top.value >= bar.bottom.value - 1f && tabs.top.value <= bar.bottom.value + 12f)
        rule.onNodeWithTag(SettingsTabTags.ROW).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToNodeSafe(text: String) {
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text))
        rule.waitForIdle()
    }

    @Test fun CFG_21_aPageSlidesInFromTheEndAndUnderReducedMotionItOnlyFades() {
        show(initial = 1)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Themes").performClick()
        rule.mainClock.advanceTimeBy(80)
        val sliding = rule.onNodeWithTag(OneUiTags.SUB_PAGE).getUnclippedBoundsInRoot().left.value
        assertTrue("the page is still arriving from the end after 80 ms: left=$sliding", sliding > 20f)
        rule.mainClock.advanceTimeBy(600)
        assertEquals(0f, rule.onNodeWithTag(OneUiTags.SUB_PAGE).getUnclippedBoundsInRoot().left.value, 0.5f)
    }

    @Test fun CFG_21_underReducedMotionTheRowPageArrivesInPlaceWithoutSliding() {
        com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
        try {
            show(initial = 1)
            rule.mainClock.autoAdvance = false
            rule.onNodeWithText("Themes").performClick()
            rule.mainClock.advanceTimeBy(40)
            assertEquals("no slide: the page is in place from the first frames", 0f, rule.onNodeWithTag(OneUiTags.SUB_PAGE).getUnclippedBoundsInRoot().left.value, 0.5f)
        } finally { com.bos.sphere.core.design.ReducedMotionTesting.force(null) }
    }

    @Test fun CFG_21_inRightToLeftThePageSlidesInFromTheLeftEnd() {
        show(rtl = true, initial = 1)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Themes").performClick()
        rule.mainClock.advanceTimeBy(80)
        val left = rule.onNodeWithTag(OneUiTags.SUB_PAGE).getUnclippedBoundsInRoot().left.value
        assertTrue("the end of a right-to-left window is its left side: left=$left", left < -20f)
    }

    @Test fun CFG_13_escapeOnAKeyboardStepsBackOnePage() {
        show(initial = 1)
        rule.onNodeWithText("Themes").performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.SUB_PAGE).assertIsDisplayed()
        rule.onRoot().performKeyInput { keyDown(androidx.compose.ui.input.key.Key.Escape); keyUp(androidx.compose.ui.input.key.Key.Escape) }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onAllNodesWithTag(OneUiTags.SUB_PAGE).assertCountEquals(0)
        assertEquals(0, closed)
    }

    @Test fun CFG_13_tabWalksTheRowsInOrderAndEnterFlipsTheFocusedSwitch() {
        show(initial = 1)
        rule.runOnIdle { inputMode!!.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) } // a hardware key puts the window in keyboard mode, where clickable rows take focus
        val seen = ArrayList<String>()
        var landed = false
        repeat(28) { // wave 57: the search field and the eight tabs come first, then the rows
            rule.onRoot().performKeyInput { keyDown(androidx.compose.ui.input.key.Key.Tab); keyUp(androidx.compose.ui.input.key.Key.Tab) }
            rule.waitForIdle()
            val d = rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
            seen += d
            if (!landed && d.any { it == "Icon plate, On" }) {
                landed = true
                rule.onRoot().performKeyInput { keyDown(androidx.compose.ui.input.key.Key.Enter); keyUp(androidx.compose.ui.input.key.Key.Enter) }
                rule.waitForIdle()
            }
        }
        assertTrue("Tab reached the switch row: $seen", landed)
        assertTrue("Enter flipped it: $log", "plate=false" in log)
        assertTrue("rows are visited top to bottom: $seen", seen.indexOf("Icon shape, Rounded") < seen.indexOf("Icon plate, On").let { if (it < 0) Int.MAX_VALUE else it })
    }

    // ---- accessibility ---------------------------------------------------------------------------------------------------------

    @Test fun CFG_21_theLargeTitleAndEverySectionTitleAreHeadingsAndCardsAreLists() {
        val sections = listOf(
            SettingCategory("Look", Glyphs.Palette, listOf(SettingItem.Toggle("Light / dark", Glyphs.Apps, true) {}, SettingItem.Toggle("Icon shape", Glyphs.Apps, true) {}), headers = mapOf(0 to "Theme", 1 to "Icons")),
        )
        show(initial = 0, cats = sections)
        val big = rule.onNodeWithTag(OneUiTags.TITLE_LARGE).fetchSemanticsNode().config
        assertTrue(big.contains(SemanticsProperties.Heading))
        val headings = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("heading") { it.config.contains(SemanticsProperties.Heading) }).fetchSemanticsNodes().size
        assertTrue("title plus the Theme and Icons section titles: $headings", headings >= 3)
        val lists = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("list") { it.config.getOrNull(SemanticsProperties.CollectionInfo)?.let { c -> c.rowCount > 0 } == true }).fetchSemanticsNodes().size
        assertTrue("each card is a list: $lists", lists >= 2)
    }

    @Test fun CFG_41_theTabRowIsATabListAndTheTitleIsAHeading() {
        show()
        assertTrue(rule.onNodeWithTag(OneUiTags.TITLE_LARGE).fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
        val info = rule.onNodeWithTag(SettingsTabTags.ROW).fetchSemanticsNode().config.getOrNull(SemanticsProperties.CollectionInfo)
        assertEquals("a collection of one row of every tab", titles().size, info?.columnCount)
        val look = rule.onNodeWithTag(SettingsTabTags.tab("Look"), useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals(androidx.compose.ui.semantics.Role.Tab, look.getOrNull(SemanticsProperties.Role))
        assertEquals("Tab, 2 of ${titles().size}: the second place", 1, look.getOrNull(SemanticsProperties.CollectionItemInfo)?.columnIndex)
        assertEquals("the words, not the capitals", "Look", look.getOrNull(SemanticsProperties.ContentDescription)?.single())
        assertEquals(false, look.getOrNull(SemanticsProperties.Selected))
    }

    @Test fun CFG_21_everyClickableTargetIsAtLeast48dpTall() {
        show(initial = 1)
        val tooSmall = rule.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().filter { it.size.height < 96 /* xhdpi is 2x: 48 dp = 96 px */ }
        assertTrue("clickable targets under 48dp: ${tooSmall.map { it.size to it.config.toString().take(200) }}", tooSmall.isEmpty())
    }

    // ---- layout ----------------------------------------------------------------------------------------------------------------

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_41_onAWideLandscapeWindowTheTabsAreARailAtTheStartAndTheSectionFillsTheEnd() {
        show()
        rule.onNodeWithTag(OneUiTags.LEFT_PANE).assertIsDisplayed()
        rule.onNodeWithTag(OneUiTags.RIGHT_PANE).assertIsDisplayed()
        val left = rule.onNodeWithTag(OneUiTags.LEFT_PANE).getUnclippedBoundsInRoot()
        val right = rule.onNodeWithTag(OneUiTags.RIGHT_PANE).getUnclippedBoundsInRoot()
        assertEquals("the start pane takes about 40 percent", 0.4f, left.width.value / (left.width.value + right.width.value), 0.02f)
        rule.onNodeWithTag(SettingsTabTags.RAIL).assertExists()
        rule.onAllNodesWithTag(SettingsTabTags.ROW).assertCountEquals(0) // no second tab row: the rail is the tabs
        listOf("Home screen").assertIsDisplayed() // the first section is shown at the end without tapping, under its own title
        val y = { t: String -> rule.onNodeWithTag(SettingsTabTags.tab(t), useUnmergedTree = true).getUnclippedBoundsInRoot().top.value }
        assertTrue("the rail lists the tabs top to bottom", y("Home screen") < y("Look"))
        tab("Look")
        rule.onNodeWithText("Icon shape").assertIsDisplayed()
        rule.onNodeWithTag(SettingsTabTags.RAIL).assertExists() // the rail is still there
        listOf("Look").performScrollToNode(hasText("Themes")); rule.waitForIdle()
        rule.onNodeWithText("Themes").performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithText("THEMES PANE BODY").assertIsDisplayed()
        assertTrue("a row's page opens inside the end pane", rule.onNodeWithTag(OneUiTags.SUB_PAGE).getUnclippedBoundsInRoot().left.value >= right.left.value - 1f)
        back(); rule.onAllNodesWithTag(OneUiTags.SUB_PAGE).assertCountEquals(0)
        back(); assertEquals("back from a section closes settings", 1, closed)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun CFG_21_inPortraitTheRootIsSinglePane() {
        show()
        rule.onAllNodesWithTag(OneUiTags.RIGHT_PANE).assertCountEquals(0)
    }

    @Test fun CFG_41_inRightToLeftTheBackArrowSitsAtTheRightAndTheTabOrderMirrors() {
        show(rtl = true)
        val back = rule.onNodeWithTag(OneUiTags.BACK).getUnclippedBoundsInRoot()
        assertTrue("back arrow at the right edge: $back", back.left.value > 411f / 2)
        val x = { t: String -> rule.onNodeWithTag(SettingsTabTags.tab(t), useUnmergedTree = true).getUnclippedBoundsInRoot().left.value }
        assertTrue("the first tab is at the right in right-to-left", x("Home screen") > x("Look"))
    }

    @Test fun CFG_21_atFontScaleTwoTheRowsWrapAndStayReadable() {
        show(font = 2f)
        rule.onNodeWithTag(SettingsTabTags.tab("Home screen"), useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Home option 0").assertExists()
        val tabs = rule.onNodeWithTag(SettingsTabTags.ROW).getUnclippedBoundsInRoot()
        assertTrue("the tab row keeps its 48 dp", tabs.height.value >= 47f)
    }

    // ---- screenshots ----------------------------------------------------------------------------------------------------------- (the real six tabs are shot in the app: SettingsTabsW57ShotsTest)

    @Test fun CFG_21_screenshotSearchDarkPortrait() { show(); rule.onNodeWithTag(OneUiTags.SEARCH).performTextInput("icon"); rule.waitForIdle(); shoot("cfg21-search-dark-portrait") }
    @Test fun CFG_21_screenshotRadioPageDarkPortrait() { show(initial = 1); rule.onNode(hasContentDescription("Icon shape, Rounded")).performClick(); rule.mainClock.advanceTimeBy(600); shoot("cfg21-radio-dark-portrait") }
}
