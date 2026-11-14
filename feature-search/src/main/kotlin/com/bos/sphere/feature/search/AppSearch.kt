package com.bos.sphere.feature.search

import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.UsageStats

/**
 * App search for the always-on keyboard, the search overlay and the pickers — the shared
 * `search-core` engine behind a small facade (see [AppSearchCore] for the query language).
 *
 * The host keeps two signals current: [usage] (launch counts / recency from the launch history) and
 * [categoryNameOf] (the app category for a package, e.g. "Games"). Without them search still works
 * on names and packages.
 */
object AppSearch {
    /** Launch stats by [AppEntry.key]. */
    @Volatile var usage: Map<String, UsageStats.Usage> = emptyMap()
        set(value) { field = value; invalidate() }

    /** Category name for a package ("Games", "Audio" …, "Other" if unknown). */
    @Volatile var categoryNameOf: (String) -> String = { "Other" }
        set(value) { field = value; invalidate() }

    private val core = AppSearchCore()
    private var indexKey: Triple<Any?, Any?, Int>? = null
    private var index: AppSearchCore.Index? = null
    private var version = 0

    private fun invalidate() { synchronized(this) { version++; index = null } }

    @Synchronized
    private fun indexFor(apps: List<AppEntry>): AppSearchCore.Index {
        val key = Triple<Any?, Any?, Int>(apps, usage, version)
        if (index == null || indexKey != key) {
            val u = usage
            val cat = categoryNameOf
            index = core.Index(
                apps.map { a ->
                    val use = u[a.key]
                    SearchEntry(
                        key = a.key,
                        label = a.label,
                        packageName = a.packageName,
                        category = cat(a.packageName),
                        lastUsedMillis = use?.lastMillis ?: 0L,
                        launches = use?.count ?: 0,
                        work = a.user != android.os.Process.myUserHandle(),
                    )
                },
            )
            indexKey = key
        }
        return index!!
    }

    /**
     * Set by the Radar screen so plain queries also match user aliases, initials and one typo (PAL-02) and learn from
     * selections (PAL-09). Null (pickers, the always-on keyboard) keeps the query-language search alone.
     */
    @Volatile var appContext: android.content.Context? = null

    private var recordsFor: Pair<List<AppEntry>, List<dev.aarso.search.palette.AppRecord>>? = null

    @Synchronized
    private fun records(apps: List<AppEntry>): List<dev.aarso.search.palette.AppRecord> {
        recordsFor?.let { if (it.first === apps) return it.second }
        val self = android.os.Process.myUserHandle()
        return apps.map { a -> dev.aarso.search.palette.AppRecord(a.key, a.label, a.packageName, 0, work = a.user != self) }
            .also { recordsFor = apps to it }
    }

    /** Apps matching [query], best first. Plain queries: label, alias, package, initials and one typo first, then the query-language hits. */
    fun rank(apps: List<AppEntry>, query: String, limit: Int = 14): List<AppEntry> {
        if (query.isBlank()) return emptyList()
        val byKey = apps.associateBy { it.key }
        val core = runCatching { indexFor(apps).search(query, limit = limit) }
            .getOrDefault(emptyList())
            .mapNotNull(byKey::get)
        val ctx = appContext
        if (ctx == null || com.bos.sphere.feature.search.palette.AppRanking.isOperatorQuery(query)) return core
        val fuzzy = runCatching {
            val rec = records(apps)
            val history = usage.mapValues { (_, u) -> dev.aarso.search.palette.UsageRecord(u.count, u.lastMillis) }
            val ranker = com.bos.sphere.feature.search.palette.PaletteHost.ranker(ctx, history)
            com.bos.sphere.feature.search.palette.AppRanking.rank(com.bos.sphere.feature.search.palette.PaletteHost.matcher(ctx, rec), query, ranker, limit)
        }.getOrDefault(emptyList()).mapNotNull(byKey::get)
        return (fuzzy + core).distinctBy { it.key }.take(limit)
    }
}
