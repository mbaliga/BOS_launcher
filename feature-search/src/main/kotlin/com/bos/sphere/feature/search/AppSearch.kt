package com.bos.sphere.feature.search

import com.bos.sphere.core.data.AppEntry

/**
 * Apps-only search ranking for the always-keyboard. A deliberately small first cut of the
 * universal-search model (brief §2.B): prefix matches beat word-prefix beat substring. The
 * provider interface, usage-weighting and non-app sources (contacts/files/calc/web) layer on
 * top of this later without changing call sites.
 */
object AppSearch {

    fun rank(apps: List<AppEntry>, query: String, limit: Int = 14): List<AppEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return apps
            .mapNotNull { app ->
                val label = app.label.lowercase()
                val score = when {
                    label == q -> 0
                    label.startsWith(q) -> 1
                    label.split(' ', '.', '-').any { it.startsWith(q) } -> 2
                    label.contains(q) -> 3
                    else -> return@mapNotNull null
                }
                app to score
            }
            .sortedWith(compareBy({ it.second }, { it.first.label.length }, { it.first.label.lowercase() }))
            .take(limit)
            .map { it.first }
    }
}
