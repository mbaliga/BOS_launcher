package com.bos.sphere.core.data

import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/** Pure web-search helpers: URL detection and Play Store links. The browser is whatever the system picks. */
object WebSearch {
    private val HOST = Regex("^(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,24}(?::\\d{1,5})?(?:[/?#]\\S*)?$")
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://\\S+$")
    private val LOCAL = Regex("^(?:localhost|\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d{1,5})?(?:[/?#]\\S*)?$")
    private val HAS_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    fun looksLikeUrl(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() } || '@' in t) return false
        return SCHEME.matches(t) || HOST.matches(t) || LOCAL.matches(t)
    }

    fun toUrl(text: String): String {
        val t = text.trim()
        return if (HAS_SCHEME.containsMatchIn(t)) t else "https://$t"
    }

    private fun enc(q: String) = URLEncoder.encode(q.trim(), "UTF-8")

    fun playStoreUrl(query: String) = "https://play.google.com/store/search?q=${enc(query)}&c=apps"
    fun playStoreMarketUri(query: String) = "market://search?q=${enc(query)}&c=apps"

    /** ACTION_VIEW for [url]; the system picks the user's browser. */
    fun intentFor(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
