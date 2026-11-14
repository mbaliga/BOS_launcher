package com.bos.sphere.core.catalog

import java.text.Normalizer

/**
 * HOM-05: the drawer's list rules as pure functions: alphabetical index, type-to-filter, work and
 * personal tabs, user tags and hidden apps. The Android drawer UI calls [DrawerModel.build].
 */
data class DrawerApp(
    val key: String,
    val label: String,
    val profile: ProfileKind = ProfileKind.Personal,
)

data class DrawerSection(val letter: Char, val apps: List<DrawerApp>)

data class DrawerResult(
    val sections: List<DrawerSection>,
    /** Index rail letters, in order. A letter with no apps is absent. */
    val letters: List<Char>,
) {
    val flat: List<DrawerApp> get() = sections.flatMap { it.apps }
    /** Position in [flat] of the first app under [letter], for the index to scroll to. */
    fun offsetOf(letter: Char): Int {
        var n = 0
        for (s in sections) { if (s.letter == letter) return n; n += s.apps.size }
        return -1
    }
}

object DrawerModel {

    const val OTHER = '#'

    fun indexLetter(label: String): Char {
        val c = Normalizer.normalize(label.trim(), Normalizer.Form.NFD).firstOrNull { it.isLetterOrDigit() } ?: return OTHER
        return if (c in 'a'..'z' || c in 'A'..'Z') c.uppercaseChar() else OTHER
    }

    /**
     * @param hidden keys the user hid; shown only when [showHidden] is set (the "hidden apps" view)
     * @param tags key -> user tags; [tag] restricts to apps carrying it, and the filter also matches tags
     * @param filter type-to-filter text; matches word prefixes first, then substrings, accent-insensitive
     */
    fun build(
        apps: List<DrawerApp>,
        tab: DrawerTab,
        filter: String = "",
        hidden: Set<String> = emptySet(),
        showHidden: Boolean = false,
        tags: Map<String, Set<String>> = emptyMap(),
        tag: String? = null,
    ): DrawerResult {
        val q = FolderSearch.normalize(filter)
        val scoped = apps.asSequence()
            .filter { tab.matches(it.profile) }
            .filter { if (showHidden) it.key in hidden else it.key !in hidden }
            .filter { tag == null || tag in (tags[it.key] ?: emptySet()) }
            .map { it to score(it, q, tags[it.key].orEmpty()) }
            .filter { it.second >= 0 }
            .toList()
        val ordered = if (q.isEmpty()) {
            scoped.map { it.first }.sortedWith(compareBy({ FolderSearch.normalize(it.label) }, { it.key }))
        } else {
            scoped.sortedWith(compareByDescending<Pair<DrawerApp, Int>> { it.second }.thenBy { FolderSearch.normalize(it.first.label) }).map { it.first }
        }
        val sections = if (q.isEmpty()) {
            ordered.groupBy { indexLetter(it.label) }
                .toSortedMap(compareBy<Char> { if (it == OTHER) 1 else 0 }.thenBy { it })
                .map { (l, a) -> DrawerSection(l, a) }
        } else {
            // While filtering the list is by relevance, not grouped.
            if (ordered.isEmpty()) emptyList() else listOf(DrawerSection(indexLetter(ordered.first().label), ordered))
        }
        return DrawerResult(sections, if (q.isEmpty()) sections.map { it.letter } else emptyList())
    }

    /** -1 no match; larger is better. Empty query matches everything. */
    internal fun score(app: DrawerApp, q: String, tags: Set<String>): Int {
        if (q.isEmpty()) return 0
        val n = FolderSearch.normalize(app.label)
        return when {
            n == q -> 100
            n.startsWith(q) -> 80
            n.split(' ', '-', '.', '_').any { it.startsWith(q) } -> 60
            n.contains(q) -> 40
            tags.any { FolderSearch.normalize(it).contains(q) } -> 30
            else -> -1
        }
    }
}

/** User tags per app, stored as one line per app: key TAB tag,tag. */
object TagCodec {
    fun encode(tags: Map<String, Set<String>>): String =
        tags.filterValues { it.isNotEmpty() }.entries.sortedBy { it.key }
            .joinToString("\n") { (k, v) -> "${esc(k)}\t${v.sorted().joinToString(",") { esc(it) }}" }

    fun decode(raw: String): Map<String, Set<String>> =
        raw.lineSequence().filter { it.contains('\t') }.associate { line ->
            val (k, v) = line.split('\t', limit = 2)
            unesc(k) to v.split(',').filter { it.isNotEmpty() }.map(::unesc).toSet()
        }

    fun toggle(tags: Map<String, Set<String>>, key: String, tag: String): Map<String, Set<String>> {
        val cur = tags[key].orEmpty()
        val next = if (tag in cur) cur - tag else cur + tag
        return if (next.isEmpty()) tags - key else tags + (key to next)
    }

    fun allTags(tags: Map<String, Set<String>>): List<String> = tags.values.flatten().toSortedSet().toList()

    private fun esc(s: String) = s.replace("%", "%25").replace(",", "%2C").replace("\t", "%09").replace("\n", "%0A")
    private fun unesc(s: String) = s.replace("%0A", "\n").replace("%09", "\t").replace("%2C", ",").replace("%25", "%")
}
