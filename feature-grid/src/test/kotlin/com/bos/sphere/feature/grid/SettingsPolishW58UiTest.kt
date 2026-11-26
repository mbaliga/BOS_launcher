package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wave 58 (settingsPolish) in the real host: section notes (CFG-65), dimmed blocks and switch rows (CFG-66), a search hit lighting a pane row (CFG-67). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class SettingsPolishW58UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun setUp() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private val g = Glyphs.Apps
    private var clicks = 0

    private fun categories() = listOf(
        SettingCategory(
            "Radar", g,
            listOf(
                SettingItem.Toggle("Web suggestions", g, true, "Sends what you type to Google") {},
                SettingItem.Pane("Themes", g, "Import, export, reset") { ActionRow("Import theme", null, g, flashId = "themes.import") {}; ActionRow("Export current look", null, g, flashId = "themes.export") {} }
                    .paneEntries(PaneEntry("themes.import", "Import theme", listOf("liftofftheme")), PaneEntry("themes.export", "Export current look")),
            ),
            headers = mapOf(0 to "Web"), notes = mapOf(0 to "Suggestions also send your typing to the Play Store."),
        ),
        SettingCategory("About", Glyphs.Info, listOf(SettingItem.Action("Version", Glyphs.Info, "1.0") {})),
    )

    private fun show() {
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = categories(), initialCategory = 0, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) } }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    @Test fun CFG_68_aSectionNoteSitsUnderItsHeader() {
        show()
        rule.onNodeWithTag(OneUiTags.SECTION_NOTE).assertIsDisplayed().assertTextEquals("Suggestions also send your typing to the Play Store.")
        val head = rule.onNodeWithText("Web").fetchSemanticsNode().boundsInRoot
        val note = rule.onNodeWithTag(OneUiTags.SECTION_NOTE).fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithText("Web suggestions").fetchSemanticsNode().boundsInRoot
        assertTrue("header, then the note, then the rows", head.bottom <= note.top + 1f && note.bottom <= row.top + 1f)
    }

    @Test fun CFG_68_theBlocksCarryTheNotesOfTheirHeaders() {
        val b = OneUiModel.blocks(categories()[0].items, mapOf(0 to "Web"), mapOf(0 to "N"))
        assertEquals(OneUiModel.Block.Header("Web", "N"), b.first())
    }

    @Test fun CFG_67_aSearchHitInsideThemesOpensThePageAndLightsThatRow() {
        show()
        rule.onNodeWithTag(OneUiTags.SEARCH).performTextInput("liftofftheme"); rule.waitForIdle()
        rule.onNodeWithText("Import theme").performClick()
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.SUB_PAGE).assertIsDisplayed()
        val lit = rule.onAllNodes(SemanticsMatcher.expectValue(PaneFlashed, "themes.import"), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("the hit's row lights", 1, lit.size)
        assertEquals("only that row", 0, rule.onAllNodes(SemanticsMatcher.expectValue(PaneFlashed, "themes.export"), useUnmergedTree = true).fetchSemanticsNodes().size)
        rule.mainClock.advanceTimeBy(2500); rule.waitForIdle()
        assertEquals("the light goes after a moment", 0, rule.onAllNodes(SemanticsMatcher.keyIsDefined(PaneFlashed), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun CFG_66_aDimmedBlockTakesNoTouchAndReadsUnavailableWithTheReason() {
        rule.setContent { SphereTheme { Column { OneUiDisabledBlock("Dock is off", "Dock style") { ActionRow("Card", null, g) { clicks++ } } } } }
        rule.onNodeWithTag(OneUiTags.DISABLED_ROW).performClick(); rule.waitForIdle()
        assertEquals("no touch reaches the cards", 0, clicks)
        rule.onNodeWithContentDescription("Dock style, Unavailable, Dock is off").assertExists()
        assertEquals("the cards are not separate nodes for a screen reader", 0, rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("Card")))).fetchSemanticsNodes().size)
    }

    @Test fun CFG_66_aBlockWithoutAReasonIsUntouched() {
        rule.setContent { SphereTheme { Column { OneUiDisabledBlock(null, "Dock style") { ActionRow("Card", null, g) { clicks++ } } } } }
        rule.onNodeWithText("Card").performClick(); rule.waitForIdle()
        assertEquals(1, clicks)
        rule.onAllNodesWithTag(OneUiTags.DISABLED_ROW).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test fun CFG_66_aSwitchRowThatIsOffShowsItsReasonAndDoesNotFlip() {
        var v = true
        rule.setContent { SphereTheme { Column { OneUiSwitchRow("Show the dock", v, { v = it }, summary = "Default for this device: on", disabledReason = "Right side is Dock rail") } } }
        rule.onNodeWithText("Right side is Dock rail").assertIsDisplayed()
        rule.onAllNodesWithTag("oneui.summary").fetchSemanticsNodes().let { assertEquals("the reason replaces the summary", 0, it.size) }
        rule.onNodeWithTag(OneUiTags.DISABLED_ROW).performClick(); rule.waitForIdle()
        assertTrue("it did not flip", v)
        val n = rule.onNodeWithTag(OneUiTags.DISABLED_ROW).fetchSemanticsNode()
        assertTrue(n.config.contains(SemanticsProperties.Disabled))
        assertEquals("Right side is Dock rail", n.config.getOrNull(SemanticsProperties.StateDescription))
        rule.onNodeWithContentDescription("Show the dock, Unavailable, Right side is Dock rail").assertExists()
    }
}
