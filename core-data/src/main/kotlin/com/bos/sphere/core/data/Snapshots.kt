package com.bos.sphere.core.data

import java.io.File
import java.security.MessageDigest

/**
 * CFG-04: rolling local snapshots in app storage. Each snapshot is a full state document ([StateCodec.export]) in its
 * own file; only the newest [max] are kept. Plain files, so the logic is testable on the JVM.
 *
 * File name: `snap-<millis, zero padded>-<seq>-<reason slug>.json`; names sort in time order.
 */
class SnapshotStore(private val dir: File, val max: Int = MAX_SNAPSHOTS, private val clock: () -> Long = System::currentTimeMillis) {
    data class Info(val id: String, val takenAt: Long, val reason: String, val bytes: Long)

    /** Takes a snapshot of [content]. Returns null when it is identical to the newest one and [force] is false. */
    @Synchronized
    fun take(reason: String, content: String, force: Boolean = false): Info? {
        dir.mkdirs()
        val newest = list().firstOrNull()
        if (!force && newest != null && digest(read(newest.id) ?: "") == digest(content)) return null
        val now = clock()
        val seq = (list().maxOfOrNull { seqOf(it.id) } ?: 0) + 1
        val id = "snap-${now.toString().padStart(15, '0')}-${seq.toString().padStart(4, '0')}-${slug(reason)}.json"
        val tmp = File(dir, "$id.tmp")
        tmp.writeText(content)
        check(tmp.renameTo(File(dir, id))) { "Could not write snapshot" }
        prune()
        return list().firstOrNull { it.id == id }
    }

    /** Newest first. */
    @Synchronized
    fun list(): List<Info> = dir.listFiles { f -> f.isFile && f.name.startsWith("snap-") && f.name.endsWith(".json") }
        .orEmpty().sortedByDescending { it.name }.mapNotNull { f ->
            val parts = f.name.removeSuffix(".json").split('-', limit = 4)
            val at = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            Info(f.name, at, parts.getOrNull(3).orEmpty().replace('_', ' '), f.length())
        }

    @Synchronized
    fun read(id: String): String? = File(dir, id).takeIf { it.isFile && it.parentFile == dir }?.readText()

    fun latest(): Info? = list().firstOrNull()

    @Synchronized
    fun delete(id: String) { File(dir, id).takeIf { it.parentFile == dir }?.delete() }

    private fun prune() = list().drop(max).forEach { File(dir, it.id).delete() }

    private fun seqOf(id: String): Int = id.split('-').getOrNull(2)?.toIntOrNull() ?: 0

    private fun slug(r: String) = r.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').take(24).ifEmpty { "manual" }

    private fun digest(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(
            // The export time changes on every export; ignore it so "nothing changed" is detected.
            s.replace(Regex("\"exportedAt\"\\s*:\\s*\\d+"), "").toByteArray(),
        ).joinToString("") { "%02x".format(it) }

    companion object { const val MAX_SNAPSHOTS = 5 }
}

/** What safe mode offers (CFG-09). */
data class SafeModeOffer(val showMinimalGrid: Boolean, val restoreFrom: SnapshotStore.Info?)

object SafeMode {
    /**
     * In safe mode the launcher boots a minimal grid with widgets off. A restore is offered only when there is a
     * snapshot to restore from; the newest one is the last known good state (a snapshot is taken after every stable
     * start, see [StateService.onStable]).
     */
    fun plan(safeMode: Boolean, snapshots: List<SnapshotStore.Info>): SafeModeOffer =
        SafeModeOffer(showMinimalGrid = safeMode, restoreFrom = if (safeMode) snapshots.maxByOrNull { it.takenAt } else null)

    /** Features that stay off for the whole of a safe-mode run: anything that binds or hosts a widget. */
    fun widgetsEnabled(safeMode: Boolean) = !safeMode
}
