package com.bos.sphere.feature.search.palette

import android.content.Context
import com.bos.sphere.core.data.SearchHit
import dev.aarso.search.palette.AppMatcher
import dev.aarso.search.palette.AppRecord
import dev.aarso.search.palette.Ranker
import dev.aarso.search.palette.UsageRecord
import dev.aarso.search.palette.UsageStore

/**
 * The one palette per process: the home host and the Radar screen share it, so a selection made in Radar is learned
 * for the next search (PAL-09), shortcuts resolve their profile (PAL-03) and the file index is one object (PAL-11).
 * Everything here stays in app-private storage.
 */
object PaletteHost {
    @Volatile private var palette: AndroidPalette? = null
    @Volatile private var usage: UsageStore? = null
    @Volatile private var aliasMap: Map<String, String>? = null
    private class Cached(val apps: List<AppRecord>, val aliasHash: Int, val matcher: AppMatcher)
    @Volatile private var matcherFor: Cached? = null

    fun get(context: Context): AndroidPalette = palette ?: synchronized(this) {
        palette ?: AndroidPalette(context.applicationContext).also { palette = it }
    }

    fun usage(context: Context): UsageStore = usage ?: synchronized(this) {
        usage ?: get(context).prefs.loadUsage().also { usage = it }
    }

    /** The learned history as a ranker. [history] seeds it with launch counts the home already keeps (key to count and last time). */
    fun ranker(context: Context, history: Map<String, UsageRecord> = emptyMap()): Ranker {
        val base = UsageStore()
        history.forEach { (k, v) -> base.put("app:$k", v) }
        return Ranker(base.mergedWith(usage(context)))
    }

    /** The user picked [key] (a result's usage key): learn it (PAL-09). */
    fun learn(context: Context, key: String, now: Long = System.currentTimeMillis()) {
        usage(context).record(key, now)
        get(context).prefs.saveUsage(usage(context))
    }

    fun learnHit(context: Context, hit: SearchHit) = learn(context, hit.extras["usage"] ?: hit.id)

    /** Forget everything learned (PAL-09 reset). */
    fun resetLearning(context: Context) {
        usage(context).reset()
        get(context).prefs.resetUsage()
    }

    fun aliases(context: Context): Map<String, String> = aliasMap ?: synchronized(this) {
        aliasMap ?: get(context).prefs.aliases().also { aliasMap = it }
    }

    fun setAlias(context: Context, packageName: String, alias: String) {
        get(context).prefs.setAlias(packageName, alias)
        aliasMap = null
        matcherFor = null
    }

    /** An [AppMatcher] over [apps] with the user's aliases; rebuilt only when the inventory or the aliases change. */
    fun matcher(context: Context, apps: List<AppRecord>): AppMatcher {
        val h = aliases(context).hashCode()
        matcherFor?.let { if (it.apps === apps && it.aliasHash == h) return it.matcher }
        return AppMatcher(apps.map { it.copy(alias = aliases(context)[it.packageName].orEmpty()) }).also { matcherFor = Cached(apps, h, it) }
    }
}
