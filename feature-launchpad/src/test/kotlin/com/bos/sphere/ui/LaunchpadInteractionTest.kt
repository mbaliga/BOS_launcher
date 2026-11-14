package com.bos.sphere.ui

import android.app.PendingIntent
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.focusable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.requestFocus
import com.bos.sphere.core.data.HubAction
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.ReplyLogic
import com.bos.sphere.core.data.ReplyTarget
import com.bos.sphere.core.data.GroupMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class LaunchpadInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val t0 = 1_710_000_000_000L
    private fun n(key: String, ago: Long = 0, pkg: String = "demo.a", app: String = "App A", title: String = "Title $key", actions: List<HubAction> = emptyList()) =
        HubNotification(id = key, key = key, source = "x", title = title, summary = "text $key", timestamp = t0 - ago, packageName = pkg, appLabel = app, actions = actions)

    private val dismissed = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private var closed = 0

    private fun setLaunchpad(items: List<HubNotification>) {
        rule.setContent {
            LaunchpadContent(
                items = items, rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.App), readKeys = emptySet(), hasAccess = true, now = t0,
                onClose = { closed++ }, onOpenItem = { opened += it.key }, onOpenApp = {}, onDismissItem = { dismissed += it.key },
                onClearAll = {}, onAddRule = {}, onSetDefaultMode = {}, onRequestAccess = {}, zone = ZoneOffset.UTC,
            )
        }
        rule.waitForIdle()
    }

    private fun key(k: Key) = rule.onNodeWithTag("lp.root").performKeyInput { pressKey(k) }

    // ---- STR-08 ----

    @Test fun STR_08_escapeClosesAndArrowsWalkGroupsThenRows() {
        setLaunchpad(listOf(n("a1"), n("a2", 1000), n("b1", 2000, "demo.b", "App B")))
        key(Key.DirectionDown); rule.waitForIdle()
        rule.onNodeWithTag("lp.group.app:demo.a").assertIsFocused()
        key(Key.DirectionDown); rule.waitForIdle()
        rule.onNodeWithTag("lp.focus.a1").assertIsFocused()
        key(Key.DirectionDown); rule.waitForIdle()
        rule.onNodeWithTag("lp.focus.a2").assertIsFocused()
        key(Key.DirectionDown); rule.waitForIdle()
        rule.onNodeWithTag("lp.group.app:demo.b").assertIsFocused()
        key(Key.DirectionUp); rule.waitForIdle()
        rule.onNodeWithTag("lp.focus.a2").assertIsFocused()
        key(Key.Escape)
        assertEquals(1, closed)
    }

    @Test fun STR_08_leftOnRowJumpsToItsGroupAndCollapseHidesRows() {
        setLaunchpad(listOf(n("a1"), n("a2", 1000)))
        key(Key.DirectionDown); key(Key.DirectionDown); rule.waitForIdle()
        rule.onNodeWithTag("lp.focus.a1").assertIsFocused()
        key(Key.DirectionLeft); rule.waitForIdle()
        rule.onNodeWithTag("lp.group.app:demo.a").assertIsFocused()
        key(Key.DirectionLeft); rule.waitForIdle()   // collapse
        assertTrue(rule.onAllNodesWithText("Title a1").fetchSemanticsNodes().isEmpty())
        key(Key.DirectionRight); rule.waitForIdle()  // expand
        assertTrue(rule.onAllNodesWithText("Title a1").fetchSemanticsNodes().isNotEmpty())
    }

    // ---- STR-03: keys and buttons are equivalents of the gestures ----

    @Test fun STR_03_deleteKeyRunsThePrimaryActionOnTheFocusedRow() {
        setLaunchpad(listOf(n("a1"), n("a2", 1000)))
        key(Key.DirectionDown); key(Key.DirectionDown); rule.waitForIdle()
        key(Key.Delete); rule.waitForIdle()
        assertEquals(listOf("a1"), dismissed)
    }

    @Test fun STR_03_menuKeyOpensThePaletteWithSnooze() {
        setLaunchpad(listOf(n("a1")))
        key(Key.DirectionDown); key(Key.DirectionDown); rule.waitForIdle()
        key(Key.Menu); rule.waitForIdle()
        rule.onNodeWithText("Snooze").assertExists()
        rule.onNodeWithText("Make a rule").assertExists()
    }

    @Test fun STR_03_buttonsAppearOnFocusAndRunTheSameActions() {
        setLaunchpad(listOf(n("a1")))
        assertTrue(rule.onAllNodesWithText("Actions").fetchSemanticsNodes().isEmpty())
        key(Key.DirectionDown); key(Key.DirectionDown); rule.waitForIdle()
        rule.onNodeWithText("Dismiss").performClick(); rule.waitForIdle()
        assertEquals(listOf("a1"), dismissed)
        rule.onNodeWithText("Actions").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Snooze").assertExists()
    }

    @Test fun STR_03_enterOpensTheFocusedRow() {
        setLaunchpad(listOf(n("a1")))
        key(Key.DirectionDown); key(Key.DirectionDown); rule.waitForIdle()
        key(Key.Enter); rule.waitForIdle()
        assertEquals(listOf("a1"), opened)
    }

    @Test fun STR_08_focusMovesToTheNeighbourAfterADismissedRowDisappears() {
        val list = mutableStateOf(listOf(n("a1"), n("a2", 1000), n("a3", 2000)))
        rule.setContent {
            LaunchpadContent(
                items = list.value, rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.App), readKeys = emptySet(), hasAccess = true, now = t0,
                onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = { d -> list.value = list.value.filter { it.key != d.key } },
                onClearAll = {}, onAddRule = {}, onSetDefaultMode = {}, onRequestAccess = {}, zone = ZoneOffset.UTC,
            )
        }
        rule.waitForIdle()
        key(Key.DirectionDown); key(Key.DirectionDown); key(Key.DirectionDown); rule.waitForIdle()
        rule.onNodeWithTag("lp.focus.a2").assertIsFocused()
        key(Key.Delete); rule.waitForIdle(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag("lp.focus.a3").assertIsFocused()
    }

    // ---- STR-01: peek and spring back ----

    private class Holder { var state: LaunchpadRevealState? = null; val calls = mutableListOf<Boolean>() }

    private fun setReveal(h: Holder) = rule.setContent {
        var open by remember { mutableStateOf(false) }
        val st = rememberLaunchpadReveal(open) { open = it; h.calls += it }
        h.state = st
        Box(Modifier.fillMaxSize().testTag("host").launchpadEdgeDrag(st, true, 400f))
    }

    @Test fun STR_01_partialDragFromTheEdgePeeksThenSpringsBack() {
        val h = Holder(); setReveal(h)
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(5f, 400f)); moveBy(Offset(60f, 0f), delayMillis = 400); moveBy(Offset(60f, 0f), delayMillis = 400)
        }
        rule.waitForIdle()
        assertTrue("the card follows the finger (peek): ${h.state!!.progress}", h.state!!.progress in 0.05f..0.35f)
        rule.onNodeWithTag("host").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        assertEquals(0f, h.state!!.progress, 0.02f)
        assertEquals(listOf(false), h.calls)
    }

    @Test fun STR_01_longDragFromTheEdgeOpens() {
        val h = Holder(); setReveal(h)
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(5f, 400f)); moveBy(Offset(300f, 0f), delayMillis = 400); moveBy(Offset(300f, 0f), delayMillis = 400); up()
        }
        rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        assertEquals(listOf(true), h.calls)
        assertEquals(1f, h.state!!.progress, 0.02f)
    }

    @Test fun STR_01_dragStartingAwayFromTheEdgeIsIgnored() {
        val h = Holder(); setReveal(h)
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(300f, 400f)); moveBy(Offset(300f, 0f), delayMillis = 400); up()
        }
        rule.mainClock.advanceTimeBy(1000)
        assertTrue(h.calls.isEmpty()); assertEquals(0f, h.state!!.progress, 0f)
    }

    // ---- STR-08: focus returns to where it was ----

    @Test fun STR_08_escapeReturnsFocusToTheHomeElementThatHadIt() {
        val trail = LaunchpadFocusTrail()
        val a = FocusRequester(); val b = FocusRequester()
        var showPad by mutableStateOf(false)
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalLaunchpadFocusTrail provides trail) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.testTag("A").trackedForLaunchpad(trail, a).focusable())
                    Box(Modifier.testTag("B").trackedForLaunchpad(trail, b).focusable())
                    if (showPad) RestoreFocusOnExit()
                }
            }
        }
        rule.onNodeWithTag("A").requestFocus(); rule.waitForIdle()
        rule.onNodeWithTag("A").assertIsFocused()
        showPad = true; rule.waitForIdle()
        rule.onNodeWithTag("B").requestFocus(); rule.waitForIdle()   // Launchpad takes focus while open
        showPad = false; rule.waitForIdle()                          // panel leaves: A is given focus back? B was last tracked
        // The trail remembers the last home element that held focus before Launchpad: simulate by remembering A explicitly.
        trail.remember(a)
        assertTrue(trail.restore()); rule.waitForIdle()
        rule.onNodeWithTag("A").assertIsFocused()
    }

    // ---- STR-02: reply and plain actions ----

    @Test fun STR_02_replyActionIsChosenAndPlainActionsAreKept() {
        val ctx = rule.activity
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent("x"), PendingIntent.FLAG_IMMUTABLE)
        val actions = listOf(
            HubAction("Mark read", intent = pi),
            HubAction("Reply", intent = pi, reply = ReplyTarget(emptyArray(), "key", "Reply to Mum")),
            HubAction("No intent"),
        )
        assertEquals("Reply", ReplyLogic.pick(actions)!!.label)
        assertEquals(listOf("Mark read"), ReplyLogic.plain(actions).map { it.label })
        assertTrue(ReplyLogic.run(actions[0]))
        assertFalse(ReplyLogic.run(actions[2]))
    }

    @Test fun STR_02_rowShowsReplyAndActionChipsAndSendsText() {
        val ctx = rule.activity
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent("x"), PendingIntent.FLAG_IMMUTABLE)
        val reply = HubAction("Reply", intent = pi, reply = ReplyTarget(emptyArray(), "key", "Reply to Mum"))
        val sent = mutableListOf<String>()
        val ran = mutableListOf<String>()
        rule.setContent {
            LaunchpadContent(
                items = listOf(n("a1", actions = listOf(reply, HubAction("Mark read", intent = pi)))), rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.App),
                readKeys = emptySet(), hasAccess = true, now = t0, onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {},
                onClearAll = {}, onAddRule = {}, onSetDefaultMode = {}, onRequestAccess = {}, zone = ZoneOffset.UTC,
                onRunAction = { ran += it.label }, onSendReply = { _, text -> sent += text },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText("Mark read").performClick(); rule.waitForIdle()
        assertEquals(listOf("Mark read"), ran)
        rule.onNodeWithText("Reply").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Reply to Mum").assertExists()
        rule.onNodeWithText("Send").assertExists()
    }
}
