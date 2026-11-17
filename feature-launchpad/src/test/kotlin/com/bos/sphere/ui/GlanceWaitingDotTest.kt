package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** STR-09, HOM-23, STR-13 (wave 17): the quiet waiting dot the Glance home draws instead of the pill. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class GlanceWaitingDotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun STR_09_theQuietDotShowsWhenSomethingWaitsOpensLaunchpadAndHasA48DpTarget() {
        var opened = 0
        rule.setContent { WaitingIndicatorView(count = 3, headline = null, onOpen = { opened++ }, quiet = true) }
        rule.onNodeWithTag("glance.waiting").assertIsDisplayed()
        val b = rule.onNodeWithTag("glance.waiting").fetchSemanticsNode().boundsInRoot
        assertEquals(48f * 2f, b.width, 1f); assertTrue(b.height >= 48f * 2f - 1f)
        rule.onNodeWithTag("glance.waiting").performClick()
        assertEquals(1, opened)
    }
    @Test fun STR_09_nothingWaitingDrawsNoDot() {
        rule.setContent { WaitingIndicatorView(count = 0, headline = null, onOpen = {}, quiet = true) }
        assertTrue(rule.onAllNodesWithTag("glance.waiting").fetchSemanticsNodes().isEmpty())
    }
    @Test fun STR_13_aPriorityAccentTintsTheQuietDotAndSaysSo() {
        rule.setContent { WaitingIndicatorView(count = 2, headline = null, onOpen = {}, priorityCount = 1, accent = Color(0xFFE53935), quiet = true) }
        rule.onNodeWithTag("glance.waiting").assertIsDisplayed()
        val d = rule.onNodeWithTag("glance.waiting").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].firstOrNull()
        assertEquals("2 waiting, 1 priority. Launchpad", d)
    }
}
