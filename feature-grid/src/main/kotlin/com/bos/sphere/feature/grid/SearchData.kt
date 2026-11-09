package com.bos.sphere.feature.grid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.withStyle
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Channel
import com.bos.sphere.core.data.ContactHit
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.data.WebSearch
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.search.Suggestion
import com.bos.sphere.feature.search.SuggestionKind
import com.bos.sphere.feature.search.WebRow
import com.bos.sphere.feature.search.suggest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The default way to open a hit: ACTION_VIEW on its [SearchHit.uri] (with the mime type and a read
 * grant), or the parsed [SearchHit.intentUri]. Null for in-launcher actions ([SearchHit.actionId],
 * which the host runs itself) and for hits with nothing to open.
 */
@Suppress("UNUSED_PARAMETER")
fun openHitIntent(context: Context, hit: SearchHit): Intent? {
    if (hit.actionId != null) return null
    hit.intentUri?.let { raw ->
        val parsed = runCatching { Intent.parseUri(raw, Intent.URI_INTENT_SCHEME) }.getOrNull()
        if (parsed != null) return parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val raw = hit.uri ?: return null
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    val i = Intent(Intent.ACTION_VIEW)
    if (hit.mime != null) i.setDataAndType(uri, hit.mime) else i.data = uri
    return i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** One thing that can sit in the filmstrip / on the orbit. */
internal sealed interface Tile {
    val key: String

    data class App(val app: AppEntry) : Tile { override val key get() = "a:${app.key}" }
    data class Person(val c: ContactRow) : Tile { override val key get() = "c:${c.hit.id}" }
    data class Hit(val hit: SearchHit) : Tile { override val key get() = "h:${hit.id}" }
    data class Web(val row: WebRow, val index: Int) : Tile { override val key get() = "w:$index" }
    data class Bang(val code: String, val name: String, val rewritten: String) : Tile { override val key get() = "b:$code" }
    data class Allow(val files: Boolean) : Tile { override val key get() = if (files) "allow:files" else "allow:contacts" }
}

internal fun Tile.caption(): String = when (this) {
    is Tile.App -> app.label
    is Tile.Person -> c.hit.name
    is Tile.Hit -> hit.title
    is Tile.Web -> when (row) {
        is WebRow.Open -> row.label
        is WebRow.BangSearch -> row.label
        is WebRow.Search -> row.label
        is WebRow.PlayStore -> row.label
        is WebRow.BangHints -> ""
    }
    is Tile.Bang -> "!$code  $name"
    is Tile.Allow -> if (files) L10n.tr("Allow file access") else L10n.tr("Allow contacts to find people")
}

/** Everything the three layouts draw, computed once per query/result change. */
internal class SearchData(
    val query: String,
    val apps: List<AppEntry>,
    val contacts: List<ContactRow>,
    val askContacts: Boolean,
    val web: List<WebRow>,
    val webFirst: Boolean,
    val hits: Map<SearchCategory, List<SearchHit>>,
    val askFiles: Boolean,
    val deepHint: Boolean,
) {
    val blank = query.isBlank()

    fun count(cat: SearchCategory): Int = when (cat) {
        SearchCategory.Apps -> apps.size
        SearchCategory.Contacts -> contacts.size
        SearchCategory.Web -> web.count { it !is WebRow.BangHints } + (hits[cat]?.size ?: 0)
        else -> hits[cat]?.size ?: 0
    }

    /** The category to show unless the user chose one: the first with results. */
    val best: SearchCategory = run {
        if (blank) return@run SearchCategory.Apps
        if (webFirst) return@run SearchCategory.Web
        listOf(SearchCategory.Apps, SearchCategory.Contacts, SearchCategory.Actions, SearchCategory.Files, SearchCategory.Web)
            .firstOrNull { count(it) > 0 } ?: SearchCategory.Apps
    }

    /** "Everything": a mix, best matches first — apps, people, actions, files, web (web first for bangs/URLs). */
    fun tiles(cat: SearchCategory?): List<Tile> {
        if (cat != null) return tilesFor(cat)
        val order = listOf(SearchCategory.Apps, SearchCategory.Contacts, SearchCategory.Actions, SearchCategory.Files, SearchCategory.Web)
            .let { if (webFirst) it.sortedBy { c -> if (c == SearchCategory.Web) 0 else 1 } else it }
        // Round-robin so every kind of result shows up near the centre, not just the first kind.
        val lists = order.map { tilesFor(it).filter { t -> t !is Tile.Allow && t !is Tile.Bang } }
        val out = ArrayList<Tile>()
        var i = 0
        while (out.size < 40 && lists.any { i < it.size }) { lists.forEach { l -> l.getOrNull(i)?.let(out::add) }; i++ }
        return out
    }

    private fun tilesFor(cat: SearchCategory): List<Tile> = when (cat) {
        SearchCategory.Apps -> apps.map { Tile.App(it) }
        SearchCategory.Contacts -> buildList {
            contacts.forEach { add(Tile.Person(it)) }
            if (askContacts) add(Tile.Allow(files = false))
        }
        SearchCategory.Files -> buildList {
            if (askFiles) add(Tile.Allow(files = true))
            hits[cat].orEmpty().forEach { add(Tile.Hit(it)) }
        }
        SearchCategory.Web -> buildList {
            web.forEachIndexed { i, w ->
                if (w is WebRow.BangHints) w.chips.forEach { add(Tile.Bang(it.code, it.name, it.rewritten)) } else add(Tile.Web(w, i))
            }
            hits[cat].orEmpty().forEach { add(Tile.Hit(it)) }
        }
        SearchCategory.Actions -> hits[cat].orEmpty().map { Tile.Hit(it) }
    }

    /** A quiet hint under the Files results when no deep-search app is installed. */
    fun footnote(cat: SearchCategory?): String? =
        if (cat == SearchCategory.Files && deepHint && !blank && count(SearchCategory.Files) > 0) L10n.tr("Install Fylz or Fotoz for deeper results") else null
}

// ---- refinement suggestions (macOS Spotlight menu) ----

internal class SuggItem(val label: String, val insertion: String?, val scope: SearchCategory?, val accent: Boolean = false)
internal class SuggGroup(val header: String, val items: List<SuggItem>)

/** Header + rows like the macOS suggestion menu: Filenames, Content, Kinds, Contacts, Web. */
internal fun buildSuggestionGroups(
    query: String,
    appCategories: List<String>,
    maxRows: Int = Int.MAX_VALUE,
    /** Only offer what can really be searched right now: no phantom rows. */
    filesAvailable: Boolean = true,
    deepAvailable: Boolean = true,
    appHits: Boolean = true,
): List<SuggGroup> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    if (WebSearch.hasBang(q)) return emptyList() // the Web section already has the bang search
    val chips = suggest(query, appCategories)
    val kinds = chips.filter { it.kind == SuggestionKind.Key || it.kind == SuggestionKind.Value }
        .map { SuggItem(it.label, it.insertion, null, accent = true) }
    // "Name contains" is already offered once under Filenames: don't repeat it per category.
    val nameLabel = L10n.tr("Name contains “%s”", q)
    val appText = chips.filter { it.kind == SuggestionKind.Text || it.kind == SuggestionKind.Natural }
        .filter { it.label != nameLabel && !it.label.startsWith(L10n.tr("Name contains")) }
        .map { SuggItem(it.label, it.insertion, null) }
    val all = buildList {
        if (filesAvailable) add(SuggGroup(L10n.tr("Filenames"), listOf(SuggItem(L10n.tr("Name contains “%s”", q), null, SearchCategory.Files))))
        if (deepAvailable) add(SuggGroup(L10n.tr("Content"), listOf(SuggItem(L10n.tr("Contains “%s”", q), null, SearchCategory.Files))))
        if (appHits && kinds.isNotEmpty()) add(SuggGroup(L10n.tr("Kinds"), kinds))
        if (appHits && appText.isNotEmpty()) add(SuggGroup(L10n.tr("Refine apps"), appText))
    }
    var left = maxRows
    val out = ArrayList<SuggGroup>()
    for (g in all) {
        if (left <= 0) break
        val items = g.items.take(left)
        left -= items.size
        out += SuggGroup(g.header, items)
    }
    return out
}

// ---- text helpers ----

/** [text] with every word of [query] (2+ chars) emphasised, case-insensitively. */
internal fun highlight(text: String, query: String, color: Color): AnnotatedString {
    val words = query.trim().split(' ', '\t').filter { it.length >= 2 && !it.contains(':') }.map { it.trim('"') }.filter { it.length >= 2 }
    if (words.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    val marks = BooleanArray(text.length)
    for (w in words) {
        val lw = w.lowercase()
        var from = 0
        while (true) {
            val at = lower.indexOf(lw, from)
            if (at < 0) break
            for (k in at until (at + lw.length).coerceAtMost(text.length)) marks[k] = true
            from = at + lw.length
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            var j = i
            while (j < text.length && marks[j] == marks[i]) j++
            val part = text.substring(i, j)
            if (marks[i]) withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
            i = j
        }
    }
}

/** "Fylz" / "Fotoz" for the packages we know; null for anything else (no badge). */
internal fun sourceBadge(pkg: String?): String? = when {
    pkg == null -> null
    pkg.contains("fylz", true) -> "Fylz"
    pkg.contains("fotoz", true) -> "Fotoz"
    else -> null
}

/** Order the channel badge on a contact tile prefers. */
internal fun badgeChannel(channels: List<Channel>): Channel? =
    listOf(Channel.Telegram, Channel.WhatsApp, Channel.Signal, Channel.Sms, Channel.Call, Channel.Email).firstOrNull { it in channels }

// ---- file thumbnails ----

internal object ThumbCache {
    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    fun get(key: String): ImageBitmap? = cache.get(key)

    /** Loads a thumbnail for a content Uri off the main thread; null when there is none. */
    suspend fun load(context: Context, uriString: String, px: Int): ImageBitmap? {
        cache.get(uriString)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                if (uri.scheme != "content") return@runCatching null
                context.contentResolver.loadThumbnail(uri, Size(px, px), null).asImageBitmap()
            }.getOrNull()?.also { cache.put(uriString, it) }
        }
    }
}
