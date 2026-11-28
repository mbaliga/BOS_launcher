package com.bos.sphere.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Wave 41 (timelineRich): the kinds of event the Timeline shows. Opens come from the launch log and notifications from the
 * notification history, so those are never stored again; the kinds in [stored] have no other record and are kept in [TimelineEventStore].
 */
enum class TimelineKind(val stored: Boolean = false, val family: Family = Family.Other) {
    AppOpened(family = Family.Open),
    ScheduledLaunch(stored = true, family = Family.Open),
    Notification(family = Family.Notification),
    Message(family = Family.Notification),
    Email(family = Family.Notification),
    Promo(family = Family.Notification),
    Download(family = Family.Notification),
    Alert(family = Family.Alert),
    Call(family = Family.Alert),
    MissedCall(family = Family.Alert),
    Calendar(family = Family.Alert),
    Alarm(family = Family.Alert),
    Security(family = Family.Alert),
    PackageInstalled(stored = true, family = Family.Package),
    PackageUpdated(stored = true, family = Family.Package),
    PackageRemoved(stored = true, family = Family.Package),
    AppHidden(stored = true, family = Family.Package),
    AppUnhidden(stored = true, family = Family.Package);

    /** What the day summary counts the kind as. */
    enum class Family { Open, Notification, Alert, Package, Other }

    companion object {
        fun parse(s: String): TimelineKind? = entries.firstOrNull { it.name == s }
    }
}

/** One recorded event of a stored kind: [packageName] is the app, [label] its name at the time (the app may be gone by now), [detail] free text. */
data class StoredTimelineEvent(val timeMillis: Long, val kind: TimelineKind, val packageName: String, val label: String = "", val detail: String = "") {
    val id: String get() = "ev|$timeMillis|${kind.name}|$packageName"
}

/** The line format of the stored events: `time TAB kind TAB package TAB label TAB detail`, `\`-escaped. */
object TimelineEventCodec {
    const val MAX = 5000
    const val SLACK = 500

    private fun esc(s: String) = buildString(s.length) { for (c in s) when (c) { '\\' -> append("\\\\"); '\t' -> append("\\t"); '\n' -> append("\\n"); '\r' -> append("\\r"); else -> append(c) } }
    private fun unesc(s: String): String {
        if ('\\' !in s) return s
        val b = StringBuilder(s.length); var i = 0
        while (i < s.length) { val c = s[i]; if (c == '\\' && i + 1 < s.length) { b.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; 'r' -> '\r'; else -> s[i + 1] }); i += 2 } else { b.append(c); i++ } }
        return b.toString()
    }

    fun encode(e: StoredTimelineEvent): String = listOf(e.timeMillis.toString(), e.kind.name, esc(e.packageName), esc(e.label), esc(e.detail)).joinToString("\t")

    fun decode(line: String): StoredTimelineEvent? {
        val f = line.split('\t')
        if (f.size != 5) return null
        val time = f[0].toLongOrNull() ?: return null
        val kind = TimelineKind.parse(f[1])?.takeIf { it.stored } ?: return null
        return StoredTimelineEvent(time, kind, unesc(f[2]), unesc(f[3]), unesc(f[4]))
    }

    fun compact(events: List<StoredTimelineEvent>, max: Int = MAX): List<StoredTimelineEvent> = if (events.size <= max) events else events.takeLast(max)
    fun needsCompaction(lines: Int, max: Int = MAX, slack: Int = SLACK) = lines >= max + slack

    /** True when [e] repeats the newest same-kind, same-app event within [windowMs] (the system repeats package callbacks in bursts). */
    fun isRepeat(existing: List<StoredTimelineEvent>, e: StoredTimelineEvent, windowMs: Long = 60_000L): Boolean =
        existing.asReversed().takeWhile { e.timeMillis - it.timeMillis <= windowMs }.any { it.kind == e.kind && it.packageName == e.packageName && it.label == e.label && it.timeMillis <= e.timeMillis }
}

/**
 * The persisted, bounded record of the Timeline events that nothing else records (scheduled launch fired, package installed / updated / removed,
 * app hidden / unhidden). A plain file under `files/usage`, a directory the backup rules exclude and [StateSchema.NEVER_EXPORTED_FILES] lists. Reading,
 * appending and compaction run on [io]; [events] shows what was recorded in this process at once. Oldest first.
 */
class TimelineEventStore(
    private val file: File,
    private val io: Executor = SHARED_IO,
    private val max: Int = TimelineEventCodec.MAX,
    private val slack: Int = TimelineEventCodec.SLACK,
) {
    private val lock = Any()
    private val _events = MutableStateFlow<List<StoredTimelineEvent>>(emptyList())
    val events: StateFlow<List<StoredTimelineEvent>> = _events.asStateFlow()
    private var lines = 0
    private var loaded = false
    private val pending = ArrayList<StoredTimelineEvent>()
    private val unwritten = ArrayList<StoredTimelineEvent>()

    init { io.execute { load() } }

    private fun load() {
        val read = runCatching { if (file.exists()) file.readLines().mapNotNull(TimelineEventCodec::decode) else emptyList() }.getOrDefault(emptyList())
        synchronized(lock) {
            val merged = TimelineEventCodec.compact((read + pending).sortedBy { it.timeMillis }, max)
            lines = read.size; loaded = true
            val rewrite = pending.isNotEmpty() || merged.size != read.size || TimelineEventCodec.needsCompaction(read.size, max, slack)
            pending.clear(); unwritten.clear()
            _events.value = merged
            if (rewrite) writeAll(merged)
        }
    }

    /** Never blocks: the in-memory list changes now, the file write is queued. A burst repeat of the same event is dropped. */
    fun record(e: StoredTimelineEvent) {
        synchronized(lock) {
            if (!e.kind.stored || TimelineEventCodec.isRepeat(_events.value, e)) return
            _events.value = TimelineEventCodec.compact(_events.value + e, max)
            if (!loaded) { pending += e; return }
            unwritten += e
        }
        io.execute {
            synchronized(lock) {
                if (unwritten.isEmpty()) return@synchronized
                val batch = unwritten.toList(); unwritten.clear()
                if (TimelineEventCodec.needsCompaction(lines + batch.size, max, slack)) writeAll(_events.value)
                else runCatching {
                    file.parentFile?.mkdirs()
                    file.appendText(batch.joinToString("") { TimelineEventCodec.encode(it) + "\n" })
                    lines += batch.size
                }
            }
        }
    }

    private fun writeAll(list: List<StoredTimelineEvent>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(list.joinToString("") { TimelineEventCodec.encode(it) + "\n" })
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
            lines = list.size
        }
    }

    companion object {
        const val RELATIVE_PATH = "usage/timeline-events.log"
        private val SHARED_IO: Executor by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "timeline-events").apply { isDaemon = true } } }
    }
}

/** The process-wide store; created on first use, safe to call from any thread. [record] does nothing before [init]. */
object TimelineEvents {
    @Volatile private var store: TimelineEventStore? = null

    fun init(context: Context): TimelineEventStore =
        store ?: synchronized(this) { store ?: TimelineEventStore(File(context.applicationContext.filesDir, TimelineEventStore.RELATIVE_PATH)).also { store = it } }

    fun get(): TimelineEventStore? = store

    fun record(kind: TimelineKind, packageName: String, label: String = "", detail: String = "", timeMillis: Long = System.currentTimeMillis()) {
        store?.record(StoredTimelineEvent(timeMillis, kind, packageName, label, detail))
    }

    /** Test seam. */
    fun setForTest(s: TimelineEventStore?) { store = s }
}
