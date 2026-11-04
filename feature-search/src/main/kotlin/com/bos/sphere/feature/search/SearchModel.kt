package com.bos.sphere.feature.search

import com.bos.sphere.core.data.WebEngine
import com.bos.sphere.core.data.WebSearch

enum class SuggestionKind { Key, Value, Text, Natural }

/**
 * A macOS-Spotlight-style refinement offered while typing. Tapping one replaces the last typed
 * word (or the whole field) with [insertion]; [insertion] is always the complete new field text.
 */
sealed interface Suggestion {
    val label: String
    val insertion: String
    val kind: SuggestionKind

    data class Key(override val label: String, override val insertion: String) : Suggestion { override val kind get() = SuggestionKind.Key }
    data class Value(override val label: String, override val insertion: String) : Suggestion { override val kind get() = SuggestionKind.Value }
    data class Text(override val label: String, override val insertion: String) : Suggestion { override val kind get() = SuggestionKind.Text }
    data class Natural(override val label: String, override val insertion: String) : Suggestion { override val kind get() = SuggestionKind.Natural }
}

/** Facet keys, and the values each offers. Matches the keys [AppSearchCore] understands. */
object SearchFacets {
    val keys = listOf("type", "used", "launches", "is", "pkg")
    private val aliases = mapOf(
        "kind" to "type", "category" to "type", "package" to "pkg",
        "opened" to "used", "last" to "used", "opens" to "launches",
    )
    fun canonical(key: String): String? = key.lowercase().let { if (it in keys) it else aliases[it] }

    /** label to what is typed after `used:`. */
    val usedValues = listOf("today" to "today", "yesterday" to "yesterday", "this week" to "this-week", "last 30 days" to ">=-30d")
    val isValues = listOf("work", "recent", "frequent", "never")
    val naturalWords = setOf("games", "music", "videos", "photos", "social", "news", "maps", "productivity")
}

private const val MAX_SUGGESTIONS = 6

private fun quoteIfNeeded(v: String) = if (v.any { it.isWhitespace() }) "\"$v\"" else v

/** Refinement chips for [text]. [categories] are the category names present on this device. */
fun suggest(text: String, categories: List<String>): List<Suggestion> {
    if (text.isBlank()) return emptyList()
    val trimmedEnd = text.trimEnd()
    // Only complete while the caret-side word is still being typed (no trailing space).
    if (trimmedEnd.length != text.length) return emptyList()
    val cut = text.lastIndexOfAny(charArrayOf(' ', '\t', '\n')) + 1
    val prefix = text.substring(0, cut)
    val token = text.substring(cut)
    if (com.bos.sphere.core.data.WebSearch.hasBang(text)) return emptyList()
    if (token.isEmpty() || token.startsWith("!") || token.startsWith("-") || token.startsWith("(") || token.startsWith("/")) return emptyList()
    val out = ArrayList<Suggestion>()

    val colon = token.indexOf(':')
    if (colon > 0) {
        val key = SearchFacets.canonical(token.substring(0, colon)) ?: return emptyList()
        val typed = token.substring(colon + 1).trim('"')
        val keyText = token.substring(0, colon)
        fun value(label: String, v: String) {
            val ins = "$prefix$keyText:${quoteIfNeeded(v)}"
            if (ins != text) out += Suggestion.Value(label, ins)
        }
        when (key) {
            "type" -> categories.filter { it.startsWith(typed, true) || it.split(' ').any { w -> w.startsWith(typed, true) } }
                .distinct().forEach { value("Kind: $it", it) }
            "used" -> SearchFacets.usedValues.filter { typed.isEmpty() || it.first.contains(typed, true) || it.second.startsWith(typed, true) }
                .forEach { value("Used: ${it.first}", it.second) }
            "is" -> SearchFacets.isValues.filter { it.startsWith(typed, true) }.forEach { value("Is: $it", it) }
            "launches" -> if (typed.isEmpty()) listOf(">5", ">20", "0").forEach { value("Launches $it", it) }
        }
        return out.take(MAX_SUGGESTIONS)
    }

    val w = token
    val lower = w.lowercase()
    if (w.length >= 2) {
        SearchFacets.keys.filter { it.startsWith(lower) }.forEach { out += Suggestion.Key("$it:", "$prefix$it:") }
        // Aliases like "kind:" / "cat…" complete to the canonical key.
        listOf("kind", "category", "package", "opened", "opens").forEach { alias ->
            val canon = SearchFacets.canonical(alias)!!
            if (alias.startsWith(lower) && out.none { it.insertion == "$prefix$canon:" }) out += Suggestion.Key("$canon:", "$prefix$canon:")
        }
        categories.distinct().filter { c -> c.startsWith(w, true) || c.split(' ').any { it.startsWith(w, true) } }
            .forEach { out += Suggestion.Value("Kind: $it", "$prefix" + "type:${quoteIfNeeded(it)}") }
    }
    if (w.length >= 1 && ':' !in w) {
        out += Suggestion.Text("Name contains “$w”", "$prefix\"$w\"")
        out += Suggestion.Text("Package contains “$w”", "${prefix}pkg:$w")
    }
    if (lower in SearchFacets.naturalWords) out += Suggestion.Natural("$w I used yesterday", "$prefix$w I used yesterday")
    return out.distinctBy { it.insertion }.take(MAX_SUGGESTIONS)
}

// ---- result sections ----

enum class SectionKind { Apps, Contacts, Web }

/** A row of the Web section. */
sealed interface WebRow {
    /** Open a URL typed as the query. */
    data class Open(val label: String, val url: String) : WebRow
    /** A `!bang` search, always sent through DuckDuckGo. */
    data class BangSearch(val label: String, val url: String) : WebRow
    data class Search(val label: String, val url: String) : WebRow
    data class PlayStore(val label: String, val url: String, val marketUri: String) : WebRow
    /** Tappable bang chips; each [Chip.rewritten] is the full new field text. */
    data class BangHints(val chips: List<Chip>) : WebRow {
        data class Chip(val code: String, val name: String, val rewritten: String)
    }
}

/** Ordered result sections for one query, generic over the host's app and contact types. */
data class Sections<out A, out C>(
    val order: List<SectionKind>,
    val apps: List<A>,
    val contacts: List<C>,
    val web: List<WebRow>,
    /** Show the dim "Allow contacts to find people" row. */
    val askContactsPermission: Boolean,
)

/**
 * Composes sections: Apps, Contacts, Web (always last) — except a bang or URL query puts Web first.
 * [apps] are already ranked (see [AppSearch.rank]); [contacts] come from the contact source.
 */
fun <A, C> sections(
    query: String,
    engine: WebEngine,
    apps: List<A>,
    contacts: List<C>,
    searchWeb: Boolean = true,
    searchContacts: Boolean = true,
    hasContactsPermission: Boolean = true,
): Sections<A, C> {
    val q = query.trim()
    if (q.isEmpty()) return Sections(listOf(SectionKind.Apps), apps, emptyList(), emptyList(), false)
    val bang = WebSearch.bang(q)
    val isUrl = WebSearch.looksLikeUrl(q)
    val web = ArrayList<WebRow>()
    if (searchWeb) {
        if (isUrl) web += WebRow.Open("Open ${q.removePrefix("https://").removePrefix("http://")}", WebSearch.toUrl(q))
        if (bang != null || WebSearch.hasBang(q)) {
            val code = bang?.code
            val name = code?.let { WebSearch.bangLabel(it) }
            web += WebRow.BangSearch(
                "Search with !${code ?: "bang"}" + (name?.let { " ($it)" } ?: ""),
                WebEngine.DuckDuckGo.url(q),
            )
        }
        val plain = bang?.rest ?: q
        if (bang == null || plain.isNotBlank()) {
            if (!(bang != null && engine == WebEngine.DuckDuckGo)) {
                web += WebRow.Search("Search ${engine.label} for “$plain”", engine.url(plain))
            }
            web += WebRow.PlayStore("Search Play Store for “$plain”", WebSearch.playStoreUrl(plain), WebSearch.playStoreMarketUri(plain))
        }
        if (engine == WebEngine.DuckDuckGo && bang == null && !WebSearch.hasBang(q)) {
            web += WebRow.BangHints(
                WebSearch.popularBangs.take(4).map { (code, name) -> WebRow.BangHints.Chip(code, name, "!$code $q") },
            )
        }
    }
    val askPerm = searchContacts && !hasContactsPermission
    val showContacts = searchContacts && (contacts.isNotEmpty() || askPerm)
    val order = buildList {
        val webFirst = searchWeb && (bang != null || isUrl || WebSearch.hasBang(q))
        if (webFirst) add(SectionKind.Web)
        if (apps.isNotEmpty()) add(SectionKind.Apps)
        if (showContacts) add(SectionKind.Contacts)
        if (!webFirst && web.isNotEmpty()) add(SectionKind.Web)
    }
    return Sections(order, apps, if (searchContacts) contacts else emptyList(), web, askPerm)
}
