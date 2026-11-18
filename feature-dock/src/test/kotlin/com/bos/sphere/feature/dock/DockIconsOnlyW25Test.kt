package com.bos.sphere.feature.dock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.L10n
import org.junit.Assert.assertEquals
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
 * W25 owner item 4: the dock shows only icons (no text, no "i", no "..."), and everything those buttons held is behind a long press
 * (icon: that icon's options; dock: the dock's options), with the menu key and TalkBack actions as the non-gesture routes.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.InternalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class DockIconsOnlyW25Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun english() { L10n.setLanguage("en"); DockInput.dialogHook = {} }

    private fun content(recent: Boolean = false, enable: Boolean = false) = DockContent(
        pinned = listOf("Alpha", "Bravo", "Charlie").mapIndexed { i, l -> DockItem(DockItemRef.app("k$i"), l) },
        recent = if (recent) listOf(DockItem(DockItemRef.app("r0"), "Recent one")) else emptyList(),
        showEnableRow = enable, showRecentSection = recent, media = null, collapsed = false,
    )

    @Composable private fun Rail(c: DockContent, actions: DockActions, edge: DockEdge = DockEdge.Bottom) {
        Box(Modifier.fillMaxSize(), contentAlignment = if (edge == DockEdge.Bottom) Alignment.BottomCenter else Alignment.CenterStart) {
            DockRail(c, edge, "Home", expanded = true, focusRequest = 0, showNumbers = false, actions = actions, graphBridge = null,
                itemExtras = { listOf(DockMenuEntry("Shortcut one") { ran += "shortcut" }) }) { item, s ->
                Box(Modifier.size(s).background(Color(0xFF4F8CFF + item.label.length.toLong())))
            }
        }
    }

    private val ran = mutableListOf<String>()
    private fun actions() = DockActions(
        onLaunch = { i, _ -> ran += "launch ${i.label}" },
        onReorder = { f, t -> ran += "reorder $f $t" },
        onUnpin = { ran += "unpin ${it.label}" },
        onPin = { ran += "pin ${it.label}" },
        onOpenPanel = { ran += "panel" },
        onAppInfo = { ran += "info ${it.label}" },
        onReplace = { ran += "replace ${it.label}" },
        onAddApps = { ran += "add" },
        onHide = { ran += "hide" },
        onSetEdge = { ran += "edge $it" },
    )

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), Paint().apply { color = 0xFF10131F.toInt() }); drawBitmap(bmp, 0f, 0f, null) }
        File(outDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun assertNoText() {
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).assertCountEquals(0)
    }

    private fun longPressDockBackground() = rule.onNodeWithContentDescription("Dock").performTouchInput { longClick(androidx.compose.ui.geometry.Offset(width - 4f, height / 2f)) }

    @Test fun DCK_01_theDockShowsOnlyIconsNoTextNoInfoButtonNoOverflowButton() {
        rule.setContent { Rail(content(recent = true, enable = true), actions()) }
        rule.waitForIdle()
        assertNoText()
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("About the dock")), useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Workspaces, groups and dock settings")), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithContentDescription("Workspace Home").assertExists() // the home button is an icon whose name is only its description
        shoot("w25-dock-icons-only-bottom")
    }

    @Test fun DCK_01_theSideRailIsIconsOnlyToo() {
        rule.setContent { Rail(content(recent = true, enable = true), actions(), DockEdge.Left) }
        rule.waitForIdle()
        assertNoText()
        shoot("w25-dock-icons-only-side")
    }

    @Test fun DCK_01_tappingAnIconStillLaunchesIt() {
        ran.clear()
        rule.setContent { Rail(content(), actions()) }
        rule.onNodeWithContentDescription("Bravo").performClick()
        assertEquals(listOf("launch Bravo"), ran)
    }

    @Test fun DCK_01_longPressOnTheDockBackgroundOpensTheDockOptionsAndEachEntryRuns() {
        ran.clear()
        rule.setContent { Rail(content(), actions()) }
        longPressDockBackground()
        rule.waitForIdle()
        rule.onNodeWithTag(DockMenuModel.TAG_DOCK_MENU).assertExists()
        shoot("w25-dock-menu")
        listOf("Edit dock", "Workspaces and groups", "Dock settings", "Hide dock", "About the dock").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("Dock at the bottom").assertDoesNotExist() // it is already at the bottom
        rule.onNodeWithText("Dock on the left").assertExists()
        rule.onNodeWithText("Dock on the right").assertExists()
        rule.onNodeWithText("Workspaces and groups").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(DockMenuModel.TAG_DOCK_MENU).assertDoesNotExist()
        longPressDockBackground(); rule.waitForIdle(); rule.onNodeWithText("Dock settings").performClick()
        longPressDockBackground(); rule.waitForIdle(); rule.onNodeWithText("Hide dock").performClick()
        longPressDockBackground(); rule.waitForIdle(); rule.onNodeWithText("Dock on the left").performClick()
        assertEquals(listOf("panel", "panel", "hide", "edge Left"), ran)
        longPressDockBackground(); rule.waitForIdle(); rule.onNodeWithText("Edit dock").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Nothing is pinned to the dock yet.").assertDoesNotExist()
        rule.onNodeWithText("Add apps").performClick()
        assertEquals("add", ran.last())
    }

    @Test fun DCK_01_aboutIsAnEntryOfTheDockMenu() {
        rule.setContent { Rail(content(), actions()) }
        longPressDockBackground(); rule.waitForIdle()
        rule.onNodeWithText("About the dock").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Alt with an arrow key, or a long press and drag, reorders pinned items.").assertExists()
    }

    @Test fun DCK_01_longPressOnAnIconOpensThatIconsOptionsAndEachEntryRuns() {
        ran.clear()
        rule.setContent { Rail(content(), actions()) }
        rule.onNodeWithContentDescription("Bravo").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(DockMenuModel.TAG_ITEM_MENU).assertExists()
        rule.onNodeWithTag(DockMenuModel.TAG_DOCK_MENU).assertDoesNotExist()
        shoot("w25-icon-menu")
        listOf("Shortcut one", "App info", "Move earlier", "Move later", "Replace with another app", "Remove from dock").forEach { rule.onNodeWithText(it).assertExists() }
        for (l in listOf("Shortcut one", "App info", "Move earlier", "Move later", "Replace with another app", "Remove from dock")) {
            rule.onNodeWithText(l).performClick(); rule.waitForIdle()
            rule.onNodeWithBravo().performTouchInput { longClick() }; rule.waitForIdle()
        }
        assertEquals(listOf("shortcut", "info Bravo", "reorder 1 0", "reorder 1 2", "replace Bravo", "unpin Bravo"), ran)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onNodeWithBravo() = onNodeWithContentDescription("Bravo")

    @Test fun DCK_01_aRecentIconsMenuOffersPinNotRemove() {
        ran.clear()
        rule.setContent { Rail(content(recent = true), actions()) }
        rule.onNodeWithContentDescription("Recent one").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithText("Pin to dock").performClick()
        assertEquals(listOf("pin Recent one"), ran)
    }

    @Test fun INP_01_theMenuKeyOnAFocusedIconOpensItsOptionsAndShiftF10Too() {
        rule.setContent { Rail(content(), actions()) }
        rule.onNodeWithContentDescription("Alpha").requestFocus()
        rule.onNodeWithContentDescription("Alpha").performKeyInput { keyDown(Key.Menu); keyUp(Key.Menu) }
        rule.waitForIdle()
        rule.onNodeWithTag(DockMenuModel.TAG_ITEM_MENU).assertExists()
        rule.onNodeWithText("Move later").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Alpha").requestFocus()
        rule.onNodeWithContentDescription("Alpha").performKeyInput { keyDown(Key.ShiftLeft); keyDown(Key.F10); keyUp(Key.F10); keyUp(Key.ShiftLeft) }
        rule.waitForIdle()
        rule.onNodeWithTag(DockMenuModel.TAG_ITEM_MENU).assertExists()
    }

    private fun customLabels(desc: String): List<String> =
        rule.onNodeWithContentDescription(desc).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    @Test fun INP_01_everyLongPressActionIsAlsoATalkBackAction() {
        ran.clear()
        rule.setContent { Rail(content(recent = true), actions()) }
        val dock = customLabels("Dock")
        assertTrue(dock.toString(), dock.containsAll(listOf("Edit dock", "Workspaces and groups", "Dock settings", "Hide dock", "About the dock", "Dock on the left")))
        val item = customLabels("Bravo")
        assertTrue(item.toString(), item.containsAll(listOf("Move earlier", "Move later", "Unpin", "Replace with another app", "App info", "Dock item options")))
        assertTrue(customLabels("Recent one").containsAll(listOf("Pin to dock", "Dock item options")))
        assertTrue(customLabels("Workspace Home").containsAll(listOf("Switch to the next workspace", "Switch to the previous workspace")))
        // And they run.
        rule.runOnUiThread {
            rule.onNodeWithContentDescription("Dock").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Hide dock" }.action()
            rule.onNodeWithContentDescription("Bravo").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Replace with another app" }.action()
            rule.onNodeWithContentDescription("Bravo").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "App info" }.action()
        }
        assertEquals(listOf("hide", "replace Bravo", "info Bravo"), ran)
    }

    @Test fun DCK_01_theDockMenuLeavesOutHideAndPositionWhereTheHostCannotOfferThem() {
        val a = DockActions(onOpenPanel = {})
        val labels = DockMenuModel.dock(a, DockEdge.Bottom, {}, {}).map { it.label }
        assertEquals(listOf("Edit dock", "Workspaces and groups", "Dock settings", "About the dock"), labels)
    }
}
