package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.catalog.WidgetStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetStackViewTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(sdk = [35])
    fun HOM_14_keyMappingStepsForwardAndBack() {
        val s = WidgetStack(listOf("a", "b", "c"))
        assertEquals(1, s.forComposeKey(Key.DirectionRight)!!.active)
        assertEquals(1, s.forComposeKey(Key.PageDown)!!.active)
        assertEquals(2, s.forComposeKey(Key.PageUp)!!.active) // wraps back
        assertNull(s.forComposeKey(Key.A))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h400dp-port-xhdpi")
    fun HOM_14_swipeSwitchesWidgetAndOnlyTheActiveOneIsShown() {
        var stack by mutableStateOf(WidgetStack(listOf("one", "two")))
        rule.setContent { WidgetStackView(stack, { stack = it }, Modifier.size(300.dp)) { Text("widget $it") } }
        rule.onNodeWithText("widget one").assertIsDisplayed()
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(1, stack.active)
        rule.onNodeWithText("widget two").assertIsDisplayed()
    }

    private fun host(initial: WidgetStack, hungryChild: Boolean = false): () -> WidgetStack {
        var stack by mutableStateOf(initial)
        rule.setContent {
            WidgetStackView(stack, { stack = it }, Modifier.size(300.dp).testTag("stack")) {
                // A hosted widget view takes every drag that starts on it; this child does the same.
                Box(Modifier.fillMaxSize().then(if (hungryChild) Modifier.pointerInput(Unit) { detectDragGestures { c, _ -> c.consume() } } else Modifier)) { Text("widget $it") }
            }
        }
        return { stack }
    }

    private fun swipe(from: Offset, to: Offset) {
        rule.onNodeWithTag("stack").performTouchInput { swipe(from, to, 200) }
        rule.waitForIdle()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h400dp-port-xhdpi")
    fun HOM_14_swipeUpAndLeftGoForwardDownAndRightGoBack() {
        val now = host(WidgetStack(listOf("a", "b", "c"), active = 1))
        rule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        rule.waitForIdle(); assertEquals(2, now().active)
        rule.onNodeWithTag("stack").performTouchInput { swipeDown() }
        rule.waitForIdle(); assertEquals(1, now().active)
        rule.onNodeWithTag("stack").performTouchInput { swipeLeft() }
        rule.waitForIdle(); assertEquals(2, now().active)
        rule.onNodeWithTag("stack").performTouchInput { swipeRight() }
        rule.waitForIdle(); assertEquals(1, now().active)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h400dp-port-xhdpi")
    fun HOM_14_aShortDragDoesNotSwitchAndTheStackWrapsAtTheEnds() {
        val now = host(WidgetStack(listOf("a", "b")))
        swipe(Offset(150f, 150f), Offset(130f, 150f)) // far below the 48dp threshold
        assertEquals(0, now().active)
        rule.onNodeWithTag("stack").performTouchInput { swipeLeft() }
        rule.waitForIdle(); assertEquals(1, now().active)
        rule.onNodeWithTag("stack").performTouchInput { swipeLeft() }
        rule.waitForIdle(); assertEquals(0, now().active)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h400dp-port-xhdpi")
    fun HOM_14_theBottomStripSwitchesEvenWhenTheHostedWidgetTakesEveryDrag() {
        val now = host(WidgetStack(listOf("a", "b", "c")), hungryChild = true)
        rule.onNodeWithTag("stack").performTouchInput { swipe(Offset(width - 60f, height - 20f), Offset(60f, height - 20f), 200) }
        rule.waitForIdle()
        assertEquals(1, now().active)
        rule.onNodeWithTag("stack").performTouchInput { swipe(Offset(60f, height - 20f), Offset(width - 60f, height - 20f), 200) }
        rule.waitForIdle()
        assertEquals(0, now().active)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h400dp-port-xhdpi")
    fun HOM_14_theEdgeStripSwitchesWithAVerticalSwipe() {
        val now = host(WidgetStack(listOf("a", "b", "c")), hungryChild = true)
        rule.onNodeWithTag("stack").performTouchInput { swipe(Offset(width - 10f, height * 0.6f), Offset(width - 10f, height * 0.1f), 200) }
        rule.waitForIdle()
        assertEquals(1, now().active)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h400dp-port-xhdpi")
    fun HOM_14_aSwipeInTheMiddleOfAWidgetThatTakesDragsDoesNotSwitch() {
        // The limit the strips exist for: the public API gives no way to take a drag back from a hosted view.
        val now = host(WidgetStack(listOf("a", "b")), hungryChild = true)
        rule.onNodeWithTag("stack").performTouchInput { swipe(Offset(width * 0.7f, height * 0.4f), Offset(width * 0.1f, height * 0.4f), 200) }
        rule.waitForIdle()
        assertEquals(0, now().active)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h400dp-port-xhdpi")
    fun HOM_14_aSingleWidgetStackHasNoStripsSoTheWidgetKeepsAllItsTouches() {
        host(WidgetStack(listOf("only")))
        rule.onNodeWithTag(STRIP_BOTTOM_TAG).assertDoesNotExist()
        rule.onNodeWithTag(STRIP_END_TAG).assertDoesNotExist()
    }
}
