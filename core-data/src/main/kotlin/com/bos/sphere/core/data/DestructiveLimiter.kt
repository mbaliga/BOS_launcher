package com.bos.sphere.core.data

/**
 * CFG-04: decides which destructive edits take a snapshot. The limit is kept per kind of edit (the reason a store passes to
 * [DestructiveEdits.before]): a second edit of the same kind within [gapMs] is part of the same burst and takes none, because
 * the first one already captured the state before it. An edit of a different kind always gets its own snapshot, so the
 * state before "clear corner" is kept even when it follows "hide app" a second later.
 *
 * A burst is a run of destructive edits with no pause of [gapMs] or more. At most [maxPerBurst] snapshots are taken in one
 * burst; with the snapshot store keeping [maxPerBurst] files, the snapshot that holds the state before the burst is never
 * the one that is pushed out.
 */
class DestructiveLimiter(private val gapMs: Long = 5_000L, private val maxPerBurst: Int = SnapshotStore.MAX_SNAPSHOTS) {
    private val lastByKind = HashMap<String, Long>()
    private var lastAny = Long.MIN_VALUE
    private var inBurst = 0

    @Synchronized
    fun allow(kind: String, now: Long): Boolean {
        if (lastAny != Long.MIN_VALUE && now - lastAny >= gapMs) { inBurst = 0; lastByKind.clear() }
        lastAny = now
        val last = lastByKind[kind]
        if (last != null && now - last < gapMs) return false
        if (inBurst >= maxPerBurst) return false
        lastByKind[kind] = now
        inBurst++
        return true
    }
}
