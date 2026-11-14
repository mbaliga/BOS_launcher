package dev.aarso.search.palette

/** A query split into its scope and the text to search. */
data class ScopedQuery(val scope: PaletteScope, val rest: String, val prefix: String? = null)

/**
 * Scope prefixes (PAL-13): `a gm` searches apps only, `f report` files, `s wifi` settings, `c anna` contacts.
 * The table is editable and persists as one string. Pure; the UI lists [entries] in the empty state.
 */
class PrefixTable(initial: Map<String, PaletteScope> = DEFAULTS) {
    private val map = LinkedHashMap<String, PaletteScope>()

    init { initial.forEach { (k, v) -> if (validate(k) == null) map[k.lowercase()] = v } }

    sealed interface Edit {
        data object Ok : Edit
        data class Invalid(val reason: String) : Edit
        data class Taken(val by: PaletteScope) : Edit
    }

    fun entries(): List<Pair<String, PaletteScope>> = map.entries.map { it.key to it.value }
    fun scopeFor(prefix: String): PaletteScope? = map[prefix.lowercase()]
    fun prefixesFor(scope: PaletteScope): List<String> = map.filterValues { it == scope }.keys.toList()

    /** Splits `"a gm"` into Apps and `"gm"`. A prefix only counts when followed by whitespace; plain text stays Everything. */
    fun parse(query: String): ScopedQuery {
        val trimmed = query.trimStart()
        val cut = trimmed.indexOfFirst { it.isWhitespace() }
        if (cut <= 0) return ScopedQuery(PaletteScope.Everything, query.trim())
        val token = trimmed.substring(0, cut)
        val scope = map[token.lowercase()] ?: return ScopedQuery(PaletteScope.Everything, query.trim())
        return ScopedQuery(scope, trimmed.substring(cut).trim(), token.lowercase())
    }

    /** Sets or moves [prefix] to [scope]; refuses a prefix another scope already uses (the clash is named). */
    fun set(prefix: String, scope: PaletteScope): Edit {
        validate(prefix)?.let { return Edit.Invalid(it) }
        val key = prefix.lowercase()
        val owner = map[key]
        if (owner != null && owner != scope) return Edit.Taken(owner)
        map[key] = scope
        return Edit.Ok
    }

    /** Renames the prefix of [scope]: removes [old] and adds [new]. */
    fun rename(old: String, new: String): Edit {
        val scope = map[old.lowercase()] ?: return Edit.Invalid("unknown prefix")
        validate(new)?.let { return Edit.Invalid(it) }
        map[new.lowercase()]?.let { if (new.lowercase() != old.lowercase()) return Edit.Taken(it) }
        map.remove(old.lowercase())
        map[new.lowercase()] = scope
        return Edit.Ok
    }

    fun remove(prefix: String) { map.remove(prefix.lowercase()) }
    fun reset() { map.clear(); map.putAll(DEFAULTS) }

    fun serialize(): String = map.entries.joinToString(";") { "${it.key}=${it.value.name}" }

    companion object {
        val DEFAULTS: Map<String, PaletteScope> = linkedMapOf(
            "a" to PaletteScope.Apps, "f" to PaletteScope.Files, "s" to PaletteScope.Settings,
            "c" to PaletteScope.Contacts, "w" to PaletteScope.Web, ">" to PaletteScope.Actions,
        )

        fun validate(prefix: String): String? = when {
            prefix.isEmpty() || prefix.length > 3 -> "1 to 3 characters"
            prefix.any { it.isWhitespace() } -> "no spaces"
            prefix.startsWith("!") -> "bangs start with !"
            prefix.contains(':') -> "no colon (used by filters)"
            else -> null
        }

        fun deserialize(raw: String?): PrefixTable {
            if (raw.isNullOrBlank()) return PrefixTable()
            val m = LinkedHashMap<String, PaletteScope>()
            raw.split(";").forEach { part ->
                val (k, v) = part.split("=", limit = 2).takeIf { it.size == 2 } ?: return@forEach
                val sc = runCatching { PaletteScope.valueOf(v) }.getOrNull() ?: return@forEach
                m[k] = sc
            }
            return PrefixTable(m)
        }
    }
}
