package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executor

/** Wave 13 audit of the edit card page rules (HOM-12) and of the Recent apps landing's clock and size rules (HOM-22). */
class EditCardAuditW13Test {
    private val cap: (PageLayout) -> Int = { 2 }
    private fun page(id: Long, vararg keys: String) = HomePage(keys.toList(), PageLayout(), id)

    @Test fun HOM_12_deletingAnyChunkOfAnOverflowedPageDeletesTheWholeStoredPage() {
        // Stored page 2 overflowed into two visual pages (same id). Deleting either chunk takes the whole page; before this it merged into
        // the sibling chunk and overflowed straight back, so the page could never be removed.
        val pages = listOf(page(1, "a"), page(2, "b", "c"), page(2, "d"), page(3, "e"))
        for (chunk in 1..2) {
            val r = HomePageOps.delete(pages, chunk)
            assertEquals("deleting chunk $chunk", listOf("a", "e"), r.pages.flatMap { it.keys })
            assertEquals(setOf(1L, 3L), r.pages.map { it.id }.filter { it != 0L }.toSet().intersect(setOf(1L, 3L)))
            assertFalse("the deleted page's id is gone", r.pages.any { it.id == 2L })
            assertEquals(setOf("b", "c", "d"), r.taken)
            assertEquals(0, r.focus)
        }
    }

    @Test fun HOM_12_deletingTheOnlyStoredPageEvenWhenItOverflowedEmptiesItAndReportsEveryKey() {
        val r = HomePageOps.delete(listOf(page(7, "a", "b"), page(7, "c")), 1)
        assertEquals(1, r.pages.size)
        assertTrue(r.pages[0].keys.isEmpty()); assertEquals(7L, r.pages[0].id)
        assertEquals(setOf("a", "b", "c"), r.taken)
        assertEquals(0, r.focus)
    }

    @Test fun HOM_12_deletingTheFirstPageDropsItsItemsAndTheFocusStaysInRange() {
        val r = HomePageOps.delete(listOf(page(1, "a"), page(2, "b")), 0)
        assertEquals(listOf(listOf("b")), r.pages.map { it.keys })
        assertEquals(setOf("a"), r.taken)
        assertEquals(0, r.focus)
        val none = HomePageOps.delete(listOf(page(1, "a")), 5) // a stale index changes nothing
        assertEquals(listOf(page(1, "a")), none.pages); assertEquals(0, none.focus)
    }

    @Test fun HOM_12_deleteNeverLosesOrDuplicatesAKeyWhateverTheIndex() {
        val pages = listOf(page(1, "a", "b"), page(2, "c", "d"), page(2, "e"), page(3, "f"), page(4))
        for (i in pages.indices) {
            val r = HomePageOps.delete(pages, i)
            val all = r.pages.flatMap { it.keys } + r.taken
            assertEquals("index $i", pages.flatMap { it.keys }.sorted(), all.sorted())
            assertTrue(r.pages.isNotEmpty())
        }
    }

    // ---- HOM-22: the removals file and the clock
    @Test fun HOM_22_aRemovalStampedByAClockThatWasAheadIsPulledBackSoRelaunchedAppsShowAgain() {
        val future = RecentsState(clearedAt = 9_000L, removedAt = mapOf("a" to 8_000L, "b" to 500L))
        val s = RecentsStateCodec.clampTo(future, now = 1_000L)
        assertEquals(1_000L, s.clearedAt)
        assertEquals(mapOf("a" to 1_000L, "b" to 500L), s.removedAt)
        // an app launched after "now" (the clock is right again) is not hidden by the stale stamp any more
        assertFalse(s.hides("a", 1_001L)); assertTrue(s.hides("a", 1_000L))
        assertEquals(future.copy(clearedAt = 100L), RecentsStateCodec.clampTo(future.copy(clearedAt = 100L), 1_000L).copy(removedAt = future.removedAt))
    }

    @Test fun HOM_22_theStoreAppliesTheClampWhenItReadsTheFile() {
        val dir = kotlin.io.path.createTempDirectory("recents").toFile()
        try {
            val f = File(dir, "usage/recents-state.txt"); f.parentFile.mkdirs()
            f.writeText(RecentsStateCodec.encode(RecentsState(clearedAt = 0L, removedAt = mapOf("a" to 9_999_999L))))
            val direct = Executor { it.run() }
            val store = RecentsStateStore(f, direct) { 5_000L }
            assertEquals(5_000L, store.state.value.removedAt["a"])
        } finally { dir.deleteRecursively() }
    }

    @Test fun HOM_22_tenThousandLaunchesStayBoundedOnePerAppAndFast() {
        val apps = (0 until 200).map { RecentCandidate("k$it", "p$it", false) }
        val own = (0 until 10_000).map { LaunchEvent("k${it % 200}", 1_000L + it, 0) }
        val t0 = System.nanoTime()
        val r = HomeRecents.compute(own, emptyList(), apps, emptySet(), limit = 48)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(48, r.size); assertEquals(r.size, r.toSet().size)
        assertEquals("k${9_999 % 200}", r.first())
        assertTrue("took $ms ms", ms < 2_000)
    }

    @Test fun HOM_22_aSightingWithAFutureTimestampCannotHideBehindARemovalMadeNow() {
        // The user removes "a" now (t=1000); a later launch (t=2000) shows it again, a clear at 1500 hides only what came before it.
        val apps = listOf(RecentCandidate("a", "pa", false), RecentCandidate("b", "pb", false))
        val own = listOf(LaunchEvent("a", 900, 0), LaunchEvent("b", 1_200, 0), LaunchEvent("a", 2_000, 0))
        assertEquals(listOf("a", "b"), HomeRecents.compute(own, emptyList(), apps, emptySet(), RecentsState(removedAt = mapOf("a" to 1_000L))))
        assertEquals(listOf("a"), HomeRecents.compute(own, emptyList(), apps, emptySet(), RecentsState(clearedAt = 1_500L)))
    }

    @Test fun HOM_22_workAndPersonalFiltersNeverDropAnAppFromTheFullListAndRecentsStateIsPerKey() {
        val apps = listOf(RecentCandidate("p#0", "pkg", false), RecentCandidate("p#10", "pkg", true))
        val own = listOf(LaunchEvent("p#0", 1, 0), LaunchEvent("p#10", 2, 0))
        assertEquals(listOf("p#10", "p#0"), HomeRecents.compute(own, emptyList(), apps, emptySet()))
        assertEquals(listOf("p#10"), HomeRecents.compute(own, emptyList(), apps, emptySet(), profile = RecentsProfile.WorkOnly))
        assertEquals(listOf("p#0"), HomeRecents.compute(own, emptyList(), apps, emptySet(), profile = RecentsProfile.PersonalOnly))
        // removing the work copy leaves the personal copy
        assertEquals(listOf("p#0"), HomeRecents.compute(own, emptyList(), apps, emptySet(), RecentsState(removedAt = mapOf("p#10" to 5L))))
    }
}
