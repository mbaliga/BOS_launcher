package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.input.FocusDir
import com.bos.sphere.core.input.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose: the focus registry, mode changes and the three menu paths, driven by the test rule. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputFocusUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val menus = mutableListOf<String>()
    private val activations = mutableListOf<String>()

    private fun content() = rule.setContent {
        InputModeProvider {
            Row(Modifier.padding(8.dp)) {
                for (id in listOf("a", "b", "c")) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.testTag(id).size(80.dp).background(Color.DarkGray)
                            .launcherItem("t.$id", onActivate = { activations += id }, onMenu = { menus += id }),
                    )
                }
            }
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h300dp-port-xhdpi")
    fun INP_01_arrows_move_between_registered_items_and_tab_wraps() {
        content(); rule.waitForIdle()
        LauncherInput.focus.request("t.a"); rule.waitForIdle()
        assertEquals("t.a", LauncherInput.focus.currentId)
        assertTrue(LauncherInput.focus.move(FocusDir.Right)); rule.waitForIdle()
        assertEquals("t.b", LauncherInput.focus.currentId)
        assertTrue(LauncherInput.focus.tab(false)); rule.waitForIdle()
        assertEquals("t.c", LauncherInput.focus.currentId)
        assertTrue(LauncherInput.focus.tab(false)); rule.waitForIdle()
        assertEquals("t.a", LauncherInput.focus.currentId)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h300dp-port-xhdpi")
    fun INP_06_changing_input_mode_never_moves_focus() {
        content(); rule.waitForIdle()
        LauncherInput.focus.request("t.b"); rule.waitForIdle()
        for (m in listOf(InputMode.Pointer, InputMode.Touch, InputMode.Keyboard)) {
            when (m) { InputMode.Pointer -> LauncherInput.tracker.onPointer(); InputMode.Touch -> LauncherInput.tracker.onTouch(); InputMode.Keyboard -> LauncherInput.tracker.onKey() }
            rule.waitForIdle()
            assertEquals("t.b", LauncherInput.focus.currentId)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h300dp-port-xhdpi")
    fun INP_03_long_press_secondary_click_and_menu_key_open_the_same_menu() {
        content(); rule.waitForIdle()
        menus.clear()
        rule.onNodeWithTag("b").performMouseInput { moveTo(center); press(MouseButton.Secondary); release(MouseButton.Secondary) }
        rule.waitForIdle()
        assertEquals("secondary click", listOf("b"), menus.toList())
        LauncherInput.focus.request("t.b"); rule.waitForIdle()
        rule.onNodeWithTag("b").performKeyInput { keyDown(Key.Menu); keyUp(Key.Menu) }
        rule.waitForIdle()
        // Secondary click and Menu key each call the item's one onMenu callback, the same one its long-press calls on the real homes.
        assertEquals(listOf("b", "b"), menus)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h300dp-port-xhdpi")
    fun INP_01_enter_activates_the_focused_item() {
        content(); rule.waitForIdle()
        activations.clear()
        LauncherInput.focus.request("t.c"); rule.waitForIdle()
        rule.onNodeWithTag("c").performKeyInput { keyDown(Key.Enter) }
        assertEquals(listOf("c"), activations)
    }
}
