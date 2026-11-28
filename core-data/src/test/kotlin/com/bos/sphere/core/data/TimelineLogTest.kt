package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executor

/** HOM-18: the timeline log is bigger than the 400-entry history, bounded, compacted, off the caller's thread and never exported. */
class TimelineLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private class QueueExecutor : Executor {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.addLast(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }

    private fun ev(i: Int) = LaunchEvent("app$i", 1_000L + i, i % 24)

    @Test
    fun HOM_18_aLineRoundTripsAndABrokenOneIsDropped() {
        val e = LaunchEvent("com.a/.Main#0", 123456789L, 7)
        assertEquals(e, TimelineLogCodec.decode(TimelineLogCodec.encode(e)))
        assertNull(TimelineLogCodec.decode("garbage"))
        assertNull(TimelineLogCodec.decode("12\t3\t"))
        // A tab or newline in a key cannot break the line format.
        assertEquals("a b c", TimelineLogCodec.decode(TimelineLogCodec.encode(LaunchEvent("a\tb\nc", 1, 1)))!!.key)
    }

    @Test
    fun HOM_18_theLogHoldsFarMoreThanTheFourHundredOfTheHistory() {
        assertTrue(TimelineLogCodec.MAX >= 5000)
        assertTrue(TimelineLogCodec.MAX > 400)
        val all = (0 until 6000).map(::ev)
        val kept = TimelineLogCodec.compact(all)
        assertEquals(5000, kept.size)
        assertEquals("app1000", kept.first().key)
        assertEquals("app5999", kept.last().key)
    }

    @Test
    fun HOM_18_compactionTriggersOnlyAfterTheSlackIsUsed() {
        assertFalse(TimelineLogCodec.needsCompaction(5499))
        assertTrue(TimelineLogCodec.needsCompaction(5500))
    }

    @Test
    fun HOM_18_recordsPersistThroughARestartAndTheFileIsBoundedAfterCompaction() {
        val file = tmp.newFolder("files").resolve("usage/timeline.log")
        val io = QueueExecutor()
        val log = TimelineLogStore(file, io, max = 10, slack = 3)
        io.drain()
        for (i in 0 until 40) { log.record(ev(i)); io.drain() }
        assertEquals(10, log.events.value.size)
        assertEquals("app39", log.events.value.last().key)
        assertTrue("the file stays below max + slack lines", file.readLines().size < 13)
        // A new process reads the same newest ten.
        val again = TimelineLogStore(file, io, max = 10, slack = 3)
        io.drain()
        assertEquals(log.events.value.map { it.key }, again.events.value.map { it.key })
    }

    @Test
    fun HOM_18_launchesQueuedTogetherAreNeverWrittenTwiceAcrossACompaction() {
        val file = tmp.newFolder("f3").resolve("usage/timeline.log")
        val io = QueueExecutor()
        val log = TimelineLogStore(file, io, max = 10, slack = 3)
        io.drain()
        for (i in 0 until 12) { log.record(ev(i)); io.drain() }
        // Three records land before the writer runs; the first write crosses the compaction line.
        for (i in 12 until 15) log.record(ev(i))
        io.drain()
        val keys = file.readLines().mapNotNull(TimelineLogCodec::decode).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("app14", keys.last())
    }

    @Test
    fun HOM_18_nothingTouchesTheFileOnTheCallingThread() {
        val file = tmp.newFolder("f2").resolve("usage/timeline.log")
        val io = QueueExecutor()
        val log = TimelineLogStore(file, io)
        log.record(ev(1))
        // The in-memory list changed at once; the file has not been touched because the executor has not run.
        assertEquals(1, log.events.value.size)
        assertFalse(file.exists())
        io.drain()
        assertTrue(file.exists())
    }

    @Test
    fun HOM_18_launchesRecordedBeforeTheReadFinishesAreKept() {
        val file = tmp.newFolder("f3").resolve("usage/timeline.log")
        file.parentFile.mkdirs()
        file.writeText((0 until 3).joinToString("") { TimelineLogCodec.encode(ev(it)) + "\n" })
        val io = QueueExecutor()
        val log = TimelineLogStore(file, io)
        log.record(ev(10))
        io.drain()
        assertEquals(listOf("app0", "app1", "app2", "app10"), log.events.value.map { it.key })
        assertEquals(4, file.readLines().size)
    }

    /** Wave 23 sweep: a FULL log (max lines) read after a launch was recorded: the list stays max long, so a size check missed it and the launch never reached the file. */
    @Test
    fun HOM_18_aLaunchRecordedWhileAFullLogLoadsStillReachesTheFile() {
        val file = tmp.newFolder("f4").resolve("usage/timeline.log")
        file.parentFile.mkdirs()
        file.writeText((0 until 5).joinToString("") { TimelineLogCodec.encode(ev(it)) + "\n" })
        val io = QueueExecutor()
        val log = TimelineLogStore(file, io, max = 5, slack = 100)
        log.record(ev(10))
        io.drain()
        assertEquals(listOf("app1", "app2", "app3", "app4", "app10"), log.events.value.map { it.key })
        assertEquals(listOf("app1", "app2", "app3", "app4", "app10"), file.readLines().mapNotNull(TimelineLogCodec::decode).map { it.key })
    }

    @Test
    fun HOM_18_theOlderHistoryIsSeededOnceWithoutDuplicates() {
        val file = tmp.newFolder("f4").resolve("usage/timeline.log")
        val io = QueueExecutor()
        val log = TimelineLogStore(file, io)
        io.drain()
        log.seedFrom((0 until 5).map(::ev))
        io.drain()
        assertEquals(5, log.events.value.size)
        log.seedFrom((0 until 5).map(::ev))
        io.drain()
        assertEquals(5, log.events.value.size)
    }

    @Test
    fun HOM_18_theLogIsClassifiedAsNeverExportedAndLivesUnderABackupExcludedDirectory() {
        assertTrue(TimelineLogStore.RELATIVE_PATH in StateSchema.NEVER_EXPORTED_FILES)
        val dir = TimelineLogStore.RELATIVE_PATH.substringBefore('/')
        assertTrue("$dir must be a backup-excluded file path", dir in StateSchema.BACKUP_EXCLUDED_FILES)
    }

    @Test
    fun HOM_18_theHistoryStoreMirrorsEveryRecordToTheLog() {
        val mirrored = ArrayList<LaunchEvent>()
        val prefs = MapSharedPreferences()
        val h = LaunchHistoryStore(prefs)
        h.mirror = { mirrored += it }
        h.record("x", 5L)
        assertEquals(listOf("x"), mirrored.map { it.key })
    }
}
