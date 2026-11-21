package com.bos.sphere.core.data

/**
 * CFG-04: decides which destructive edits take a snapshot. The limit is kept per kind of edit (the reason a store passes to
 * [DestructiveEdits.before]): a second edit of the same kind within [gapMs] is part of the same burst and takes none, because
 * the first one already captured the state before it. An edit of a different kind always gets its own snapshot, so the
 * state before "clear corner" is kept even when it follows "hide app" a second later.
 *
 * A burst is a run of destructive edits with no pause of [gapMs] or more. At most [maxPerBurst] snapshots are taken in one
 * burst; with the snapshot store keeping [maxPerBurst] files, the snapshot that holds the state before the burst is never
 * the one that is pushed out. The app builds it without that cap ([Int.MAX_VALUE]) and instead pins the first snapshot of
 * each burst in the store ([SnapshotStore.take] with `pin`), so a burst of any number of distinct kinds keeps both the state
 * before its first edit and the latest distinct kinds.
 */
class DestructiveLimiter(private val gapMs: Long = 5_000L, private val maxPerBurst: Int = SnapshotStore.MAX_SNAPSHOTS) {
    private val lastByKind = HashMap<String, Long>()
    private var lastAny = Long.MIN_VALUE
    private var inBurst = 0

    /** [take]: store a snapshot of the state before this edit. [burstStart]: it is the first of a new burst, so the store pins it. */
    data class Decision(val take: Boolean, val burstStart: Boolean)

    @Synchronized
    fun allow(kind: String, now: Long): Boolean = decide(kind, now).take

    @Synchronized
    fun decide(kind: String, now: Long): Decision {
        var start = false
        if (lastAny == Long.MIN_VALUE || now - lastAny >= gapMs) { inBurst = 0; lastByKind.clear(); start = true }
        lastAny = now
        val last = lastByKind[kind]
        if (last != null && now - last < gapMs) return Decision(false, false)
        if (inBurst >= maxPerBurst) return Decision(false, false)
        lastByKind[kind] = now
        inBurst++
        return Decision(true, start)
    }
}
