package com.bos.sphere.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * HOM-18: the pure rules of the timeline log. One launch is one line `time TAB hour TAB key`; the log keeps the newest
 * [MAX] launches and is compacted (rewritten without the oldest lines) once it holds [MAX] + [SLACK] lines, so a write is
 * almost always a one-line append.
 */
object TimelineLogCodec {
    const val MAX = 5000
    const val SLACK = 500

    fun encode(e: LaunchEvent): String = "${e.timeMillis}\t${e.hourOfDay}\t${e.key.replace('\t', ' ').replace('\n', ' ')}"

    fun decode(line: String): LaunchEvent? {
        val a = line.indexOf('\t'); if (a <= 0) return null
        val b = line.indexOf('\t', a + 1); if (b <= a + 1) return null
        val time = line.substring(0, a).toLongOrNull() ?: return null
        val hour = line.substring(a + 1, b).toIntOrNull() ?: return null
        val key = line.substring(b + 1)
        return if (key.isEmpty()) null else LaunchEvent(key, time, hour)
    }

    /** The newest [max] events (the list is in recording order). */
    fun compact(events: List<LaunchEvent>, max: Int = MAX): List<LaunchEvent> = if (events.size <= max) events else events.takeLast(max)

    /** True when a log that holds [lines] lines should be rewritten. */
    fun needsCompaction(lines: Int, max: Int = MAX, slack: Int = SLACK): Boolean = lines >= max + slack

    /** [seed] events that are older than everything in [existing] and not already in it, then [existing]; bounded. */
    fun merge(seed: List<LaunchEvent>, existing: List<LaunchEvent>, max: Int = MAX): List<LaunchEvent> {
        val oldest = existing.firstOrNull()?.timeMillis ?: Long.MAX_VALUE
        val older = seed.filter { it.timeMillis < oldest }
        return compact(older + existing, max)
    }
}

/**
 * HOM-18: the launch log behind the Timeline panel, larger than the 400-entry history that feeds the suggester. It lives in
 * a plain file under `files/usage` (a directory the backup rules already exclude, listed in
 * [StateSchema.NEVER_EXPORTED_FILES]), so a few thousand lines never grow a preferences file. Reading, appending and
 * compaction all run on [io], never on the caller's thread; [events] shows what was recorded in this process at once.
 */
class TimelineLogStore(
    private val file: File,
    private val io: Executor = SHARED_IO,
    private val max: Int = TimelineLogCodec.MAX,
    private val slack: Int = TimelineLogCodec.SLACK,
) {
    private val lock = Any()
    private val _events = MutableStateFlow<List<LaunchEvent>>(emptyList())
    val events: StateFlow<List<LaunchEvent>> = _events.asStateFlow()
    private var lines = 0
    private var loaded = false
    private val pending = ArrayList<LaunchEvent>()
    private var seed: List<LaunchEvent> = emptyList()

    init { io.execute { load() } }

    private fun load() {
        val read = runCatching { if (file.exists()) file.readLines().mapNotNull(TimelineLogCodec::decode) else emptyList() }.getOrDefault(emptyList())
        synchronized(lock) {
            val merged = TimelineLogCodec.merge(seed, read + pending.filter { p -> read.none { it == p } })
            lines = read.size
            loaded = true
            pending.clear()
            // What was recorded before the read finished is part of the log: write the merged result once.
            val rewrite = merged.size != read.size || TimelineLogCodec.needsCompaction(read.size, max, slack)
            _events.value = merged
            if (rewrite) writeAll(merged)
        }
    }

    /** Called once with the older 400-entry history so a first run does not lose it. */
    fun seedFrom(history: List<LaunchEvent>) {
        if (history.isEmpty()) return
        synchronized(lock) {
            seed = history
            if (loaded) {
                val merged = TimelineLogCodec.merge(history, _events.value, max)
                if (merged.size != _events.value.size) { _events.value = merged; io.execute { synchronized(lock) { writeAll(merged) } } }
            }
        }
    }

    /** Never blocks: the in-memory list changes now, the file write is queued. */
    fun record(e: LaunchEvent) {
        synchronized(lock) {
            _events.value = TimelineLogCodec.compact(_events.value + e, max)
            if (!loaded) { pending += e; return }
        }
        io.execute {
            synchronized(lock) {
                if (TimelineLogCodec.needsCompaction(lines + 1, max, slack)) writeAll(_events.value)
                else runCatching { file.parentFile?.mkdirs(); file.appendText(TimelineLogCodec.encode(e) + "\n"); lines++ }
            }
        }
    }

    private fun writeAll(list: List<LaunchEvent>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(list.joinToString("") { TimelineLogCodec.encode(it) + "\n" })
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
            lines = list.size
        }
    }

    companion object {
        const val RELATIVE_PATH = "usage/timeline.log"
        private val SHARED_IO: Executor by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "timeline-log").apply { isDaemon = true } } }
    }
}
