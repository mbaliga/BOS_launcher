package com.bos.sphere.core.catalog

import java.text.Normalizer

/** HOM-04: folders are searchable by name (Radar and the drawer filter use this). */
object FolderSearch {

    data class Hit(val folder: Folder, val score: Int)

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

    /** Folders whose name matches [query]: prefix of the name or of a word scores above a plain substring. Best first. */
    fun search(folders: Collection<Folder>, query: String): List<Hit> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return folders.mapNotNull { f ->
            val n = normalize(f.name)
            val score = when {
                n == q -> 100
                n.startsWith(q) -> 80
                n.split(' ', '-', '_').any { it.startsWith(q) } -> 60
                n.contains(q) -> 40
                else -> return@mapNotNull null
            }
            Hit(f, score)
        }.sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.folder.name.lowercase() })
    }
}
