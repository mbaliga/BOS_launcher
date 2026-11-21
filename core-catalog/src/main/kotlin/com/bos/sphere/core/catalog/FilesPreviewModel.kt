package com.bos.sphere.core.catalog

import java.util.Locale

/** W31 (filesViews): what a file is, for choosing its preview and its tile. Pure; the extension wins over a vague mime type. */
enum class FileKind(val label: String, val previewable: Boolean) {
    Image("Image", true), Video("Video", true), Audio("Audio", true), Pdf("PDF document", true), Apk("Android app", true),
    Archive("Archive", false), Document("Document", false), Spreadsheet("Spreadsheet", false), Slides("Presentation", false),
    Text("Text", false), Other("File", false);

    companion object {
        private val byExt: Map<String, FileKind> = buildMap {
            fun add(kind: FileKind, vararg e: String) = e.forEach { put(it, kind) }
            add(Image, "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg")
            add(Video, "mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v")
            add(Audio, "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "mid")
            add(Pdf, "pdf")
            add(Apk, "apk", "apks", "xapk")
            add(Archive, "zip", "rar", "7z", "tar", "gz", "bz2", "xz")
            add(Document, "doc", "docx", "odt", "rtf", "pages")
            add(Spreadsheet, "xls", "xlsx", "ods", "csv", "numbers")
            add(Slides, "ppt", "pptx", "odp", "key")
            add(Text, "txt", "md", "log", "json", "xml", "html", "htm")
        }

        fun classify(name: String, mime: String = ""): FileKind {
            // A ".apk.temp" download is not an installable file: only the last extension counts.
            FileNames.extension(name).let { e -> byExt[e]?.let { return it } }
            val m = mime.lowercase(Locale.ROOT)
            return when {
                m.startsWith("image/") -> Image
                m.startsWith("video/") -> Video
                m.startsWith("audio/") -> Audio
                m == "application/pdf" -> Pdf
                m == "application/vnd.android.package-archive" -> Apk
                m.startsWith("text/") -> Text
                m.contains("zip") || m.contains("compressed") -> Archive
                m.contains("spreadsheet") || m.contains("excel") -> Spreadsheet
                m.contains("presentation") || m.contains("powerpoint") -> Slides
                m.contains("word") || m.contains("opendocument.text") -> Document
                else -> Other
            }
        }
    }
}

object FileNames {
    /** Lower-case extension without the dot; "" when there is none (a leading dot is not an extension). */
    fun extension(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i <= 0 || i == name.lastIndex || name.length - i > 8) "" else name.substring(i + 1).lowercase(Locale.ROOT)
    }

    /** The name split so the stem can ellipsise and the extension stays whole: "report.final.pdf" -> ("report.final", ".pdf"). */
    fun split(name: String): Pair<String, String> {
        val e = extension(name)
        return if (e.isEmpty()) name to "" else name.dropLast(e.length + 1) to name.takeLast(e.length + 1)
    }

    /** The label on a generic file tile: the extension in capitals, at most four letters, or "FILE". */
    fun tileLabel(name: String): String = extension(name).uppercase(Locale.ROOT).take(4).ifEmpty { "FILE" }
}

object FileFormat {
    /** "812 B", "4.2 KB", "62 MB", "1.3 GB" (1024 steps, one decimal under 10); "" for an unknown size. */
    fun size(bytes: Long): String {
        if (bytes < 0) return ""
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes / 1024.0
        var u = 0
        while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
        val n = if (v < 10) String.format(Locale.US, "%.1f", v) else Math.round(v).toString()
        return "$n ${units[u]}"
    }

    /** "1:05", "12:09", "1:02:03" for a video's duration; "" when unknown. */
    fun duration(ms: Long): String {
        if (ms <= 0) return ""
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** The secondary line: age, size (when known) and kind, joined with a middle dot. [kind] is already translated. */
    fun meta(age: String, sizeBytes: Long, kind: String): String = listOf(age, size(sizeBytes), kind).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** How the Files tab lays out folders and files (persisted in [HomePanelPrefs.filesView]). */
enum class FilesViewMode { List, Grid;
    fun other(): FilesViewMode = if (this == List) Grid else List
    companion object { fun parse(raw: String?): FilesViewMode = entries.firstOrNull { it.name == raw } ?: List }
}

/**
 * A bounded least-recently-used cache weighed in bytes (thumbnails). Thread safe. An entry heavier than the whole budget is not kept.
 * [V] is whatever the caller caches; a failed load is cached as a light "no thumbnail" value so it is not retried at once.
 */
class WeightedLruCache<K : Any, V : Any>(private val maxWeight: Long, private val weigh: (V) -> Long) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var total = 0L

    val weight: Long @Synchronized get() = total
    val size: Int @Synchronized get() = map.size

    @Synchronized operator fun get(key: K): V? = map[key]

    @Synchronized fun put(key: K, value: V) {
        map.remove(key)?.let { total -= weigh(it) }
        val w = weigh(value)
        if (w > maxWeight) return
        map[key] = value
        total += w
        val it = map.entries.iterator()
        while (total > maxWeight && it.hasNext()) { val e = it.next(); total -= weigh(e.value); it.remove() }
    }

    @Synchronized fun clear() { map.clear(); total = 0 }
}
