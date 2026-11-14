package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** INP-12: with "raise the keyboard" on for this orientation, the drawer's filter field takes focus as it opens. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerKeyboardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun focusedAfterOpening(wanted: Boolean): Boolean {
        LauncherInput.keyboardWanted.value = wanted
        var focused = false
        rule.setContent {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.onFocusChanged { focused = it.hasFocus }) { DrawerFilterBar(DrawerFilterState()) }
        }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        return focused
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_12_the_drawer_filter_takes_focus_when_the_keyboard_is_wanted() {
        assertTrue(focusedAfterOpening(true))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_12_the_drawer_filter_does_not_grab_focus_when_the_keyboard_is_not_wanted() {
        assertFalse(focusedAfterOpening(false))
    }
}
