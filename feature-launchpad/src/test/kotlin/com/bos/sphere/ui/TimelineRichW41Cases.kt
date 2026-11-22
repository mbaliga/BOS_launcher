package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.CommsScale
import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.data.LaunchEvent
import com.bos.sphere.core.data.StoredTimelineEvent
import com.bos.sphere.core.data.TimelineEvent
import com.bos.sphere.core.data.TimelineInputs
import com.bos.sphere.core.data.TimelineKind
import com.bos.sphere.core.data.UpcomingLaunch
import com.bos.sphere.core.data.UsageSessionIndex
import com.bos.sphere.core.data.RawUsageEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 41 (timelineRich): fixtures and the cases of the richer Timeline list. Time is fixed at 12:00 on 2024-03-10 UTC ([LaunchpadShots.now]). */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class TimelineRichW41Base : LaunchpadShots() {
    @After fun resetMerge() { TimelineMergeConfig.reset() }

    protected val min = 60_000L
    protected fun note(id: String, pkg: String, label: String, title: String, text: String, t: Long, category: String = "", priority: Int = 0, read: Boolean = true, sender: String = "") =
        HistoryEntry("$id|$t", id, pkg, label, sender, title, text, "", t, read = read, category = category, priority = priority)

    /** One of every kind, spread over today and yesterday, newest first by construction. */
    protected fun everyKind(): TimelineInputs {
        val notes = listOf(
            note("n", "demo.news", "News", "Morning briefing", "Seven stories", now - 10 * min, read = false),
            note("m", "demo.chat", "Chat", "Mum", "Are you coming on Sunday?", now - 20 * min, category = "msg", sender = "Mum", read = false),
            note("e", "demo.mail", "Mail", "Invoice 2291", "Payment received", now - 30 * min, category = "email"),
            note("c", "demo.cal", "Calendar", "Standup in 15 minutes", "Room 4", now - 40 * min, category = "event"),
            note("a", "demo.clock", "Clock", "Wake up", "07:00", now - 50 * min, category = "alarm"),
            note("d", "demo.dialer", "Phone", "Incoming call", "Ann", now - 60 * min, category = "call"),
            note("x", "demo.dialer", "Phone", "Missed call", "Ann", now - 70 * min, category = "call"),
            note("p", "demo.shop", "Shop", "Sale ends today", "50 percent off", now - 80 * min, category = "promo"),
            note("l", "demo.files", "Files", "Download finished", "report.pdf", now - 90 * min, category = "progress"),
            note("s", "android", "System", "Security update", "Installed", now - 100 * min, category = "sys"),
            note("h", "demo.build", "Build", "Build failed", "main", now - 110 * min, priority = 2),
        )
        val stored = listOf(
            StoredTimelineEvent(now - 120 * min, TimelineKind.PackageInstalled, "demo.new", "NewApp"),
            StoredTimelineEvent(now - 130 * min, TimelineKind.PackageUpdated, "demo.chat", "Chat"),
            StoredTimelineEvent(now - 140 * min, TimelineKind.PackageRemoved, "demo.gone", "Gone"),
            StoredTimelineEvent(now - 150 * min, TimelineKind.AppHidden, "demo.chat", "Chat"),
            StoredTimelineEvent(now - 160 * min, TimelineKind.AppUnhidden, "demo.chat", "Chat"),
            StoredTimelineEvent(now - 170 * min, TimelineKind.ScheduledLaunch, "demo.chat", "Morning mail", "App"),
        )
        val opens = listOf(LaunchEvent("demo.chat/.Main#0", now - 5 * min, 11), LaunchEvent("demo.maps/.Main#0", now - 25 * min, 11), LaunchEvent("demo.chat/.Main#0", now - 26 * 60 * min, 9))
        return TimelineInputs(
            opens = opens, notifications = notes, stored = stored, upcoming = emptyList(),
            labelOfKey = mapOf("demo.chat/.Main#0" to "Chat", "demo.maps/.Main#0" to "Maps"), labelOfPackage = mapOf("demo.chat" to "Chat", "demo.maps" to "Maps"),
            sessions = UsageSessionIndex.from(listOf(RawUsageEvent("demo.chat", now - 5 * min, true), RawUsageEvent("demo.chat", now + 7 * min, false))),
        )
    }

    protected val opened = ArrayList<TimelineEvent>()
    protected var hidden: TimelineEvent? = null
    protected var alarmOpened = false

    protected fun host(
        inputs: TimelineInputs, dark: Boolean = true, fontScale: Float = 1f, percent: Int = 100, rtl: Boolean = false, is24: Boolean = true,
        nextAlarm: NextAlarm? = null, header: Boolean = false, hide: Boolean = true,
    ) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale), LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                CommsScaleHost(percent) {
                    Themed(dark) {
                        Box(Modifier.fillMaxSize().background(LocalLp.current.bg).padding(horizontal = 18.dp)) {
                            TimelineEventsList(
                                inputs, now, is24, TimelineActions(open = { opened += it }, hideApp = if (hide) ({ hidden = it }) else null, openNextAlarm = { alarmOpened = true }),
                                nextAlarm = nextAlarm, zone = zone, header = { if (header) Text("usage block", color = LocalLp.current.ink, modifier = Modifier.testTag("usage.block")) },
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    protected fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

}

abstract class TimelineRichW41Cases : TimelineRichW41Base() {

    @Test fun HOM_18_w41_everyKindShowsItsOwnIconTileAndATalkBackDescription() {
        host(everyKind())
        val kinds = TimelineKind.entries
        for (k in kinds) assertTrue("an icon tile for $k", (0..60).any { exists("timeline.icon.${k.name}.$it") })
        // Each row's description names the kind, the app and the time.
        val desc = (0..60).mapNotNull { i -> rule.onAllNodesWithTag("timeline.row.$i", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
        assertTrue(desc.any { it.startsWith("Notification: News, 11:50") })
        assertTrue(desc.any { it.startsWith("Missed call: Phone") })
        assertTrue(desc.any { it.startsWith("Alarm: Clock, 11:10") })
        assertTrue(desc.any { it.startsWith("App opened: Chat, 11:55") })
    }

    @Test fun HOM_18_w41_eachKindHasADistinctGlyphAndAFullyTranslatedName() {
        val glyphs = TimelineKind.entries.map { TimelineGlyphs.of(it).glyph.name }
        assertEquals("one glyph per kind", TimelineKind.entries.size, glyphs.toSet().size)
        for (k in TimelineKind.entries) for (c in listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar"))
            assertTrue("$k name in $c", com.bos.sphere.core.design.L10n.tableFor(c)!!.containsKey(TimelineGlyphs.of(k).nameKey))
    }

    @Test fun HOM_18_w41_aDayShowsWeekdayDateAndAPlainCountLineThatIsNotAFilter() {
        host(everyKind(), header = true)
        val head = rule.onNodeWithTag("timeline.day.0", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Today · Sunday, 10 March", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Yesterday · Saturday, 9 March", useUnmergedTree = true).assertExists()
        // Today: 3 opens (Chat 5 min ago, Maps, the scheduled launch that fired), notifications n m e p l = 5, alerts c a d x s h = 6, 5 package/visibility events (the scheduled one is not).
        val summary = rule.onNodeWithTag("timeline.summary.0", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
        assertEquals("3 opens · 5 notifications · 6 alerts · 5 app changes", summary)
        rule.onNodeWithTag("timeline.summary.0", useUnmergedTree = true).assertHasNoClickAction()
        val headingFlag = rule.onNodeWithText("Today · Sunday, 10 March", useUnmergedTree = true).fetchSemanticsNode().config.contains(SemanticsProperties.Heading)
        assertTrue("day titles are TalkBack headings", headingFlag)
    }

    @Test fun HOM_18_w41_aSingleDayWithOneOfEachShowsTheSingularCounts() {
        host(TimelineInputs(opens = listOf(LaunchEvent("demo.chat/.Main#0", now - min, 11)), notifications = listOf(note("n", "demo.news", "News", "Hi", "t", now - 2 * min), note("a", "demo.clock", "Clock", "Ring", "", now - 3 * min, category = "alarm")), labelOfPackage = mapOf("demo.chat" to "Chat")))
        val s = rule.onNodeWithTag("timeline.summary.0", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
        assertEquals("1 open · 1 notification · 1 alert", s)
    }

    @Test fun HOM_18_w41_anOpenSaysOpenedAndItsSessionLengthAndTheTimeFollowsThe24HourSetting() {
        host(everyKind())
        assertEquals("today and yesterday", 2, rule.onAllNodesWithText("Opened Chat", useUnmergedTree = true).fetchSemanticsNodes().size)
        rule.onNodeWithText("Opened · 12 min", useUnmergedTree = true).assertExists()
        fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
        assertEquals("11:55", text("timeline.time.0")); assertEquals("11:50", text("timeline.time.1"))
    }

    @Test fun HOM_18_w41_theTimeIsTwelveHourWhenThePhoneIsSet() {
        host(everyKind(), is24 = false)
        val texts = (0..30).mapNotNull { rule.onAllNodesWithTag("timeline.time.$it", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { t -> t.text } }
        assertTrue(texts.any { it.matches(Regex("\\d{1,2}:\\d\\d[\\s\\u202f\\u00a0]*(AM|PM|am|pm).*")) })
        assertFalse(texts.any { it == "11:55" })
    }

    @Test fun HOM_18_w41_consecutiveOpensOfOneAppWithinTwoMinutesShowOneRowWithXThree() {
        val opens = listOf(LaunchEvent("demo.chat/.Main#0", now - 10 * min, 11), LaunchEvent("demo.chat/.Main#0", now - 9 * min, 11), LaunchEvent("demo.chat/.Main#0", now - 8 * min, 11))
        host(TimelineInputs(opens = opens, labelOfPackage = mapOf("demo.chat" to "Chat")))
        assertEquals(1, rule.onAllNodesWithText("Opened Chat", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("x3", rule.onNodeWithTag("timeline.count.0", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text })
    }

    @Test fun HOM_18_w41_aBurstFromOneAppIsOneRowThatExpandsInlineAndCollapsesAgain() {
        val notes = (1..4).map { note("w$it", "demo.chat", "WhatsApp", "Ann", "message $it", now - it * min, category = "msg", read = it != 2) }
        host(TimelineInputs(notifications = notes))
        rule.onNodeWithText("4 notifications from WhatsApp", useUnmergedTree = true).assertExists()
        assertFalse(exists("timeline.child.0.0"))
        rule.onNodeWithTag("timeline.chevron.0", useUnmergedTree = true).performClick()
        for (j in 0..3) assertTrue("child $j", exists("timeline.child.0.$j"))
        assertTrue("the unread member shows its own dot", exists("timeline.child.dot.0.1"))
        assertFalse(exists("timeline.child.dot.0.0"))
        rule.onNodeWithTag("timeline.chevron.0", useUnmergedTree = true).performClick()
        assertFalse(exists("timeline.child.0.0"))
        assertTrue("the burst row itself is unread when any member is", exists("timeline.dot.0"))
    }

    @Test fun HOM_18_w41_tappingARowOpensItAndALongPressOffersOpenHideAndCopy() {
        host(everyKind())
        rule.onNodeWithTag("timeline.row.0", useUnmergedTree = true).performClick()
        assertEquals(1, opened.size)
        assertEquals(TimelineKind.AppOpened, opened[0].kind)
        rule.onNodeWithTag("timeline.row.1", useUnmergedTree = true).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("timeline.menu.open", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("timeline.menu.hide", useUnmergedTree = true).performClick()
        assertNotNull(hidden); assertEquals("demo.news", hidden!!.packageName)
    }

    @Test fun HOM_18_w41_copyTextPutsTheRowTextOnTheClipboard() {
        host(everyKind())
        rule.onNodeWithTag("timeline.row.1", useUnmergedTree = true).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("timeline.menu.copy", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        val clip = rule.activity.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(clip, clip.contains("Morning briefing") || clip.contains("Opened"))
    }

    @Test fun HOM_18_w41_withoutAHideActionTheMenuHasNoHideEntry() {
        host(everyKind(), hide = false)
        rule.onNodeWithTag("timeline.row.1", useUnmergedTree = true).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("timeline.menu.copy", useUnmergedTree = true).assertExists()
        assertFalse(exists("timeline.menu.hide"))
    }

    @Test fun HOM_18_w41_anUnreadNotificationHasTheDotAndStateAndAReadOneDoesNot() {
        host(everyKind())
        val rows = (0..30).filter { exists("timeline.row.$it") }
        val unread = rows.filter { exists("timeline.dot.$it") }
        val states = unread.map { rule.onNodeWithTag("timeline.row.$it", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) }
        assertEquals("the two unread notifications", 2, unread.size)
        assertTrue(states.all { it == "Unread" })
        assertTrue("read rows have no state text", rows.filter { it !in unread }.all { rule.onNodeWithTag("timeline.row.$it", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == null })
    }

    @Test fun HOM_18_w41_theNextAlarmIsPinnedAtTheTopOfTodayAboveTheFirstEvent() {
        host(everyKind(), nextAlarm = NextAlarm(now + 19 * 60 * min))
        val alarm = rule.onNodeWithTag("timeline.nextAlarm", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val first = rule.onNodeWithTag("timeline.row.0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val todayHead = rule.onNodeWithTag("timeline.day.0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(todayHead.bottom.value <= alarm.top.value + 0.5f)
        assertTrue(alarm.bottom.value <= first.top.value + 0.5f)
        rule.onNodeWithTag("timeline.nextAlarm", useUnmergedTree = true).performClick()
        assertTrue(alarmOpened)
        val d = rule.onNodeWithTag("timeline.nextAlarm", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.first()
        assertTrue(d, d.startsWith("Next alarm, Mon 07:00"))
    }

    @Test fun HOM_18_w41_aTimelineWithNoEventsButAnAlarmStillShowsTodayAndTheAlarm() {
        host(TimelineInputs(), nextAlarm = NextAlarm(now + 60 * min))
        assertTrue(exists("timeline.nextAlarm"))
        assertFalse(exists("timeline.empty"))
        rule.onNodeWithText("Today · Sunday, 10 March", useUnmergedTree = true).assertExists()
    }

    @Test fun HOM_18_w41_withNothingRecordedTheEmptyTextShowsAndThereIsNoFilter() {
        host(TimelineInputs(), header = true)
        rule.onNodeWithTag("timeline.empty", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("usage.block", useUnmergedTree = true).assertExists()
        assertEquals(0, rule.onAllNodesWithTag("timeline.filter", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun HOM_18_w41_fiveThousandEventsStayLazyAndTheMergeRunsOffTheMainThread() {
        val q = object : kotlinx.coroutines.CoroutineDispatcher() {
            val tasks = ArrayDeque<Runnable>()
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { tasks += block }
            fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
        }
        TimelineMergeConfig.dispatcher = q
        val notes = (0 until 5000).map { note("k$it", "p${it % 40}", "App ${it % 40}", "Title $it", "Text $it", now - it * 7 * min, read = it % 3 == 0) }
        host(TimelineInputs(notifications = notes))
        assertFalse("before the merge ran nothing is listed", exists("timeline.row.0"))
        assertTrue(q.tasks.isNotEmpty())
        rule.runOnIdle { q.drain() }
        rule.waitForIdle()
        val composed = (0..4999).count { exists("timeline.row.$it") }
        assertTrue("lazy: $composed rows composed", composed in 1..120)
    }

    private fun targetAt(pct: Int) {
        host(everyKind(), percent = pct)
        val b = rule.onNodeWithTag("timeline.row.0", useUnmergedTree = true).getUnclippedBoundsInRoot(); val h = (b.bottom - b.top).value
        assertTrue("$pct%: ${h * CommsScale.factor(pct)} real dp", h * CommsScale.factor(pct) >= 47.9f)
    }
    @Test fun HOM_18_w41_theRowsKeepA48dpTargetAtCommsSize75() = targetAt(75)
    @Test fun HOM_18_w41_theRowsKeepA48dpTargetAtCommsSize100() = targetAt(100)
    @Test fun HOM_18_w41_theRowsKeepA48dpTargetAtCommsSize125() = targetAt(125)

    @Test fun HOM_18_w41_atFontScaleTwoTheTitleDetailAndTimeStayApartAndInsideTheRow() {
        host(everyKind(), fontScale = 2f)
        val row = rule.onNodeWithTag("timeline.row.1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val detail = rule.onNodeWithTag("timeline.detail.1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val time = rule.onNodeWithTag("timeline.time.1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("detail ${detail.right} ends before the time ${time.left}", detail.right.value <= time.left.value + 0.5f)
        assertTrue(time.right.value <= row.right.value + 0.5f && detail.left.value >= row.left.value)
    }

    @Test fun HOM_18_w54_theEventIconIsSmallAnd28DpCircleWithA16DpGlyph() {
        host(everyKind())
        val b = rule.onNodeWithTag("timeline.icon.AppOpened.0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("the circle is 28 dp wide", 28f, b.right.value - b.left.value, 0.5f)
        assertEquals("the circle is 28 dp high", 28f, b.bottom.value - b.top.value, 0.5f)
    }

    @Test fun HOM_18_w41_inRightToLeftTheIconIsOnTheRightAndTheTimeOnTheLeft() {
        host(everyKind(), rtl = true)
        val icon = rule.onNodeWithTag("timeline.icon.AppOpened.0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val time = rule.onNodeWithTag("timeline.time.0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("icon ${icon.left} is right of the time ${time.right}", icon.left.value > time.right.value)
    }

    @Test fun HOM_18_w41_anUpcomingScheduledLaunchIsInATomorrowSectionAndSaysWhenItWillRun() {
        host(everyKind().let { TimelineInputs(it.opens, it.notifications, it.stored, listOf(UpcomingLaunch("u1", "Evening news", now + 13 * 60 * min)), it.labelOfKey, it.labelOfPackage, it.sessions) })
        rule.onNodeWithText("Tomorrow · Monday, 11 March", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Scheduled: Evening news", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Scheduled for 01:00", useUnmergedTree = true).assertExists()
    }
}
