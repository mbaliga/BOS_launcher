package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Wave 46 (settingsPages), CFG-21: one presentation per option set. A choice with preview cards shows them inline and nothing else (no
 * chevron, no page, no caption repeating the value); a choice without cards opens a full page of radio rows (never a dialog); a multi
 * choice is a full page of checkbox rows; the large title sits in the vertical centre of its band; screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class SettingsPagesW46Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); L10n.setLanguage("en") }

    private val log = ArrayList<String>()
    private var left by mutableStateOf("Comms")
    private var theme by mutableStateOf("Dark")
    private var shape by mutableStateOf("Rounded")
    private var closed = 0

    private fun categories(): List<SettingCategory> = listOf(
        SettingCategory("Home screen", Glyphs.Apps, listOf(SettingItem.Toggle("Home option", Glyphs.Apps, true) {})),
        SettingCategory(
            "Comms", Glyphs.Bell,
            listOf(
                SettingItem.Choice(
                    "Left page", Glyphs.Bell, listOf("Off", "Comms", "A widget"), left, { it },
                    hint = "One swipe right from the first page: your notifications, or any app's widget full-screen.",
                    preview = { OneUiVisualChoice(listOf("Off", "Comms", "A widget"), left, { left = it; log += "left=$it" }, { it }, { SettingsArtHome.leftPage.art(com.bos.sphere.core.data.LeftPage.Off) }, tag = "t.left") },
                ) { left = it },
                SettingItem.Choice(
                    "Comms theme", Glyphs.Moon, listOf("Dark", "Light", "Follow the phone"), theme, { it },
                    hint = "Dark suits the rest of the launcher.",
                    preview = { OneUiVisualChoice(listOf("Dark", "Light", "Follow the phone"), theme, { theme = it; log += "theme=$it" }, { it }, { SettingsArtHome.leftPage.art(com.bos.sphere.core.data.LeftPage.Off) }, tag = "t.theme") },
                ) { theme = it },
                SettingItem.Choice("Icon shape", Glyphs.Apps, listOf("Rounded", "Square", "Circle", "Squircle"), shape, { it }, hint = "The shape of every icon") { shape = it; log += "shape=$it" },
                SettingItem.Toggle("Unread strip", Glyphs.Bell, true) {},
            ),
        ),
    )

    private fun show(initial: Int = -1) {
        rule.setContent { SettingsSpace(visible = true, categories = categories(), initialCategory = initial, sounds = null, onClose = { closed++ }, modifier = Modifier.fillMaxSize()) }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    /** Wave 57: the Comms section's list scrolled to its Icon shape row (two rails sit above it). */
    private fun toIconShape() { rule.onNodeWithTag(SettingsTabTags.list("Comms")).performScrollToNode(hasContentDescription("Icon shape, Rounded")); rule.waitForIdle() }

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.mainClock.advanceTimeBy(500); rule.waitForIdle() }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 2000)
    }

    // ---- a row with cards: the cards are the control ------------------------------------------------------------------------

    @Test fun CFG_21_w46_aRowWithCardsHasNoChevronNoPageAndNoCaptionRepeatingTheValue() {
        show(initial = 1)
        val rail = hasTestTag(OneUiTags.RAIL)
        rule.onAllNodesWithTag(OneUiTags.RAIL).assertCountEquals(2)
        // the title row of the rail is not a button: no click action, so no page can open from it
        val head = rule.onNode(hasContentDescription("Left page, Comms"))
        assertNull("the title row of a row with cards does not click", head.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
        // the only clickable things inside the first rail are its three cards
        val clicks = rule.onAllNodes(hasClickAction() and hasAnyAncestor(rail)).fetchSemanticsNodes().size
        assertEquals("three cards, nothing else to tap (no chevron)", 6, clicks) // two rails of three cards
        // the picked value is on its card once and is not repeated under the cards
        rule.onAllNodes(androidx.compose.ui.test.hasText("Comms") and hasAnyAncestor(rail), useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodes(androidx.compose.ui.test.hasText("Dark") and hasAnyAncestor(rail), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun CFG_21_w46_tappingTheTitleOfARowWithCardsOpensNothingAndACardApplies() {
        show(initial = 1)
        rule.onNode(hasContentDescription("Left page, Comms")).performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag(OneUiTags.SUB_PAGE).assertCountEquals(0)
        rule.onNodeWithTag(OneUiTags.ROOT).assertIsDisplayed()
        rule.onAllNodesWithText("Off", useUnmergedTree = true)[0].performClick(); rule.waitForIdle()
        assertEquals(listOf("left=Off"), log)
        rule.onAllNodesWithTag(OneUiTags.SUB_PAGE).assertCountEquals(0)
    }

    // ---- a row without cards: a full page, never a dialog ---------------------------------------------------------------

    @Test fun CFG_21_w46_aPlainChoiceShowsItsValueInGreyAndOpensAFullPageNotADialog() {
        show(initial = 1); toIconShape()
        rule.onNode(hasContentDescription("Icon shape, Rounded")).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.SUB_PAGE).assertIsDisplayed()
        rule.onAllNodesWithTag(OneUiDialogTags.DIALOG).assertCountEquals(0)
        rule.onNodeWithTag(OneUiTags.TITLE_LARGE).assertIsDisplayed()
        rule.onNodeWithTag(OneUiTags.SUBTITLE).assertIsDisplayed() // the one-line explanation under the title
        rule.onNodeWithTag(OneUiTags.BACK).assertIsDisplayed()
        assertEquals(true, rule.onNode(hasContentDescription("Rounded") and hasClickAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNode(hasContentDescription("Circle") and hasClickAction()).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        assertEquals(listOf("shape=Circle"), log)
        rule.onAllNodesWithTag(OneUiTags.SUB_PAGE).assertCountEquals(0)
        rule.onNode(hasContentDescription("Icon shape, Circle")).assertIsDisplayed()
    }

    @Test fun CFG_21_w46_backFromAChoicePageReturnsWithoutChangingIt() {
        show(initial = 1); toIconShape()
        rule.onNode(hasContentDescription("Icon shape, Rounded")).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        back()
        rule.onAllNodesWithTag(OneUiTags.SUB_PAGE).assertCountEquals(0)
        rule.onNodeWithTag(OneUiTags.ROOT).assertIsDisplayed()
        assertTrue(log.isEmpty())
        assertEquals(0, closed)
    }

    @Test fun CFG_21_w46_aMultiChoiceIsAFullPageOfCheckboxRowsWithDone() {
        var got: Set<String>? = null
        rule.setContent { OneUiCheckPage("Radar actions", listOf("A", "B", "C"), setOf("A"), { it }, { got = it }, { log += "dismiss" }, min = 1, body = "Pinned actions show in Radar.") }
        rule.waitForIdle()
        rule.onAllNodesWithTag(OneUiDialogTags.PAGE).assertCountEquals(1)
        rule.onAllNodesWithTag(OneUiDialogTags.DIALOG).assertCountEquals(0)
        rule.onNodeWithTag(OneUiTags.TITLE_LARGE).assertIsDisplayed()
        rule.onNodeWithTag(OneUiTags.SUBTITLE).assertIsDisplayed()
        assertEquals(true, rule.onNodeWithTag(OneUiDialogTags.option(0)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithTag(OneUiDialogTags.option(2)).performClick()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick()
        assertEquals(setOf("A", "C"), got)
    }

    @Test fun CFG_21_w46_theBackArrowOfAPickerPageLeavesWithoutApplying() {
        var got: Set<String>? = null
        rule.setContent { OneUiCheckPage("Apps", listOf("A", "B"), setOf("A"), { it }, { got = it }, { log += "dismiss" }) }
        rule.waitForIdle()
        rule.onNodeWithTag(OneUiDialogTags.option(1)).performClick()
        rule.onNodeWithTag(OneUiTags.BACK).performClick()
        assertEquals(listOf("dismiss"), log); assertNull(got)
    }

    @Test fun CFG_21_w46_aChoicePageCanStillBeReachedByDeepLinkOnItsOwnRow() {
        // a search result or Radar settings result lands on the exact row and lights it, rows with cards included
        SettingsFocus.request(1)
        show(initial = 1)
        rule.onNodeWithTag(OneUiTags.ROOT).assertIsDisplayed()
        rule.onAllNodesWithTag(OneUiTags.RAIL).assertCountEquals(2)
        SettingsFocus.clear()
    }

    // ---- the large title sits in the middle of its band ---------------------------------------------------------------------

    private fun assertCentred(band: androidx.compose.ui.test.SemanticsNodeInteraction, title: androidx.compose.ui.test.SemanticsNodeInteraction, sub: androidx.compose.ui.test.SemanticsNodeInteraction? = null, label: String, minBand: Float = 172f) {
        val b = band.getUnclippedBoundsInRoot(); val t = title.getUnclippedBoundsInRoot(); val s = sub?.getUnclippedBoundsInRoot()
        val top = t.top.value; val bottom = (s ?: t).bottom.value
        val bandMid = (b.top.value + b.bottom.value) / 2f
        assertEquals("$label: the title block is centred in its band", bandMid, (top + bottom) / 2f, 2f)
        assertTrue("$label: the band is tall enough to centre in: ${b.bottom.value - b.top.value}", b.bottom.value - b.top.value >= minBand)
    }

    @Test fun CFG_21_w46_theSettingsTitleIsInTheVerticalCentreOfItsBand() {
        show()
        assertCentred(rule.onNodeWithTag(OneUiTags.TITLE_BAND), rule.onNodeWithTag(OneUiTags.TITLE_LARGE), label = "root")
    }

    @Test fun CFG_21_w46_aCategoryTitleAndAChoicePageTitleWithItsExplanationAreCentredToo() {
        show(initial = 1)
        assertCentred(rule.onNodeWithTag(OneUiTags.TITLE_BAND), rule.onNodeWithTag(OneUiTags.TITLE_LARGE), label = "settings home")
        toIconShape()
        rule.onNode(hasContentDescription("Icon shape, Rounded")).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        val bands = rule.onAllNodesWithTag(OneUiTags.TITLE_BAND)
        assertTrue(bands.fetchSemanticsNodes().isNotEmpty())
        assertCentred(rule.onAllNodesWithTag(OneUiTags.TITLE_BAND)[bands.fetchSemanticsNodes().size - 1], rule.onNode(hasTestTag(OneUiTags.TITLE_LARGE) and hasAnyAncestor(hasTestTag(OneUiTags.SUB_PAGE))), rule.onNodeWithTag(OneUiTags.SUBTITLE), label = "choice page")
    }

    @Test fun CFG_21_w46_theTitleStaysBelowTheBarAndAboveTheFirstRow() {
        show()
        val title = rule.onNodeWithTag(OneUiTags.TITLE_LARGE).getUnclippedBoundsInRoot()
        val search = rule.onNodeWithTag(OneUiTags.SEARCH).getUnclippedBoundsInRoot()
        val bar = rule.onNodeWithTag(OneUiTags.BACK).getUnclippedBoundsInRoot()
        assertTrue("under the back arrow: ${title.top} vs ${bar.bottom}", title.top.value > bar.bottom.value + 40f)
        assertTrue("clear of the search field: ${title.bottom} vs ${search.top}", search.top.value - title.bottom.value > 40f)
    }

    @Test fun CFG_21_w46_theLargeTitleStillCollapsesIntoTheSmallOneOnScroll() {
        val many = (0 until 30).map { SettingItem.Toggle("Option $it", Glyphs.Apps, true) {} }
        rule.setContent { SettingsSpace(visible = true, categories = listOf(SettingCategory("Home screen", Glyphs.Apps, many)), initialCategory = 0, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.TITLE_LARGE).assertIsDisplayed()
        rule.onAllNodesWithTag(OneUiTags.TITLE_SMALL).assertCountEquals(0)
        // wave 57: the title band goes with a drag of the section's list (a scroll by the list's own actions keeps it, so the list's viewport is always the visible part)
        repeat(3) { rule.onNodeWithTag(SettingsTabTags.list("Home screen")).performTouchInput { swipeUp() }; rule.mainClock.advanceTimeBy(400) }
        rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.TITLE_SMALL).assertIsDisplayed()
        rule.onNodeWithTag(OneUiTags.TITLE_LARGE).assertIsNotDisplayed()
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_21_w46_inLandscapeEachPaneCentresItsOwnTitle() {
        show(initial = 1)
        val left = rule.onNodeWithTag(OneUiTags.LEFT_PANE).getUnclippedBoundsInRoot()
        val right = rule.onNodeWithTag(OneUiTags.RIGHT_PANE).getUnclippedBoundsInRoot()
        val bands = rule.onAllNodesWithTag(OneUiTags.TITLE_BAND); val titles = rule.onAllNodesWithTag(OneUiTags.TITLE_LARGE)
        assertEquals(2, bands.fetchSemanticsNodes().size)
        (0..1).forEach { i -> assertCentred(bands[i], titles[i], label = if (i == 0) "left pane" else "right pane", minBand = if (i == 0) 112f else 172f) }
        assertTrue(titles[0].getUnclippedBoundsInRoot().right.value <= left.right.value)
        assertTrue(titles[1].getUnclippedBoundsInRoot().left.value >= right.left.value)
    }

    // ---- screenshots (read by a person) -------------------------------------------------------------------------------------

    @Test fun CFG_21_w46_screenshotRootDarkPortrait() { show(); shoot("w46-root-dark-portrait") }
    @Test fun CFG_21_w46_screenshotCommsDarkPortrait() { show(initial = 1); shoot("w46-comms-dark-portrait") }
    @Test fun CFG_21_w46_screenshotChoicePageDarkPortrait() {
        show(initial = 1); rule.onNode(hasContentDescription("Icon shape, Rounded")).performClick(); rule.mainClock.advanceTimeBy(600); shoot("w46-choice-page-dark-portrait")
    }
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_21_w46_screenshotTwoPaneDarkLandscape() { show(initial = 1); shoot("w46-twopane-dark-landscape") }
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_21_w46_screenshotChoicePageDarkLandscape() {
        show(initial = 1)
        toIconShape()
        rule.onNode(hasContentDescription("Icon shape, Rounded")).performClick(); rule.mainClock.advanceTimeBy(600); shoot("w46-choice-page-dark-landscape")
    }
}
