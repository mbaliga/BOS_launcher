package com.bos.sphere.core.data

import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Wave 43 (radarWeb): the ONE class in the launcher that opens a network connection (the privacy audits allow exactly this file).
 * HTTPS only, GET only, 2 s connect and 2 s read timeout, at most 64 KB of the answer, no redirects, no cookies (nothing installs a cookie handler and no
 * Cookie header is sent), a generic User-Agent, no other headers. It runs on its own short-lived background thread and aborts the connection when its
 * coroutine is cancelled (a new keystroke, Radar closing). Any failure returns null.
 */
object HttpsFetcher : WebFetcher {
    private const val TIMEOUT_MS = 2000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Suggest"

    override suspend fun get(url: String): String? {
        if (!url.startsWith("https://")) return null
        return suspendCancellableCoroutine { cont ->
            val conn = AtomicReference<HttpURLConnection?>(null)
            cont.invokeOnCancellation { runCatching { conn.get()?.disconnect() } }
            Thread({
                val r = runCatching { fetch(url, conn) }.getOrNull()
                if (cont.isActive) cont.resume(r)
            }, "web-suggest").apply { isDaemon = true }.start()
        }
    }

    private fun fetch(url: String, holder: AtomicReference<HttpURLConnection?>): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        holder.set(c)
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.requestMethod = "GET"
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) return null
            val cap = WebSuggestParse.MAX_BYTES
            val buf = ByteArray(cap + 1)
            var n = 0
            c.inputStream.use { ins ->
                while (n <= cap) { val r = ins.read(buf, n, buf.size - n); if (r < 0) break; n += r }
            }
            return if (n > cap) null else String(buf, 0, n, Charsets.UTF_8)
        } finally {
            runCatching { c.disconnect() }
        }
    }
}
