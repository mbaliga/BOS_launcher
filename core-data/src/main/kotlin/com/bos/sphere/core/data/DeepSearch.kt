package com.bos.sphere.core.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Pure parsing for the `content://<pkg>.search/query` contract (see docs/SEARCH_PROVIDER.md). */
object DeepSearchLogic {
    /** Packages that are expected to expose a search provider. */
    val knownSources: List<String> = listOf(
        "io.github.mbaliga.fylz", "io.github.mbaliga.fylz.debug",
        "com.fotoxplorr.app", "com.fotoxplorr.app.debug", "com.fotoxplorr.app.connect",
    )

    fun authority(pkg: String) = "$pkg.search"

    fun parseKind(s: String?): HitKind = when (s?.trim()?.lowercase()) {
        "image" -> HitKind.Image
        "video" -> HitKind.Video
        "audio" -> HitKind.Audio
        "document" -> HitKind.Document
        "archive" -> HitKind.Archive
        "folder" -> HitKind.Folder
        else -> HitKind.Other
    }

    /** Maps one cursor row (column name to string value) to a hit; null when it has no uri. */
    fun parseRow(pkg: String, row: Map<String, String?>): SearchHit? {
        fun v(k: String) = row[k]?.takeIf { it.isNotBlank() }
        val uri = v("uri") ?: return null
        val title = v("title") ?: uri.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
        val id = v("_id") ?: uri
        return SearchHit(
            id = "deep:$pkg:$id",
            category = SearchCategory.Files,
            title = title,
            subtitle = v("subtitle"),
            snippet = v("snippet"),
            kind = parseKind(v("kind")),
            uri = uri,
            mime = v("mime"),
            thumbUri = v("thumb"),
            sourcePackage = pkg,
            score = (v("score")?.toFloatOrNull() ?: 0.5f).coerceIn(0f, 1f),
        )
    }
}

/** Queries Fylz / Fotoz (or any app following the contract) for content-level matches. Never throws. */
class DeepSearch(private val context: Context) : SearchProvider {
    private val unavailable = ConcurrentHashMap.newKeySet<String>()

    /** Packages whose provider resolves right now. Ones that denied us earlier are left out. */
    fun installedSources(): List<String> = DeepSearchLogic.knownSources.filter { it !in unavailable && providerPresent(it) }

    /** Sources that refused access (SecurityException) during this process's lifetime. */
    fun unavailableSources(): Set<String> = unavailable.toSet()

    private fun providerPresent(pkg: String): Boolean = runCatching {
        context.packageManager.resolveContentProvider(DeepSearchLogic.authority(pkg), 0) != null
    }.getOrDefault(false)

    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty() || limit <= 0) return emptyList()
        return withContext(Dispatchers.IO) {
            coroutineScope {
                installedSources().map { pkg -> async { querySource(pkg, q, limit) } }.awaitAll()
            }.flatten().sortedByDescending { it.score }.take(limit)
        }
    }

    private fun querySource(pkg: String, q: String, limit: Int): List<SearchHit> {
        val uri = Uri.Builder().scheme("content").authority(DeepSearchLogic.authority(pkg)).path("query")
            .appendQueryParameter("q", q).appendQueryParameter("limit", limit.toString()).build()
        return try {
            val out = ArrayList<SearchHit>()
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val row = HashMap<String, String?>()
                    for (i in 0 until c.columnCount) {
                        row[c.getColumnName(i)] = runCatching { c.getString(i) }.getOrNull()
                    }
                    DeepSearchLogic.parseRow(pkg, row)?.let(out::add)
                }
            }
            out
        } catch (_: SecurityException) {
            unavailable += pkg
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }
}
