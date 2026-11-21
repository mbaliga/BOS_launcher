package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import com.bos.sphere.core.input.HotCornerId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 6 audit: the real hot-corner pointer layer (INP-16), not only its tracker. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class W6HotCornerLayerUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before fun clean() { LauncherInput.overlayOpen = false; LauncherInput.hotCorner.value = null }

    @After fun reset() { LauncherInput.overlayOpen = false; LauncherInput.hotCorner.value = null }

    private fun host(activated: MutableList<HotCornerId>) {
        rule.runOnUiThread { LauncherInput.hotCorner.value = HotCornerBinding(100L, { "Go" }, { activated += it }) }
        rule.setContent { Box(Modifier.fillMaxSize().hotCorners().testTag("root")) }
        rule.waitForIdle()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_16_resting_in_a_corner_then_clicking_runs_it_but_a_click_before_the_dwell_or_a_finger_does_not() {
        val ran = mutableListOf<HotCornerId>()
        host(ran)
        rule.onNodeWithTag("root").performTouchInput { click(Offset(2f, 2f)) }
        rule.waitForIdle()
        assertEquals("a finger never activates a hot corner", emptyList<HotCornerId>(), ran)
        rule.onNodeWithTag("root").performMouseInput { moveTo(Offset(2f, 2f)); click(Offset(2f, 2f)) }
        rule.waitForIdle()
        assertEquals("a click before the dwell has passed is an ordinary click", emptyList<HotCornerId>(), ran)
        rule.onNodeWithTag("root").performMouseInput { moveTo(Offset(300f, 300f)); moveTo(Offset(2f, 2f)) }
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithTag("root").performMouseInput { click(Offset(2f, 2f)) }
        rule.waitForIdle()
        assertEquals(listOf(HotCornerId.TopLeft), ran)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_16_a_surface_above_the_home_owns_the_pointer_so_no_corner_reveals_or_runs_through_it() {
        val ran = mutableListOf<HotCornerId>()
        host(ran)
        LauncherInput.overlayOpen = true
        rule.onNodeWithTag("root").performMouseInput { moveTo(Offset(300f, 300f)); moveTo(Offset(2f, 2f)) }
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithTag("root").performMouseInput { click(Offset(2f, 2f)) }
        rule.waitForIdle()
        assertEquals(emptyList<HotCornerId>(), ran)
    }
}
