package com.bos.sphere.ui

import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.data.HubNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** W33 (commsDebt): the merge of the live feed with up to 5000 history entries is cheap enough to run on a background thread without a visible wait. */
class CommsMergeBenchW33Test {
    private fun entry(i: Int) = HistoryEntry(
        id = "h$i", key = "k${i % 3000}", packageName = "pkg.${i % 40}", appLabel = "App ${i % 40}", sender = if (i % 3 == 0) "Sender $i" else "",
        title = "Title ${i % 2500}", text = "Text ${i % 1800}", conversation = "", timeMillis = 1_700_000_000_000L + i * 1000L, read = i % 4 != 0,
    )
    private fun live(i: Int) = HubNotification(id = "l$i", key = "live$i", source = "x", title = "Live $i", summary = "Body $i", timestamp = 1_700_000_000_000L + i * 2000L, packageName = "pkg.${i % 40}", appLabel = "App")

    @Test fun CMS_W33_20_mergingFiveThousandHistoryEntriesAndTheLiveFeedTakesWellUnder100Ms() {
        val history = (0 until 5000).map(::entry)
        val liveItems = (0 until 60).map(::live)
        repeat(8) { CommsRead.merge(liveItems, history, emptySet()) } // warm the JIT: the first calls of a cold JVM are not the steady state
        val best = (0 until 5).minOf { val t = System.nanoTime(); CommsRead.merge(liveItems, history, emptySet()); (System.nanoTime() - t) / 1_000_000.0 }
        val m = CommsRead.merge(liveItems, history, emptySet())
        assertTrue("merged ${m.items.size} rows", m.items.size > 2000)
        assertTrue("5000 history entries took $best ms (budget 100 ms)", best < 100.0)
    }

    @Test fun CMS_W33_21_theMergeIsDeterministicSoTheOffThreadResultEqualsTheInlineOne() {
        val history = (0 until 1200).map(::entry); val liveItems = (0 until 20).map(::live)
        val a = CommsRead.merge(liveItems, history, setOf("live3")); val b = CommsRead.merge(liveItems, history, setOf("live3"))
        assertEquals(a.items.map { it.id }, b.items.map { it.id }); assertEquals(a.readKeys, b.readKeys)
    }
}
