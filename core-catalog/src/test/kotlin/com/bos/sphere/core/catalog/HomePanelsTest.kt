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
