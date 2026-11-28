package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bos.sphere.core.input.ShortcutRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
@OptIn(ExperimentalTestApi::class)
class LaunchpadFocusAndShortcutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun STR_01_ctrlNOpensLaunchpadFromAFocusedHomeSurface() {
        var opened = 0
        rule.setContent { Box(Modifier.fillMaxSize().testTag("home").launchpadShortcut(ShortcutRegistry()) { opened++ }.focusable()) }
        rule.onNodeWithTag("home").requestFocus()
        rule.onNodeWithTag("home").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.N); keyUp(Key.CtrlLeft) }
        assertEquals(1, opened)
        rule.onNodeWithTag("home").performKeyInput { pressKey(Key.N) }
        assertEquals("N alone must not open it", 1, opened)
    }

    @Test fun STR_08_focusReturnsToTheRegisteredHomeElementThroughTheInputGraph() {
        // The app bridges the trail to the home's focus registry by id; here a fake registry stands in for it.
        val registry = mutableMapOf<String, FocusRequester>()
        var current: String? = null
        val trail = LaunchpadFocusTrail().also {
            it.currentId = { current }
            it.requestId = { id -> registry[id]?.let { r -> r.requestFocus(); true } ?: false }
        }
        val a = FocusRequester(); val b = FocusRequester()
        registry["a"] = a; registry["b"] = b
        var showPad by mutableStateOf(false)
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalLaunchpadFocusTrail provides trail) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.testTag("A").focusRequester(a).focusable())
                    Box(Modifier.testTag("B").focusRequester(b).focusable())
                    if (showPad) RestoreFocusOnExit()
                }
            }
        }
        rule.onNodeWithTag("B").requestFocus(); rule.waitForIdle()
        current = "b"
        showPad = true; rule.waitForIdle()            // Launchpad opens: the trail notes "b"
        current = null
        rule.onNodeWithTag("A").requestFocus(); rule.waitForIdle()   // focus moves while Launchpad is up
        showPad = false; rule.waitForIdle()           // Launchpad leaves: "b" gets focus back
        rule.onNodeWithTag("B").assertIsFocused()
        assertTrue(true)
    }

}

