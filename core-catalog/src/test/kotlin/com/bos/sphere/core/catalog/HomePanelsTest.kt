package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.LaunchEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePanelsTest {
    @Test
    fun HOM_17_splitsTimeIntoTopAppsAndOther() {
        val s = UsageSplit.build(listOf(UsageSample("a", 60_000), UsageSample("b", 30_000), UsageSample("c", 10_000), UsageSample("a", 40_000), UsageSample("z", 0)), top = 2)
        assertEquals(listOf("a", "b", ""), s.map { it.packageName })
        assertEquals(100_000L, s[0].ms)
        assertEquals(1f, s.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
        assertTrue(UsageSplit.build(emptyList()).isEmpty())
    }

    @Test
    fun HOM_17_durationText() {
        assertEquals("<1 min", UsageSplit.duration(10_000))
        assertEquals("35 min", UsageSplit.duration(35 * 60_000L))
        assertEquals("2 h 5 min", UsageSplit.duration(125 * 60_000L))
    }

    @Test
    fun HOM_18_upcomingFirstThenPastNewestFirstUnknownAppsDropped() {
        val now = 1_000_000L
        val opens = listOf(LaunchEvent("a", now - 10, 9), LaunchEvent("gone", now - 5, 9), LaunchEvent("b", now - 50, 8))
        val sched = listOf(TimelineEntry(now + 500, "a", "A", true, "s2"), TimelineEntry(now + 100, "b", "B", true, "s1"), TimelineEntry(now - 1, "a", "A", true, "old"))
        val t = Timeline.build(opens, sched, now) { if (it == "gone") null else it.uppercase() }
        assertEquals(listOf("s1", "s2", "", ""), t.map { it.id })
        assertEquals(listOf("A", "B"), t.filter { !it.scheduled }.map { it.label })
        assertTrue(t.filter { !it.scheduled }.zipWithNext().all { (x, y) -> x.timeMs >= y.timeMs })
    }

    @Test
    fun HOM_18_dayLabels() {
        val d = 86_400_000L
        assertEquals("Today", Timeline.dayLabel(0, 5))
        assertEquals("Yesterday", Timeline.dayLabel(d, 5))
        assertEquals("Tomorrow", Timeline.dayLabel(0, d + 1))
        assertEquals("3 days ago", Timeline.dayLabel(3 * d, 5))
    }

    @Test
    fun HOM_15_revokedGrantLeavesNothingAndListIsNewestFirst() {
        val f = { u: String, t: Long, s: String -> RecentFile(u, u, "", t, s) }
        val merged = RecentFilesModel.merge(listOf(listOf(f("1", 5, "A"), f("2", 9, "A")), listOf(f("3", 7, "B"))))
        assertEquals(listOf("2", "3", "1"), merged.map { it.uri })
        assertEquals(listOf("3"), RecentFilesModel.withinGrants(merged, setOf("B")).map { it.uri })
    }
}

class TimelineDayW6Test {
    @Test
    fun HOM_18_daysCountCalendarDaysAcrossAClockChange() {
        // 2026-03-29 is the 23-hour day in Berlin: 00:30 the next morning is still tomorrow, not today.
        val zone = java.util.TimeZone.getTimeZone("Europe/Berlin")
        val z = zone.toZoneId()
        val start = java.time.LocalDate.of(2026, 3, 29).atStartOfDay(z).toInstant().toEpochMilli()
        val nextMorning = java.time.LocalDate.of(2026, 3, 30).atTime(0, 30).atZone(z).toInstant().toEpochMilli()
        org.junit.Assert.assertEquals("Tomorrow", Timeline.dayLabel(start, nextMorning, zone))
        org.junit.Assert.assertEquals("Today", Timeline.dayLabel(start, start + 60_000, zone))
        // The 25-hour day: 23:30 of the same day is still today.
        val fall = java.time.LocalDate.of(2026, 10, 25).atStartOfDay(z).toInstant().toEpochMilli()
        val lateFall = java.time.LocalDate.of(2026, 10, 25).atTime(23, 30).atZone(z).toInstant().toEpochMilli()
        org.junit.Assert.assertEquals("Today", Timeline.dayLabel(fall, lateFall, zone))
    }

    @Test
    fun HOM_18_dayHeadingsOfSeveralDaysAreTranslatableKeysWithTheNumberApart() {
        val d = 86_400_000L
        org.junit.Assert.assertEquals("%d days ago" to 3, Timeline.dayParts(3 * d, 5))
        org.junit.Assert.assertEquals("In %d days" to 4, Timeline.dayParts(0, 4 * d + 1))
        org.junit.Assert.assertEquals("Today" to null, Timeline.dayParts(0, 5))
        val rows = TimelineView.rows(listOf(TimelineEntry(5, "a", "A", false)), 3 * d)
        org.junit.Assert.assertEquals("%d days ago", rows.single().dayKey)
        org.junit.Assert.assertEquals(3, rows.single().dayArg)
    }
}
