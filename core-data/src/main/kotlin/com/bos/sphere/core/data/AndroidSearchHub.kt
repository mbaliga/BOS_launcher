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

    /** The category map the UI gets: only categories that have hits (PAL-22: no empty Files group when Fylz/Fotoz are absent). */
    fun categories(actions: List<SearchHit>, media: List<SearchHit>, deep: List<SearchHit>, extra: List<SearchHit>): Map<SearchCategory, List<SearchHit>> {
        val map = LinkedHashMap<SearchCategory, List<SearchHit>>()
        val ex = extra.sortedByDescending { it.score }
        val acts = (actions + ex.filter { it.category == SearchCategory.Actions }).sortedByDescending { it.score }
        if (acts.isNotEmpty()) map[SearchCategory.Actions] = acts
        val f = mergeFiles(deep, media + ex.filter { it.category == SearchCategory.Files })
        if (f.isNotEmpty()) map[SearchCategory.Files] = f
        return map
    }
}

/** [extraProviders] contribute hits to their own [SearchHit.category] (the palette engine's settings, calculator, shortcuts, file index). */
class AndroidSearchHub(context: Context, private val extraProviders: List<SearchProvider> = emptyList()) : SearchHub {
    private val files = FileSearch(context)
    private val deep = DeepSearch(context)

    override fun search(query: String, prefs: LauncherPrefs): Flow<Map<SearchCategory, List<SearchHit>>> = channelFlow {
        val q = query.trim()
        val lock = Mutex()
        var actions = emptyList<SearchHit>()
        var media = emptyList<SearchHit>()
        var deepHits = emptyList<SearchHit>()
        val extraHits = HashMap<Int, List<SearchHit>>()

        suspend fun publish(update: () -> Unit) = lock.withLock {
            update()
            send(SearchMerge.categories(actions, media, deepHits, extraHits.values.flatten()))
        }

        extraProviders.forEachIndexed { i, p -> launch { val r = try { p.search(q, 8) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }; publish { extraHits[i] = r } } }
        if (prefs.searchActions) publish { actions = ActionHits.match(q, if (q.isEmpty()) 10 else 6) } else send(emptyMap())
        if (prefs.searchFiles) launch { val r = files.search(q, SearchMerge.MAX_FILES); publish { media = r } }
        if (prefs.searchDeep && q.isNotEmpty()) launch { val r = deep.search(q, SearchMerge.MAX_FILES); publish { deepHits = r } }
    }

    override fun hasFileAccess(): Boolean = files.hasAccess()

    override fun deepSources(): List<String> = deep.installedSources()
}
