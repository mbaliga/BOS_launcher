package com.bos.sphere.core.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Pure merge rules for the Files category. */
object SearchMerge {
    const val MAX_FILES = 12

    /** Deep hits first (best score first), then MediaStore hits; one entry per uri, deep wins. */
    fun mergeFiles(deep: List<SearchHit>, media: List<SearchHit>, max: Int = MAX_FILES): List<SearchHit> {
        val seen = HashSet<String>()
        val out = ArrayList<SearchHit>()
        for (h in deep.sortedByDescending { it.score } + media) {
            val key = h.uri ?: h.id
            if (seen.add(key)) out += h
            if (out.size >= max) break
        }
        return out
    }
}

class AndroidSearchHub(context: Context) : SearchHub {
    private val files = FileSearch(context)
    private val deep = DeepSearch(context)

    override fun search(query: String, prefs: LauncherPrefs): Flow<Map<SearchCategory, List<SearchHit>>> = channelFlow {
        val q = query.trim()
        val lock = Mutex()
        var actions = emptyList<SearchHit>()
        var media = emptyList<SearchHit>()
        var deepHits = emptyList<SearchHit>()

        suspend fun publish(update: () -> Unit) = lock.withLock {
            update()
            val map = LinkedHashMap<SearchCategory, List<SearchHit>>()
            if (actions.isNotEmpty()) map[SearchCategory.Actions] = actions
            val f = SearchMerge.mergeFiles(deepHits, media)
            if (f.isNotEmpty()) map[SearchCategory.Files] = f
            send(map)
        }

        if (prefs.searchActions) publish { actions = ActionHits.match(q, if (q.isEmpty()) 10 else 6) } else send(emptyMap())
        if (prefs.searchFiles) launch { val r = files.search(q, SearchMerge.MAX_FILES); publish { media = r } }
        if (prefs.searchDeep && q.isNotEmpty()) launch { val r = deep.search(q, SearchMerge.MAX_FILES); publish { deepHits = r } }
    }

    override fun hasFileAccess(): Boolean = files.hasAccess()

    override fun deepSources(): List<String> = deep.installedSources()
}
