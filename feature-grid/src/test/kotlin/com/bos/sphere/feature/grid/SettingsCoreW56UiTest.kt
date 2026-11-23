package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.SemanticsMatcher
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.SphereTheme
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

/** Wave 56 (settingsCore) in the real host: Finish setup row and page, slider default tick and action, dimmed rows with their reason, reset with confirm and undo, a search hit inside a pane. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class SettingsCoreW56UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private val g = Glyphs.Apps
    private val log = ArrayList<String>()
    private var iconSize by mutableStateOf(0.7f)
    private var rings by mutableStateOf(2)
    private var setup by mutableStateOf(SetupState(defaultHome = false, notificationAccess = true, usageAccess = false, dndAccess = false, dndActionBound = false))
    private var resets = 0

    private class FakeSnapshots : SettingsSnapshots {
        val log = ArrayList<String>()
        override fun take(reason: String): String? { log += "take:$reason"; return "snap-7" }
        override fun restore(id: String): Boolean { log += "restore:$id"; return true }
    }
    private val snaps = FakeSnapshots()

    private fun categories(): List<SettingCategory> {
        val out = ArrayList<SettingCategory>()
        out += SettingCategory(
            "Home screen", g,
            listOf(
                SettingItem.Slider("Icon size", g, iconSize, 0.5f..0.95f, "${(iconSize * 100).toInt()}%", default = 0.82f) { iconSize = it; log += "size=$it" },
                SettingItem.Stepper("Rings", g, rings, 1..3, "2 apps fit") { rings = it; log += "rings=$it" }.disabledUnless(false, "Needs Circle layout"),
                SettingItem.Toggle("Count in badges", g, true) { log += "badge=$it" }.disabledUnless(false, "Notification badges are off"),
                SettingItem.Pane("Export, import and restore", g, "One file") { androidx.compose.material3.Text("PANE BODY"); ActionRow("Take a snapshot now", null, g, flashId = "backup.snapshot") {} }
                    .paneEntries(PaneEntry("backup.snapshot", "Take a snapshot now", listOf("restore point"))),
            ),
            reset = { resets++ },
        )
        out += SettingCategory("About", Glyphs.Info, listOf(SettingItem.Action("Version", Glyphs.Info, "1.0") {}))
        FinishSetup.category(setup) { log += "open:${it.id}" }?.let { out += it }
        return out
    }

    private fun show(initial: Int = -1) {
        rule.setContent {
            SphereTheme { SettingsSpace(visible = true, categories = categories(), initialCategory = initial, sounds = null, onClose = {}, snapshots = snaps, modifier = Modifier.fillMaxSize()) }
        }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "settings-w56-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- Finish setup --------------------------------------------------------------------------------------------------------------

    @Test fun CFG_32_theRootShowsOneFlatFinishSetupRowWithWhatIsLeft() {
        show()
        rule.onNodeWithTag(SetupTags.ROW).assertIsDisplayed()
        rule.onNodeWithText("Finish setup").assertIsDisplayed()
        rule.onNodeWithText("2 left").assertIsDisplayed()
        rule.onAllNodesWithTag(OneUiTags.row("Home screen")).assertCountEquals(1)
        shoot("root-finish-setup")
    }

    @Test fun CFG_32_theRowOpensAPageThatListsEachItemWithItsStateAndAButton() {
        show()
        rule.onNodeWithTag(SetupTags.ROW).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag(SetupTags.LIST).assertIsDisplayed()
        rule.onNodeWithTag(SetupTags.step("defaultHome")).assertIsDisplayed()
        rule.onNodeWithTag(SetupTags.button("defaultHome")).assertIsDisplayed()
        rule.onNodeWithTag(SetupTags.button("usage")).assertIsDisplayed()
        rule.onAllNodesWithTag(SetupTags.button("notifications")).assertCountEquals(0) // done: a tick, no button
        rule.onAllNodesWithTag(SetupTags.step("dnd")).assertCountEquals(0) // no Do Not Disturb action is bound
        rule.onNodeWithTag(SetupTags.button("usage")).performClick()
        assertEquals(listOf("open:usage"), log.filter { it.startsWith("open:") })
        shoot("finish-setup-page")
    }

    @Test fun CFG_32_theRowIsGoneWhenEverythingIsDone() {
        setup = SetupState.Complete
        show()
        rule.onAllNodesWithTag(SetupTags.ROW).assertCountEquals(0)
    }

    // ---- sliders -------------------------------------------------------------------------------------------------------------------

    @Test fun CFG_29_aSliderShowsItsDefaultTickAndAFlatDefaultActionThatPutsTheDefaultBack() {
        show(0)
        rule.onNodeWithTag(OneUiTags.SLIDER_TICK, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(OneUiTags.SLIDER_DEFAULT).assertIsDisplayed()
        rule.onNodeWithTag(OneUiTags.SLIDER_READOUT).assertTextEquals("70%")
        shoot("slider-default-tick")
        rule.onNodeWithTag(OneUiTags.SLIDER_DEFAULT).performClick(); rule.waitForIdle()
        assertEquals(0.82f, iconSize, 0.011f)
        rule.onAllNodesWithTag(OneUiTags.SLIDER_DEFAULT).assertCountEquals(0) // at the default the action goes away
        rule.onNodeWithTag(OneUiTags.SLIDER_TICK, useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- disabled rows -------------------------------------------------------------------------------------------------------------

    @Test fun CFG_31_aRowThatIsOffStaysOnThePageDimmedWithItsReasonAndDoesNothing() {
        show(0)
        rule.onNodeWithText("Rings").assertIsDisplayed()
        rule.onNodeWithText("Needs Circle layout").assertIsDisplayed()
        rule.onNodeWithText("Notification badges are off").assertIsDisplayed()
        rule.onNodeWithText("Count in badges").performClick(); rule.waitForIdle()
        assertTrue("a disabled switch does not flip", log.none { it.startsWith("badge=") })
        val disabled = rule.onAllNodesWithTag(OneUiTags.DISABLED_ROW)
        disabled.assertCountEquals(2)
        // TalkBack: the description carries the state and the reason
        rule.onNode(SemanticsMatcher("count in badges described as unavailable") { n ->
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == "Count in badges, Unavailable, Notification badges are off" } == true
        }, useUnmergedTree = true).assertExists()
        shoot("dimmed-row-with-reason")
    }

    // ---- reset ---------------------------------------------------------------------------------------------------------------------

    @Test fun CFG_30_resetThisPageAsksTakesASnapshotThenOffersUndo() {
        show(0)
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTestTag(OneUiTags.RESET_ROW))
        rule.onNodeWithTag(OneUiTags.RESET_ROW).assertIsDisplayed().performClick(); rule.waitForIdle()
        // the confirmation: the primary action is a box on top, Cancel plain text below it
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.CANCEL).assertIsDisplayed()
        val confirmTop = rule.onNodeWithTag(OneUiDialogTags.CONFIRM).fetchSemanticsNode().boundsInRoot.top
        val cancelTop = rule.onNodeWithTag(OneUiDialogTags.CANCEL).fetchSemanticsNode().boundsInRoot.top
        assertTrue("stacked, primary above secondary", confirmTop < cancelTop)
        assertEquals("nothing changed yet", 0, resets)
        shoot("reset-confirm")
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick()
        rule.waitUntil(5_000) { resets == 1 }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(OneUiTags.UNDO_ROW).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("take:before reset this page to defaults"), snaps.log)
        rule.onNodeWithTag(OneUiTags.UNDO_ROW).assertIsDisplayed()
        shoot("reset-done-undo")
        rule.onNodeWithTag(OneUiTags.UNDO_ROW + ".undo").performClick()
        rule.waitUntil(5_000) { snaps.log.contains("restore:snap-7") }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Settings restored.").fetchSemanticsNodes().isNotEmpty() }
    }

    /** The dialog lives in its own window, which a view capture does not include: the same card, drawn on the page. */
    @Test fun CFG_30_theConfirmCardStacksThePrimaryBoxAboveThePlainCancel() {
        rule.setContent {
            SphereTheme {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(OneUi.Page), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    OneUiDialogCard(
                        title = SettingsResetFlow.PAGE_TITLE, body = SettingsResetFlow.PAGE_BODY, confirmLabel = SettingsResetFlow.CONFIRM, onDismiss = {}, onConfirm = {}, destructive = true,
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        val confirm = rule.onNodeWithTag(OneUiDialogTags.CONFIRM).fetchSemanticsNode().boundsInRoot
        val cancel = rule.onNodeWithTag(OneUiDialogTags.CANCEL).fetchSemanticsNode().boundsInRoot
        assertTrue("primary above secondary", confirm.top < cancel.top)
        assertEquals("one column, not side by side", confirm.left, cancel.left, 2f)
        shoot("reset-confirm-card")
    }

    @Test fun CFG_30_cancelLeavesEverythingAlone() {
        show(0)
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTestTag(OneUiTags.RESET_ROW))
        rule.onNodeWithTag(OneUiTags.RESET_ROW).performClick(); rule.waitForIdle()
        rule.onNodeWithTag(OneUiDialogTags.CANCEL).performClick(); rule.waitForIdle()
        assertEquals(0, resets); assertTrue(snaps.log.isEmpty())
        rule.onAllNodesWithTag(OneUiTags.UNDO_ROW).assertCountEquals(0)
    }

    // ---- search into a pane --------------------------------------------------------------------------------------------------------

    @Test fun CFG_28_aSearchHitInsideAPaneOpensThePaneAndTheRowFlashes() {
        show()
        rule.onNodeWithTag(OneUiTags.SEARCH).performTextInput("restore point"); rule.waitForIdle()
        rule.onNodeWithText("Take a snapshot now").assertIsDisplayed().performClick()
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag(OneUiTags.SUB_PAGE).assertIsDisplayed()
        rule.onNodeWithText("PANE BODY").assertIsDisplayed()
        shoot("search-pane-hit")
    }
}
