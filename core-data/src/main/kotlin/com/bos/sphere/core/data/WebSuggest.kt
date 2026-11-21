package com.bos.sphere.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder

/**
 * Wave 43 (radarWeb, docs/decisions/radarWebSuggest.md): live web suggestions for Radar. The ONLY network use of the launcher.
 *
 * This file is pure Kotlin (rules, URL builders, parsers, the debounce and the cache); the one class that opens a connection is
 * [HttpsFetcher] in WebSuggestHttp.kt, and the privacy audits allow exactly that file. What goes out: the typed query text, in the URL of one
 * HTTPS GET to the chosen engine's public suggestion endpoint (or the Play Store's suggestion endpoint). Nothing else: no cookies, no
 * identifiers, no locale (the Play Store request names a fixed language and country), a generic User-Agent.
 */
data class WebAnswer(val heading: String, val text: String, val url: String?)

data class WebSuggestions(val suggestions: List<String> = emptyList(), val answer: WebAnswer? = null) {
    val isEmpty: Boolean get() = suggestions.isEmpty() && answer == null
}

/** Whether a typed text may be sent at all. */
object WebSuggestRules {
    const val MIN_CHARS = 2
    const val MAX_CHARS = 100
    private val EMAIL = Regex("""\S+@\S+\.\S+""")
    private val PHONE = Regex("""^[+()\d\s.\-/]+$""")

    /** False for text shorter than 2 characters, a URL, an email address, a phone number or anything longer than 100 characters. */
    fun eligible(text: String): Boolean {
        val t = text.trim()
        if (t.length < MIN_CHARS || t.length > MAX_CHARS) return false
        if (WebSearch.looksLikeUrl(t) || "://" in t || EMAIL.containsMatchIn(t)) return false
        if (PHONE.matches(t) && t.count { it.isDigit() } >= 3) return false
        return true
    }
}

object WebSuggestUrls {
    private fun enc(q: String) = URLEncoder.encode(q.trim(), "UTF-8")

    /** The Play Store's suggestion endpoint; language and country are fixed values so nothing about the device goes out. */
    fun playSuggest(query: String) = "https://market.android.com/suggest/SuggRequest?json=1&query=${enc(query)}&hl=en&gl=us&c=3"

    /** DuckDuckGo's instant answer API. */
    fun duckAnswer(query: String) = "https://api.duckduckgo.com/?q=${enc(query)}&format=json&no_html=1&skip_disambig=1"
}

/** Parsers for the public endpoints; every one returns empty on malformed or oversized input and never throws. */
object WebSuggestParse {
    const val MAX_BYTES = 64 * 1024
    const val MAX_ITEMS = 8
    private const val MAX_ITEM_CHARS = 120
    private const val MAX_ANSWER_CHARS = 280

    private fun root(body: String?): Any? {
        if (body == null || body.length > MAX_BYTES) return null
        return runCatching { JSONTokener(body.trim()).nextValue() }.getOrNull()
    }

    /**
     * Google (`["q", ["a", "b"]]`), Bing (osjson, same shape), Brave (same shape), DuckDuckGo (type=list, same shape, or the object form
     * `[{"phrase": "a"}]`) and the Play Store (`[{"s": "a", "t": "q"}]` or a plain array of strings).
     */
    fun suggestions(body: String?): List<String> {
        val v = root(body) as? JSONArray ?: return emptyList()
        val out = ArrayList<String>()
        fun add(s: String?) {
            val t = s?.trim()?.replace(Regex("\\s+"), " ")
            if (!t.isNullOrEmpty() && t.length <= MAX_ITEM_CHARS && out.none { it.equals(t, ignoreCase = true) }) out += t
        }
        val second = if (v.length() >= 2) v.opt(1) else null
        if (v.opt(0) is String && second is JSONArray) {
            for (i in 0 until second.length()) { if (out.size >= MAX_ITEMS) break; (second.opt(i) as? String)?.let(::add) }
            return out
        }
        for (i in 0 until v.length()) {
            if (out.size >= MAX_ITEMS) break
            when (val e = v.opt(i)) {
                is String -> add(e)
                is JSONObject -> add(e.optString("phrase", "").ifEmpty { e.optString("s", "") }.ifEmpty { e.optString("suggestion", "") })
            }
        }
        return out
    }

    /** DuckDuckGo instant answer: the abstract (or a computed answer, or a definition) with its heading and an https source link; null when there is none. */
    fun duckAnswer(body: String?): WebAnswer? {
        val o = root(body) as? JSONObject ?: return null
        val text = listOf(o.optString("AbstractText", ""), (o.opt("Answer") as? String).orEmpty(), o.optString("Definition", "")).firstOrNull { it.isNotBlank() } ?: return null
        val url = o.optString("AbstractURL", "").takeIf { it.startsWith("https://") && it.length <= 300 }
        val t = text.trim().replace(Regex("\\s+"), " ")
        return WebAnswer(o.optString("Heading", "").trim().take(MAX_ITEM_CHARS), if (t.length > MAX_ANSWER_CHARS) t.take(MAX_ANSWER_CHARS - 1) + "…" else t, url)
    }
}

/** How a request is made: one GET, null on any failure. The real one is [HttpsFetcher]; tests pass a fake. */
fun interface WebFetcher {
    suspend fun get(url: String): String?
}

/**
 * The debounce, the in-memory cache of the last [CACHE_SIZE] queries and the choice of request. A call is cancelled by cancelling its coroutine (a new
 * keystroke restarts the caller's effect; closing Radar leaves the composition), which also aborts a request in flight. Nothing is stored on disk.
 */
class WebSuggestSource(
    private val fetcher: WebFetcher,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val cache = object : LinkedHashMap<String, WebSuggestions>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WebSuggestions>?) = size > CACHE_SIZE
    }

    private fun cached(key: String): WebSuggestions? = synchronized(cache) { cache[key] }
    private fun remember(key: String, v: WebSuggestions) { synchronized(cache) { cache[key] = v } }
    fun cacheSize(): Int = synchronized(cache) { cache.size }
    fun clear() { synchronized(cache) { cache.clear() } }

    private suspend fun safeGet(url: String): String? = try { fetcher.get(url) } catch (e: CancellationException) { throw e } catch (_: Throwable) { null }

    /** Suggestions (and, for DuckDuckGo, an instant answer) for [query] from [engine]; empty when not eligible, no endpoint, offline or failed. */
    suspend fun web(query: String, engine: WebEngine): WebSuggestions {
        val q = query.trim()
        if (!WebSuggestRules.eligible(q) || !engine.hasSuggestions) return WebSuggestions()
        val key = "${engine.name}|${q.lowercase()}"
        cached(key)?.let { return it }
        wait(debounceMs)
        val got = coroutineScope {
            val list = async { safeGet(engine.suggestUrl(q)!!) }
            val ans = if (engine == WebEngine.DuckDuckGo) async { safeGet(WebSuggestUrls.duckAnswer(q)) } else null
            list.await() to ans?.await()
        }
        if (got.first == null && got.second == null) return WebSuggestions() // failed: not cached, silent
        val out = WebSuggestions(WebSuggestParse.suggestions(got.first).filter { !it.equals(q, ignoreCase = true) }, WebSuggestParse.duckAnswer(got.second))
        remember(key, out)
        return out
    }

    /** Play Store app suggestions for [query]. */
    suspend fun play(query: String): List<String> {
        val q = query.trim()
        if (!WebSuggestRules.eligible(q)) return emptyList()
        val key = "play|${q.lowercase()}"
        cached(key)?.let { return it.suggestions }
        wait(debounceMs)
        val body = safeGet(WebSuggestUrls.playSuggest(q)) ?: return emptyList()
        val out = WebSuggestParse.suggestions(body).filter { !it.equals(q, ignoreCase = true) }
        remember(key, WebSuggestions(out))
        return out
    }

    companion object {
        const val DEBOUNCE_MS = 800L
        const val CACHE_SIZE = 30

        /** The process-wide instance (one cache for every Radar visit). */
        val shared: WebSuggestSource by lazy { WebSuggestSource(HttpsFetcher) }
    }
}

/** What Radar holds for the typed query: engine suggestions, the answer, and Play Store suggestions. */
data class WebLive(val suggestions: List<String> = emptyList(), val answer: WebAnswer? = null, val play: List<String> = emptyList()) {
    val isEmpty: Boolean get() = suggestions.isEmpty() && answer == null && play.isEmpty()
}

/** The SearchHits Radar draws for [WebLive]; they open with an ordinary browser or store intent (see openHitIntent), never learn, never persist. */
object WebHits {
    const val MAX_SHOWN = 8
    const val EVERYTHING_SHOWN = 3

    fun web(engine: WebEngine, query: String, live: WebLive): List<SearchHit> = buildList {
        live.answer?.let { a ->
            add(SearchHit(
                id = "web:answer", category = SearchCategory.Web, title = a.heading.ifBlank { engine.label }, snippet = a.text, subtitle = engine.label,
                kind = HitKind.WebAnswer, uri = a.url ?: engine.url(query), score = 1f,
            ))
        }
        live.suggestions.take(MAX_SHOWN).forEachIndexed { i, s ->
            add(SearchHit(id = "web:sug:${s.lowercase()}", category = SearchCategory.Web, title = s, kind = HitKind.WebSuggestion, uri = engine.url(s), score = 0.9f - i * 0.01f))
        }
    }

    fun play(live: WebLive): List<SearchHit> = live.play.take(MAX_SHOWN).mapIndexed { i, s ->
        SearchHit(
            id = "play:sug:${s.lowercase()}", category = SearchCategory.Apps, title = s, subtitle = "Play Store", kind = HitKind.PlayStore,
            uri = WebSearch.playStoreMarketUri(s), extras = mapOf("https" to WebSearch.playStoreUrl(s)), score = 0.5f - i * 0.01f,
        )
    }
}
