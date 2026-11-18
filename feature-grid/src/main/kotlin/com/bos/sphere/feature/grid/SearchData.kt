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

    /** PAL-03: [shortcuts] are the app's own launcher shortcuts that matched the query; they are drawn inside this tile or row. */
    data class App(val app: AppEntry, val shortcuts: List<SearchHit> = emptyList()) : Tile { override val key get() = "a:${app.key}" }
    data class Person(val c: ContactRow) : Tile { override val key get() = "c:${c.hit.id}" }
    data class Hit(val hit: SearchHit) : Tile { override val key get() = "h:${hit.id}" }
    data class Web(val row: WebRow, val index: Int) : Tile { override val key get() = "w:$index" }
    data class Allow(val files: Boolean) : Tile { override val key get() = if (files) "allow:files" else "allow:contacts" }
    /** A Ground Control setting as a Radar result (CFG-05); it opens Ground Control on that row. */
    data class Setting(val entry: SettingEntry) : Tile { override val key get() = "set:${entry.id}" }
}

/** The key Radar learns selections under (PaletteHost.learn), so a tile finds its use in [SearchData.habit]. */
internal fun Tile.habitKey(): String = when (this) {
    is Tile.App -> RadarHabits.appKey(app)
    is Tile.Person -> "contact:${c.hit.id}"
    is Tile.Hit -> hit.extras["usage"] ?: hit.id
    else -> key
}

internal fun Tile.caption(): String = when (this) {
    is Tile.App -> app.label
    is Tile.Person -> c.hit.name
    is Tile.Hit -> hit.title
    is Tile.Web -> when (row) {
        is WebRow.Open -> row.label
        is WebRow.Search -> row.label
        is WebRow.PlayStore -> row.label
    }
    is Tile.Allow -> if (files) L10n.tr("Allow file access") else L10n.tr("Allow contacts to find people")
    is Tile.Setting -> entry.title
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
    /** Settings that match the query (CFG-05); shown with the Actions. */
    val settings: List<SettingEntry> = emptyList(),
    /** Wave 18: decayed use per usage key (see [RadarHabits]); orders "Everything" before anything is typed. */
    val habit: Map<String, Double> = emptyMap(),
) {
    val blank = query.isBlank()

    /** PAL-03: app shortcuts nested in their app's tile (app key to shortcuts); the rest of the Actions hits stay Actions rows. */
    private val nesting: NestedShortcuts.Split = NestedShortcuts.split(apps, hits[SearchCategory.Actions].orEmpty())
    private val actionHits: List<SearchHit> get() = nesting.rest

    /** PAL-05: an inline answer (calculator, unit conversion) sits on top; Enter copies it. */
    val answer: SearchHit? = hits[SearchCategory.Actions]?.firstOrNull { it.copyText != null }

    fun count(cat: SearchCategory): Int = when (cat) {
        SearchCategory.Apps -> apps.size
        SearchCategory.Contacts -> contacts.size
        SearchCategory.Web -> web.size + (hits[cat]?.size ?: 0)
        SearchCategory.Actions -> actionHits.size + settings.size
        else -> hits[cat]?.size ?: 0
    }

    /** The category to show unless the user chose one: the first with results. */
    val best: SearchCategory = run {
        if (blank) return@run SearchCategory.Apps
        if (webFirst) return@run SearchCategory.Web
        listOf(SearchCategory.Apps, SearchCategory.Contacts, SearchCategory.Actions, SearchCategory.Files, SearchCategory.Web)
            .firstOrNull { count(it) > 0 } ?: SearchCategory.Apps
    }

    /** "Everything": a mix, best matches first — apps, people, actions, files, web (web first for URLs). */
    fun tiles(cat: SearchCategory?): List<Tile> {
        if (cat != null) return tilesFor(cat)
        // PAL-06: the web hand-off is always the last result, URL queries included (Enter still reaches it first, see webFirst).
        val order = listOf(SearchCategory.Apps, SearchCategory.Contacts, SearchCategory.Actions, SearchCategory.Files, SearchCategory.Web)
        // Round-robin so every kind of result shows up near the centre, not just the first kind.
        // Before anything is typed, "Everything" is only what you really used: recent apps and people.
        // Actions, files and web rows are suggestions, not recents, so they wait for their own category.
        val usable = if (blank) order.filter { it == SearchCategory.Apps || it == SearchCategory.Contacts } else order
        // Wave 18: at rest the apps and people you use most come first (apps before people when equally used); the Orbit
        // lays them out ring by ring in this order.
        if (blank) return usable.flatMap { tilesFor(it).filter { t -> t !is Tile.Allow } }.sortedByDescending { habit[it.habitKey()] ?: 0.0 }
        val mixed = usable.filter { it != SearchCategory.Web }.map { tilesFor(it).filter { t -> t !is Tile.Allow } }
        val out = ArrayList<Tile>()
        answer?.let { out += Tile.Hit(it) }
        var i = 0
        while (out.size < 40 && mixed.any { i < it.size }) { mixed.forEach { l -> l.getOrNull(i)?.let { t -> if (out.none { o -> o.key == t.key }) out.add(t) } }; i++ }
        if (SearchCategory.Web in usable) out += tilesFor(SearchCategory.Web).filter { t -> t !is Tile.Allow }
        return out
    }

    /** Wave 20: the typed Orbit's ring results: [tiles] without the web hand-off rows, which sit on the result strip instead. */
    fun ringTiles(cat: SearchCategory?): List<Tile> = tiles(cat).filter { it !is Tile.Web }

    /** Wave 20: the web hand-off rows ("Search <engine> for ...", the page of a URL, the Play Store row) for the compact strip: the URL's own row, then the engine, then the app store. */
    fun stripTiles(): List<Tile> {
        if (blank) return emptyList()
        val rows = tilesFor(SearchCategory.Web).filterIsInstance<Tile.Web>()
        // The browser hand-off is the first pill (it is the tap people want, and the strip scrolls); the app store comes after it.
        return rows.sortedBy { when (it.row) { is WebRow.Open -> 0; is WebRow.Search -> 1; is WebRow.PlayStore -> 2 } }
    }

    private fun tilesFor(cat: SearchCategory): List<Tile> = when (cat) {
        SearchCategory.Apps -> apps.map { Tile.App(it, nesting.byApp[it.key].orEmpty()) }
        SearchCategory.Contacts -> buildList {
            contacts.forEach { add(Tile.Person(it)) }
            if (askContacts) add(Tile.Allow(files = false))
        }
        SearchCategory.Files -> buildList {
            if (askFiles) add(Tile.Allow(files = true))
            hits[cat].orEmpty().forEach { add(Tile.Hit(it)) }
        }
        // PAL-06 / PAL-15: any hits first, then the app-store lookup, and the hand-off to the browser last.
        SearchCategory.Web -> buildList {
            hits[cat].orEmpty().forEach { add(Tile.Hit(it)) }
            web.withIndex().filter { it.value is WebRow.PlayStore }.forEach { add(Tile.Web(it.value, it.index)) }
            web.withIndex().filter { it.value !is WebRow.PlayStore }.forEach { add(Tile.Web(it.value, it.index)) }
        }
        SearchCategory.Actions -> actionHits.sortedByDescending { it.copyText != null }.map<SearchHit, Tile> { Tile.Hit(it) } + settings.map { Tile.Setting(it) }
    }

    /**
     * What Enter runs (PAL-05, PAL-06): a URL query goes to the web row that opens it, even though the web rows
     * sit last; otherwise the top result, which is the inline answer when there is one.
     */
    fun enterTile(cat: SearchCategory?): Tile? {
        if (webFirst && (cat == null || cat == SearchCategory.Web)) {
            tilesFor(SearchCategory.Web).firstOrNull { it is Tile.Web && it.row is WebRow.Open }?.let { return it }
        }
        return tiles(cat).firstOrNull()
    }

    /** A quiet hint under the Files results when no deep-search app is installed. */
    fun footnote(cat: SearchCategory?): String? =
        if (cat == SearchCategory.Files && deepHint && !blank && count(SearchCategory.Files) > 0) L10n.tr("Install Fylz or Fotoz for deeper results") else null
}

// ---- refinement suggestions (macOS Spotlight menu) ----

internal class SuggItem(val label: String, val insertion: String?, val scope: SearchCategory?, val accent: Boolean = false)
internal class SuggGroup(val header: String, val items: List<SuggItem>)

/**
 * The refinement chips come from the search model with English labels; this puts the words around the user's text into the app language
 * ("Package contains “x”", "Kind: Games", "Used: today", "x I used yesterday"). What the user typed or the device names (a category) stays as is.
 */
internal fun localizeChip(label: String): String {
    fun quoted(prefix: String) = label.removePrefix(prefix).removeSuffix("”")
    return when {
        label.startsWith("Name contains “") && label.endsWith("”") -> L10n.tr("Name contains “%s”", quoted("Name contains “"))
        label.startsWith("Package contains “") && label.endsWith("”") -> L10n.tr("Package contains “%s”", quoted("Package contains “"))
        label.startsWith("Kind: ") -> L10n.tr("Kind: %s", label.removePrefix("Kind: "))
        label.startsWith("Used: ") || label.startsWith("Is: ") || label.startsWith("Launches ") -> L10n.tr(label)
        label.endsWith(" I used yesterday") -> L10n.tr("%s I used yesterday", label.removeSuffix(" I used yesterday"))
        else -> label
    }
}

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
    val chips = suggest(query, appCategories)
    val kinds = chips.filter { it.kind == SuggestionKind.Key || it.kind == SuggestionKind.Value }
        .map { SuggItem(localizeChip(it.label), it.insertion, null, accent = true) }
    // "Name contains" is already offered once under Filenames: don't repeat it per category. The model's labels are English: the
    // test is on that English, never on the translated words (a translated phone showed the chip twice), and each shown label is translated.
    val appText = chips.filter { it.kind == SuggestionKind.Text || it.kind == SuggestionKind.Natural }
        // Wave 23 second review: only while the Filenames row is there; without file search the app name chip is the only way to refine by name.
        .filter { !filesAvailable || !it.label.startsWith("Name contains") }
        .map { SuggItem(localizeChip(it.label), it.insertion, null) }
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
