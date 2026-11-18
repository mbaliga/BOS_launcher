package com.bos.sphere.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.NotificationHistory
import com.bos.sphere.core.data.NotificationHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Executor

/** Wave 25 (hubData, owner item 2): the Launchpad hub's views, default view, quick filters, density and two-finger gestures. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
class HubTest {
    @get:org.junit.Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneOffset.UTC
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDate.of(2024, 3, day).atTime(h, m).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val now = at(10, 12)
    private lateinit var store: NotificationHistoryStore
    private lateinit var state: HubState
    private val opened = mutableListOf<String>()

    private fun n(key: String, pkg: String, app: String, who: String, text: String, ts: Long) =
        HubNotification(id = key, key = key, source = app, title = who, summary = text, timestamp = ts, packageName = pkg, appLabel = app, sender = who)

    private val feed = listOf(
        n("m1", "demo.chat", "Chat", "Mum", "Are you coming on Sunday?", at(10, 11, 40)),
        n("m2", "demo.chat", "Chat", "Team", "Build is green", at(10, 10, 5)),
        n("c1", "demo.cal", "Calendar", "Standup", "Room 4", at(10, 9, 0)),
        n("e1", "demo.mail", "Mail", "Invoice", "Payment received", at(5, 9, 30)),
    )

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("launchpad_hub", 0).edit().clear().commit()
        store = NotificationHistoryStore(File(java.nio.file.Files.createTempDirectory("hub").toFile(), "usage/h.log"), Executor { it.run() })
        store.observe(feed)
        state = newState(true)
    }

    private fun newState(panels: Boolean) = HubState(HubPrefs(ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("launchpad_hub", 0)), store, panels)

    /** The minimal host the brief asks for: the switcher, the hub body and the root gesture and key modifiers, as LaunchpadContent wires them. */
    private fun host(s: HubState = state) {
        rule.setContent {
            CompositionLocalProvider(LocalLp provides LpDark) {
                Column(
                    Modifier.fillMaxSize().background(LpDark.bg).testTag("host")
                        .hubKeys(onView = { d -> HubLogic.step(s.views, s.view, d)?.let(s::select) }, onDensity = { l -> (if (l) s.density.looser else s.density.denser)?.let(s::chooseDensity) }, onMarkRead = { s.markVisibleRead(now, zone) })
                        .focusable()
                        .hubGestures(onPinch = { s.pinch(it) }, onSwipe = { sw -> s.swipe(sw) { s.markVisibleRead(now, zone) } }),
                ) {
                    HubSwitcher(s)
                    HubBody(s, now, zone, true, liveKeys = setOf("m1"), onOpen = { opened += it.key }, onMore = {}, modifier = Modifier.weight(1f), panelBody = { v -> Text("panel ${v.id}", color = LpDark.ink, modifier = Modifier.testTag("panel.${v.id}")) })
                }
            }
        }
        rule.waitForIdle()
    }

    private fun twoFingers(dx: Float, dy: Float, spread: Float = 1f) = rule.onNodeWithTag("host").performTouchInput {
        val c = center; val a = c + Offset(-70f, 0f); val b = c + Offset(70f, 0f)
        down(0, a); down(1, b)
        for (i in 1..12) {
            val f = i / 12f
            updatePointerTo(0, a + Offset(dx * f, dy * f) + Offset(-70f * (spread - 1f) * f, 0f))
            updatePointerTo(1, b + Offset(dx * f, dy * f) + Offset(70f * (spread - 1f) * f, 0f))
            move()
        }
        up(0); up(1)
    }

    /** Wave 27: the Read history has no tab and the filter chips are gone, so tests drive those through the state (the menu panel's rows are covered in CommsDesignCases). */
    private fun selectView(v: HubView, s: HubState = state) { rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { s.select(v) } }; rule.waitForIdle() }
    private fun setFilters(f: HubFilters, v: HubView = state.view) { rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { state.setFilters(f, v) } }; rule.waitForIdle() }
    private fun rows() = store.entries.value
    private fun rowTag(key: String) = "hub.row." + rows().first { it.key == key }.id

    // ---- b: the switcher ----

    @Test fun HUB_10_theSwitcherIs48dpHighShowsTheFiveTabsAndSwitchesOnATap() {
        host()
        rule.onNodeWithTag("hub.switcher").assertHeightIsAtLeast(48.dp)
        HubView.entries.filter { it != HubView.Read }.forEach { rule.onNodeWithTag("hub.view.${it.id}").assertExists() }
        rule.onNodeWithTag("hub.view.read").assertDoesNotExist() // wave 27: Read has no tab
        rule.onNodeWithTag("hub.view.usage").assertDoesNotExist() // wave 28: Usage is part of Timeline; the tabs are ALL UNREAD FILES TIMELINE
        rule.onNodeWithTag("hub.view.all").assertIsSelected()
        rule.onNodeWithTag("hub.view.files").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("panel.files").assertExists()
        rule.onNodeWithTag("hub.view.timeline").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("panel.timeline").assertExists()
        rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("hub.list").assertExists()
    }

    @Test fun HUB_10_withoutPanelsTheSwitcherOffersOnlyTheHistoryViews() {
        val s = newState(false); host(s)
        rule.onNodeWithTag("hub.view.files").assertDoesNotExist()
        rule.onNodeWithTag("hub.view.unread").assertExists()
        assertEquals(listOf(HubView.All, HubView.Unread), s.tabs)
    }

    @Test fun HUB_10_aHomePanelLinkLandsOnTheMatchingHubView() {
        assertEquals(HubView.Timeline, HubView.forPanel("usage")) // wave 28: the removed Usage view opens Timeline
        assertEquals(HubView.Timeline, HubView.forPanel("timeline"))
        assertEquals(HubView.Files, HubView.forPanel("files"))
        assertEquals(HubView.Files, HubView.forPanel("anything-else"))
        LaunchpadHubNav.request(HubView.Timeline)
        val s = newState(true)
        rule.setContent { androidx.compose.runtime.LaunchedEffect(Unit) { LaunchpadHubNav.requests.collect { v -> if (v != null) { s.select(v); LaunchpadHubNav.take() } } } }
        rule.waitForIdle()
        assertEquals(HubView.Timeline, s.view)
        assertEquals(null, LaunchpadHubNav.requests.value)
    }

    // ---- default view ----

    @Test fun HUB_11_setAsDefaultViewPersistsAndTheHubOpensThereNextTime() {
        host()
        rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { state.setDefault() } }; rule.waitForIdle()
        assertEquals(HubView.Unread, newState(true).view)                    // a new hub (a new open) starts there
        assertEquals(HubView.Unread, newState(true).defaultView)
        rule.onNodeWithTag("hub.view.files").performClick(); rule.waitForIdle()
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { state.setDefault() } }; rule.waitForIdle()
        assertEquals(HubView.Files, newState(true).view)
        assertEquals(HubView.All, newState(false).view)                       // a hub without panels cannot open on Files
    }

    // ---- views and read/unread ----

    @Test fun HUB_12_unreadAndReadViewsFollowTheReadStateAndATapMarksARowRead() {
        host()
        rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
        rule.onNodeWithTag(rowTag("m1")).assertExists(); rule.onNodeWithTag(rowTag("e1")).assertExists()
        rule.onNodeWithTag(rowTag("m1")).performClick(); rule.waitForIdle()
        assertTrue(rows().first { it.key == "m1" }.read)
        rule.onNodeWithTag(rowTag("m1")).assertDoesNotExist()                  // gone from Unread
        selectView(HubView.Read)
        rule.onNodeWithTag(rowTag("m1")).assertExists()
        rule.onNodeWithTag(rowTag("m2")).assertDoesNotExist()
    }

    @Test fun HUB_12_theExpandedRowOpensTheNotificationFlipsItsMarkAndDeletesOnlyThatEntry() {
        host()
        rule.onNodeWithTag(rowTag("m2")).performClick(); rule.waitForIdle()
        rule.onNodeWithTag("hub.open.${rows().first { it.key == "m2" }.id}").performClick()
        assertEquals(listOf("m2"), opened)
        rule.onNodeWithTag("hub.toggleRead.${rows().first { it.key == "m2" }.id}").performClick(); rule.waitForIdle()
        assertFalse(rows().first { it.key == "m2" }.read)
        rule.onNodeWithTag("hub.more.${rows().first { it.key == "m2" }.id}").assertDoesNotExist() // m2 is not in the shade
        rule.onNodeWithTag("hub.delete.${rows().first { it.key == "m2" }.id}").performClick(); rule.waitForIdle()
        assertEquals(3, rows().size)
    }

    @Test fun HUB_12_aRowStillInTheShadeOffersTheLiveOptions() {
        host()
        rule.onNodeWithTag(rowTag("m1")).performClick(); rule.waitForIdle()
        rule.onNodeWithTag("hub.more.${rows().first { it.key == "m1" }.id}").assertExists()
    }

    // ---- quick filters ----

    // Wave 27: the chip row is gone from the screen (the mock-up shows none); the filters live on in the state, driven by the menu's Show rows.
    @Test fun HUB_13_filtersNarrowTheListAndAreKeptPerView() {
        host()
        rule.onNodeWithTag(rowTag("e1")).assertExists()
        setFilters(HubFilters(range = HubRange.Today))
        rule.onNodeWithTag(rowTag("e1")).assertDoesNotExist()                  // five days old
        setFilters(HubFilters(range = HubRange.Week))
        rule.onNodeWithTag(rowTag("e1")).assertExists()                        // inside 7 days
        setFilters(HubFilters(range = HubRange.Week, app = "demo.chat"))
        rule.onNodeWithTag(rowTag("c1")).assertDoesNotExist(); rule.onNodeWithTag(rowTag("m1")).assertExists()
        setFilters(HubFilters(range = HubRange.Week, app = "demo.chat", sender = "Mum"))
        rule.onNodeWithTag(rowTag("m2")).assertDoesNotExist()
        // State is per view: Read has no filter, and All still has its own when the user returns.
        selectView(HubView.Read)
        assertFalse(state.filters(HubView.Read).active)
        selectView(HubView.All)
        assertEquals(HubFilters(app = "demo.chat", sender = "Mum", range = HubRange.Week), state.filters(HubView.All))
        assertEquals("no chip row on screen", 0, rule.onAllNodesWithTag("hub.filters").fetchSemanticsNodes().size)
        state.clearFilters()
        rule.waitForIdle()
        rule.onNodeWithTag(rowTag("c1")).assertExists()
    }

    @Test fun HUB_13_unreadOnlyAndTheEmptyMessageForFilters() {
        store.setRead(listOf(rows().first { it.key == "c1" }.id), true)
        host()
        setFilters(HubFilters(unreadOnly = true))
        rule.onNodeWithTag(rowTag("c1")).assertDoesNotExist(); rule.onNodeWithTag(rowTag("m1")).assertExists()
        setFilters(HubFilters(unreadOnly = true, app = "demo.cal"))
        rule.onNodeWithText("Nothing matches these filters").assertExists()
    }

    @Test fun HUB_13_theQuerySelectsByRangeAppSenderAndReadState() {
        val all = store.entries.value
        assertEquals(3, HubLogic.query(all, HubView.All, HubFilters(range = HubRange.Today), now, zone).size)
        assertEquals(4, HubLogic.query(all, HubView.All, HubFilters(range = HubRange.Week), now, zone).size)
        assertEquals(listOf("Chat", "Calendar", "Mail"), HubLogic.topApps(all).map { it.second })
        assertEquals(0, HubLogic.query(all, HubView.Read, HubFilters(), now, zone).size)
        assertTrue(HubLogic.query(all, HubView.Files, HubFilters(), now, zone).isEmpty())
    }

    // ---- density and pinch ----

    @Test fun HUB_14_pinchOutMakesRowsLooserPinchInDenserAndTheChoiceIsPersisted() {
        host()
        assertEquals(HubDensity.Comfortable, state.density)
        rule.onNodeWithTag("host").performTouchInput { pinch(center + Offset(-40f, 0f), center + Offset(-200f, 0f), center + Offset(40f, 0f), center + Offset(200f, 0f)) }
        rule.waitForIdle()
        assertEquals(HubDensity.Expanded, state.density)
        assertEquals(HubDensity.Expanded, newState(true).density)
        rule.onNodeWithTag("host").performTouchInput { pinch(center + Offset(-200f, 0f), center + Offset(-40f, 0f), center + Offset(200f, 0f), center + Offset(40f, 0f)) }
        rule.waitForIdle()
        assertEquals(HubDensity.Comfortable, state.density)
    }

    @Test fun HUB_14_atTheEndOfTheDensityScaleAFurtherPinchChangesTheGroupingGranularity() {
        assertEquals(HubDensity.Expanded to HubGrouping.Sender, HubLogic.pinch(HubDensity.Expanded, HubGrouping.Day, true))
        assertEquals(HubDensity.Expanded to HubGrouping.Sender, HubLogic.pinch(HubDensity.Expanded, HubGrouping.Sender, true))
        assertEquals(HubDensity.Compact to HubGrouping.App, HubLogic.pinch(HubDensity.Compact, HubGrouping.Day, false))
        assertEquals(HubDensity.Compact to HubGrouping.Day, HubLogic.pinch(HubDensity.Comfortable, HubGrouping.Day, false).let { HubDensity.Compact to HubGrouping.Day })
        assertEquals("wave 27: the default is a flat list, no bands", HubGrouping.Flat, state.grouping)
        host(); rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { state.chooseDensity(HubDensity.Expanded); state.chooseGrouping(HubGrouping.Day) } }; rule.waitForIdle()
        rule.onNodeWithTag("host").performTouchInput { pinch(center + Offset(-40f, 0f), center + Offset(-200f, 0f), center + Offset(40f, 0f), center + Offset(200f, 0f)) }
        rule.waitForIdle()
        assertEquals(HubGrouping.Sender, state.grouping)
        // Wave 26: Mum has a single row here, so there is no header that would repeat it (was: a "Mum" header above the "Mum" row).
        rule.onAllNodesWithTag("hub.header.s:Mum").assertCountEquals(0)
        assertEquals(HubGrouping.Sender, newState(true).grouping)
    }

    // Wave 26 (launchpadPolish, owner item 2): an app or sender header only for two or more rows, with the count; day bands always.
    @Test fun HUB_26_historyHeadersAreShownOnlyForTwoOrMoreRowsAndCarryTheCount() {
        store.observe(feed + n("m3", "demo.chat", "Chat", "Mum", "Bring the dessert", at(10, 11, 50)))
        state = newState(true)
        host()
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { state.chooseGrouping(HubGrouping.Sender) } }; rule.waitForIdle()
        rule.onNodeWithTag("hub.header.s:Mum").assertExists()
        rule.onNodeWithTag("hub.headerCount.s:Mum", useUnmergedTree = true).assertExists()
        assertEquals("2", rule.onNodeWithTag("hub.headerCount.s:Mum", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text })
        for (single in listOf("s:Team", "s:Standup", "s:Invoice")) rule.onAllNodesWithTag("hub.header.$single").assertCountEquals(0)
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { state.chooseGrouping(HubGrouping.App) } }; rule.waitForIdle()
        rule.onNodeWithTag("hub.header.a:demo.chat").assertExists()
        rule.onAllNodesWithTag("hub.header.a:demo.cal").assertCountEquals(0)
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { state.chooseGrouping(HubGrouping.Day) } }; rule.waitForIdle()
        rule.onAllNodesWithTag("hub.headerCount.d:", useUnmergedTree = true).assertCountEquals(0)
        assertTrue(HubLogic.showsGroupHeader(HubGrouping.Day, 1))
        assertFalse(HubLogic.showsGroupHeader(HubGrouping.Sender, 1))
        assertFalse("flat never has headers", HubLogic.showsGroupHeader(HubGrouping.Flat, 5))
        assertTrue(HubLogic.showsGroupHeader(HubGrouping.App, 2))
    }

    // ---- two-finger gestures ----

    @Test fun HUB_15_aTwoFingerSwipeLeftOrRightMovesBetweenViews() {
        host()
        twoFingers(-300f, 0f); rule.waitForIdle()
        assertEquals(HubView.Unread, state.view)
        twoFingers(-300f, 0f); rule.waitForIdle()
        assertEquals("wave 27: Read has no tab, the next one after Unread is Files", HubView.Files, state.view)
        twoFingers(300f, 0f); rule.waitForIdle()
        assertEquals(HubView.Unread, state.view)
        twoFingers(300f, 0f); twoFingers(300f, 0f); rule.waitForIdle()
        assertEquals(HubView.All, state.view)                                // the first view: a further swipe stops there
    }

    @Test fun HUB_15_aTwoFingerSwipeDownMarksTheVisibleItemsReadAndTheUndoBarRestoresThem() {
        host()
        rule.mainClock.autoAdvance = false
        twoFingers(0f, 300f); rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        assertTrue(rows().all { it.read })
        rule.onNodeWithText("Marked 4 as read").assertExists()
        rule.onNodeWithTag("hub.undo").performClick(); rule.mainClock.advanceTimeByFrame()
        assertTrue(rows().none { it.read })
        rule.onNodeWithTag("hub.undoBar").assertDoesNotExist()
    }

    @Test fun HUB_15_swipeDownOnlyMarksWhatTheFiltersShow() {
        host()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { state.setFilters(HubFilters(app = "demo.chat")) } }; rule.mainClock.advanceTimeByFrame()
        twoFingers(0f, 300f); rule.mainClock.advanceTimeByFrame()
        assertEquals(setOf("m1", "m2"), rows().filter { it.read }.map { it.key }.toSet())
        rule.onNodeWithText("Marked 2 as read").assertExists()
    }

    @Test fun HUB_15_theUndoBarGoesAwayByItself() {
        host()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { state.undo = HubUndo("Marked 1 as read", emptyList()) } }; rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("hub.undoBar").assertExists()
        rule.mainClock.advanceTimeBy(6500)
        rule.onNodeWithTag("hub.undoBar").assertDoesNotExist()
    }

    @Test fun HUB_15_oneFingerScrollingAndHorizontalSwipesAreLeftAlone() {
        val many = (0 until 40).map { n("k$it", "demo.chat", "Chat", "Sender $it", "message number $it", at(10, 11) - it * 60_000L) }
        store.observe(many)
        host()
        val first = rows().first().id
        rule.onNodeWithTag("hub.list").performTouchInput { swipeUp() }; rule.waitForIdle()
        assertEquals("up", emptyList<String>(), rows().filter { it.read }.map { it.key })
        rule.onNodeWithTag("hub.row.$first").assertDoesNotExist()              // the list scrolled
        rule.onNodeWithTag("hub.list").performTouchInput { swipeDown() }; rule.waitForIdle()
        assertEquals("down", emptyList<String>(), rows().filter { it.read }.map { it.key })
        rule.onNodeWithTag("hub.row.$first").assertExists()
        rule.onNodeWithTag("hub.list").performTouchInput { swipeLeft() }; rule.waitForIdle()
        assertEquals(HubView.All, state.view)                                // one finger never switches views
        assertEquals(emptyList<String>(), rows().filter { it.read }.map { it.key })
        assertEquals(HubDensity.Comfortable, state.density)
    }

    // ---- non-gesture equivalents ----

    @Test fun HUB_13_theEmptyStatesSayWhyTheListIsEmpty() {
        store.deleteAll()
        host()
        rule.onNodeWithText("No notifications in history yet").assertExists()
        rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
        rule.onNodeWithText("No unread notifications").assertExists()
        selectView(HubView.Read)
        rule.onNodeWithText("Nothing read yet").assertExists()
    }

    @Test fun HUB_13_withoutNotificationAccessTheHubSaysNewNotificationsAreNotRecordedAndOffersTheGrant() {
        var asked = 0
        rule.setContent {
            CompositionLocalProvider(LocalLp provides LpDark) {
                HubBody(state, now, zone, true, liveKeys = emptySet(), onOpen = {}, onMore = {}, hasAccess = false, onRequestAccess = { asked++ })
            }
        }
        rule.onNodeWithTag("hub.noAccess").assertExists()
        rule.onNodeWithText("Notification access is off, so new notifications are not recorded.").assertExists()
        rule.onNodeWithTag("hub.grant").performClick()
        assertEquals(1, asked)
    }

    @Test fun HUB_16_theHardwareKeyboardSwitchesViewsChangesDensityAndMarksRead() {
        host()
        rule.onNodeWithTag("host").requestFocus()
        rule.onNodeWithTag("host").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.DirectionRight); keyUp(Key.CtrlLeft) }
        rule.waitForIdle()
        assertEquals(HubView.Unread, state.view)
        rule.onNodeWithTag("host").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.DirectionLeft); keyUp(Key.CtrlLeft) }
        rule.onNodeWithTag("host").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Minus); keyUp(Key.CtrlLeft) }
        rule.waitForIdle()
        assertEquals(HubView.All, state.view); assertEquals(HubDensity.Compact, state.density)
        rule.onNodeWithTag("host").performKeyInput { keyDown(Key.CtrlLeft); keyDown(Key.ShiftLeft); pressKey(Key.M); keyUp(Key.ShiftLeft); keyUp(Key.CtrlLeft) }
        rule.waitForIdle()
        assertTrue(rows().all { it.read })
    }

    // ---- inside the real Launchpad screen ----

    @Test fun HUB_17_launchpadContentShowsTheSwitcherInItsSlotAndClearAllKeepsTheHistory() {
        var cleared = 0
        val s = state
        rule.setContent {
            CompositionLocalProvider(LocalLp provides LpDark) {
                LaunchpadContent(
                    items = feed, rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.App), readKeys = emptySet(), hasAccess = true, now = now,
                    onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = { cleared++; store.observe(emptyList()) }, onAddRule = {},
                    onSetDefaultMode = {}, onRequestAccess = {}, zone = zone,
                    hub = s, viewSwitcher = { HubSwitcher(s) },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("hub.switcher").assertExists()
        rule.onNodeWithTag("lp.root").assertExists()
        // The old one-finger-pair pinch (collapse all / show unread) is replaced by the hub's: pinch out changes the density.
        rule.onNodeWithTag("lp.root").performTouchInput { pinch(center + Offset(-40f, 0f), center + Offset(-200f, 0f), center + Offset(40f, 0f), center + Offset(200f, 0f)) }
        rule.waitForIdle()
        assertEquals(HubDensity.Expanded, s.density)
        // Clear all empties the shade; the history keeps every entry, flagged as cleared.
        store.observe(emptyList())
        rule.waitForIdle()
        assertEquals(4, store.entries.value.size)
        assertTrue(store.entries.value.all { it.dismissed })
        rule.onNodeWithTag(rowTag("m1")).assertExists()
    }

    @Test fun HUB_19_theRealPanelShowsTheHubWhenTheHostSuppliesThePanelsAndAnOldPanelLinkOpensTheMatchingView() {
        NotificationHistory.setForTest(store)
        com.bos.sphere.core.data.NotificationProvider.publishForTest(feed)
        try {
            LaunchpadHubNav.request(HubView.forPanel("usage"))
            rule.setContent { LaunchpadPanel(visible = true, onDismiss = {}, onOpenApp = {}, panelBody = { v -> Text("panel ${v.id}", modifier = Modifier.testTag("panel.${v.id}")) }) }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("hub.switcher").fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("panel.timeline").fetchSemanticsNodes().isNotEmpty() }   // landed on the requested view (an old Usage link opens Timeline)
            rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
            rule.onNodeWithTag("hub.list").assertExists()
        } finally { NotificationHistory.setForTest(null); com.bos.sphere.core.data.NotificationProvider.publishForTest(emptyList()); com.bos.sphere.core.data.LaunchpadSettings.unbindForTest() }
    }

    @Test fun HUB_19_aBarePanelWithoutPanelsKeepsTheLiveGroupedList() {
        com.bos.sphere.core.data.NotificationProvider.publishForTest(feed)
        try {
            rule.setContent { LaunchpadPanel(visible = true, onDismiss = {}, onOpenApp = {}) }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("lp.root").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("hub.switcher").assertDoesNotExist()
        } finally { com.bos.sphere.core.data.NotificationProvider.publishForTest(emptyList()); com.bos.sphere.core.data.LaunchpadSettings.unbindForTest() }
    }

    @Test fun HUB_20_withLiveAllTheAllViewIsTheLiveGroupedListAndUnreadAndReadAreTheHistory() {
        val live = newState(true).let { HubState(HubPrefs(ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("launchpad_hub", 0)), store, true, liveAll = true) }
        rule.setContent {
            CompositionLocalProvider(LocalLp provides LpDark) {
                LaunchpadContent(
                    items = feed, rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.App), readKeys = emptySet(), hasAccess = true, now = now,
                    onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                    onSetDefaultMode = {}, onRequestAccess = {}, zone = zone, hub = live, viewSwitcher = { HubSwitcher(live) },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("lp.row.m1").assertExists()                             // the live list with its own rows and groups
        rule.onNodeWithTag("hub.list").assertDoesNotExist()
        // The old pinch actions still apply there; the hub's density does not change.
        rule.onNodeWithTag("lp.root").performTouchInput { pinch(center + Offset(-40f, 0f), center + Offset(-200f, 0f), center + Offset(40f, 0f), center + Offset(200f, 0f)) }
        rule.waitForIdle()
        assertEquals(HubDensity.Comfortable, live.density)
        rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
        rule.onNodeWithTag(rowTag("m1")).assertExists()                            // persistent history
        rule.onNodeWithTag("hub.view.all").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("lp.row.m1").assertExists()
    }

    // ---- screenshot ----

    @Test fun HUB_18_screenshotOfTheHub() {
        host()
        rule.mainClock.autoAdvance = false
        shoot("hub-all")
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { state.chooseDensity(HubDensity.Expanded) } }; rule.onNodeWithTag(rowTag("m1")).performClick(); rule.mainClock.advanceTimeByFrame()
        shoot("hub-expanded")
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
