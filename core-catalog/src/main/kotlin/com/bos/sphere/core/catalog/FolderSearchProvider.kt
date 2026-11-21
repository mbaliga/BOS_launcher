package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HitKind
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.data.SearchProvider

/**
 * HOM-04: Radar lists home folders by name. [folders] reads the live folders at query time and
 * [intentFor] gives the intent (as a URI string) that brings the home forward with that folder open.
 * Answers from memory, so it is quick and never throws.
 */
class FolderSearchProvider(
    private val folders: () -> Collection<Folder>,
    private val intentFor: (Folder) -> String,
    private val subtitle: (Folder) -> String = { "Folder, ${it.keys.size} items" },
) : SearchProvider {
    override suspend fun search(query: String, limit: Int): List<SearchHit> = hits(query).take(limit)

    fun hits(query: String): List<SearchHit> =
        runCatching {
            FolderSearch.search(folders(), query).map { h ->
                SearchHit(
                    id = "folder:${h.folder.id}", category = SearchCategory.Actions, title = h.folder.name,
                    subtitle = subtitle(h.folder), kind = HitKind.Folder, intentUri = intentFor(h.folder),
                    score = 0.6f + h.score / 500f,
                )
            }
        }.getOrDefault(emptyList())
}
