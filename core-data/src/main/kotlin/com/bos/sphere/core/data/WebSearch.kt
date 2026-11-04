package com.bos.sphere.core.data

import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/** A leading `!bang` token (DuckDuckGo style) and the rest of the query. */
data class Bang(val code: String, val rest: String)

/** Pure web-search helpers: URL detection, bangs, Play Store links. The browser is whatever the system picks. */
object WebSearch {
    private val BANG = Regex("^!([A-Za-z0-9_.-]{1,20})(?:\\s+(.*))?$", RegexOption.DOT_MATCHES_ALL)
    private val HOST = Regex("^(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,24}(?::\\d{1,5})?(?:[/?#]\\S*)?$")
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://\\S+$")
    private val LOCAL = Regex("^(?:localhost|\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d{1,5})?(?:[/?#]\\S*)?$")
    private val HAS_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    val popularBangs: List<Pair<String, String>> = listOf(
        "g" to "Google", "gi" to "Google Images", "yt" to "YouTube", "w" to "Wikipedia",
        "gh" to "GitHub", "maps" to "Google Maps", "a" to "Amazon", "r" to "Reddit",
    )

    fun looksLikeUrl(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() } || '@' in t) return false
        return SCHEME.matches(t) || HOST.matches(t) || LOCAL.matches(t)
    }

    fun toUrl(text: String): String {
        val t = text.trim()
        return if (HAS_SCHEME.containsMatchIn(t)) t else "https://$t"
    }

    /** `!yt cats` becomes Bang("yt", "cats"); null when the text does not start with a bang. */
    fun bang(text: String): Bang? {
        val m = BANG.matchEntire(text.trim()) ?: return null
        return Bang(m.groupValues[1].lowercase(), m.groupValues[2].trim())
    }

    /** True if any whitespace-separated token is a bang (DuckDuckGo also accepts trailing bangs). */
    fun hasBang(text: String): Boolean =
        text.trim().split(Regex("\\s+")).any { it.length > 1 && it[0] == '!' && it[1].isLetterOrDigit() }

    fun bangLabel(code: String): String? = popularBangs.firstOrNull { it.first == code }?.second

    private fun enc(q: String) = URLEncoder.encode(q.trim(), "UTF-8")

    fun playStoreUrl(query: String) = "https://play.google.com/store/search?q=${enc(query)}&c=apps"
    fun playStoreMarketUri(query: String) = "market://search?q=${enc(query)}&c=apps"

    /** ACTION_VIEW for [url]; the system picks the user's browser. */
    fun intentFor(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
