package com.bos.sphere.core.catalog

import java.text.Normalizer
import java.util.Locale

/**
 * W53 (filesCollections, HOM-15): collections for the Files section. A collection is a saved query over the files the launcher can already see (the
 * Radar file index: the folders the owner granted, plus a media listing only when the system permission was granted elsewhere). It is a [FileRule]
 * plus a name. This file is pure: no Android types, so the matcher is unit-tested table-driven.
 */

/** The type groups a rule can pick. [kinds] are the [FileKind]s that belong to the group. */
enum class TypeGroup(val label: String, val kinds: Set<FileKind>) {
    Documents("Documents", setOf(FileKind.Pdf, FileKind.Document, FileKind.Spreadsheet, FileKind.Slides, FileKind.Text)),
    Images("Images", setOf(FileKind.Image)),
    Videos("Videos", setOf(FileKind.Video)),
    Audio("Audio", setOf(FileKind.Audio)),
    Archives("Archives", setOf(FileKind.Archive)),
    Apps("Apps", setOf(FileKind.Apk));

    companion object { fun parse(raw: String): TypeGroup? = entries.firstOrNull { it.name == raw } }
}

/**
 * The criteria of a collection. Every criterion that is set must hold (AND); an empty rule matches every file. The type criterion is one test: a file
 * passes it when its type group is in [types] OR its extension is in [extensions]. [folders] are granted folder URIs (device-bound: a folder that is
 * not granted on this device matches nothing); [folderNames] match the file's own folder name, case-insensitively (the Downloads built-in).
 */
data class FileRule(
    val types: Set<TypeGroup> = emptySet(),
    val extensions: Set<String> = emptySet(),
    val nameContains: String = "",
    val folders: Set<String> = emptySet(),
    val folderNames: Set<String> = emptySet(),
    /** Only files modified in the last N days (a rolling window from "now"); 0 means any age. */
    val maxAgeDays: Int = 0,
    /** Inclusive size bounds in bytes; -1 means no bound. A file of unknown size fails a size bound. */
    val minBytes: Long = -1,
    val maxBytes: Long = -1,
) {
    val isEmpty: Boolean get() = types.isEmpty() && extensions.isEmpty() && nameContains.isBlank() && folders.isEmpty() && folderNames.isEmpty() && maxAgeDays <= 0 && minBytes < 0 && maxBytes < 0

    fun matches(f: RecentFile, nowMs: Long): Boolean {
        if (types.isNotEmpty() || extensions.isNotEmpty()) {
            val kind = FileKind.classify(f.name, f.mime)
            if (types.none { kind in it.kinds } && FileNames.extension(f.name) !in extensions) return false
        }
        if (nameContains.isNotBlank() && !FileText.fold(f.name).contains(FileText.fold(nameContains.trim()))) return false
        if (folders.isNotEmpty() && f.source !in folders) return false
        if (folderNames.isNotEmpty() && FileText.fold(f.folder) !in folderNames) return false
        if (maxAgeDays > 0 && nowMs - f.modifiedMs > maxAgeDays * DAY_MS) return false
        if (minBytes >= 0 && (f.sizeBytes < 0 || f.sizeBytes < minBytes)) return false
        if (maxBytes >= 0 && (f.sizeBytes < 0 || f.sizeBytes > maxBytes)) return false
        return true
    }

    companion object { const val DAY_MS = 86_400_000L }
}

object FileText {
    /** Case and accent folded text for "name contains" (Unicode normalised, so "É" finds "e" and a composed or decomposed name matches alike). */
    fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }.lowercase(Locale.ROOT)

    /** "pdf, .DOCX; txt" to {pdf, docx, txt}: lower-case, no dots, at most 12 entries. */
    fun parseExtensions(raw: String): Set<String> =
        raw.split(',', ';', ' ', '\n').map { it.trim().trimStart('.').lowercase(Locale.ROOT) }.filter { it.isNotEmpty() && it.length <= 10 && it.none { c -> c == '/' || c == '\\' } }.take(12).toSet()
}

/** "Large files": 100 MB and over. */
const val LARGE_FILE_BYTES = 100L * 1024 * 1024

/** The ready-made collections the owner can switch on. [example] is the English example line shown on the chooser card; [title] and [example] are translated at use. */
enum class BuiltInCollection(val title: String, val example: String, val rule: FileRule, val defaultSort: SortKey = SortKey.Modified, val defaultAscending: Boolean = false) {
    Recents("Recents", "Changed in the last 30 days, newest first", FileRule(maxAgeDays = 30)),
    Documents("Documents", "PDF, Word, Excel, PowerPoint, text, ebooks", FileRule(types = setOf(TypeGroup.Documents))),
    Images("Images", "Photos and pictures: JPG, PNG, HEIC", FileRule(types = setOf(TypeGroup.Images))),
    Videos("Videos", "Clips and movies: MP4, MKV", FileRule(types = setOf(TypeGroup.Videos))),
    Audio("Audio", "Music and recordings: MP3, M4A, FLAC", FileRule(types = setOf(TypeGroup.Audio))),
    Downloads("Downloads", "Everything in a folder called Download", FileRule(folderNames = setOf("download", "downloads"))),
    Archives("Archives", "ZIP, RAR, 7z and other packed files", FileRule(types = setOf(TypeGroup.Archives))),
    Screenshots("Screenshots", "Pictures whose name starts with Screenshot", FileRule(types = setOf(TypeGroup.Images), nameContains = "screenshot")),
    Large("Large files", "100 MB and over, biggest first", FileRule(minBytes = LARGE_FILE_BYTES), SortKey.Size);

    val id: String get() = "b:" + name.lowercase(Locale.ROOT)

    companion object {
        /** What a fresh install shows. */
        val DEFAULT_SHOWN: List<BuiltInCollection> = listOf(Recents, Documents, Images, Downloads)
        fun byId(id: String): BuiltInCollection? = entries.firstOrNull { it.id == id }
    }
}

/** A collection the owner made: a name and its rule. [id] is "c:" plus a short random token. */
data class CustomCollection(val id: String, val name: String, val rule: FileRule)

/** What the Files switcher shows for a collection. [builtIn] is null for a custom one. */
data class FileCollection(val id: String, val name: String, val builtIn: BuiltInCollection?, val rule: FileRule)

/** The ids that name a source in the Files section. */
object FileSourceIds {
    const val ALL = "all"
    fun folder(tree: String) = "f:$tree"
    fun isFolder(id: String) = id.startsWith("f:")
    fun treeOf(id: String) = id.removePrefix("f:")
}

/**
 * Which collections the Files section shows and in what order: [shown] holds source ids ("b:documents", "c:7f3a"), [custom] the owner's own. A built-in
 * that is not in [shown] is switched off. Pure value; every change returns a new one. [encode] and [decode] keep it in one preference string.
 */
data class FileCollections(val shown: List<String> = BuiltInCollection.DEFAULT_SHOWN.map { it.id }, val custom: List<CustomCollection> = emptyList()) {
    /** The shown collections that still exist (a custom one that was removed is dropped), in order. */
    fun resolved(): List<FileCollection> = shown.mapNotNull { id ->
        BuiltInCollection.byId(id)?.let { FileCollection(it.id, it.title, it, it.rule) }
            ?: custom.firstOrNull { it.id == id }?.let { FileCollection(it.id, it.name, null, it.rule) }
    }

    fun isShown(id: String) = id in shown
    fun toggleBuiltIn(b: BuiltInCollection): FileCollections = if (b.id in shown) copy(shown = shown - b.id) else copy(shown = shown + b.id)

    /** Moves a shown collection by [delta] places (clamped). */
    fun move(id: String, delta: Int): FileCollections {
        val i = shown.indexOf(id)
        if (i < 0) return this
        val j = (i + delta).coerceIn(0, shown.lastIndex)
        if (i == j) return this
        val l = shown.toMutableList(); l.add(j, l.removeAt(i))
        return copy(shown = l)
    }

    /** Removes a custom collection for good, or hides a built-in. */
    fun remove(id: String): FileCollections = copy(shown = shown - id, custom = custom.filterNot { it.id == id })

    /** Adds a new custom collection (shown, last) or replaces the one with the same id. */
    fun upsert(c: CustomCollection): FileCollections =
        if (custom.any { it.id == c.id }) copy(custom = custom.map { if (it.id == c.id) c else it }, shown = if (c.id in shown) shown else shown + c.id)
        else copy(custom = custom + c, shown = shown + c.id)

    fun encode(): String = buildString {
        append("S\t").append(shown.joinToString(",") { esc(it) })
        custom.forEach { c ->
            val r = c.rule
            append('\n').append(listOf(
                "C", c.id, c.name, r.types.joinToString(",") { it.name }, r.extensions.joinToString(","), r.nameContains,
                r.folders.joinToString("\u0001"), r.folderNames.joinToString("\u0001"), r.maxAgeDays.toString(), r.minBytes.toString(), r.maxBytes.toString(),
            ).joinToString("\t") { esc(it) })
        }
    }

    companion object {
        private const val MAX_CUSTOM = 30

        /** A corrupt or empty value gives the defaults; a bad line is skipped, never an error. */
        fun decode(raw: String?): FileCollections {
            if (raw.isNullOrBlank()) return FileCollections()
            var shown: List<String>? = null
            val custom = ArrayList<CustomCollection>()
            raw.lines().forEach { line ->
                val p = line.split('\t').map { unesc(it) }
                when (p.firstOrNull()) {
                    "S" -> shown = p.getOrNull(1).orEmpty().split(',').map { unesc(it) }.filter { it.isNotEmpty() }
                    "C" -> if (p.size >= 11 && p[1].startsWith("c:") && custom.size < MAX_CUSTOM && custom.none { it.id == p[1] }) custom += CustomCollection(
                        p[1], p[2].ifBlank { "Collection" },
                        FileRule(
                            p[3].split(',').mapNotNull { TypeGroup.parse(it) }.toSet(), p[4].split(',').filter { it.isNotEmpty() }.toSet(), p[5],
                            p[6].split('\u0001').filter { it.isNotEmpty() }.toSet(), p[7].split('\u0001').filter { it.isNotEmpty() }.toSet(),
                            p[8].toIntOrNull()?.coerceIn(0, 3650) ?: 0, p[9].toLongOrNull() ?: -1, p[10].toLongOrNull() ?: -1,
                        ),
                    )
                }
            }
            val s = shown ?: return FileCollections(custom = custom.toList())
            return FileCollections(s.filter { BuiltInCollection.byId(it) != null || custom.any { c -> c.id == it } }.distinct(), custom.toList())
        }

        fun newId(taken: Set<String>, random: () -> Int = { (Math.random() * 0xFFFFFF).toInt() }): String {
            var id: String
            do { id = "c:" + Integer.toHexString(random() and 0xFFFFFF) } while (id in taken)
            return id
        }

        /** Tabs, newlines, commas in names and backslashes are escaped so one line stays one record. */
        internal fun esc(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace(",", "\\c")
        internal fun unesc(s: String): String {
            val sb = StringBuilder(); var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) { sb.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; 'c' -> ','; else -> s[i + 1] }); i += 2 } else { sb.append(c); i++ }
            }
            return sb.toString()
        }
    }
}

/** Which of the indexed files a source shows. [MEDIA] is the source name of the media listing (present only when the system permission was granted elsewhere). */
object FilesSources {
    const val MEDIA = "media"

    /** All and each folder show the granted folders only; a collection also reads the media listing when there is one. */
    fun select(everything: List<RecentFile>, sourceId: String, collections: FileCollections, nowMs: Long, grantedTrees: Set<String>): List<RecentFile> = when {
        sourceId == FileSourceIds.ALL -> everything.filter { it.source in grantedTrees }
        FileSourceIds.isFolder(sourceId) -> FileSourceIds.treeOf(sourceId).let { t -> if (t in grantedTrees) everything.filter { it.source == t } else emptyList() }
        else -> collections.resolved().firstOrNull { it.id == sourceId }?.let { c -> everything.filter { (it.source in grantedTrees || it.source == MEDIA) && c.rule.matches(it, nowMs) } } ?: emptyList()
    }

    /** The index row to a file: the folder shown is its own folder, or the granted folder's name for a file at the top. [treeName] names a granted tree. */
    fun fromIndex(uri: String, name: String, mime: String, size: Long, modified: Long, source: String, folder: String, treeName: (String) -> String): RecentFile {
        val tree = source.removePrefix("tree:")
        val place = folder.ifEmpty { if (source == MEDIA) "" else treeName(tree) }
        return RecentFile(uri, name, mime, FilesArrange.normalizeMs(modified), if (source == MEDIA) MEDIA else tree, size.coerceAtLeast(-1), place)
    }
}
