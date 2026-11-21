package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.bos.sphere.core.data.HubNotification
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** INP-07: the unread rail is a touch way into Launchpad (the edge drag is STR_01 in LaunchpadInteractionTest). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
class LaunchpadTouchPathTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val unread = listOf(
        HubNotification(id = "1", key = "1", source = "x", title = "Hi", summary = "there", timestamp = System.currentTimeMillis(), packageName = "demo.a", appLabel = "App A"),
        HubNotification(id = "2", key = "2", source = "x", title = "Yo", summary = "again", timestamp = System.currentTimeMillis(), packageName = "demo.b", appLabel = "App B"),
    )

    @Test fun INP_07_tapping_the_unread_rail_opens_launchpad() {
        var opened = 0
        rule.setContent { LaunchpadRail(onOpen = { opened++ }, source = flowOf(unread), accessOverride = true) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("2 unread. Comms").performClick()
        assertEquals(1, opened)
    }

    @Test fun INP_07_swiping_right_on_the_unread_rail_opens_launchpad() {
        var opened = 0
        rule.setContent { LaunchpadRail(onOpen = { opened++ }, source = flowOf(unread), accessOverride = true) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("2 unread. Comms").performTouchInput { swipeRight() }
        assertEquals(1, opened)
    }

    @Test fun INP_07_the_rail_draws_nothing_without_access_so_there_is_nothing_to_tap() {
        var opened = 0
        rule.setContent { LaunchpadRail(onOpen = { opened++ }, source = flowOf(unread), accessOverride = false) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("2 unread. Comms").assertDoesNotExistCompat()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
}
