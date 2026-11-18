package com.bos.sphere.ui

import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.NotificationHistoryCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 29 (commsRead, STR-02): the read filter state machine and the merge of the live list with the history, as pure functions. */
class CommsReadLogicTest {
    private fun live(key: String, pkg: String, ts: Long, text: String = "t$key") =
        HubNotification(id = key, key = key, source = pkg, title = "Who $key", summary = text, timestamp = ts, packageName = pkg, appLabel = pkg.uppercase(), sender = "Who $key", isMessaging = true)
    private fun hist(key: String, pkg: String, ts: Long, read: Boolean = false, dismissed: Boolean = true, text: String = "t$key") =
        HistoryEntry(NotificationHistoryCodec.idOf(key, ts), key, pkg, pkg.uppercase(), "Who $key", "Who $key", text, "", ts, read, dismissed)

    // ---- the three-stop filter ----

    @Test fun STR_02_w29_everyPinchTransitionOfTheThreeStopFilter() {
        val all = HubView.All
        assertEquals(all to ReadFilter.Unread, CommsRead.step(all, ReadFilter.All, PinchDir.In))
        assertEquals(all to ReadFilter.Read, CommsRead.step(all, ReadFilter.All, PinchDir.Out))
        assertEquals(all to ReadFilter.All, CommsRead.step(all, ReadFilter.Unread, PinchDir.Out))
        assertEquals(all to ReadFilter.All, CommsRead.step(all, ReadFilter.Read, PinchDir.In))
        assertNull("already at the Unread end", CommsRead.step(all, ReadFilter.Unread, PinchDir.In))
        assertNull("already at the Read end", CommsRead.step(all, ReadFilter.Read, PinchDir.Out))
        // the Unread tab and the Read history are the end stops of the same machine
        assertEquals(all to ReadFilter.All, CommsRead.step(HubView.Unread, ReadFilter.Read, PinchDir.Out))
        assertNull(CommsRead.step(HubView.Unread, ReadFilter.All, PinchDir.In))
        assertEquals(all to ReadFilter.All, CommsRead.step(HubView.Read, ReadFilter.Unread, PinchDir.In))
        assertNull(CommsRead.step(HubView.Read, ReadFilter.All, PinchDir.Out))
        // the Files and Timeline panels have no read state
        assertNull(CommsRead.step(HubView.Files, ReadFilter.All, PinchDir.In))
        assertNull(CommsRead.step(HubView.Timeline, ReadFilter.All, PinchDir.Out))
    }

    @Test fun STR_02_w29_theFilterIsOneStopPerStepAndNeverSkipsAStop() {
        var f = ReadFilter.Unread
        val seen = mutableListOf(f)
        repeat(4) { f = f.towardRead(); seen += f }
        assertEquals(listOf(ReadFilter.Unread, ReadFilter.All, ReadFilter.Read, ReadFilter.Read, ReadFilter.Read), seen)
        assertEquals(ReadFilter.All, ReadFilter.Read.towardUnread())
        assertTrue(ReadFilter.Unread.matches(false) && !ReadFilter.Unread.matches(true))
        assertTrue(ReadFilter.Read.matches(true) && !ReadFilter.Read.matches(false))
        assertTrue(ReadFilter.All.matches(true) && ReadFilter.All.matches(false))
    }

    @Test fun STR_02_w29_aSpreadChangeUnderEightPercentIsInsideTheDeadZone() {
        assertNull(CommsRead.direction(1f)); assertNull(CommsRead.direction(1.079f)); assertNull(CommsRead.direction(0.921f))
        assertEquals(PinchDir.Out, CommsRead.direction(1.09f)); assertEquals(PinchDir.In, CommsRead.direction(0.91f))
        assertNull(CommsRead.direction(Float.NaN)); assertNull(CommsRead.direction(-2f)); assertNull(CommsRead.direction(Float.POSITIVE_INFINITY))
    }

    // ---- the merge ----

    @Test fun STR_02_w29_allIsTheLiveListPlusTheHistoryOneRowPerKeyNewestFirst() {
        val liveRows = listOf(live("a", "chat", 500), live("b", "chat", 300))
        val history = listOf(hist("a", "chat", 500, dismissed = false), hist("b", "chat", 300, dismissed = false), hist("c", "mail", 400), hist("d", "cal", 100, read = true), hist("c", "mail", 200))
        val m = CommsRead.merge(liveRows, history, emptySet())
        assertEquals(listOf("a", "c", "b", "d"), m.items.map { it.key })
        assertEquals("one row per key, the newest history entry for a key that left the shade", 400L, m.items.first { it.key == "c" }.timestamp)
        assertEquals(setOf("c", "d"), m.historyOnly.keys)
        assertEquals("the live row wins over its history entry", liveRows[0], m.items.first { it.key == "a" })
    }

    @Test fun STR_02_w29_aReadNotificationThatLeftTheShadeStaysAndStaysRead() {
        val before = CommsRead.merge(listOf(live("a", "chat", 500)), listOf(hist("a", "chat", 500, read = true, dismissed = false)), setOf("a"))
        assertEquals(listOf("a"), before.items.map { it.key }); assertTrue("a" in before.readKeys)
        // the app cancelled it: it is gone from the shade, the history entry is flagged dismissed
        val after = CommsRead.merge(emptyList(), listOf(hist("a", "chat", 500, read = true, dismissed = true)), emptySet())
        assertEquals("a read notification that left the shade is still in ALL", listOf("a"), after.items.map { it.key })
        assertTrue("and still read", "a" in after.readKeys)
        assertNotNull(after.historyOnly["a"])
    }

    @Test fun STR_02_w29_aLiveRowIsReadWhenItsHistoryEntryOfTheSamePostIsReadButNotWhenItWasUpdated() {
        val same = CommsRead.merge(listOf(live("a", "chat", 500)), listOf(hist("a", "chat", 500, read = true, dismissed = false)), emptySet())
        assertTrue("the shade's read marks do not survive a restart, the history does", "a" in same.readKeys)
        val updated = CommsRead.merge(listOf(live("a", "chat", 900)), listOf(hist("a", "chat", 500, read = true, dismissed = false), hist("a", "chat", 900, dismissed = false)), emptySet())
        assertTrue("a newer message under the same key is unread again", "a" !in updated.readKeys)
    }

    @Test fun STR_02_w29_deletingFromHistoryRemovesTheRowOnlyOnceItIsOutOfTheShade() {
        val e = hist("c", "mail", 400)
        assertEquals(listOf("c"), CommsRead.merge(emptyList(), listOf(e), emptySet()).items.map { it.key })
        assertTrue(CommsRead.merge(emptyList(), emptyList(), emptySet()).items.isEmpty())
        assertEquals("a live notification stays while it is in the shade", listOf("c"), CommsRead.merge(listOf(live("c", "mail", 400)), emptyList(), emptySet()).items.map { it.key })
    }

    @Test fun STR_02_w29_aHistoryOnlyRowCarriesNoIntentAndNoActions() {
        val n = CommsRead.fromEntry(hist("c", "mail", 400, text = "Payment received"))
        assertNull(n.contentIntent); assertTrue(n.actions.isEmpty()); assertEquals("Payment received", n.summary); assertEquals("mail", n.packageName)
        assertTrue(n.isMessaging)
    }

    @Test fun STR_02_w29_filteredCountsKeepEveryAppListedAndZeroAppsStayAtZero() {
        val rows = listOf(live("a", "chat", 500), live("b", "chat", 400), live("c", "mail", 300), live("d", "cal", 200))
        val read = setOf("c", "d", "a")
        val all = CommsRead.counts(rows, read, ReadFilter.All)
        assertEquals(listOf("chat" to 2, "cal" to 1, "mail" to 1), all.map { it.packageName to it.count })
        val unread = CommsRead.counts(rows, read, ReadFilter.Unread)
        assertEquals("same apps, same order, the filter's counts", listOf("chat" to 1, "cal" to 0, "mail" to 0), unread.map { it.packageName to it.count })
        val onlyRead = CommsRead.counts(rows, read, ReadFilter.Read)
        assertEquals(listOf("chat" to 1, "cal" to 1, "mail" to 1), onlyRead.map { it.packageName to it.count })
    }

    @Test fun STR_02_w29_theHistoryBoundStillPrunesTheOldestAt5000() {
        val many = (1..5001).map { hist("k$it", "chat", it.toLong()) }
        val kept = NotificationHistoryCodec.bound(many)
        assertEquals(5000, kept.size)
        assertTrue("the oldest went", kept.none { it.key == "k1" })
        assertEquals(5000, CommsRead.merge(emptyList(), kept, emptySet()).items.size)
    }

    @Test fun STR_02_w29_aLiveRowReadInTheShadeIsUnreadAgainWhenItsChatGetsANewMessageUnderTheSameKey() {
        // the in-memory read mark is by key; the history knows the post: read at 500, the new post at 900 is unread
        val m = CommsRead.merge(listOf(live("a", "chat", 900)), listOf(hist("a", "chat", 500, read = true, dismissed = false), hist("a", "chat", 900, dismissed = false)), setOf("a"))
        assertTrue("the new message is unread although the key carries an old read mark", "a" !in m.readKeys)
        // a mark that the history has not caught up with (no older read post) still counts
        val fresh = CommsRead.merge(listOf(live("b", "chat", 900)), listOf(hist("b", "chat", 900, dismissed = false)), setOf("b"))
        assertTrue("b" in fresh.readKeys)
    }

    @Test fun STR_02_w29_aSwipeWhoseSpreadDriftsIsNotAPinchButARealPinchIs() {
        assertNull("centre moved 150 px, spread 17 px: a swipe", CommsRead.pinchOf(1.12f, 17f, 150f))
        assertEquals(PinchDir.Out, CommsRead.pinchOf(1.5f, 100f, 0f))
        assertEquals("one finger fixed: the centre moves half the spread change", PinchDir.In, CommsRead.pinchOf(0.5f, -100f, 50f))
        assertNull("inside the dead zone whatever the centre did", CommsRead.pinchOf(1.05f, 10f, 0f))
    }
}
