package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.SearchCategory

/**
 * PAL-13 / PAL-19: the scope prefixes and the category wheel are one setting. A query that starts with a
 * prefix letter and a space (or a colon) is limited to that scope; picking a wheel entry writes the same
 * prefix into the query. Shared with the palette stream, so the API is fixed:
 * `parse(query)` and `prefixFor(category)`.
 *
 * Defaults: `a` apps, `c` contacts, `f` files, `s` settings and actions, `w` the web. The user may edit the
 * letters ([Mapping]); the object itself always uses [DEFAULT].
 */
object ScopePrefixes {
    /** An editable prefix table: one distinct letter per category. */
    class Mapping(letters: Map<SearchCategory, Char>) {
        val letters: Map<SearchCategory, Char> = SearchCategory.entries.associateWith { letters[it] ?: DEFAULT.letterOf(it) }

        init {
            require(this.letters.values.toSet().size == this.letters.size) { "Scope prefixes must be distinct" }
            require(this.letters.values.all { it.isLetter() }) { "Scope prefixes must be letters" }
        }

        fun letterOf(c: SearchCategory): Char = letters.getValue(c)

        fun categoryOf(ch: Char): SearchCategory? = letters.entries.firstOrNull { it.value.equals(ch, ignoreCase = true) }?.key

        /** The same table with one letter changed; refused (null) when another category already uses it. */
        fun with(c: SearchCategory, ch: Char): Mapping? {
            val l = ch.lowercaseChar()
            if (!l.isLetter() || letters.any { it.key != c && it.value == l }) return null
            return Mapping(letters + (c to l))
        }

        /** Encoded as `Apps=a;Files=f;...` for storage. */
        fun encode(): String = letters.entries.joinToString(";") { "${it.key.name}=${it.value}" }

        companion object {
            fun decode(raw: String?): Mapping {
                if (raw.isNullOrBlank()) return DEFAULT
                val m = raw.split(";").mapNotNull { p ->
                    val (k, v) = p.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                    val cat = SearchCategory.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
                    val ch = v.singleOrNull()?.takeIf { it.isLetter() } ?: return@mapNotNull null
                    cat to ch.lowercaseChar()
                }.toMap()
                return runCatching { Mapping(m) }.getOrDefault(DEFAULT)
            }
        }
    }

    val DEFAULT = Mapping(
        mapOf(
            SearchCategory.Apps to 'a', SearchCategory.Contacts to 'c', SearchCategory.Files to 'f',
            SearchCategory.Actions to 's', SearchCategory.Web to 'w',
        ),
    )

    /** The scope a query asks for, and the query without its prefix. `a  term` gives (Apps, "term"); no prefix gives (null, query). */
    fun parse(query: String): Pair<SearchCategory?, String> = parse(query, DEFAULT)

    fun parse(query: String, mapping: Mapping): Pair<SearchCategory?, String> {
        val q = query.trimStart()
        if (q.length < 2) return null to query
        val cat = mapping.categoryOf(q[0]) ?: return null to query
        val sep = q[1]
        if (sep != ' ' && sep != ':') return null to query
        return cat to q.substring(2).trimStart()
    }

    /** The prefix letter of [category] (as it is typed, without the space). */
    fun prefixFor(category: SearchCategory): String = DEFAULT.letterOf(category).toString()

    fun prefixFor(category: SearchCategory, mapping: Mapping): String = mapping.letterOf(category).toString()

    /**
     * The wheel changed to [category]: rewrite [query] so the text says the same thing. A query with a prefix
     * gets the new prefix (or none for Everything). A query without one only gains a prefix when it has text;
     * an empty field keeps the scope in the wheel alone.
     */
    fun applyWheel(query: String, category: SearchCategory?, mapping: Mapping = DEFAULT): String {
        val (current, rest) = parse(query, mapping)
        return when {
            category == null -> if (current != null) rest else query
            current != null -> "${prefixFor(category, mapping)} $rest"
            query.isBlank() -> query
            else -> "${prefixFor(category, mapping)} ${query.trimStart()}"
        }
    }

    /** The scope a typed prefix selects, or the wheel's own choice when the query has none. */
    fun effectiveScope(query: String, wheel: SearchCategory?, mapping: Mapping = DEFAULT): SearchCategory? = parse(query, mapping).first ?: wheel
}

/** The category wheel as a list, so the keyboard can move it (PAL-19): Everything first, then the categories in wheel order. */
object ScopeWheel {
    val entries: List<SearchCategory?> = listOf<SearchCategory?>(null) + SearchCategory.entries

    fun label(c: SearchCategory?): String = c?.label ?: "Everything"

    /** One step along the wheel; it stops at the ends like the wheel itself. */
    fun step(current: SearchCategory?, delta: Int): SearchCategory? =
        entries[(entries.indexOf(current) + delta).coerceIn(0, entries.lastIndex)]
}
