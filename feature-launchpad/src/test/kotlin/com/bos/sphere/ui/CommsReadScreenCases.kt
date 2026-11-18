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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import com.bos.sphere.core.data.CommsScaleSettings
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.NotificationHistoryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Executor

/**
 * Wave 29 (commsRead, STR-02): the pinch filters Comms by read state and read notifications stay in the list. The fixture is the shared grouped feed
 * (Chat x3, Calendar, Mail); the store is the history behind it. Screenshots: build/screenshots/comms-read-*.png.
 */
@OptIn(ExperimentalTestApi::class)
abstract class CommsReadScreenCases(private val orientation: String) : LaunchpadShots() {
    private var liveItems by mutableStateOf(grouped)
    private var readSet by mutableStateOf(setOf<String>())
    private lateinit var store: NotificationHistoryStore
    private lateinit var hub: HubState
    private var menuRef: LaunchpadMenuState? = null
    private val appsOpened = mutableListOf<String>()
    private var cleared = 0

    @Before fun seed() {
        CommsScaleSettings.resetForTest()
        liveItems = grouped; readSet = emptySet()
        store = NotificationHistoryStore(File(java.nio.file.Files.createTempDirectory("commsread").toFile(), "usage/h.log"), Executor { it.run() })
        store.observe(grouped)
        hub = HubState(HubPrefs(null), store, true, liveAll = true)
    }

    @Composable
    private fun Stage(dark: Boolean = true) {
        CompositionLocalProvider(LocalLp provides if (dark) LpDark else LpLight) {
            Box(Modifier.fillMaxSize().background(LocalLp.current.bg)) {
                val menu = rememberLaunchpadMenu().also { menuRef = it }
                LaunchpadContent(
                    items = liveItems, rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.Flat), readKeys = readSet, hasAccess = true, now = now,
                    onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = { cleared++; liveItems = emptyList(); store.observe(emptyList()) }, onAddRule = {},
                    onSetDefaultMode = {}, onRequestAccess = {}, zone = zone, menu = menu, systemState = system, hub = hub, viewSwitcher = { HubSwitcher(hub) },
                    openApp = { appsOpened += it; true },
                )
            }
        }
    }

    private fun render(dark: Boolean = true) { rule.setContent { Stage(dark) }; rule.waitForIdle() }
    private fun edit(block: () -> Unit) { rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { block() } }; rule.waitForIdle() }
    private fun markRead(vararg keys: String) = edit { keys.forEach { store.markRead(it) }; readSet = readSet + keys }
    /** The system took these out of the shade (the app cancelled them, the user swiped them away). */
    private fun leaveShade(vararg keys: String) = edit { liveItems = liveItems.filter { it.key !in keys }; store.observe(liveItems) }
    private fun present(key: String) = rule.onAllNodesWithTag("lp.row.$key").fetchSemanticsNodes().isNotEmpty()
    private fun pinchIn() { rule.onNodeWithTag("lp.root").performTouchInput { pinch(center + Offset(-200f, 0f), center + Offset(-40f, 0f), center + Offset(200f, 0f), center + Offset(40f, 0f)) }; rule.waitForIdle() }
    private fun pinchOut() { rule.onNodeWithTag("lp.root").performTouchInput { pinch(center + Offset(-40f, 0f), center + Offset(-200f, 0f), center + Offset(40f, 0f), center + Offset(200f, 0f)) }; rule.waitForIdle() }
    private fun openMenu() { rule.onNodeWithTag("lp.menu").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle() }
    private fun text(tag: String, unmerged: Boolean = false) = rule.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
    private fun a11y(label: String) = rule.onNodeWithTag("lp.root").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }

    // ---- read notifications must not disappear ----

    @Test fun STR_02_w29_aNotificationMarkedReadAndThenCancelledByTheSystemIsStillListedInAll() {
        render()
        assertTrue(present("m1"))
        markRead("m1")
        assertTrue("read, still in the shade", present("m1"))
        leaveShade("m1") // the app cancelled it when it was opened
        assertTrue("wave 29: read and gone from the shade, it is still in Comms", present("m1"))
        assertEquals("the quiet style: the read mark and the row's tag survive", "Are you coming on Sunday?", text("lp.title.m1", true))
        assertTrue(store.entries.value.first { it.key == "m1" }.read)
        leaveShade("m2", "m3", "c1", "e1") // the user swiped everything away in the shade
        listOf("m1", "m2", "m3", "c1", "e1").forEach { assertTrue("$it stays after the shade is empty", present(it)) }
    }

    @Test fun STR_02_w29_clearAllOnlyEmptiesTheShadeAndNeverTheHistory() {
        render()
        openMenu()
        rule.onNodeWithTag("lp.menu.list").performScrollToNode(hasTestTag("lp.menu.clear")); rule.onNodeWithTag("lp.menu.clear").performClick(); rule.waitForIdle()
        assertEquals(1, cleared)
        assertEquals(5, store.entries.value.size)
        assertTrue(present("m1") && present("e1"))
    }

    @Test fun STR_02_w29_theMenuShowListKeepsAnAppWhoseNotificationsAreAllReadAndGoneWithItsCount() {
        render()
        markRead("m1", "m2", "m3"); leaveShade("m1", "m2", "m3")
        openMenu()
        assertEquals("the app stays with live plus history rows", "3", text("lp.menu.show.demo.chat.count", true))
        assertEquals("All Comms counts the history too", "5", text("lp.menu.show.all.count", true))
    }

    @Test fun STR_02_w29_underTheUnreadFilterAnAppWithNothingUnreadStaysListedAtZero() {
        render()
        markRead("c1")
        a11y("Show unread only").action!!.invoke(); rule.waitForIdle()
        openMenu()
        assertEquals("0", text("lp.menu.show.demo.cal.count", true))
        rule.onNodeWithTag("lp.menu.show.demo.cal").assertIsDisplayed()
        assertEquals("the other apps show their unread counts", "3", text("lp.menu.show.demo.chat.count", true))
        assertEquals("4", text("lp.menu.show.all.count", true))
    }

    // ---- history-only rows ----

    @Test fun STR_02_w29_tappingAHistoryOnlyRowReadsItAndOpensItsApp() {
        render(); leaveShade("e1")
        rule.onNodeWithTag("lp.focus.e1").performClick(); rule.waitForIdle()
        assertEquals(listOf("demo.mail"), appsOpened)
        assertTrue(store.entries.value.first { it.key == "e1" }.read)
    }

    @Test fun STR_02_w29_aHistoryOnlyRowOffersReadUnreadAndDeleteFromHistory() {
        render(); leaveShade("e1")
        rule.onNodeWithTag("lp.focus.e1").performTouchInput { longClick() }; rule.waitForIdle()
        rule.onNodeWithTag("lp.chip.toggleRead.e1").performClick(); rule.waitForIdle()
        assertTrue(store.entries.value.first { it.key == "e1" }.read)
        rule.onNodeWithTag("lp.chip.toggleRead.e1").performClick(); rule.waitForIdle()
        assertFalse("marked unread again", store.entries.value.first { it.key == "e1" }.read)
        rule.onNodeWithTag("lp.chip.delete.e1").performClick(); rule.waitForIdle()
        assertFalse("deleted from history, it is gone", present("e1"))
        assertTrue(store.entries.value.none { it.key == "e1" })
        assertTrue("the rest are untouched", present("m1"))
    }

    @Test fun STR_02_w29_aLiveRowStillHasItsOwnActionsWhileItIsInTheShade() {
        render()
        rule.onNodeWithTag("lp.focus.m1").performTouchInput { longClick() }; rule.waitForIdle()
        rule.onNodeWithTag("lp.chips.m1").assertExists()
        rule.onNodeWithTag("lp.chip.delete.m1").assertDoesNotExist() // delete-from-history belongs to rows that left the shade
    }

    // ---- the pinch ----

    @Test fun STR_02_w29_pinchInShowsOnlyUnreadPinchOutShowsOnlyReadAndNeverTouchesTheSize() {
        render()
        markRead("m1", "c1")
        pinchIn()
        assertEquals(ReadFilter.Unread, hub.readFilter)
        assertEquals("Unread only", text("lp.readFilter"))
        assertFalse(present("m1")); assertFalse(present("c1")); assertTrue(present("m2") && present("e1"))
        pinchOut()
        assertEquals(ReadFilter.All, hub.readFilter)
        rule.onNodeWithTag("lp.readFilter").assertDoesNotExist()
        assertTrue(present("m1") && present("m2"))
        pinchOut()
        assertEquals(ReadFilter.Read, hub.readFilter)
        assertEquals("Read only", text("lp.readFilter"))
        assertTrue(present("m1") && present("c1")); assertFalse(present("m2"))
        assertEquals("the pinch left the Comms size alone", 100, CommsScaleSettings.percent.value)
    }

    @Test fun STR_02_w29_theReadFilterStepsOncePerPinchAndTheLabelResetsItWithATap() {
        render()
        pinchIn(); pinchIn()
        assertEquals("two pinches in a row end at the Unread stop", ReadFilter.Unread, hub.readFilter)
        rule.onNodeWithTag("lp.readFilter").performClick(); rule.waitForIdle()
        assertEquals(ReadFilter.All, hub.readFilter)
    }

    @Test fun STR_02_w29_theTalkBackActionsSetTheThreeStops() {
        render(); markRead("m1")
        a11y("Show read only").action!!.invoke(); rule.waitForIdle()
        assertEquals(ReadFilter.Read, hub.readFilter); assertTrue(present("m1")); assertFalse(present("m2"))
        a11y("Show unread only").action!!.invoke(); rule.waitForIdle()
        assertEquals(ReadFilter.Unread, hub.readFilter); assertFalse(present("m1")); assertTrue(present("m2"))
        a11y("Show all").action!!.invoke(); rule.waitForIdle()
        assertEquals(ReadFilter.All, hub.readFilter); assertTrue(present("m1") && present("m2"))
    }

    @Test fun STR_02_w29_theKeyboardChordsStepTheFilterToo() {
        render()
        rule.onNodeWithTag("lp.root").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.CtrlLeft); pressKey(androidx.compose.ui.input.key.Key.U); keyUp(androidx.compose.ui.input.key.Key.CtrlLeft) }
        rule.waitForIdle()
        assertEquals(ReadFilter.Unread, hub.readFilter)
    }

    @Test fun STR_02_w29_aTwoFingerSwipeStillSwitchesViewsAndIsNotAPinch() {
        render()
        assertEquals(HubView.All, hub.view)
        rule.onNodeWithTag("lp.root").performTouchInput {
            val c = center; val a = c + Offset(-70f, 0f); val b = c + Offset(70f, 0f)
            down(0, a); down(1, b)
            for (i in 1..12) { val f = i / 12f; updatePointerTo(0, a + Offset(-300f * f, 0f)); updatePointerTo(1, b + Offset(-300f * f, 0f)); move() }
            up(0); up(1)
        }
        rule.waitForIdle()
        assertEquals("two fingers moving together left: the next view", HubView.Unread, hub.view)
        assertEquals("not a pinch", ReadFilter.All, hub.readFilter)
    }

    @Test fun STR_02_w29_aSlowPinchWithTheSameSpreadChangeIsOneStepNotTwo() {
        render()
        rule.onNodeWithTag("lp.root").performTouchInput {
            val c = center; val a = c + Offset(-100f, 0f); val b = c + Offset(100f, 0f)
            down(0, a); down(1, b)
            for (i in 1..40) { val f = i / 40f; updatePointerTo(0, a + Offset(-300f * f, 0f)); updatePointerTo(1, b + Offset(300f * f, 0f)); move() } // spread x4
            up(0); up(1)
        }
        rule.waitForIdle()
        assertEquals(ReadFilter.Read, hub.readFilter)
    }

    // ---- screenshots (read by hand) ----

    @Test fun STR_02_w29_screens_mixedAll() { render(); markRead("m2", "c1"); shoot("comms-read-$orientation-all") }
    @Test fun STR_02_w29_screens_mixedAllWithGoneRows() { render(); markRead("m2", "c1"); leaveShade("c1", "m2"); shoot("comms-read-$orientation-all-gone") }
    @Test fun STR_02_w29_screens_filterUnread() { render(); markRead("m2", "c1"); pinchIn(); shoot("comms-read-$orientation-unread") }
    @Test fun STR_02_w29_screens_filterRead() { render(); markRead("m2", "c1"); leaveShade("c1"); pinchOut(); pinchOut(); shoot("comms-read-$orientation-read") }
    @Test fun STR_02_w29_screens_menuWithAZeroUnreadApp() {
        render(); markRead("c1"); pinchIn(); openMenu(); shoot("comms-read-$orientation-menu")
    }
}
