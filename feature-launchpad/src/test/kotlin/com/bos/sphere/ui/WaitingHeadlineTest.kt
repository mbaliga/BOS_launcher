package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.HubNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** STR-09: the indicator widens for a new item and always shrinks back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WaitingHeadlineTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun n(key: String, at: Long) = HubNotification(id = key, key = key, source = "S", title = "title $key", summary = "", timestamp = at, packageName = "p")

    @Test fun STR_09_theWidenedIndicatorShrinksBackEvenWhenTheListChangesWithoutANewItem() {
        var list by mutableStateOf(listOf(n("a", 1)))
        var headline: String? = "unset"
        rule.mainClock.autoAdvance = false
        rule.setContent { headline = rememberWaitingHeadline(list, expandMs = 1_000) }
        rule.mainClock.advanceTimeBy(100)
        assertNull("the items already waiting when it first shows are not new", headline)

        Snapshot.withMutableSnapshot { list = listOf(n("a", 1), n("b", 2)) }
        rule.mainClock.advanceTimeBy(100)
        assertEquals("title b", headline)

        // The new item is dismissed while the indicator is wide: the list changes and nothing in it is new.
        Snapshot.withMutableSnapshot { list = listOf(n("a", 1)) }
        rule.mainClock.advanceTimeBy(100)
        assertEquals("title b", headline)
        rule.mainClock.advanceTimeBy(1_200)
        assertNull("it must not stay wide for good", headline)
    }

    @Test fun STR_09_aSecondNewItemRestartsTheTimerAndShowsTheNewestTitle() {
        var list by mutableStateOf(listOf(n("a", 1)))
        var headline: String? = null
        rule.mainClock.autoAdvance = false
        rule.setContent { headline = rememberWaitingHeadline(list, expandMs = 1_000) }
        rule.mainClock.advanceTimeBy(100)
        Snapshot.withMutableSnapshot { list = list + n("b", 2) }
        rule.mainClock.advanceTimeBy(800)
        Snapshot.withMutableSnapshot { list = list + n("c", 3) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals("title c", headline)
        rule.mainClock.advanceTimeBy(700)
        assertNull(headline)
    }
}

/** STR-01, STR-09: the left-edge indicator stays on the physical left, where the Launchpad opens from, in a right-to-left language. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class WaitingIndicatorRtlTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun STR_09_inARightToLeftLanguageTheIndicatorStillHugsTheLeftEdgeAndReadsLeftToRight() {
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    WaitingIndicatorView(
                        3, "Hello", {},
                        androidx.compose.ui.Modifier.align(androidx.compose.ui.BiasAbsoluteAlignment(-1f, 0f)).testTag("ind"),
                    )
                }
            }
        }
        rule.onNodeWithTag("ind").assertLeftPositionInRootIsEqualTo(0.dp)
        val countX = rule.onNodeWithText("3").fetchSemanticsNode().positionInRoot.x
        val headlineX = rule.onNodeWithText("Hello").fetchSemanticsNode().positionInRoot.x
        assertTrue("the count comes first, on the left", countX < headlineX)
    }
}
