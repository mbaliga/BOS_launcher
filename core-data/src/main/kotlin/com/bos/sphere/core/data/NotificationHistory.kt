package com.bos.sphere.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Wave 25 (hubData): one recorded notification. [id] is `key|time`, so a chat that posts a new message under the same key is a new
 * entry while a repeat of the same post is not. [dismissed] means it is no longer in the system shade (the user cleared it or the
 * app cancelled it); the entry stays in history either way.
 */
data class HistoryEntry(
    val id: String,
    val key: String,
    val packageName: String,
    val appLabel: String,
    val sender: String,
    val title: String,
    val text: String,
    val conversation: String,
    val timeMillis: Long,
    val read: Boolean = false,
    val dismissed: Boolean = false,
    /** W42: the app's conversation shortcut for this notification (empty when it had none) and the user it belongs to: a deep link that survives a restart. */
    val shortcutId: String = "",
    val userId: Int = 0,
    /** Wave 41: the notification category (`msg`, `call`, `alarm`...) and priority, kept so the Timeline can tell an alert from a plain post. Empty / 0 on older lines. */
    val category: String = "",
    val priority: Int = 0,
) {
    /** The name shown in bold: the sender, else the title, else the app. */
    val who: String get() = sender.ifBlank { title.ifBlank { appLabel.ifBlank { packageName } } }
}

/** The pure line format: one record per line, fields separated by tab, `\`-escaped; the last line for an id wins. */
object NotificationHistoryCodec {
    const val MAX = 5000
    const val SLACK = 500

    private fun esc(s: String) = buildString(s.length) {
        for (c in s) when (c) { '\\' -> append("\\\\"); '\t' -> append("\\t"); '\n' -> append("\\n"); '\r' -> append("\\r"); else -> append(c) }
    }

    private fun unesc(s: String): String {
        if ('\\' !in s) return s
        val b = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { b.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; 'r' -> '\r'; else -> s[i + 1] }); i += 2 } else { b.append(c); i++ }
        }
        return b.toString()
    }

    fun encode(e: HistoryEntry): String = listOf(
        e.timeMillis.toString(), (if (e.read) "r" else "u") + (if (e.dismissed) "d" else "a"),
        esc(e.key), esc(e.packageName), esc(e.appLabel), esc(e.sender), esc(e.title), esc(e.text), esc(e.conversation),
    ).let { if (e.shortcutId.isEmpty() && e.category.isEmpty() && e.priority == 0) it else it + listOf(esc(e.shortcutId), e.userId.toString(), esc(e.category), e.priority.toString()) }.joinToString("\t") // W42 + W41: four optional trailing fields (shortcut, user, category, priority), so 9-field lines of older installs read unchanged

    fun decode(line: String): HistoryEntry? {
        val f = line.split('\t')
        if (f.size != 9 && f.size != 13) return null
        val time = f[0].toLongOrNull() ?: return null
        val flags = f[1]
        if (flags.length != 2 || flags[0] !in "ru" || flags[1] !in "ad") return null
        val key = unesc(f[2]); if (key.isEmpty()) return null
        return HistoryEntry(
            id = idOf(key, time), key = key, packageName = unesc(f[3]), appLabel = unesc(f[4]), sender = unesc(f[5]), title = unesc(f[6]),
            text = unesc(f[7]), conversation = unesc(f[8]), timeMillis = time, read = flags[0] == 'r', dismissed = flags[1] == 'd',
            shortcutId = if (f.size == 13) unesc(f[9]) else "", userId = if (f.size == 13) f[10].toIntOrNull() ?: 0 else 0,
            category = if (f.size == 13) unesc(f[11]) else "", priority = if (f.size == 13) f[12].toIntOrNull() ?: 0 else 0,
        )
    }

    fun idOf(key: String, time: Long) = "$key|$time"

    fun needsCompaction(lines: Int, max: Int = MAX, slack: Int = SLACK) = lines >= max + slack

    /** Keeps the newest [max] entries (by time). */
    fun bound(all: Collection<HistoryEntry>, max: Int = MAX): List<HistoryEntry> =
        if (all.size <= max) all.sortedBy { it.timeMillis } else all.sortedBy { it.timeMillis }.takeLast(max)
}

/**
 * Wave 25 (hubData): the persistent notification history behind the Launchpad hub views (All, Unread, Read). Every posted
 * notification is recorded with its read state, so history survives a restart and survives the notification leaving the shade.
 *
 * - File: `files/usage/notification-history.log`. `usage` is already excluded from system backup and device transfer and the path is in
 *   [StateSchema.NEVER_EXPORTED_FILES]: message text is the most private data this app holds, so it never leaves the device by any route.
 * - Bounded to [max] entries (newest kept). A change is one appended line; once the file holds [max] + [slack] lines it is rewritten with
 *   one line per entry. Reading, appending and rewriting run on [io] (a background thread), never on the caller; [entries] changes at once.
 * - Ongoing and progress notifications (music, a running download) are not history; a repeat of the same text under the same key is not a new entry.
 * - Clearing the shade only flags entries dismissed. Entries leave history only through [delete] or [deleteAll] (the user's explicit Delete history).
 */
class NotificationHistoryStore(
    private val file: File,
    private val io: Executor = SHARED_IO,
    private val max: Int = NotificationHistoryCodec.MAX,
    private val slack: Int = NotificationHistoryCodec.SLACK,
) {
    private val lock = Any()
    private val map = LinkedHashMap<String, HistoryEntry>()
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    /** Newest first. */
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()
    private var lines = 0
    private var loaded = false
    /** Changes made before the file was read; written once the read is done. */
    private val early = ArrayList<HistoryEntry>()
    private val unwritten = ArrayList<HistoryEntry>()
    private var rewritePending = false

    init { io.execute { load() } }

    private fun publish() { _entries.value = map.values.sortedByDescending { it.timeMillis } }

    private fun load() {
        val read = runCatching { if (file.exists()) file.readLines() else emptyList() }.getOrDefault(emptyList())
        synchronized(lock) {
            val fromFile = LinkedHashMap<String, HistoryEntry>()
            read.forEach { l -> NotificationHistoryCodec.decode(l)?.let { fromFile[it.id] = it } }
            // What changed in memory before the read finished wins over the file.
            val inMemory = LinkedHashMap(map)
            map.clear(); map.putAll(fromFile); map.putAll(inMemory)
            early.clear()
            boundMap()
            loaded = true
            lines = read.size
            publish()
            if (inMemory.isNotEmpty() || NotificationHistoryCodec.needsCompaction(read.size, max, slack) || map.size != fromFile.size) writeAll()
            unwritten.clear()
        }
    }

    private fun boundMap() {
        if (map.size <= max) return
        val keep = NotificationHistoryCodec.bound(map.values, max)
        map.clear(); keep.forEach { map[it.id] = it }
    }

    private fun write(changed: List<HistoryEntry>, rewrite: Boolean = false) {
        synchronized(lock) {
            publish()
            if (!loaded) return
            if (rewrite) rewritePending = true else unwritten += changed
        }
        io.execute {
            synchronized(lock) {
                if (rewritePending || NotificationHistoryCodec.needsCompaction(lines + unwritten.size, max, slack)) { rewritePending = false; unwritten.clear(); writeAll() }
                else if (unwritten.isNotEmpty()) {
                    val batch = unwritten.toList(); unwritten.clear()
                    runCatching {
                        file.parentFile?.mkdirs()
                        file.appendText(batch.joinToString("") { NotificationHistoryCodec.encode(it) + "\n" })
                        lines += batch.size
                    }
                }
            }
        }
    }

    private fun writeAll() {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(map.values.sortedBy { it.timeMillis }.joinToString("") { NotificationHistoryCodec.encode(it) + "\n" })
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
            lines = map.size
        }
    }

    /**
     * The system feed changed: records what is new, flags what left the shade as dismissed (and clears the flag on what came back).
     * Never blocks the caller.
     */
    fun observe(feed: List<HubNotification>) {
        val changed = ArrayList<HistoryEntry>()
        synchronized(lock) {
            val live = HashSet<String>()
            for (n in feed) {
                if (n.isOngoing || n.progress) continue
                live += n.key
                val sender = n.sender.orEmpty().ifBlank { n.title }
                val newest = latestFor(n.key)
                val same = newest != null && newest.text == n.summary && newest.title == n.title && newest.sender == sender
                if (same) {
                    if (newest!!.dismissed) { val u = newest.copy(dismissed = false); map[u.id] = u; changed += u }
                } else {
                    val e = HistoryEntry(
                        NotificationHistoryCodec.idOf(n.key, n.timestamp), n.key, n.packageName, n.appLabel.ifBlank { n.source }, sender, n.title,
                        n.summary, n.conversation.orEmpty(), n.timestamp,
                        shortcutId = n.shortcutId.orEmpty(), userId = n.profileId,
                        category = n.category ?: if (n.isMessaging) "msg" else "", priority = n.priority,
                    )
                    map[e.id] = e; changed += e
                }
            }
            for (e in map.values.toList()) if (!e.dismissed && e.key !in live) { val u = e.copy(dismissed = true); map[u.id] = u; changed += u }
            if (changed.isEmpty()) return
            boundMap()
        }
        write(changed)
    }

    private fun latestFor(key: String): HistoryEntry? = map.values.filter { it.key == key }.maxByOrNull { it.timeMillis }

    /** The user opened or marked the notification [key] (all its entries). */
    fun markRead(key: String) = setReadWhere(true) { it.key == key }

    fun markAllRead() = setReadWhere(true) { true }

    /** Sets read state for the given entry ids; returns the ids whose state actually changed (for an undo). */
    fun setRead(ids: Collection<String>, read: Boolean): List<String> {
        val set = ids.toSet()
        return setReadWhere(read) { it.id in set }
    }

    private fun setReadWhere(read: Boolean, pred: (HistoryEntry) -> Boolean): List<String> {
        val changed = ArrayList<HistoryEntry>()
        synchronized(lock) {
            for (e in map.values.toList()) if (pred(e) && e.read != read) { val u = e.copy(read = read); map[u.id] = u; changed += u }
            if (changed.isEmpty()) return emptyList()
        }
        write(changed)
        return changed.map { it.id }
    }

    /** The user's explicit "delete" of single entries (a rewrite of the file). */
    fun delete(ids: Collection<String>) {
        synchronized(lock) { val set = ids.toSet(); if (set.none { it in map }) return; set.forEach { map.remove(it) } }
        write(emptyList(), rewrite = true)
    }

    /** The user's explicit "Delete history" (after a confirmation). */
    fun deleteAll() {
        synchronized(lock) { if (map.isEmpty()) return; map.clear() }
        write(emptyList(), rewrite = true)
    }

    companion object {
        const val RELATIVE_PATH = "usage/notification-history.log"
        private val SHARED_IO: Executor by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "notification-history").apply { isDaemon = true } } }
    }
}

/** The process-wide history; created on first use (the listener service or the Launchpad), safe to call from any thread. */
object NotificationHistory {
    @Volatile private var store: NotificationHistoryStore? = null

    fun init(context: Context): NotificationHistoryStore =
        store ?: synchronized(this) { store ?: NotificationHistoryStore(File(context.applicationContext.filesDir, NotificationHistoryStore.RELATIVE_PATH)).also { store = it } }

    /** Null before [init]. */
    fun get(): NotificationHistoryStore? = store

    internal fun observe(feed: List<HubNotification>) { store?.observe(feed) }
    internal fun markRead(key: String) { store?.markRead(key) }
    /** Test seam. */
    fun setForTest(s: NotificationHistoryStore?) { store = s }
}
