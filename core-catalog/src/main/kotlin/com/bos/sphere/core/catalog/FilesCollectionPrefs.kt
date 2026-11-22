package com.bos.sphere.core.catalog

import android.content.Context

/**
 * W53 (filesCollections, HOM-15): what the owner chose for the Files section, kept in the existing `home_state` file under `panel.files.*` keys
 * (read through TolerantPrefs). That file is in the backup and export allow-list, so the collections, the sort and group choices and the last source
 * travel with a backup. A granted folder is device-bound: a folder URI inside a rule or a `f:` source id is only meaningful where that grant exists,
 * and on another device it simply matches nothing. The grants themselves are held by the system and are never exported.
 */
class FilesCollectionPrefs(context: Context) {
    private val sp = com.bos.sphere.core.data.TolerantPrefs.of(context.applicationContext, "home_state")

    var collections: FileCollections
        get() = FileCollections.decode(runCatching { sp.getString(K_COLLECTIONS, null) }.getOrNull())
        set(v) { sp.edit().putString(K_COLLECTIONS, v.encode()).apply() }

    /**
     * W54 (filesChips): the order the owner dragged the folder chips into, as folder URIs (the collections keep theirs in [collections]). A folder not
     * named here follows the named ones in the order the system lists the grants; a name with no grant any more is ignored.
     */
    var folderOrder: List<String>
        get() = runCatching { sp.getString(K_FOLDER_ORDER, null) }.getOrNull()?.lines()?.filter { it.isNotEmpty() }.orEmpty()
        set(v) { sp.edit().putString(K_FOLDER_ORDER, v.distinct().joinToString("\n")).apply() }

    /** The source shown last (`all`, `b:...`, `c:...`, `f:<tree>`). */
    var source: String
        get() = runCatching { sp.getString(K_SOURCE, null) }.getOrNull() ?: FileSourceIds.ALL
        set(v) { sp.edit().putString(K_SOURCE, v).apply() }

    /** The sort and grouping used by every source that has no choice of its own. */
    var defaultOptions: ViewOptions
        get() = ViewOptions.decode(runCatching { sp.getString(K_DEFAULT, null) }.getOrNull()) ?: ViewOptions.Default
        set(v) { sp.edit().putString(K_DEFAULT, v.encode()).apply() }

    private fun perSource(): LinkedHashMap<String, ViewOptions> {
        val m = LinkedHashMap<String, ViewOptions>()
        runCatching { sp.getString(K_PER_SOURCE, null) }.getOrNull()?.lines()?.forEach { line ->
            val i = line.indexOf('\t')
            if (i > 0) ViewOptions.decode(line.substring(i + 1))?.let { m[line.substring(0, i)] = it }
        }
        return m
    }

    /** The options a source has chosen for itself, or null when it follows the default. */
    fun ownOptions(sourceId: String): ViewOptions? = perSource()[sourceId]

    /** What a source shows: its own choice, else its built-in's usual order (Large files by size, until the owner sets a default for all), else the global default. */
    fun optionsFor(sourceId: String): ViewOptions = ownOptions(sourceId)
        ?: BuiltInCollection.byId(sourceId)?.takeIf { !sp.contains(K_DEFAULT) && (it.defaultSort != SortKey.Modified || it.defaultAscending) }?.let { ViewOptions(it.defaultSort, it.defaultAscending, defaultOptions.group) }
        ?: defaultOptions

    fun setOptions(sourceId: String, o: ViewOptions) {
        val m = perSource(); m.remove(sourceId); m[sourceId] = o
        while (m.size > MAX_SOURCES) m.remove(m.keys.first())
        write(m)
    }

    /** Back to the default for this source. */
    fun clearOptions(sourceId: String) { val m = perSource(); if (m.remove(sourceId) != null) write(m) }

    /** Makes [o] the default for every source and forgets the per-source choices, so all of them follow it. */
    fun setDefaultForAll(o: ViewOptions) { defaultOptions = o; write(LinkedHashMap()) }

    /** Forget what a removed collection had chosen. */
    fun forget(sourceId: String) = clearOptions(sourceId)

    private fun write(m: Map<String, ViewOptions>) {
        sp.edit().putString(K_PER_SOURCE, m.entries.joinToString("\n") { it.key + "\t" + it.value.encode() }).apply()
    }

    companion object {
        const val K_COLLECTIONS = "panel.files.collections"
        const val K_FOLDER_ORDER = "panel.files.folders.order"
        const val K_SOURCE = "panel.files.source"
        const val K_DEFAULT = "panel.files.sort.default"
        const val K_PER_SOURCE = "panel.files.sort.by"
        const val MAX_SOURCES = 200
    }
}
