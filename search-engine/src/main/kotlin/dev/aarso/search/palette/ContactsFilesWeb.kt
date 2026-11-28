package dev.aarso.search.palette

/** One indexed file or document. [source] is "media" or "tree:<uri>" so reconciliation is per source. */
data class FileEntry(val uri: String, val name: String, val mime: String?, val size: Long, val modified: Long, val source: String, val folder: String = "")

/**
 * The on-device file index (PAL-11): MediaStore plus user-granted folders. [reconcile] takes a fresh listing of one
 * source and applies only the difference, so scans are incremental. [encode]/[decode] persist it to app-private
 * storage, which is what lets it survive a reboot.
 */
class FileIndex {
    private val byUri = LinkedHashMap<String, FileEntry>()

    data class Diff(val added: Int, val removed: Int, val changed: Int) { val any get() = added + removed + changed > 0 }

    @Synchronized fun size() = byUri.size
    @Synchronized fun sources(): Set<String> = byUri.values.map { it.source }.toSet()

    /** Replaces what the index knows about [source] with [listing]; unchanged entries are left untouched. */
    @Synchronized fun reconcile(source: String, listing: List<FileEntry>): Diff {
        var added = 0; var changed = 0
        val seen = HashSet<String>(listing.size * 2)
        for (f in listing) {
            seen += f.uri
            val old = byUri[f.uri]
            if (old == null) { byUri[f.uri] = f; added++ } else if (old != f) { byUri[f.uri] = f; changed++ }
        }
        val gone = byUri.values.filter { it.source == source && it.uri !in seen }.map { it.uri }
        gone.forEach { byUri.remove(it) }
        return Diff(added, gone.size, changed)
    }

    /** Forget a source entirely, for example when a folder grant is revoked. */
    @Synchronized fun dropSource(source: String): Int { val gone = byUri.values.filter { it.source == source }.map { it.uri }; gone.forEach { byUri.remove(it) }; return gone.size }

    @Synchronized fun search(query: String, limit: Int = 12): List<Pair<FileEntry, Double>> {
        val q = TextMatch.norm(query)
        if (q.isEmpty()) return emptyList()
        val maxMod = byUri.values.maxOfOrNull { it.modified } ?: 0L
        return byUri.values.mapNotNull { f ->
            val s = TextMatch.score(q, TextMatch.Target(f.name))
            if (s <= 0) null else f to (s + if (maxMod > 0) 0.05 * f.modified / maxMod else 0.0)
        }.sortedByDescending { it.second }.take(limit)
    }

    /** A copy of every entry (W53: the Files collections query it off the main thread). */
    @Synchronized fun all(): List<FileEntry> = byUri.values.toList()

    @Synchronized fun recent(limit: Int = 8): List<FileEntry> = byUri.values.sortedByDescending { it.modified }.take(limit)

    @Synchronized fun encode(): String = byUri.values.joinToString("\n") {
        listOf(it.uri, it.name, it.mime ?: "", it.size.toString(), it.modified.toString(), it.source, it.folder).joinToString("\t") { s -> s.replace('\t', ' ').replace('\n', ' ') }
    }

    companion object {
        fun decode(raw: String?): FileIndex {
            val idx = FileIndex()
            raw?.lineSequence()?.forEach { line ->
                val p = line.split('\t')
                if (p.size >= 7) idx.byUri[p[0]] = FileEntry(p[0], p[1], p[2].ifEmpty { null }, p[3].toLongOrNull() ?: 0, p[4].toLongOrNull() ?: 0, p[5], p[6])
            }
            return idx
        }
    }
}

/** Files provider over a [FileIndex]. Results open with the default handler (ACTION_VIEW on [PaletteResult.payload] uri). */
class FileProvider(private val indexOf: () -> FileIndex, private val hasAccess: () -> Boolean = { true }, private val strings: PaletteStrings = PaletteStrings()) : PaletteProvider {
    constructor(index: FileIndex, hasAccess: () -> Boolean = { true }, strings: PaletteStrings = PaletteStrings()) : this({ index }, hasAccess, strings)

    // Resolved on first query, on the provider's own thread, so building the provider never reads the index from disk.
    private val index: FileIndex get() = indexOf()

    override val id = "files"
    override val scope = PaletteScope.Files

    override fun serves(requested: PaletteScope) = requested == PaletteScope.Files || (requested == PaletteScope.Everything && hasAccess())

    private fun row(f: FileEntry, s: Double) = PaletteResult(
        "file:${f.uri}", id, scope, f.name, f.folder.ifBlank { null }, s * 0.85,
        listOf(ResultAction(ResultActionId.Open)), mapOf("uri" to f.uri, "mime" to (f.mime ?: "")),
    )

    override suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult> {
        if (!hasAccess()) {
            return if (q.scope == PaletteScope.Files) listOf(PaletteResult("files:enable", id, scope, strings.enableFiles, null, 0.3, listOf(ResultAction(ResultActionId.EnablePermission)), learn = false)) else emptyList()
        }
        return index.search(q.text, limit).map { (f, s) -> row(f, s) }
    }

    override suspend fun suggestions(limit: Int): List<PaletteResult> = if (hasAccess()) index.recent(limit).mapIndexed { i, f -> row(f, 0.6 - i * 0.01) } else emptyList()
}

/** The web fallback and store lookup rows (PAL-06, PAL-15). They only build URLs; nothing is fetched. */
class WebFallback(
    /** Search URL template with `%s` for the query; the user's chosen engine. */
    private val template: () -> String = { "https://duckduckgo.com/?q=%s" },
    private val strings: PaletteStrings = PaletteStrings(),
) {
    /** The last row of every non-empty search. Handing the query to the browser happens only when it is selected. */
    fun row(query: String): PaletteResult? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val url = if (q.contains("://") && q.none { it.isWhitespace() }) q else template().replace("%s", encode(q))
        return PaletteResult("web:fallback", "web", PaletteScope.Web, strings.searchWeb(q), url, -1.0, listOf(ResultAction(ResultActionId.SearchWeb)), mapOf("url" to url), pinnedLast = true, learn = false)
    }

    companion object {
        /** Percent-encoding written out so the engine package needs no networking class at all. */
        fun encode(s: String): String {
            val sb = StringBuilder()
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                val ch = c.toChar()
                if (ch.isLetterOrDigit() && c < 128 || ch in "-_.~") sb.append(ch)
                else if (ch == ' ') sb.append('+')
                else sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
            }
            return sb.toString()
        }
    }
}
