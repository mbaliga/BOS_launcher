package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executor

/** Wave 25 (hubData, owner item 2): the persistent notification history. */
class NotificationHistoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private class QueueExecutor : Executor {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.addLast(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }

    private fun n(key: String, text: String = "hello", time: Long = 1000, title: String = "Anna", pkg: String = "com.chat", ongoing: Boolean = false, progress: Boolean = false) =
        HubNotification(id = key, key = key, source = "Chat", title = title, summary = text, timestamp = time, packageName = pkg, appLabel = "Chat",
            isOngoing = ongoing, progress = progress, sender = title, conversation = "Family")

    private fun file(name: String = "f") = tmp.newFolder(name).resolve("usage/notification-history.log")

    @Test
    fun HUB_01_aLineRoundTripsIncludingTabsAndNewlinesInTheText() {
        val e = HistoryEntry("k|5", "k", "p", "App", "Anna", "T\title", "line1\nline2\\x\r", "Conv", 5, read = true, dismissed = true)
        assertEquals(e, NotificationHistoryCodec.decode(NotificationHistoryCodec.encode(e)))
        assertNull(NotificationHistoryCodec.decode("garbage"))
        assertNull(NotificationHistoryCodec.decode("1\txx\tk\tp\ta\ts\tt\tx\tc"))
    }

    @Test
    fun HUB_01_everyPostedNotificationIsRecordedUnreadAndSurvivesARestart() {
        val f = file(); val io = QueueExecutor()
        val a = NotificationHistoryStore(f, io); io.drain()
        a.observe(listOf(n("k1", "first", 1000), n("k2", "second", 2000, "Bob"))); io.drain()
        assertEquals(listOf("second", "first"), a.entries.value.map { it.text })
        assertTrue(a.entries.value.all { !it.read && !it.dismissed })
        val b = NotificationHistoryStore(f, io); io.drain()
        assertEquals(a.entries.value, b.entries.value)
    }

    @Test
    fun HUB_01_aNotificationLeavingTheShadeStaysInHistoryAsDismissedAndComesBackClear() {
        val f = file(); val io = QueueExecutor()
        val s = NotificationHistoryStore(f, io); io.drain()
        s.observe(listOf(n("k1"))); s.observe(emptyList()); io.drain()
        assertEquals(1, s.entries.value.size)
        assertTrue(s.entries.value[0].dismissed)
        // The dismissed flag is on disk too.
        val again = NotificationHistoryStore(f, io); io.drain()
        assertTrue(again.entries.value[0].dismissed)
        s.observe(listOf(n("k1"))); io.drain()
        assertEquals(1, s.entries.value.size); assertFalse(s.entries.value[0].dismissed)
    }

    @Test
    fun HUB_01_aRepeatOfTheSamePostIsNotANewEntryButANewMessageIs() {
        val s = NotificationHistoryStore(file(), Executor { it.run() })
        s.observe(listOf(n("k1", "hi", 1000))); s.observe(listOf(n("k1", "hi", 1500)))
        assertEquals(1, s.entries.value.size)
        s.observe(listOf(n("k1", "second message", 2000)))
        assertEquals(2, s.entries.value.size)
    }

    @Test
    fun HUB_01_ongoingAndProgressNotificationsAreNotHistory() {
        val s = NotificationHistoryStore(file(), Executor { it.run() })
        s.observe(listOf(n("m", ongoing = true), n("d", progress = true), n("k")))
        assertEquals(listOf("k"), s.entries.value.map { it.key })
    }

    @Test
    fun HUB_02_readStateIsSetByOpeningMarkAllReadAndPerIdAndPersists() {
        val f = file(); val io = QueueExecutor()
        val s = NotificationHistoryStore(f, io); io.drain()
        s.observe(listOf(n("k1", "a", 1), n("k2", "b", 2), n("k3", "c", 3))); io.drain()
        s.markRead("k1"); io.drain()
        assertEquals(listOf("k1"), s.entries.value.filter { it.read }.map { it.key })
        val changed = s.setRead(s.entries.value.map { it.id }, true)
        assertEquals(2, changed.size)                                    // only the unread ones changed: this is what an undo reverts
        s.setRead(changed, false); io.drain()
        assertEquals(listOf("k1"), s.entries.value.filter { it.read }.map { it.key })
        s.markAllRead(); io.drain()
        assertTrue(s.entries.value.all { it.read })
        val again = NotificationHistoryStore(f, io); io.drain()
        assertTrue(again.entries.value.all { it.read })
    }

    @Test
    fun HUB_03_clearingTheShadeKeepsHistoryAndOnlyAnExplicitDeleteRemovesIt() {
        val f = file(); val io = QueueExecutor()
        val s = NotificationHistoryStore(f, io); io.drain()
        s.observe(listOf(n("k1", "a", 1), n("k2", "b", 2))); io.drain()
        s.observe(emptyList())                                           // "Clear all" cancels everything in the shade
        io.drain()
        assertEquals(2, s.entries.value.size)
        s.delete(listOf(s.entries.value.first().id)); io.drain()
        assertEquals(1, NotificationHistoryStore(f, io).also { io.drain() }.entries.value.size)
        s.deleteAll(); io.drain()
        assertTrue(s.entries.value.isEmpty())
        assertTrue(NotificationHistoryStore(f, io).also { io.drain() }.entries.value.isEmpty())
    }

    @Test
    fun HUB_01_historyIsBoundedAndTheFileIsCompacted() {
        val f = file(); val io = QueueExecutor()
        val s = NotificationHistoryStore(f, io, max = 10, slack = 3); io.drain()
        for (i in 0 until 40) { s.observe(listOf(n("k$i", "m$i", 1000L + i))); io.drain() }
        assertEquals(10, s.entries.value.size)
        assertEquals("m39", s.entries.value.first().text)
        assertEquals("m30", s.entries.value.last().text)
        assertTrue("file stays under max + slack lines: ${f.readLines().size}", f.readLines().size < 13 + 2)
        val again = NotificationHistoryStore(f, io, max = 10, slack = 3); io.drain()
        assertEquals(10, again.entries.value.size)
    }

    @Test
    fun HUB_01_changesBeforeTheFileIsReadAreKeptAndNothingBlocksTheCaller() {
        val f = file("pre"); f.parentFile.mkdirs()
        f.writeText(NotificationHistoryCodec.encode(HistoryEntry("old|1", "old", "p", "A", "S", "T", "x", "", 1)) + "\n")
        val io = QueueExecutor()
        val s = NotificationHistoryStore(f, io)
        s.observe(listOf(n("new", "n", 5)))                              // before load ran
        assertEquals(listOf("new"), s.entries.value.map { it.key })
        assertFalse("no file write happened on the caller", f.readText().contains("new"))
        io.drain()
        assertEquals(setOf("old", "new"), s.entries.value.map { it.key }.toSet())
        assertTrue(f.readText().contains("new"))
    }

    @Test
    fun HUB_04_theHistoryFileIsNeverExportedAndExcludedFromBackup() {
        assertTrue(NotificationHistoryStore.RELATIVE_PATH in StateSchema.NEVER_EXPORTED_FILES)
        assertTrue(NotificationHistoryStore.RELATIVE_PATH.substringBefore('/') in StateSchema.BACKUP_EXCLUDED_FILES)
        assertTrue(StateSchema.COMMS_WIDGETS_FILE in StateSchema.BACKUP_EXCLUDED_PREF_FILES) // wave 58: the hub settings are exported; only its widget list stays on the device
    }

    @Test
    fun HUB_02_openingANotificationThroughLaunchpadSettingsMarksItsHistoryRead() {
        val s = NotificationHistoryStore(file("lp"), Executor { it.run() })
        NotificationHistory.setForTest(s)
        try {
            s.observe(listOf(n("k1")))
            LaunchpadSettings.markRead("k1")
            assertTrue(s.entries.value.single().read)
        } finally { NotificationHistory.setForTest(null); LaunchpadSettings.pruneToFeed(setOf("none"), 0) } // forget the shared read mark
    }
}
