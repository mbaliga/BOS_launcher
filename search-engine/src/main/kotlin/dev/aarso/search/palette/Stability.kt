package dev.aarso.search.palette

/**
 * PAL-12 for any row type: late results never move the row under the selection (generic twin of [StableMerge], which
 * works on [PaletteResult]). Used by the Radar screen for the hits the hub streams in.
 */
object StableOrder {
    /**
     * [shown] is what the user sees now, [ideal] the freshly ranked list. Everything up to and including the row with
     * [selectedKey] keeps its position (its data is refreshed from [ideal]); the rest follows the new ranking. Rows
     * that vanished from [ideal] before the selection are dropped (they no longer match), so the selected row may move
     * up by that many, never down because of newcomers.
     */
    fun <T> merge(shown: List<T>, ideal: List<T>, selectedKey: String?, key: (T) -> String): List<T> {
        val at = if (selectedKey == null) -1 else shown.indexOfFirst { key(it) == selectedKey }
        if (at < 0) return ideal
        val fresh = ideal.associateBy(key)
        val prefix = shown.take(at + 1).mapNotNull { fresh[key(it)] ?: it.takeIf { r -> key(r) == selectedKey } }
        val used = prefix.map(key).toSet()
        return prefix + ideal.filter { key(it) !in used }
    }
}

/** Remembers the last results per query so reopening the palette with the same query restores them at once (PAL-12). */
class QueryCache<V>(private val capacity: Int = 8) {
    private val map = object : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?) = size > capacity
    }

    @Synchronized fun get(query: String): V? = map[query]
    @Synchronized fun put(query: String, value: V) { map[query] = value }
    @Synchronized fun clear() = map.clear()
}
