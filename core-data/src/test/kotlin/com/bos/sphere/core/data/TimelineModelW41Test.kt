package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.ZoneId
import java.util.concurrent.Executor

/** Wave 41 (HOM-18): the unified Timeline model: classification, collapse, bursts, merge order, bounds and the stored events. */
class TimelineModelW41Test {
    @get:Rule val tmp = TemporaryFolder()
    private val utc = ZoneId.of("UTC")
    private val min = 60_000L
    private val noon = 1_700_000_000_000L - (1_700_000_000_000L % 86_400_000L) + 12 * 3_600_000L

    private fun note(i: Int, pkg: String = "com.chat", t: Long = noon - i * min, category: String = "", priority: Int = 0, read: Boolean = false, title: String = "Ann", text: String = "hi $i") =
        HistoryEntry("k$i|$t", "k$i", pkg, "Chat", "", title, text, "", t, read = read, category = category, priority = priority)

    @Test fun HOM_18_w41_eachNotificationKindIsClassifiedFromCategoryPriorityAndPackage() {
        fun c(cat: String?, pr: Int, pkg: String, title: String = "", text: String = "") = TimelineClassifier.classify(cat, pr, pkg, title, text)
        assertEquals(TimelineKind.Call, c("call", 0, "com.dialer", "Incoming call", ""))
        assertEquals(TimelineKind.MissedCall, c("call", 0, "com.dialer", "Missed call", "Ann"))
        assertEquals(TimelineKind.Message, c("msg", 0, "com.chat"))
        assertEquals(TimelineKind.Email, c("email", 0, "com.mail"))
        assertEquals(TimelineKind.Calendar, c("event", 0, "com.cal"))
        assertEquals(TimelineKind.Calendar, c("reminder", 0, "com.cal"))
        assertEquals(TimelineKind.Alarm, c("alarm", 0, "x"))
        assertEquals(TimelineKind.Alarm, c(null, 0, "com.google.android.deskclock"))
        assertEquals(TimelineKind.Download, c("progress", 0, "x"))
        assertEquals(TimelineKind.Download, c(null, 0, "com.android.providers.downloads"))
        assertEquals(TimelineKind.Security, c("sys", 0, "x"))
        assertEquals(TimelineKind.Alert, c("err", 0, "x"))
        assertEquals(TimelineKind.Promo, c("promo", 0, "x"))
        assertEquals(TimelineKind.Alert, c(null, 1, "x"))
        assertEquals(TimelineKind.Alert, c("", 2, "x"))
        assertEquals(TimelineKind.Notification, c(null, 0, "x"))
        assertEquals(TimelineKind.Message, c("msg", 2, "com.chat")) // a heads-up message is still a message
    }

    @Test fun HOM_18_w41_consecutiveOpensOfOneAppWithinTwoMinutesCollapseWithACount() {
        val opens = listOf(LaunchEvent("a/.A#0", noon, 12), LaunchEvent("a/.A#0", noon + min, 12), LaunchEvent("a/.A#0", noon + 2 * min + 1000, 12), LaunchEvent("b/.B#0", noon + 3 * min, 12), LaunchEvent("a/.A#0", noon + 10 * min, 12))
        val ev = TimelineModel.collapseOpens(TimelineInputs(opens = opens, labelOfPackage = mapOf("a" to "Alpha", "b" to "Beta")))
        assertEquals(listOf("Alpha" to 1, "Beta" to 1, "Alpha" to 3), ev.map { it.label to it.count })
        assertEquals(noon + 2 * min + 1000, ev.last().timeMs)
        assertEquals(listOf(noon + 10 * min, noon + 3 * min, noon + 2 * min + 1000), ev.map { it.timeMs })
    }

    @Test fun HOM_18_w41_aGapOverTwoMinutesOrAnotherAppInBetweenBreaksTheRun() {
        val opens = listOf(LaunchEvent("a/.A#0", noon, 12), LaunchEvent("a/.A#0", noon + 3 * min, 12), LaunchEvent("b/.B#0", noon + 3 * min + 1, 12), LaunchEvent("a/.A#0", noon + 4 * min, 12))
        assertEquals(4, TimelineModel.collapseOpens(TimelineInputs(opens = opens)).size)
    }

    @Test fun HOM_18_w41_anOpenShowsItsSessionLengthWhenUsageEventsKnowIt() {
        val sessions = UsageSessionIndex.from(listOf(RawUsageEvent("a", noon + 2_000, true), RawUsageEvent("a", noon + 12 * min + 2_000, false)))
        val ev = TimelineModel.collapseOpens(TimelineInputs(opens = listOf(LaunchEvent("a/.A#0", noon, 12), LaunchEvent("c/.C#0", noon + 20 * min, 12)), sessions = sessions))
        assertEquals(12 * min, ev.last().durationMs)
        assertNull(ev.first().durationMs)
    }

    @Test fun HOM_18_w41_sessionsMergeABriefPauseBetweenActivitiesOfOneApp() {
        val idx = UsageSessionIndex.from(listOf(
            RawUsageEvent("a", 1000, true), RawUsageEvent("a", 5000, false), RawUsageEvent("a", 6000, true), RawUsageEvent("a", 9000, false), RawUsageEvent("a", 60_000, true),
        ))
        assertEquals(8000L, idx.durationOf("a", 1000))
        assertNull(idx.durationOf("a", 60_000)) // still open: no end yet
    }

    @Test fun HOM_18_w41_notificationsFromOneAppWithinFiveMinutesBecomeOneBurstHoldingThemNewestFirst() {
        val n = listOf(note(1, t = noon), note(2, t = noon - 2 * min), note(3, t = noon - 4 * min), note(4, t = noon - 20 * min), note(5, pkg = "other", t = noon - min))
        val ev = TimelineModel.bursts(TimelineModel.notificationEvents(TimelineInputs(notifications = n)))
        val burst = ev.single { it.isBurst }
        assertEquals(3, burst.count)
        assertEquals(listOf(noon, noon - 2 * min, noon - 4 * min), burst.children.map { it.timeMs })
        assertEquals(3, ev.size) // the burst, the lone one 20 minutes before, the other app
    }

    @Test fun HOM_18_w41_aBurstIsUnreadWhenAnyMemberIsAndAlertsAndCallsAreNeverGrouped() {
        val n = listOf(note(1, t = noon, read = true), note(2, t = noon - min, read = false), note(3, category = "alarm", t = noon - 2 * min), note(4, category = "alarm", t = noon - 3 * min), note(5, priority = 2, t = noon - 4 * min))
        val ev = TimelineModel.bursts(TimelineModel.notificationEvents(TimelineInputs(notifications = n)))
        assertTrue(ev.single { it.isBurst }.unread)
        assertEquals(1 + 3, ev.size)
        assertFalse(ev.filter { it.kind.family == TimelineKind.Family.Alert }.any { it.isBurst })
    }

    @Test fun HOM_18_w41_theMergeOrdersAllSourcesNewestFirstAndGroupsByDayWithPlainCounts() {
        val input = TimelineInputs(
            opens = listOf(LaunchEvent("a/.A#0", noon - 5 * min, 12), LaunchEvent("a/.A#0", noon - 24 * 3_600_000L, 12)),
            notifications = listOf(note(1, t = noon - 10 * min), note(2, category = "alarm", t = noon - 30 * min)),
            stored = listOf(StoredTimelineEvent(noon - 20 * min, TimelineKind.PackageInstalled, "n.p", "New"), StoredTimelineEvent(noon - 40 * min, TimelineKind.AppHidden, "a")),
            upcoming = listOf(UpcomingLaunch("s1", "Morning", noon + 24 * 3_600_000L), UpcomingLaunch("old", "Gone", noon - 99 * min)),
            labelOfPackage = mapOf("a" to "Alpha"),
        )
        val days = TimelineModel.build(input, noon, utc)
        assertEquals(listOf(DayRelative.Tomorrow, DayRelative.Today, DayRelative.Yesterday), days.map { it.relative })
        val today = days[1]
        assertEquals(listOf(TimelineKind.AppOpened, TimelineKind.Notification, TimelineKind.PackageInstalled, TimelineKind.Alarm, TimelineKind.AppHidden), today.items.map { it.kind })
        assertEquals(DaySummary(opens = 1, notifications = 1, alerts = 1, packages = 2), today.summary)
        assertTrue(days[0].items.single().upcoming)
        assertEquals(DaySummary(0, 0, 0, 0), days[0].summary) // an upcoming launch is not an open
        assertEquals("Alpha", today.items.last().title)
    }

    @Test fun HOM_18_w41_theMergeIsBoundedAndFastForFiveThousandOfEverything() {
        val opens = (0 until 5000).map { LaunchEvent("a$it/.A#0", noon - it * 10 * min, 12) }
        val notes = (0 until 5000).map { note(it, pkg = "p${it % 50}", t = noon - it * 7 * min) }
        val stored = (0 until 5000).map { StoredTimelineEvent(noon - it * 11 * min, TimelineKind.PackageUpdated, "u$it") }
        val t0 = System.nanoTime()
        val days = TimelineModel.build(TimelineInputs(opens = opens, notifications = notes, stored = stored), noon, utc)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(TimelineModel.MAX_EVENTS, days.sumOf { it.items.size })
        assertTrue("merge took $ms ms", ms < 2000)
    }

    @Test fun HOM_18_w41_aStoredEventRoundTripsAndOnlyStoredKindsDecode() {
        val e = StoredTimelineEvent(5L, TimelineKind.PackageRemoved, "p\tq", "La\nbel", "d\\x")
        assertEquals(e, TimelineEventCodec.decode(TimelineEventCodec.encode(e)))
        assertNull(TimelineEventCodec.decode("5\tAppOpened\tp\tl\td")) // opens are never stored twice
        assertNull(TimelineEventCodec.decode("garbage"))
    }

    private class Queue : Executor { val q = ArrayDeque<Runnable>(); override fun execute(command: Runnable) { q.addLast(command) }; fun drain() { while (q.isNotEmpty()) q.removeFirst().run() } }

    @Test fun HOM_18_w41_theStoreIsBoundedPersistsAndDropsBurstRepeats() {
        val file = tmp.newFile("events.log"); val io = Queue()
        val s = TimelineEventStore(file, io, max = 5, slack = 2)
        io.drain()
        for (i in 0 until 8) s.record(StoredTimelineEvent(1_000_000L + i * 120_000L, TimelineKind.PackageUpdated, "p$i"))
        s.record(StoredTimelineEvent(1_000_000L + 7 * 120_000L + 5_000L, TimelineKind.PackageUpdated, "p7")) // the system repeats a callback within a minute
        io.drain()
        assertEquals((3 until 8).map { "p$it" }, s.events.value.map { it.packageName })
        val again = TimelineEventStore(file, io, max = 5, slack = 2); io.drain()
        assertEquals(s.events.value, again.events.value)
        assertTrue(file.readLines().size <= 7)
    }

    @Test fun HOM_18_w41_aRecordBeforeTheFileIsReadIsKeptAndAnUnstoredKindIsRefused() {
        val file = tmp.newFile("e2.log"); val io = Queue()
        val s = TimelineEventStore(file, io)
        s.record(StoredTimelineEvent(10L, TimelineKind.AppHidden, "a"))
        s.record(StoredTimelineEvent(11L, TimelineKind.AppOpened, "a"))
        io.drain()
        assertEquals(listOf(TimelineKind.AppHidden), s.events.value.map { it.kind })
        assertEquals(1, file.readLines().size)
    }

    @Test fun HOM_18_w41_theEventFileIsNeverExportedAndHistoryLinesKeepTheirCategoryAndStillReadWithoutIt() {
        assertTrue(TimelineEventStore.RELATIVE_PATH in StateSchema.NEVER_EXPORTED_FILES)
        assertTrue(TimelineEventStore.RELATIVE_PATH.startsWith("usage/"))
        val e = note(1, category = "call", priority = 1)
        assertEquals(e.copy(), NotificationHistoryCodec.decode(NotificationHistoryCodec.encode(e)))
        val old = NotificationHistoryCodec.encode(note(2)).also { assertEquals(8, it.count { c -> c == '\t' }) } // no category: the nine-field line of older installs
        assertEquals("", NotificationHistoryCodec.decode(old)!!.category)
    }
}
