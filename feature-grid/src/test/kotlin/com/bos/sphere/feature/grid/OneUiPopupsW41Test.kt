package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
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
 * Wave 41 (settingsPopups), CFG-21: the shared One UI popups. Dialog (28 dp card, centred 20 sp heading title, grey body, a row of flat
 * text buttons 56 dp tall, destructive primary), radio and check dialogs (56 dp rows), the text-entry dialog (one underlined field, blank and
 * duplicate guards), the segmented control, the popup menu (48 dp rows, red destructive), the sheet (grabber, 28 dp corners), and screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class OneUiPopupsW41Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); L10n.setLanguage("en") }

    private val log = ArrayList<String>()

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 2000)
    }

    private fun showCard(light: Boolean = false, font: Float = 1f, rtl: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        if (light) HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, LocalDensity provides Density(d.density, font)) {
                Box(Modifier.fillMaxSize().background(if (light) Color(0xFF8A8A92) else Color(0xFF05060A)), contentAlignment = androidx.compose.ui.Alignment.Center) { content() }
            }
        }
        rule.waitForIdle()
    }

    // ---- the numbers --------------------------------------------------------------------------------------------------------

    @Test fun CFG_21_popups_01_theSpecNumbersAreOneUis() {
        assertEquals(28, OneUiDialogSpec.CORNER_DP); assertEquals(20, OneUiDialogSpec.TITLE_SP); assertEquals(15, OneUiDialogSpec.BODY_SP)
        assertEquals(56, OneUiDialogSpec.BUTTON_HEIGHT_DP); assertEquals(56, OneUiDialogSpec.OPTION_HEIGHT_DP)
        assertEquals(16, OneUiDialogSpec.MENU_CORNER_DP); assertEquals(48, OneUiDialogSpec.MENU_ROW_DP); assertEquals(28, OneUiDialogSpec.SHEET_CORNER_DP)
    }

    @Test fun CFG_21_popups_02_aSegmentedControlIsForTwoOrThreeShortOptionsOnly() {
        assertTrue(OneUiSegmentedRule.fits(listOf("Bottom", "Left", "Right")))
        assertTrue(OneUiSegmentedRule.fits(listOf("On", "Off")))
        assertFalse("four options are a radio dialog", OneUiSegmentedRule.fits(listOf("A", "B", "C", "D")))
        assertFalse("a long label is a radio dialog", OneUiSegmentedRule.fits(listOf("Collapsed band", "Pinned and recent")))
        assertFalse(OneUiSegmentedRule.fits(listOf("Only")))
    }

    // ---- the dialog ---------------------------------------------------------------------------------------------------------

    @Test fun CFG_21_popups_03_aDialogHasAHeadingTitleBodyAndTwoButtonsAtLeast56DpTall() {
        showCard { OneUiDialogCard("Delete Work?", {}, body = "It is removed.", confirmLabel = "Delete", onConfirm = { log += "ok" }, destructive = true) }
        val title = rule.onNodeWithTag(OneUiDialogTags.TITLE)
        assertTrue(title.fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
        rule.onNodeWithTag(OneUiDialogTags.BODY).assertIsDisplayed()
        listOf(OneUiDialogTags.CANCEL, OneUiDialogTags.CONFIRM).forEach { t ->
            assertTrue("$t is at least 56 dp tall", rule.onNodeWithTag(t).getUnclippedBoundsInRoot().let { it.bottom - it.top } >= 56.dp)
        }
        assertEquals("the card is one pane titled for the screen reader", "Delete Work?", rule.onNodeWithTag(OneUiDialogTags.DIALOG).fetchSemanticsNode().config.getOrNull(SemanticsProperties.PaneTitle))
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick()
        assertEquals(listOf("ok"), log)
    }

    @Test fun CFG_21_popups_04_aDisabledConfirmDoesNothing() {
        showCard { OneUiDialogCard("Save", {}, confirmLabel = "Save", onConfirm = { log += "ok" }, confirmEnabled = false) }
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsNotEnabled().performClick()
        assertTrue(log.isEmpty())
    }

    @Test fun CFG_21_popups_05_theRealDialogWindowOpensAndCancelCloses() {
        var open by mutableStateOf(true)
        rule.setContent { if (open) OneUiConfirmDialog("Sure?", "Really.", "Yes", { log += "yes" }, { open = false; log += "dismiss" }) }
        rule.waitForIdle()
        rule.onNodeWithText("Sure?").assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.CANCEL).performClick(); rule.waitForIdle()
        assertEquals(listOf("dismiss"), log)
        rule.onAllNodesWithTag(OneUiDialogTags.DIALOG).assertCountEquals(0)
    }

    @Test fun CFG_21_popups_06_theOldConfirmDialogIsNowTheOneUiOne() {
        rule.setContent { ConfirmDialog("Remove it?", "Gone.", "Remove", "Keep", { log += "remove" }, { log += "keep" }, destructive = true) }
        rule.waitForIdle()
        rule.onNodeWithText("Keep").performClick(); rule.onNodeWithText("Remove").performClick()
        assertEquals(listOf("keep", "remove"), log)
        assertTrue(rule.onNodeWithTag(OneUiDialogTags.TITLE).fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
    }

    // ---- radio and check ----------------------------------------------------------------------------------------------------

    @Test fun CFG_21_popups_07_aRadioDialogHasBigRowsMarksTheSelectionAndAppliesOnTap() {
        var open by mutableStateOf(true)
        rule.setContent { if (open) OneUiRadioDialog("Dock mode", listOf("Band", "Pinned", "Both"), "Pinned", { it }, { log += "pick $it" }, { open = false }) }
        rule.waitForIdle()
        (0..2).forEach { i -> assertTrue(rule.onNodeWithTag(OneUiDialogTags.option(i)).getUnclippedBoundsInRoot().let { it.bottom - it.top } >= 56.dp) }
        assertEquals(true, rule.onNodeWithTag(OneUiDialogTags.option(1)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(false, rule.onNodeWithTag(OneUiDialogTags.option(0)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithTag(OneUiDialogTags.option(2)).performClick(); rule.waitForIdle()
        assertEquals(listOf("pick Both"), log)
        rule.onAllNodesWithTag(OneUiDialogTags.DIALOG).assertCountEquals(0)
    }

    @Test fun CFG_21_popups_08_aCheckDialogKeepsToItsLimitsAndReturnsTheSet() {
        var got: Set<String>? = null
        rule.setContent { OneUiCheckDialog("Apps", listOf("A", "B", "C"), setOf("A"), { it }, { got = it }, {}, min = 1, max = 2) }
        rule.waitForIdle()
        rule.onNodeWithTag(OneUiDialogTags.option(1)).performClick()
        rule.onNodeWithTag(OneUiDialogTags.option(2)).performClick() // a third is refused at max 2
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).performClick()
        assertEquals(setOf("A", "B"), got)
    }

    @Test fun CFG_21_popups_09_aCheckDialogCannotBeDoneBelowItsMinimum() {
        rule.setContent { OneUiCheckDialog("Apps", listOf("A", "B"), setOf("A"), { it }, { log += "done" }, {}, min = 1) }
        rule.waitForIdle()
        rule.onNodeWithTag(OneUiDialogTags.option(0)).performClick()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsNotEnabled()
        rule.onNodeWithTag(OneUiDialogTags.option(1)).performClick()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsEnabled()
    }

    // ---- text entry ---------------------------------------------------------------------------------------------------------

    @Test fun CFG_21_popups_10_aTextEntryDialogIsOneFieldAndConfirmNeedsText() {
        rule.mainClock.autoAdvance = false
        rule.setContent { OneUiTextEntryDialog("Add workspace", "", "Workspace name", "Add", { log += "add $it" }, {}) }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsNotEnabled()
        rule.onAllNodesWithTag(OneUiDialogTags.FIELD).assertCountEquals(1)
        rule.onNodeWithTag(OneUiDialogTags.FIELD).performTextInput("  Work ")
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsEnabled().performClick()
        assertEquals(listOf("add Work"), log)
    }

    @Test fun CFG_21_popups_11_aTextEntryDialogShowsAProblemAndBlocksConfirm() {
        rule.mainClock.autoAdvance = false
        rule.setContent { OneUiTextEntryDialog("Rename", "Home", "Workspace name", "Rename", { log += "x" }, {}, error = { if (it == "Home") "Already used" else null }) }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Already used").assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsNotEnabled()
    }

    @Test fun CFG_21_popups_11b_anUnderlineFieldThatStartsWithTextTakesTypedTextAtTheEnd() {
        rule.mainClock.autoAdvance = false
        var text by mutableStateOf("Study")
        rule.setContent { OneUiUnderlineField(text, { text = it }, "Session name") }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag(OneUiDialogTags.FIELD).performTextInput("x")
        rule.mainClock.advanceTimeBy(300)
        assertEquals("Studyx", text)
    }

    // ---- segmented ----------------------------------------------------------------------------------------------------------

    @Test fun CFG_21_popups_12_aSegmentedControlSelectsOneOfThreeAnd48DpTargets() {
        var sel by mutableStateOf(0)
        showCard { OneUiSegmented(listOf("Bottom", "Left", "Right"), sel, { sel = it; log += "edge $it" }) }
        (0..2).forEach { i -> assertTrue(rule.onNodeWithTag(oneUiSegmentedTag(i)).getUnclippedBoundsInRoot().let { it.bottom - it.top } >= 48.dp) }
        assertEquals(true, rule.onNodeWithTag(oneUiSegmentedTag(0)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithTag(oneUiSegmentedTag(2)).performClick(); rule.waitForIdle()
        assertEquals(listOf("edge 2"), log)
        assertEquals(true, rule.onNodeWithTag(oneUiSegmentedTag(2)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    // ---- menu and sheet -----------------------------------------------------------------------------------------------------

    @Test fun CFG_21_popups_13_aMenuHas48DpRowsAndRunsTheTappedOneAndCloses() {
        var open by mutableStateOf(true)
        rule.setContent {
            Box { OneUiMenu(open, { open = false }, listOf(OneUiMenuEntry("Rename") { log += "rename" }, OneUiMenuEntry("Delete", destructive = true) { log += "delete" })) }
        }
        rule.waitForIdle()
        assertTrue(rule.onNodeWithTag(OneUiMenuTags.entry(0)).getUnclippedBoundsInRoot().let { it.bottom - it.top } >= 48.dp)
        rule.onNodeWithTag(OneUiMenuTags.entry(1)).performClick(); rule.waitForIdle()
        assertEquals(listOf("delete"), log)
        assertFalse(open)
    }

    @Test fun CFG_21_popups_14_aSheetHasAGrabberAndItsContent() {
        rule.setContent { OneUiSheet({}, title = "Options") { Column { androidx.compose.material3.Text("Row one") } } }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        rule.onNodeWithTag(OneUiMenuTags.GRABBER, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Options").assertExists()
        rule.onNodeWithText("Row one").assertExists()
        assertTrue(rule.onNodeWithTag(OneUiMenuTags.SHEET).fetchSemanticsNode().layoutInfo.isPlaced)
    }

    @Test fun CFG_21_popups_15_aGlassDialogEditorIsAOneUiPageWithABackArrowAndFlatFooterButtons() {
        rule.setContent { GlassDialog("Drawer shortcut 1", { log += "back" }, footer = { TextButtonPill("Cancel") { log += "cancel" }; TextButtonPill("Save", primary = true) { log += "save" } }) { androidx.compose.material3.Text("BODY") } }
        rule.waitForIdle()
        rule.onNodeWithText("BODY").assertIsDisplayed()
        assertTrue("footer text button is 56 dp tall", rule.onNodeWithText("Save").getUnclippedBoundsInRoot().let { it.bottom - it.top } >= 56.dp)
        rule.onNodeWithText("Save").performClick()
        assertEquals(listOf("save"), log)
    }

    // ---- accessibility, font scale, RTL -------------------------------------------------------------------------------------

    @Test fun CFG_21_popups_16_theDialogReadsAtFontScale2AndRightToLeft() {
        showCard(font = 2f, rtl = true) { OneUiDialogCard("Delete Work?", {}, body = "Its saved home pages, dock and widgets are removed.", confirmLabel = "Delete", onConfirm = {}, destructive = true) }
        rule.onNodeWithTag(OneUiDialogTags.CONFIRM).assertIsDisplayed()
        rule.onNodeWithTag(OneUiDialogTags.CANCEL).assertIsDisplayed()
        shoot("w41-dialog-confirm-fontscale2-rtl")
    }

    // ---- screenshots (read by a person) -------------------------------------------------------------------------------------

    @Test fun CFG_21_popups_20_screenshotConfirmDark() { showCard { OneUiDialogCard("Delete Work?", {}, body = "Its saved home pages, dock and widgets are removed. The apps themselves are not touched.", confirmLabel = "Delete", onConfirm = {}, destructive = true) }; shoot("w41-dialog-confirm-dark") }
    @Test fun CFG_21_popups_21_screenshotConfirmLight() { showCard(light = true) { OneUiDialogCard("Delete Work?", {}, body = "Its saved home pages, dock and widgets are removed. The apps themselves are not touched.", confirmLabel = "Delete", onConfirm = {}, destructive = true) }; shoot("w41-dialog-confirm-light") }
    @Test fun CFG_21_popups_22_screenshotRadioDark() {
        showCard {
            OneUiDialogCard("Dock mode", {}) {
                listOf("Collapsed band", "Pinned and recent").forEachIndexed { i, l -> OneUiInlineRadio(l, i == 1) {} }
            }
        }
        shoot("w41-dialog-radio-dark")
    }
    @Test fun CFG_21_popups_23_screenshotTextEntryLight() {
        showCard(light = true) {
            OneUiDialogCard("Add workspace", {}, confirmLabel = "Add", onConfirm = {}) {
                Column(Modifier.padding(16.dp)) {
                    OneUiUnderlineField("Work", {}, "Workspace name")
                    OneUiInlineRadio("An empty home", true) {}
                    OneUiInlineRadio("A copy of Home", false) {}
                }
            }
        }
        shoot("w41-dialog-textentry-light")
    }
    @Test fun CFG_21_popups_24_screenshotSegmentedAndMenuDark() {
        showCard {
            Column(Modifier.padding(16.dp)) {
                OneUiSegmented(listOf("Bottom", "Left", "Right"), 0, {})
                androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                Column(Modifier.background(androidx.compose.ui.graphics.Color(0xFF1B1D26), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
                    OneUiMenuRows(listOf(OneUiMenuEntry("Make active") {}, OneUiMenuEntry("Rename") {}, OneUiMenuEntry("Duplicate") {}, OneUiMenuEntry("Delete", destructive = true) {}), {})
                }
            }
        }
        shoot("w41-segmented-menu-dark")
    }
}

