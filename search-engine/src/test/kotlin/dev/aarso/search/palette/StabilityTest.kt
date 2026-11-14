package dev.aarso.search.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StabilityTest {
    private fun ids(l: List<String>) = l

    @Test fun PAL_12_lateResultsNeverMoveTheSelectedRow() {
        val shown = listOf("a", "b", "c")
        val ideal = listOf("x", "a", "b", "y", "c") // two better rows arrive late
        val merged = StableOrder.merge(shown, ideal, "b") { it }
        // a and b keep their places at the top; the newcomers go below the selection.
        assertEquals(listOf("a", "b", "x", "y", "c"), merged)
        assertEquals(1, merged.indexOf("b"))
    }

    @Test fun PAL_12_withoutASelectionTheFreshRankingWins() {
        assertEquals(listOf("x", "a"), StableOrder.merge(listOf("a"), listOf("x", "a"), null) { it })
        assertEquals(listOf("x", "a"), StableOrder.merge(listOf("a"), listOf("x", "a"), "gone") { it })
    }

    @Test fun PAL_12_aSelectedRowThatStopsMatchingStaysUntilTheUserMoves() {
        assertEquals(listOf("b", "x"), StableOrder.merge(listOf("b"), listOf("x"), "b") { it })
    }

    @Test fun PAL_12_reopeningWithTheSameQueryRestoresResults() {
        val cache = QueryCache<List<String>>(capacity = 2)
        cache.put("gm", listOf("Google Maps"))
        cache.put("ca", listOf("Calendar"))
        assertEquals(listOf("Google Maps"), cache.get("gm"))
        cache.put("wi", listOf("Wifi")) // evicts the least recently used: "ca"
        assertNull(cache.get("ca"))
        assertEquals(listOf("Google Maps"), cache.get("gm"))
    }

    @Test fun PAL_09_mergedStoresKeepTheLargerCountAndLaterTime() {
        val a = UsageStore().apply { put("k", UsageRecord(3, 100)); put("only-a", UsageRecord(1, 5)) }
        val b = UsageStore().apply { put("k", UsageRecord(2, 900)) }
        val m = a.mergedWith(b)
        assertEquals(UsageRecord(3, 900), m.get("k"))
        assertEquals(UsageRecord(1, 5), m.get("only-a"))
        assertEquals(UsageRecord(3, 100), a.get("k")) // the originals are untouched
    }
}
