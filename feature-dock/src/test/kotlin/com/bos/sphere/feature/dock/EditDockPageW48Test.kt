package com.bos.sphere.feature.dock

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.design.L10n
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 48 (popupPages), CFG-21 / DCK-01: "Edit dock" is a full One UI page (back arrow, large title, the pinned items in one rounded card,
 * Add apps), not a dialog card. The dock's dialog layer hook (INP-20) still applies: the page window calls it through the provider that
 * DockPagesScope installs (the module audit in `W4RadarInputDockTest` lists the page window).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class EditDockPageW48Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ran = mutableListOf<String>()
    private var hookCalls = 0

    @Before fun setUp() { L10n.setLanguage("en"); DockInput.dialogHook = { hookCalls++ } }

    private fun content(vararg labels: String) = DockContent(
        pinned = labels.mapIndexed { i, l -> DockItem(DockItemRef.app("k$i"), l) }, recent = emptyList(), showEnableRow = false, showRecentSection = false, collapsed = false,
    )
    private fun actions() = DockActions(onUnpin = { ran += "unpin ${it.label}" }, onReorder = { f, t -> ran += "reorder $f $t" }, onAddApps = { ran += "add" })

    @Test fun CFG_21_w48_editDockIsAPageNotADialogAndTakesTheDockDialogHook() {
        rule.setContent { EditDockDialog(content("Alpha", "Bravo"), actions()) { ran += "dismiss" } }
        rule.waitForIdle()
        rule.onNodeWithTag(DockPanelTags.EDIT_PAGE).assertIsDisplayed()
        assertEquals("no dialog card", 0, rule.onAllNodesWithTag(com.bos.sphere.feature.grid.OneUiDialogTags.DIALOG).fetchSemanticsNodes().size)
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        rule.onNodeWithText("Bravo").assertIsDisplayed()
        assertEquals("the dock's window hook ran for the page window", true, hookCalls > 0)
    }

    @Test fun CFG_21_w48_editDockRowMenuReordersAndUnpinsAndAddAppsRuns() {
        rule.setContent { EditDockDialog(content("Alpha", "Bravo"), actions()) { ran += "dismiss" } }
        rule.onNodeWithContentDescription("More options for Alpha").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Move later").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("More options for Bravo").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Unpin").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Add apps").performClick()
        assertEquals(listOf("reorder 0 1", "unpin Bravo", "add"), ran)
    }

    @Test fun CFG_21_w48_editDockBackArrowLeavesAndAnEmptyDockSaysSo() {
        rule.setContent { EditDockDialog(content(), actions()) { ran += "dismiss" } }
        rule.onNodeWithText("Nothing is pinned to the dock yet.").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Alpha").fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("dismiss"), ran)
    }
}
