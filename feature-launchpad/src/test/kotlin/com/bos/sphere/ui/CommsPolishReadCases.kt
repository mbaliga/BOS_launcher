package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import com.bos.sphere.core.data.AppPolicy
import com.bos.sphere.core.data.CommsScaleSettings
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.NotificationHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Executor

/**
 * Wave 30 (commsPolish): read and unread rows are told apart at a glance in every Comms list (owner: "there is no visible distinction between read and
 * unread"). Unread: the headline in the full ink, SemiBold, the accent time, the full icon, a solid 8 dp dot in the left margin; read: the headline muted,
 * normal weight, dimmer second line and time, the icon at 55 percent, no dot. Screenshots: build/screenshots/comms-read-style-*.png.
 */
@OptIn(ExperimentalTestApi::class)
abstract class CommsPolishReadCases(private val orientation: String) : LaunchpadShots() {
    private var liveItems by mutableStateOf(grouped)
    private var readSet by mutableStateOf(setOf<String>())
    private var mode by mutableStateOf(GroupMode.Flat)
    private var policies by mutableStateOf(emptyList<AppPolicy>())
    private lateinit var store: NotificationHistoryStore
    private lateinit var hub: HubState

    @Before fun seed() {
        CommsScaleSettings.resetForTest()
        liveItems = grouped; readSet = emptySet(); mode = GroupMode.Flat; policies = emptyList()
        store = NotificationHistoryStore(File(java.nio.file.Files.createTempDirectory("commspolish").toFile(), "usage/h.log"), Executor { it.run() })
        store.observe(grouped)
        hub = HubState(HubPrefs(null), store, true, liveAll = true)
    }

    @Composable
    private fun Stage(dark: Boolean) {
        CompositionLocalProvider(LocalLp provides if (dark) LpDark else LpLight) {
            Box(Modifier.fillMaxSize().background(LocalLp.current.bg)) {
                val menu = rememberLaunchpadMenu()
                LaunchpadContent(
                    items = liveItems, rules = emptyList(), prefs = LaunchpadPrefs(mode), readKeys = readSet, hasAccess = true, now = now,
                    onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                    onSetDefaultMode = {}, onRequestAccess = {}, zone = zone, menu = menu, systemState = system, hub = hub, viewSwitcher = { HubSwitcher(hub) },
                    policies = policies, openApp = { true },
                )
            }
        }
    }

    private fun render(dark: Boolean = true) { rule.setContent { Stage(dark) }; rule.waitForIdle() }
    private fun edit(block: () -> Unit) { rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { block() } }; rule.waitForIdle() }
    private fun markRead(vararg keys: String) = edit { keys.forEach { store.markRead(it) }; readSet = readSet + keys }
    private fun leaveShade(vararg keys: String) = edit { liveItems = liveItems.filter { it.key !in keys }; store.observe(liveItems) }
    private fun hasDot(key: String) = rule.onAllNodesWithTag("lp.row.unreadDot.$key", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun present(key: String) = rule.onAllNodesWithTag("lp.row.$key").fetchSemanticsNodes().isNotEmpty()
    private fun state(key: String) = rule.onNodeWithTag("lp.focus.$key").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
    private fun style(tag: String): androidx.compose.ui.text.TextStyle {
        val out = ArrayList<TextLayoutResult>()
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out[0].layoutInput.style
    }
    private fun titleWeight(key: String) = style("lp.title.$key").fontWeight
    private fun titleColor(key: String) = style("lp.title.$key").color
    private fun timeColor(key: String) = style("lp.time.$key").color
    private fun subColor(key: String) = style("lp.preview.$key").color
    private fun pinchIn() { rule.onNodeWithTag("lp.root").performTouchInput { pinch(center + Offset(-200f, 0f), center + Offset(-40f, 0f), center + Offset(200f, 0f), center + Offset(40f, 0f)) }; rule.waitForIdle() }
    private fun pinchOut() { rule.onNodeWithTag("lp.root").performTouchInput { pinch(center + Offset(-40f, 0f), center + Offset(-200f, 0f), center + Offset(40f, 0f), center + Offset(200f, 0f)) }; rule.waitForIdle() }

    // ---- the style of a row ----

    @Test fun CMP_10_unreadRowsHaveTheDotTheHeavyFullInkHeadlineAndTheAccentTimeReadRowsNeither() {
        render()
        markRead("m1", "c1")
        val lp = LpDark
        for (k in listOf("m2", "m3", "e1")) {
            assertTrue("$k has the dot", hasDot(k))
            assertEquals("$k headline weight", FontWeight.SemiBold, titleWeight(k))
            assertEquals("$k headline is the full ink", lp.ink, titleColor(k))
            assertEquals("$k second line", lp.dim, subColor(k))
            assertEquals("$k time is the accent ink", CommsRowTone.accentInk(BlueLight, lp.bg), timeColor(k))
            assertEquals("$k state", "Unread", state(k))
        }
        for (k in listOf("m1", "c1")) {
            assertFalse("$k has no dot", hasDot(k))
            assertEquals("$k headline weight", FontWeight.Normal, titleWeight(k))
            assertNotEquals("$k headline is muted", lp.ink, titleColor(k))
            assertNotEquals("$k second line is dimmer", lp.dim, subColor(k))
            assertEquals("$k state", "Read", state(k))
        }
        assertNotEquals("the unread and the read headline differ in colour", titleColor("m2"), titleColor("m1"))
    }

    @Test fun CMP_10_theDotSitsInTheLeftMarginOnTheCentreOfTheIcon() {
        render()
        val dot = rule.onNodeWithTag("lp.row.unreadDot.m1", true).fetchSemanticsNode().boundsInRoot
        val icon = rule.onNodeWithTag("lp.badge.m1", true).fetchSemanticsNode().boundsInRoot
        val d = rule.activity.resources.displayMetrics.density
        assertEquals("8 dp", 8f * d, dot.width, 1f)
        assertTrue("left of the icon, inside the margin: ${dot.right} < ${icon.left}", dot.right < icon.left)
        assertEquals("centred on the icon", (icon.top + icon.bottom) / 2f, (dot.top + dot.bottom) / 2f, 1f)
        assertEquals("centre 10.9 dp from the edge", CommsStyle.UNREAD_DOT_CENTRE_X_DP * d, (dot.left + dot.right) / 2f, 1f)
    }

    @Test fun CMP_11_markingARowReadFlipsItsStyleLive() {
        render()
        assertTrue(hasDot("m3")); assertEquals(FontWeight.SemiBold, titleWeight("m3"))
        markRead("m3")
        assertFalse(hasDot("m3")); assertEquals(FontWeight.Normal, titleWeight("m3")); assertEquals("Read", state("m3"))
        assertTrue("the others are untouched", hasDot("m2"))
    }

    @Test fun CMP_12_theUnreadFilterShowsOnlyDottedRowsAndTheReadFilterOnlyUndottedOnes() {
        render()
        markRead("m1", "c1")
        pinchIn() // toward Unread
        listOf("m2", "m3", "e1").forEach { assertTrue("$it listed and dotted", present(it) && hasDot(it)) }
        listOf("m1", "c1").forEach { assertFalse("$it hidden", present(it)) }
        pinchOut(); pinchOut() // back to All, then Read
        listOf("m1", "c1").forEach { assertTrue("$it listed", present(it)); assertFalse("$it undotted", hasDot(it)) }
        listOf("m2", "m3", "e1").forEach { assertFalse("$it hidden", present(it)) }
    }

    @Test fun CMP_13_historyOnlyRowsFollowTheSameRule() {
        render()
        markRead("m1")
        leaveShade("m1", "m2") // m1 read and gone, m2 unread and gone: both only in the history
        assertTrue(present("m1") && present("m2"))
        assertFalse("read history row: no dot", hasDot("m1")); assertEquals("Read", state("m1")); assertEquals(FontWeight.Normal, titleWeight("m1"))
        assertTrue("unread history row: dot", hasDot("m2")); assertEquals("Unread", state("m2")); assertEquals(FontWeight.SemiBold, titleWeight("m2"))
    }

    @Test fun CMP_14_theDigestRowShowsTheDotWhileAnyOfItsItemsIsUnreadAndNotWhenAllAreRead() {
        liveItems = digestItems; policies = digestPolicies
        render()
        assertTrue("digest has unread items", rule.onAllNodesWithTag("lp.group.unreadDot.digest", true).fetchSemanticsNodes().isNotEmpty())
        markRead("d1", "d2")
        assertTrue("all read: no dot", rule.onAllNodesWithTag("lp.group.unreadDot.digest", true).fetchSemanticsNodes().isEmpty())
    }

    // ---- screenshots ----

    private fun looks(name: String, dark: Boolean, prepare: () -> Unit = {}) {
        render(dark); markRead("m1", "c1"); prepare()
        shoot("comms-read-style-$orientation-$name-${if (dark) "dark" else "light"}")
    }
    @Test fun CMP_15_shot_all_dark() = looks("all", true)
    @Test fun CMP_15_shot_all_light() = looks("all", false)
    @Test fun CMP_15_shot_unread_dark() = looks("unread", true) { pinchIn() }
    @Test fun CMP_15_shot_unread_light() = looks("unread", false) { pinchIn() }
    @Test fun CMP_15_shot_read_dark() = looks("read", true) { pinchOut() }
    @Test fun CMP_15_shot_read_light() = looks("read", false) { pinchOut() }
}
