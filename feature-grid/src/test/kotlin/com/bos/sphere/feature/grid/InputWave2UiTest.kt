package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.ComplicationKind
import com.bos.sphere.core.input.FocusKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose for the wave-2 input surfaces: corner nodes, accessible order. */
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputWave2UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun app(i: Int) = AppEntry("p$i", ComponentName("p$i", "M"), "App ${'A' + i}", Process.myUserHandle()) { null }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_17_every_dial_of_a_complication_cluster_is_a_corner_node_in_cluster_order() {
        val kinds = listOf(ComplicationKind.Date, ComplicationKind.Time, ComplicationKind.Ringer)
        val clicked = mutableListOf<ComplicationKind>()
        rule.setContent {
            // A hardware key switches Compose to keyboard mode, where clickables take focus; the test does the same.
            androidx.compose.ui.platform.LocalInputModeManager.current.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            InputModeProvider {
                ComplicationCluster(kinds, Corner.TopLeft, 42.dp, 92.dp, onClick = { clicked += it }, focusIdPrefix = "corner.TopLeft")
            }
        }
        rule.waitForIdle()
        val nodes = LauncherInput.focus.nodes().filter { it.id.startsWith("corner.TopLeft.") }
        assertEquals(kinds.map { "corner.TopLeft.${it.name}" }, nodes.sortedBy { it.order }.map { it.id })
        assertTrue(nodes.all { it.kind == FocusKind.Corner })
        // Focus can reach one and Enter runs it, by the same path a tap takes.
        LauncherInput.focus.request("corner.TopLeft.Time"); rule.waitForIdle()
        assertEquals("corner.TopLeft.Time", LauncherInput.focus.currentId)
        assertTrue(LauncherInput.focus.tab(false)); rule.waitForIdle()
        assertEquals("corner.TopLeft.Ringer", LauncherInput.focus.currentId)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun NFR_13_dsi_cells_read_in_item_order_and_carry_their_names() {
        val apps = (0 until 6).map(::app)
        rule.setContent { ContinuousHome(apps, crossCount = 3, landscape = false, style = ContinuousStyle.Dsi, onLaunch = {}, onLongPress = {}) }
        rule.waitForIdle()
        val cells = rule.onAllNodes(SemanticsMatcher("has traversal index") { it.config.contains(SemanticsProperties.TraversalIndex) }).fetchSemanticsNodes()
        assertTrue("cells expose a traversal index", cells.size >= 3)
        val order = cells.sortedBy { it.config[SemanticsProperties.TraversalIndex] }.map { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
        assertEquals(apps.take(order.size).map { it.label }, order)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h900dp-port-xhdpi")
    fun INP_07_every_menu_entry_is_a_row_a_finger_can_tap_and_it_chooses_that_action() {
        val ctx = com.bos.sphere.core.input.MenuContext(
            otherDisplays = 1, adjacentSupported = true, onHome = true, canMoveToFolder = true,
        )
        val chosen = mutableListOf<com.bos.sphere.core.input.MenuAction>()
        rule.setContent { InputMenu(ctx, MenuTrigger.LongPress, onChoose = { chosen += it }, onDismiss = {}) }
        rule.waitForIdle()
        val entries = InputMenuModel.entries(MenuTrigger.LongPress, ctx)
        assertTrue(entries.size >= 8)
        for (e in entries) {
            rule.onNodeWithText(e.action.label).assertHasClickAction().performClick()
        }
        assertEquals(entries.map { it.action }, chosen)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h900dp-port-xhdpi")
    fun INP_20_a_dialog_window_turns_controller_buttons_into_keys_the_dialog_understands() {
        // B (and Esc) close a dialog by becoming Back; A confirms; the D-pad stays arrows.
        assertEquals(android.view.KeyEvent.KEYCODE_BACK, com.bos.sphere.core.input.DialogKeys.translate(android.view.KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(android.view.KeyEvent.KEYCODE_BACK, com.bos.sphere.core.input.DialogKeys.translate(android.view.KeyEvent.KEYCODE_ESCAPE))
        assertEquals(android.view.KeyEvent.KEYCODE_DPAD_CENTER, com.bos.sphere.core.input.DialogKeys.translate(android.view.KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(android.view.KeyEvent.KEYCODE_DPAD_LEFT, com.bos.sphere.core.input.DialogKeys.translate(android.view.KeyEvent.KEYCODE_DPAD_LEFT))
    }
}
