package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
}
