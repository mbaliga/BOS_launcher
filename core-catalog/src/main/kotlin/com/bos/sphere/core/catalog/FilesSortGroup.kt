package com.bos.sphere.core.catalog

import java.text.Collator
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** W53 (filesCollections, HOM-15): how the Files view orders and groups files. Pure and table-tested; the clock, zone and locale are injected. */
enum class SortKey { Name, Modified, Added, Size, Type;
    companion object { fun parse(raw: String?): SortKey? = entries.firstOrNull { it.name == raw } }
}

enum class GroupKey { None, Date, Type, Folder, Size;
    companion object { fun parse(raw: String?): GroupKey? = entries.firstOrNull { it.name == raw } }
}

/** The sort and grouping of one source. The default is "Date modified, newest first, no grouping". */
data class ViewOptions(val sort: SortKey = SortKey.Modified, val ascending: Boolean = false, val group: GroupKey = GroupKey.None) {
    fun encode(): String = "${sort.name}\t${if (ascending) 1 else 0}\t${group.name}"

    companion object {
        val Default = ViewOptions()
        fun decode(raw: String?): ViewOptions? {
            val p = raw?.split('\t') ?: return null
            if (p.size < 3) return null
            return ViewOptions(SortKey.parse(p[0]) ?: return null, p[1] == "1", GroupKey.parse(p[2]) ?: return null)
        }
    }
}

/** The date buckets of "Group by Date". */
enum class DateBucket(val label: String) {
    Today("Today"), Yesterday("Yesterday"), ThisWeek("This week"), ThisMonth("This month"), Older("Older");

    companion object {
        /** The bucket of [ms] on the calendar of [zone] at [nowMs]; a date in the future counts as today. The week starts on [firstDayOfWeek]. */
        fun of(ms: Long, nowMs: Long, zone: TimeZone, firstDayOfWeek: Int = Calendar.getInstance(zone).firstDayOfWeek): DateBucket {
            fun midnight(c: Calendar) { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0) }
            val c = Calendar.getInstance(zone); c.timeInMillis = nowMs; midnight(c)
            val today = c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, -1); val yesterday = c.timeInMillis
            c.timeInMillis = today
            val back = ((c.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek) + 7) % 7
            c.add(Calendar.DAY_OF_YEAR, -back); val week = c.timeInMillis
            c.timeInMillis = today; c.set(Calendar.DAY_OF_MONTH, 1); val month = c.timeInMillis
            return when {
                ms >= today -> Today
                ms >= yesterday -> Yesterday
                ms >= week -> ThisWeek
                ms >= month -> ThisMonth
                else -> Older
            }
        }
    }
}

/** The size bands of "Group by Size", biggest first. */
enum class SizeBand(val label: String) {
    Huge("Over 1 GB"), Large("100 MB to 1 GB"), Medium("1 to 100 MB"), Small("Under 1 MB"), Unknown("Unknown size");

    companion object {
        private const val MB = 1024L * 1024
        fun of(bytes: Long): SizeBand = when {
            bytes < 0 -> Unknown
            bytes >= 1024 * MB -> Huge
            bytes >= 100 * MB -> Large
            bytes >= MB -> Medium
            else -> Small
        }
    }
}

/**
 * One section of the arranged list. [id] is stable (it keys the lazy item); [title] is an English key that the screen translates, unless
 * [literal] (a folder name, which is shown as it is).
 */
data class FileSection(val id: String, val title: String, val literal: Boolean, val files: List<RecentFile>) { val count: Int get() = files.size }

/** The result: [sections] hold only the first [shown] of [total] files; with no grouping there is one section with an empty title. */
data class ArrangedFiles(val sections: List<FileSection>, val shown: Int, val total: Int) { val capped: Boolean get() = shown < total }

object FilesArrange {
    const val CAP = 2000

    /** Natural, locale-aware, accent-insensitive-first name order: "file2" before "file10", "Ärzte" next to "Arzt", ties broken by the raw text. */
    fun nameComparator(locale: Locale): Comparator<String> {
        val coll = Collator.getInstance(locale).apply { strength = Collator.SECONDARY; decomposition = Collator.CANONICAL_DECOMPOSITION }
        return Comparator { a, b ->
            val ca = chunks(a); val cb = chunks(b)
            var i = 0
            while (i < ca.size && i < cb.size) {
                val x = ca[i]; val y = cb[i]
                val r = if (x.firstOrNull()?.isDigit() == true && y.firstOrNull()?.isDigit() == true) {
                    val xs = x.trimStart('0'); val ys = y.trimStart('0')
                    if (xs.length != ys.length) xs.length - ys.length else xs.compareTo(ys)
                } else coll.compare(x, y)
                if (r != 0) return@Comparator r
                i++
            }
            if (ca.size != cb.size) ca.size - cb.size else a.compareTo(b)
        }
    }

    private fun chunks(s: String): List<String> {
        if (s.isEmpty()) return listOf("")
        val out = ArrayList<String>(); var start = 0
        for (i in 1..s.length) if (i == s.length || s[i].isDigit() != s[start].isDigit()) { out += s.substring(start, i); start = i }
        return out
    }

    fun comparator(o: ViewOptions, locale: Locale): Comparator<RecentFile> {
        val names = nameComparator(locale)
        val byName = Comparator<RecentFile> { a, b -> names.compare(a.name, b.name) }.thenBy { it.uri }
        fun dir(c: Comparator<RecentFile>) = if (o.ascending) c else c.reversed()
        return when (o.sort) {
            SortKey.Name -> if (o.ascending) byName else byName.reversed()
            SortKey.Modified -> dir(compareBy<RecentFile> { it.modifiedMs }).then(byName)
            // Files without an added date go last in either direction.
            SortKey.Added -> Comparator<RecentFile> { a, b ->
                when {
                    a.addedMs < 0 && b.addedMs < 0 -> 0
                    a.addedMs < 0 -> 1
                    b.addedMs < 0 -> -1
                    else -> if (o.ascending) a.addedMs.compareTo(b.addedMs) else b.addedMs.compareTo(a.addedMs)
                }
            }.then(byName)
            // Unknown sizes (-1) sort as the smallest.
            SortKey.Size -> dir(compareBy<RecentFile> { it.sizeBytes }).then(byName)
            SortKey.Type -> dir(compareBy<RecentFile> { FileKind.classify(it.name, it.mime).ordinal }.thenBy { FileNames.extension(it.name) }).then(byName)
        }
    }

    /** Sorts [files], keeps the first [cap], and groups them. Section order is documented in the decision note: dates newest first (oldest first when sorting a date ascending), types in kind order, folders by name, sizes biggest first. */
    fun arrange(
        files: List<RecentFile>, o: ViewOptions, nowMs: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault(), cap: Int = CAP,
    ): ArrangedFiles {
        val sorted = files.sortedWith(comparator(o, locale))
        val shown = if (sorted.size > cap) sorted.subList(0, cap) else sorted
        val sections: List<FileSection> = when (o.group) {
            GroupKey.None -> listOf(FileSection("all", "", false, shown))
            GroupKey.Date -> {
                val dateOf: (RecentFile) -> Long = { if (o.sort == SortKey.Added && it.addedMs >= 0) it.addedMs else it.modifiedMs }
                val by = shown.groupBy { DateBucket.of(dateOf(it), nowMs, zone) }
                val flip = o.ascending && (o.sort == SortKey.Modified || o.sort == SortKey.Added)
                DateBucket.entries.let { if (flip) it.reversed() else it }.mapNotNull { b -> by[b]?.let { FileSection("d:${b.name}", b.label, false, it) } }
            }
            GroupKey.Type -> {
                val by = shown.groupBy { FileKind.classify(it.name, it.mime) }
                FileKind.entries.mapNotNull { k -> by[k]?.let { FileSection("t:${k.name}", k.label, false, it) } }
            }
            GroupKey.Folder -> {
                val names = nameComparator(locale)
                shown.groupBy { it.folder }.entries.sortedWith { a, b -> if (a.key.isEmpty() != b.key.isEmpty()) (if (a.key.isEmpty()) 1 else -1) else names.compare(a.key, b.key) }
                    .map { (k, v) -> if (k.isEmpty()) FileSection("f:", "No folder", false, v) else FileSection("f:$k", k, true, v) }
            }
            GroupKey.Size -> {
                val by = shown.groupBy { SizeBand.of(it.sizeBytes) }
                val flip = o.ascending && o.sort == SortKey.Size
                // "Unknown size" stays last in both directions.
                val order = SizeBand.entries.filter { it != SizeBand.Unknown }.let { if (flip) it.reversed() else it } + SizeBand.Unknown
                order.mapNotNull { b -> by[b]?.let { FileSection("s:${b.name}", b.label, false, it) } }
            }
        }
        return ArrangedFiles(sections.filter { it.files.isNotEmpty() }, shown.size, sorted.size)
    }

    /** The index's "modified" is milliseconds for folder listings but seconds for the media listing; a value below 10^11 is read as seconds. */
    fun normalizeMs(modified: Long): Long = if (modified in 1..99_999_999_999L) modified * 1000 else modified
}

/** The English words of the sort and group choices (translated by the screen). The order words depend on what is sorted ("Newest first" for a date). */
object FilesSortText {
    fun sortLabel(k: SortKey) = when (k) { SortKey.Name -> "Name"; SortKey.Modified -> "Date modified"; SortKey.Added -> "Date added"; SortKey.Size -> "Size"; SortKey.Type -> "Type" }
    fun groupLabel(k: GroupKey) = when (k) { GroupKey.None -> "No grouping"; GroupKey.Date -> "Date"; GroupKey.Type -> "Type"; GroupKey.Folder -> "Folder"; GroupKey.Size -> "Size band" }
    fun orderLabel(k: SortKey, ascending: Boolean) = when (k) {
        SortKey.Name, SortKey.Type -> if (ascending) "A to Z" else "Z to A"
        SortKey.Modified, SortKey.Added -> if (ascending) "Oldest first" else "Newest first"
        SortKey.Size -> if (ascending) "Smallest first" else "Largest first"
    }
}
