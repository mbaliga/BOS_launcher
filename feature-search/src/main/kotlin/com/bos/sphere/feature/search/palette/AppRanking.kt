package com.bos.sphere.feature.search.palette

import dev.aarso.search.palette.AppMatcher
import dev.aarso.search.palette.AppRecord
import dev.aarso.search.palette.Ranker

/**
 * PAL-02 and PAL-09 for the live app search: label, package, user alias, initials ("gm" finds Google Maps) and one
 * typo through the engine's [AppMatcher], blended with local history by the engine's [Ranker]. Pure Kotlin.
 */
object AppRanking {
    /** True for queries that use the app query language (`type:games`, quotes, `-x`, `/re/`, `OR`, parentheses). */
    fun isOperatorQuery(q: String): Boolean {
        val t = q.trim()
        return t.contains(':') || t.contains('"') || t.contains('(') || t.startsWith("-") || t.contains(" -") ||
            (t.length > 2 && t.startsWith("/") && t.endsWith("/")) || t.split(' ').any { it == "OR" }
    }

    /** Keys of the matching apps, best first. [ranker] adds frequency and recency on top of the match quality. */
    fun rank(matcher: AppMatcher, query: String, ranker: Ranker, limit: Int = 24): List<String> {
        val hits = matcher.search(query, limit * 2)
        return hits.map { it to ranker.blend(it.score, usageKey(it.app)) }
            .sortedWith(compareByDescending<Pair<AppMatcher.Hit, Double>> { it.second }.thenBy { it.first.app.label.lowercase() })
            .take(limit).map { it.first.app.key }
    }

    /** What selections of an app are learned under: its key (package and user). */
    fun usageKey(a: AppRecord): String = "app:${a.key}"
}
