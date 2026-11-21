package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.PageLayout

/**
 * HOM-10: the reconcile step for folders and the dock when a package goes away (uninstall, disable of the last
 * launcher activity, or an inventory that no longer lists it). Pages already drop a missing key without a gap; folder
 * members and dock pins are stored state, so something has to say when they go.
 *
 * An app that is missing from the inventory is first *unavailable*: its folder cell or dock pin is kept (so an update
 * that briefly removes and re-adds a package, a profile that is locked for a while, or a re-install, keeps the place
 * the user gave it) and the surface draws it as unavailable. It is *removed* once it has been missing for [GRACE_MS]
 * (no gap, a folder left with one item dissolves, an empty folder goes, via [HomeEdit.pruneMissing]).
 * Archived and suspended apps stay in the inventory, so they are never "missing" here: they are shown dimmed by the
 * app presentation and stay launchable or explained there.
 *
 * Pure: state and the `missingSince` map go in, the next of both come out. The caller persists the map.
 */
object MemberReconcile {
    /** Seven days: long enough for a work profile that is locked over a holiday or a clean reinstall, short enough not to keep a ghost forever. */
    const val GRACE_MS: Long = 7L * 24 * 60 * 60 * 1000

    data class Result(
        val state: HomeState,
        /** App key to the time it was first seen missing, for the keys still waiting out the grace period. */
        val missingSince: Map<String, Long>,
        /** Keys referenced by a folder or the dock that are not installed right now, and are still kept. */
        val unavailable: Set<String>,
        /** Keys removed by this call. */
        val removed: Set<String>,
    )

    /** App keys the folders and the dock refer to (the only stored places that outlive a page's own resolve). */
    fun referenced(state: HomeState): Set<String> = buildSet {
        for (f in state.folders.values) for (k in f.keys) if (ItemKeys.kind(k) == ItemKind.App) add(k)
        for (e in state.dock) DockEntries.appKey(e)?.let { add(it) }
    }

    /**
     * [inventory] is every launcher key the platform lists now (hidden apps included). An empty inventory means "not loaded yet" and
     * changes nothing, so a cold start or a failed query never wipes folders.
     */
    fun reconcile(
        state: HomeState, inventory: Set<String>, missingSince: Map<String, Long>, now: Long,
        cap: (PageLayout) -> Int, graceMs: Long = GRACE_MS,
    ): Result {
        if (inventory.isEmpty()) return Result(state, missingSince, missingSince.keys.filter { it in referenced(state) }.toSet(), emptySet())
        val refs = referenced(state)
        val gone = refs.filter { it !in inventory }
        val since = HashMap<String, Long>()
        for (k in gone) since[k] = missingSince[k]?.takeIf { it <= now } ?: now
        val expired = since.filter { now - it.value >= graceMs }.keys
        val keep = since.filterKeys { it !in expired }
        if (expired.isEmpty()) return Result(state, keep, keep.keys, emptySet())
        // pruneMissing keeps what is "live": every app key the state holds (pages too, which this step never judges) except the expired ones.
        val live = state.allKeys().mapNotNull { DockEntries.appKey(it) }.toSet() - expired
        val next = HomeEdit.pruneMissing(state, live, cap)
        return Result(next, keep, keep.keys, expired)
    }

    /** One line per entry, `key<TAB>time`, for the small preference this is kept in. */
    fun encode(m: Map<String, Long>): String = m.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    fun decode(s: String?): Map<String, Long> = s.orEmpty().lineSequence().mapNotNull { l ->
        val t = l.lastIndexOf('\t')
        if (t <= 0) null else l.substring(t + 1).toLongOrNull()?.let { l.substring(0, t) to it }
    }.toMap()
}
