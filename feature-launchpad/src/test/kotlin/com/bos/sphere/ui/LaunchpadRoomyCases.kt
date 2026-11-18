package com.bos.sphere.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** STR-02: the roomy, fully expanded notification list and the section strip, in one orientation. */
abstract class LaunchpadRoomyCases(private val orientation: String) : LaunchpadShots() {
    private fun n(key: String, pkg: String, app: String, title: String, text: String, ts: Long, conv: String) =
        HubNotification(id = key, key = key, source = "x", title = title, summary = text, timestamp = ts, packageName = pkg, appLabel = app, conversation = conv, sender = title, isMessaging = true)

    /** The owner's build-43 shot: one live chat, then many single-line conversations that used to be folded into grey titles. */
    private val feed = listOf(
        n("w1", "demo.wa", "WhatsApp", "Garv Sachdeva", "Liked your status", at(11, 40), "Garv Sachdeva"),
        n("w2", "demo.wa", "WhatsApp", "Garv Sachdeva", "Are you free tonight? I wanted to talk about the trip, and the tickets are almost gone", at(11, 50), "Garv Sachdeva"),
        n("w3", "demo.wa", "WhatsApp", "Garv Sachdeva", "Call me", at(11, 55), "Garv Sachdeva"),
        n("s1", "demo.sms", "Messages", "57575022", "Your code is 4411", at(11, 0), "57575022"),
        n("r1", "demo.reddit", "Reddit", "IndiaFashionCommunity", "My new top collection, which photo is the best?", at(10, 20), "IndiaFashionCommunity"),
        n("c1", "demo.ai", "Claude", "Claude responded", "Here is the summary you asked for", at(9, 0), "Claude responded"),
        n("c2", "demo.ai", "Claude", "Claude responded", "And the second part", at(9, 5), "Claude responded"),
    )

    protected fun render(name: String, dark: Boolean, openItem: ((HubNotification) -> Boolean)? = null, onClose: () -> Unit = {}): Double {
        rule.setContent {
            Themed(dark) {
                Box(Modifier.fillMaxSize()) {
                    LaunchpadContent(
                        items = feed, rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.AppConversation), readKeys = emptySet(), hasAccess = true, now = now,
                        onClose = onClose, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                        onSetDefaultMode = {}, onRequestAccess = {}, systemState = system, zone = zone, openItem = openItem,
                    )
                }
            }
        }
        return shoot("launchpad-roomy-$orientation-$name-${if (dark) "dark" else "light"}")
    }

    private fun scrollTo(text: String) {
        rule.onNodeWithTag("lp.list").performScrollToNode(androidx.compose.ui.test.hasText(text))
        rule.waitForIdle()
    }

    @Test fun STR_02_roomy_light() { render("list", dark = false); assertTrue(rule.onAllNodesWithText("Garv Sachdeva").fetchSemanticsNodes().isNotEmpty()) }
    @Test fun STR_02_roomy_dark() { render("list", dark = true); assertTrue(rule.onAllNodesWithText("Garv Sachdeva").fetchSemanticsNodes().isNotEmpty()) }

    @Test fun STR_02_noAccordion_everyConversationShowsItsRowAtOnce() {
        render("all", dark = false)
        // The old column showed only the selected section's rows; now a later section's row sits in the same list.
        val titles = listOf("57575022", "IndiaFashionCommunity", "Claude responded")
        titles.forEach { t -> scrollTo(t); assertTrue("$t row is shown", rule.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()) }
        scrollTo("And the second part"); rule.onNodeWithText("And the second part").assertIsDisplayed()
    }

    // Updated for wave 25 (owner item 2): the hub's flat rows have a small 32 dp badge and no gap between rows (a divider instead).
    @Test fun STR_02_row_isAtLeast72dpTall_withA32dpBadge() {
        render("height", dark = false)
        scrollTo("Your code is 4411")
        val h = rule.onNodeWithTag("lp.row.s1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("row height ${h.bottom - h.top} is under 72 dp", (h.bottom - h.top) >= 72.dp)
        assertEquals(32, RoomyList.ICON_DP); assertEquals(72, RoomyList.ROW_MIN_DP)
        assertEquals(16, RoomyList.H_PAD_DP); assertEquals(12, RoomyList.V_PAD_DP); assertEquals(0, RoomyList.ROW_GAP_DP)
    }

    @Test fun STR_02_conversation_showsLatestWithMoreThatExpandsInPlace() {
        render("more", dark = false)
        scrollTo("Call me")
        assertTrue("the latest message leads", rule.onAllNodesWithText("Call me").fetchSemanticsNodes().isNotEmpty())
        assertTrue("older messages are folded by default", rule.onAllNodesWithText("Liked your status").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("+2 more").performClick(); rule.waitForIdle(); scrollTo("Liked your status")
        assertTrue("older messages appear in place", rule.onAllNodesWithText("Liked your status").fetchSemanticsNodes().isNotEmpty())
        scrollTo("Show fewer"); rule.onNodeWithText("Show fewer").performClick(); rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Liked your status").fetchSemanticsNodes().isEmpty())
    }

    @Test fun STR_02_singleNotificationsAreNeverHidden() {
        render("single", dark = false)
        scrollTo("Your code is 4411")
        assertTrue(rule.onAllNodesWithText("Your code is 4411").fetchSemanticsNodes().isNotEmpty())
        assertTrue("a single notification has no +N more", rule.onAllNodesWithText("+0 more").fetchSemanticsNodes().isEmpty())
    }

    @Test fun STR_02_strip_tapScrollsToTheSection() {
        render("strip", dark = false)
        rule.onNodeWithTag("lp.strip").assertIsDisplayed()
        // The strip itself scrolls sideways; bring the last title into view.
        rule.onNodeWithTag("lp.strip").performScrollToNode(hasTestTag("lp.strip.appconv:demo.ai:claude responded"))
        rule.onNodeWithTag("lp.strip.appconv:demo.ai:claude responded").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("lp.group.appconv:demo.ai:claude responded").assertIsDisplayed()
    }

    @Test fun STR_02_tap_failedOpenKeepsLaunchpadOpenAndSaysSo() {
        var closed = 0
        render("fail", dark = false, openItem = { false }, onClose = { closed++ })
        scrollTo("Your code is 4411")
        rule.onNodeWithText("Your code is 4411").performClick(); rule.waitForIdle()
        assertEquals(0, closed)
        rule.onNodeWithText("Couldn't open Messages").assertIsDisplayed()
    }

    @Test fun STR_02_tap_successfulOpenIsHandedToTheHost() {
        val opened = ArrayList<String>()
        render("ok", dark = false, openItem = { opened += it.key; true })
        scrollTo("Your code is 4411")
        rule.onNodeWithText("Your code is 4411").performClick(); rule.waitForIdle()
        assertEquals(listOf("s1"), opened)
        assertTrue(rule.onAllNodesWithText("Couldn't open Messages").fetchSemanticsNodes().isEmpty())
    }
}
