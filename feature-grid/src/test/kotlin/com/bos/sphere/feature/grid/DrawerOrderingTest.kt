package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.DrawerSort
import com.bos.sphere.core.data.UsageStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawerOrderingTest {
    private data class A(val label: String, val key: String = label)

    private val today = 10 * DrawerOrdering.DAY_MS

    private fun secs(
        items: List<A>, sort: DrawerSort,
        usage: Map<String, UsageStats.Usage> = emptyMap(),
        cat: (A) -> String? = { null },
    ) = DrawerOrdering.sections(items, sort, { it.label }, { it.key }, usage, cat, today)

    @Test fun alphabeticalGroupsByLetterWithHashLast() {
        val s = secs(listOf(A("beta"), A("Alpha"), A("1Pass"), A("apple")), DrawerSort.Alphabetical)
        assertEquals(listOf("A", "B", "#"), s.map { it.title })
        assertEquals(listOf("Alpha", "apple"), s[0].items.map { it.label })
    }

    @Test fun categoryGroupsOtherLast() {
        val cats = mapOf("a" to "Games", "b" to "Audio", "c" to null)
        val s = secs(listOf(A("a"), A("b"), A("c")), DrawerSort.Category, cat = { cats[it.key] })
        assertEquals(listOf("Audio", "Games", "Other"), s.map { it.title })
    }

    @Test fun recentBucketsAndNeverLaunchedLast() {
        val u = mapOf(
            "a" to UsageStats.Usage(1, today + 5),
            "b" to UsageStats.Usage(1, today - 5),
            "c" to UsageStats.Usage(1, today - 3 * DrawerOrdering.DAY_MS),
            "d" to UsageStats.Usage(1, today - 9 * DrawerOrdering.DAY_MS),
        )
        val s = secs(listOf("e", "d", "c", "b", "a").map { A(it) }, DrawerSort.Recent, u)
        assertEquals(listOf("Today", "Yesterday", "This week", "Earlier"), s.map { it.title })
        assertEquals(listOf("d", "e"), s[3].items.map { it.label })
    }

    @Test fun frequentSingleGroupTiesAlphabetical() {
        val u = mapOf("a" to UsageStats.Usage(2, 1), "b" to UsageStats.Usage(5, 1), "c" to UsageStats.Usage(2, 1))
        val s = secs(listOf(A("c"), A("z"), A("a"), A("b")), DrawerSort.Frequent, u)
        assertEquals(1, s.size)
        assertEquals("Most used", s[0].title)
        assertEquals(listOf("b", "a", "c", "z"), s[0].items.map { it.label })
    }

    @Test fun emptyHasNoSections() {
        assertEquals(0, secs(emptyList(), DrawerSort.Alphabetical).size)
        assertNull(DrawerOrdering.railTarget(0.5f, emptyList()))
    }

    @Test fun railTargetSectionsAndClamping() {
        val sizes = listOf(3, 2, 4, 1)
        assertEquals(DrawerOrdering.RailTarget(0, -1), DrawerOrdering.railTarget(-1f, sizes))
        assertEquals(DrawerOrdering.RailTarget(1, -1), DrawerOrdering.railTarget(0.3f, sizes))
        assertEquals(DrawerOrdering.RailTarget(3, -1), DrawerOrdering.railTarget(5f, sizes))
        assertEquals(DrawerOrdering.RailTarget(0, -1), DrawerOrdering.railTarget(Float.NaN, sizes))
    }

    @Test fun railTargetSingleSectionPicksItem() {
        assertEquals(DrawerOrdering.RailTarget(0, 0), DrawerOrdering.railTarget(0f, listOf(10)))
        assertEquals(DrawerOrdering.RailTarget(0, 5), DrawerOrdering.railTarget(0.55f, listOf(10)))
        assertEquals(DrawerOrdering.RailTarget(0, 9), DrawerOrdering.railTarget(1f, listOf(10)))
    }

    @Test fun flatIndexCountsHeaders() {
        val sizes = listOf(3, 2, 4)
        assertEquals(0, DrawerOrdering.flatIndex(sizes, DrawerOrdering.RailTarget(0, -1)))
        assertEquals(4, DrawerOrdering.flatIndex(sizes, DrawerOrdering.RailTarget(1, -1)))
        assertEquals(7, DrawerOrdering.flatIndex(sizes, DrawerOrdering.RailTarget(2, -1)))
        assertEquals(3, DrawerOrdering.flatIndex(listOf(10), DrawerOrdering.RailTarget(0, 2)))
    }

    @Test fun relativeTime() {
        val now = 1_000_000_000L
        assertEquals("Never", DrawerOrdering.relativeTime(now, 0))
        assertEquals("Just now", DrawerOrdering.relativeTime(now, now - 1000))
        assertEquals("5 min ago", DrawerOrdering.relativeTime(now, now - 5 * 60_000))
        assertEquals("3 h ago", DrawerOrdering.relativeTime(now, now - 3 * 3_600_000L))
        assertEquals("2 d ago", DrawerOrdering.relativeTime(now, now - 2 * DrawerOrdering.DAY_MS))
    }
}
