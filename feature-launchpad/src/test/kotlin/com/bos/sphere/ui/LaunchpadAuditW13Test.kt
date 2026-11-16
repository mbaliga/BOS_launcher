package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset

/** Wave 13 audit of the Launchpad stream: the card never sticks, row slides really slide, the indicator never widens spuriously. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class LaunchpadAuditW13Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val t0 = 1_710_000_000_000L
    private fun n(key: String) =
        HubNotification(id = key, key = key, source = "x", title = "Title $key", summary = "text $key", timestamp = t0, packageName = "demo.a", appLabel = "App A")

    private val dismissed = mutableListOf<String>()

    private fun setLaunchpad(items: List<HubNotification>, dir: LayoutDirection = LayoutDirection.Ltr) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
                LaunchpadContent(
                    items = items, rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.App), readKeys = emptySet(), hasAccess = true, now = t0,
                    onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = { dismissed += it.key },
                    onClearAll = {}, onAddRule = {}, onSetDefaultMode = {}, onRequestAccess = {}, zone = ZoneOffset.UTC,
                )
            }
        }
        rule.waitForIdle()
    }

    // ---- STR-01: an interrupted drag never leaves the card half open ----

    @Test fun STR_01_aDragCutShortByARotationSettlesInsteadOfSticking() {
        var state: LaunchpadRevealState? = null
        var slide by mutableStateOf(400f)
        rule.setContent {
            var open by remember { mutableStateOf(false) }
            val st = rememberLaunchpadReveal(open) { open = it }
            state = st
            Box(Modifier.fillMaxSize().testTag("host").launchpadEdgeDrag(st, true, slide))
        }
        rule.onNodeWithTag("host").performTouchInput { down(Offset(5f, 400f)); moveBy(Offset(60f, 0f), delayMillis = 300); moveBy(Offset(60f, 0f), delayMillis = 300) }
        rule.waitForIdle()
        assertTrue("dragging", state!!.dragging)
        // Rotation: the slide distance changes, so the gesture block restarts without an up event.
        Snapshot.withMutableSnapshot { slide = 300f }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        assertFalse("no longer dragging", state!!.dragging)
        assertEquals("sprang back, not stuck half open", 0f, state!!.progress, 0.02f)
        assertFalse(state!!.open)
    }

    // ---- STR-03: the slides themselves ----

    @Test fun STR_03_slidingARowLeftRunsThePrimaryAction() {
        setLaunchpad(listOf(n("a1"), n("a2")))
        rule.onNodeWithTag("lp.focus.a1").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf("a1"), dismissed)
    }

    @Test fun STR_03_slidingARowRightOpensThePaletteAndItStaysUntilChosenOrTapped() {
        setLaunchpad(listOf(n("a1")))
        rule.onNodeWithTag("lp.focus.a1").performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f) }
        rule.waitForIdle()
        rule.onNodeWithText("Snooze").assertIsDisplayed()
        assertTrue(dismissed.isEmpty())
    }

    @Test fun STR_03_inARightToLeftLayoutTheRightSlideStillUncoversThePaletteOnTheLeft() {
        setLaunchpad(listOf(n("a1")), LayoutDirection.Rtl)
        rule.onNodeWithTag("lp.focus.a1").performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f) }
        rule.waitForIdle()
        val bounds = rule.onNodeWithText("Snooze").getUnclippedBoundsInRoot()
        assertTrue("the palette cell sits in the uncovered left part, not under the row: $bounds", bounds.left < 200.dp)
    }

    // ---- STR-09: the indicator must not widen because the grouped list merely arrived ----

    @Test fun STR_09_aListThatAppearsOnlyWhenNotReadyIsNotNewItems() {
        var list by mutableStateOf(emptyList<HubNotification>())
        var ready by mutableStateOf(false)
        var headline: String? = "unset"
        rule.mainClock.autoAdvance = false
        rule.setContent { headline = rememberWaitingHeadline(list, expandMs = 1_000, ready = ready) }
        rule.mainClock.advanceTimeBy(100)
        Snapshot.withMutableSnapshot { list = listOf(n("a"), n("b")); ready = true }
        rule.mainClock.advanceTimeBy(100)
        assertNull("what was already waiting is not new once it is computed", headline)
        Snapshot.withMutableSnapshot { list = list + n("c").copy(timestamp = t0 + 5) }
        rule.mainClock.advanceTimeBy(100)
        assertEquals("title c".replace("title", "Title"), headline)
    }
}
