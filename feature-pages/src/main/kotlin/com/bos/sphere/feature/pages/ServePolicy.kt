package com.bos.sphere.feature.pages

import java.net.URI
import java.util.Locale

/**
 * Each HTML page or widget runs at an https origin of its own, derived from its page id, so each has
 * separate storage and none can read another's. The host is `<page id>.invalid`: the TLD `.invalid`
 * (RFC 2606) can never resolve, so a request that slipped past the interceptor could not reach any
 * real host, and every page is its **own site** (its own registrable domain). A shared parent such as
 * `pages.invalid` would let one page set a `Domain=pages.invalid` cookie that every other page reads,
 * an undeclared channel around `reads` / `writes`; a cookie for `Domain=invalid` is refused, because
 * `invalid` is the registry itself.
 */
object RuntimeOrigin {
    const val TLD = "invalid"

    private val HOST = Regex("^p[0-9a-f]{16}\\.$TLD$")

    fun hostFor(pageId: String): String = "$pageId.$TLD"

    fun originFor(pageId: String): String = "https://${hostFor(pageId)}"

    /** Where the page's view starts: the package's `widget.html`, or a page's root. */
    fun entryUrl(pageId: String, isPackage: Boolean): String =
        originFor(pageId) + if (isPackage) "/widget.html" else "/"

    fun isRuntimeHost(host: String?): Boolean = host != null && HOST.matches(host.lowercase(Locale.ROOT))
}

/** What the request interceptor does with one request from an HTML page or widget. */
sealed interface ServeDecision {
    /** Serve this stored file (path relative to the page's content directory). */
    data class Serve(val path: String) : ServeDecision

    /** Own origin, but no such file. */
    data object NotFound : ServeDecision

    /** Let the WebView fetch it from the network (only a single-file page whose network switch is on). */
    data class Network(val host: String) : ServeDecision

    /** Refused: an empty 403 answers it. [host] is recorded for the page's info ("blocked"). */
    data class Deny(val host: String) : ServeDecision

    /** An inline `data:` URL: no network involved. */
    data object Inline : ServeDecision
}

object ServePolicy {

    /**
     * - Own origin: a package serves `widget.html` and `assets/…`; a page serves its one file at `/`
     *   (or `/index.html`). Other paths are not found.
     * - `data:`: inline, allowed.
     * - Anything else: a package never reaches it (`network: false` is enforced, never trusted); a page
     *   reaches it only with its network switch on and only over http(s); another page's origin is
     *   always refused.
     */
    fun decide(url: String, pageId: String, isPackage: Boolean, networkOn: Boolean): ServeDecision {
        val trimmed = url.trim()
        if (trimmed.regionMatches(0, "data:", 0, 5, ignoreCase = true)) return ServeDecision.Inline
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return ServeDecision.Deny("")
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT) ?: ""
        if (scheme == "https" && host == RuntimeOrigin.hostFor(pageId) && (uri.port == -1 || uri.port == 443)) {
            val path = (uri.rawPath ?: "/").let { decodePath(it) } ?: return ServeDecision.NotFound
            return ownFile(path, isPackage)?.let { ServeDecision.Serve(it) } ?: ServeDecision.NotFound
        }
        if (RuntimeOrigin.isRuntimeHost(host)) return ServeDecision.Deny(host)
        if (isPackage || !networkOn) return ServeDecision.Deny(host)
        return if ((scheme == "http" || scheme == "https") && host.isNotEmpty()) ServeDecision.Network(host)
        else ServeDecision.Deny(host)
    }

    private fun ownFile(path: String, isPackage: Boolean): String? {
        val rel = path.removePrefix("/")
        if (!isPackage) return if (rel.isEmpty() || rel == "index.html") PAGE_FILE else null
        if (rel == PackageLayout.HTML) return rel
        if (!rel.startsWith(PackageLayout.ASSETS)) return null
        return PackageLayout.safePath(rel)?.takeUnless { it.endsWith("/") }
    }

    private fun decodePath(raw: String): String? =
        runCatching { java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrNull()

    /** The stored file name of a single-file page. */
    const val PAGE_FILE = "index.html"

    /**
     * D-AC §3.3's CSP for widget packages, plus `'self'` for images, fonts, styles and media so the
     * package's own `assets/` load (same origin, served from storage: still no network).
     */
    const val PACKAGE_CSP =
        "default-src 'none'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; " +
            "script-src 'unsafe-inline'; font-src 'self' data:; media-src 'self' data: blob:; " +
            "form-action 'none'; base-uri 'none'"

    /**
     * A single-file page with its network off: everything from its own origin, inline, `data:` and
     * `blob:`; nothing else. This also covers what the interceptor can't see (WebSocket and
     * hyperlink-auditing pings fall under `connect-src`).
     */
    const val OFFLINE_PAGE_CSP =
        "default-src 'self' data: blob: 'unsafe-inline' 'unsafe-eval'; connect-src 'self' data: blob:; " +
            "frame-src 'self' data: blob:; worker-src 'self' blob:; form-action 'self'; base-uri 'self'"

    /** The CSP served with the page's document, or null (a page with its network on keeps its own). */
    fun csp(isPackage: Boolean, networkOn: Boolean): String? = when {
        isPackage -> PACKAGE_CSP
        !networkOn -> OFFLINE_PAGE_CSP
        else -> null
    }

    /**
     * The CSP of a package's `assets/…` file. A file there is an image, a font, a style sheet; if
     * one is an HTML or SVG document and the widget navigates or frames it, `sandbox` (no tokens)
     * gives that document no script and an opaque origin, so it can't run without the runtime's head
     * block (which is put only into `widget.html`). Subresources ignore `sandbox`.
     */
    const val PACKAGE_ASSET_CSP =
        "default-src 'none'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; " +
            "font-src 'self' data:; media-src 'self' data: blob:; form-action 'none'; base-uri 'none'; sandbox"

    /** The CSP header for one response from a page's own origin: its document, or a package asset. */
    fun responseCsp(isPackage: Boolean, networkOn: Boolean, isDocument: Boolean): String? =
        if (isPackage && !isDocument) PACKAGE_ASSET_CSP else csp(isPackage, networkOn)

    fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "html", "htm" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "ico" -> "image/x-icon"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}

/** Scripts the runtime puts at the top of a served document (inline: they need no network). */
object RuntimeScripts {
    /**
     * WebRTC could open connections the interceptor and the CSP don't see, so it is removed from the
     * page's window before any of its own scripts run. A page that deliberately reaches through a child
     * frame could still find it; see docs/pages.md.
     */
    const val NO_RTC =
        "(function(){var n=['RTCPeerConnection','webkitRTCPeerConnection','RTCDataChannel','RTCIceCandidate'];" +
            "for(var i=0;i<n.length;i++){try{Object.defineProperty(window,n[i],{value:undefined,writable:false,configurable:false});}catch(e){}}})();"

    /**
     * D-AC §3.3: a widget package gets no storage. DOM storage is already off in its WebView; this
     * also removes IndexedDB and Cache Storage from its window and makes `document.cookie` read empty
     * and ignore writes, before any of its own scripts run. (Like [NO_RTC], a widget that reaches
     * through a child frame could still find them; see docs/pages.md.)
     */
    const val NO_STORAGE =
        "(function(){var n=['indexedDB','caches','openDatabase'];" +
            "for(var i=0;i<n.length;i++){try{Object.defineProperty(window,n[i],{value:undefined,writable:false,configurable:false});}catch(e){}}" +
            "try{Object.defineProperty(Document.prototype,'cookie',{get:function(){return '';},set:function(){},configurable:false});}catch(e){}})();"

    /**
     * The D-AC host API inside a widget: `orrery.ready(cb)`, `orrery.on(channel, cb)`,
     * `orrery.publish(channel, data)`, `orrery.open(route)`. It talks to the launcher over the
     * MessagePort the launcher posts after load (handshake [HostProtocol.HANDSHAKE]); calls made
     * before that are queued. The launcher enforces every declaration; a refusal arrives as an
     * `orrery-refused` DOM event (nothing is logged). Theme tokens are applied to `:root` as CSS
     * custom properties before `ready` callbacks run.
     */
    val HOST_API: String = """
(function(){'use strict';
if(window.orrery)return;
var port=null,queue=[],readyCbs=[],ctx=null,subs={};
function send(m){var s=JSON.stringify(m);if(port)port.postMessage(s);else queue.push(s);}
function applyTheme(t){if(!t)return;var r=document.documentElement;for(var k in t){if(Object.prototype.hasOwnProperty.call(t,k)){try{r.style.setProperty(k,t[k]);}catch(e){}}}}
function later(e){setTimeout(function(){throw e;},0);}
window.addEventListener('message',function(e){
 if(port||e.data!=='${HostProtocol.HANDSHAKE}'||!e.ports||!e.ports.length)return;
 port=e.ports[0];
 port.onmessage=function(ev){var m;try{m=JSON.parse(ev.data);}catch(x){return;}
  if(m.op==='ready'){ctx=m.ctx;applyTheme(ctx&&ctx.theme);var cbs=readyCbs;readyCbs=[];for(var i=0;i<cbs.length;i++){try{cbs[i](ctx);}catch(x){later(x);}}}
  else if(m.op==='message'&&m.envelope){var l=subs[m.envelope.channel]||[];for(var j=0;j<l.length;j++){try{l[j](m.envelope);}catch(x){later(x);}}}
  else if(m.op==='refused'){try{window.dispatchEvent(new CustomEvent('orrery-refused',{detail:{what:m.what,detail:m.detail}}));}catch(x){}}
 };
 for(var i=0;i<queue.length;i++)port.postMessage(queue[i]);queue=[];
});
window.orrery=Object.freeze({
 ready:function(cb){if(typeof cb!=='function')return;if(ctx){try{cb(ctx);}catch(x){later(x);}}else readyCbs.push(cb);},
 on:function(channel,cb){if(typeof channel!=='string'||typeof cb!=='function')return;var first=!subs[channel];(subs[channel]=subs[channel]||[]).push(cb);if(first)send({op:'on',channel:channel});},
 publish:function(channel,data,ttl){if(typeof channel!=='string')return;var m={op:'publish',channel:channel,data:data===undefined?null:data};if(typeof ttl==='number')m.ttl=ttl;send(m);},
 open:function(route){send({op:'open',route:String(route)});}
});
})();
""".trim()
}

/** Prepares an HTML document for serving: the skeleton for fragments, then the runtime's head block. */
object HtmlPrep {
    /**
     * The skeleton a page fragment is served in. Orrery's `web/build.py` writes the page without one
     * and wraps its own test copy in this same skeleton ("the skeleton the publisher adds"), so a
     * fragment renders here as it is tested there.
     */
    const val SKELETON_HEAD =
        "<!doctype html><html><head><meta charset=utf8>" +
            "<meta name=viewport content=\"width=device-width,initial-scale=1,viewport-fit=cover\">" +
            "<style>:root{color-scheme:light;padding:env(safe-area-inset-top,0px) 0 env(safe-area-inset-bottom,0px)}" +
            "body{margin:0;font:14px system-ui,sans-serif;background:#faf9f5}img{max-width:100%}" +
            "[hidden]{display:none!important}</style></head><body>"
    const val SKELETON_TAIL = "</body></html>"

    /** No `<!doctype>` and no `<html>` at the top (after a BOM, whitespace or comments). */
    fun isFragment(html: String): Boolean {
        val start = html.substring(skipLeading(html)).take(64).lowercase(Locale.ROOT)
        return !(start.startsWith("<!doctype") || start.startsWith("<html"))
    }

    fun prepare(html: String, isPackage: Boolean, networkOn: Boolean): String {
        val doc = if (!isPackage && isFragment(html)) SKELETON_HEAD + html + SKELETON_TAIL else html
        val block = StringBuilder()
        ServePolicy.csp(isPackage, networkOn)?.let { csp ->
            block.append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(csp).append("\">")
            block.append("<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\">")
        }
        if (isPackage || !networkOn) block.append("<script>").append(RuntimeScripts.NO_RTC).append("</script>")
        if (isPackage) {
            block.append("<script>").append(RuntimeScripts.NO_STORAGE).append("</script>")
            block.append("<script>").append(RuntimeScripts.HOST_API).append("</script>")
        }
        return if (block.isEmpty()) doc else injectAtTop(doc, block.toString())
    }

    /**
     * Puts [block] before anything of the document's own: after a BOM, leading whitespace and
     * comments, and the doctype, and nowhere else. It never searches for `<head`: the first match
     * could sit inside a comment or a script string, or come after a `<script>` of the page's own, and
     * then the page's code would run before the block (or the block never at all). A `<meta>` or
     * `<script>` there opens the implicit `<html>` and `<head>`, so the block lands first in the head;
     * the document's own `<html>` attributes still apply and its `<head>` tag is simply absorbed.
     */
    fun injectAtTop(html: String, block: String): String {
        val at = skipLeading(html).let { skipDoctype(html, it) }
        return html.substring(0, at) + block + html.substring(at)
    }

    /** Index after a BOM and any whitespace and complete comments at the very top. */
    private fun skipLeading(html: String): Int {
        var i = 0
        if (html.startsWith("\uFEFF")) i++
        while (true) {
            while (i < html.length && html[i].isWhitespace()) i++
            if (!html.startsWith("<!--", i)) return i
            val end = html.indexOf("-->", i + 4)
            if (end < 0) return i
            i = end + 3
        }
    }

    private fun skipDoctype(html: String, from: Int): Int {
        if (!html.regionMatches(from, "<!doctype", 0, 9, ignoreCase = true)) return from
        val end = html.indexOf('>', from)
        return if (end < 0) from else end + 1
    }
}

/**
 * Removing a page clears its origin's cookies: for each cookie name seen, expiring `Set-Cookie` values
 * for the paths a runtime origin serves (`/` and `/assets`), host-only and for the host's own domain.
 * A cookie a page set with some other path can't be listed by the platform and stays on the device,
 * but no page can read it: the origin's id is random and never reused.
 */
object CookieWipe {
    val PATHS = listOf("/", "/assets")

    fun expiries(cookieHeader: String?, host: String): List<String> =
        cookieHeader.orEmpty().split(';')
            .map { it.substringBefore('=').trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .flatMap { name ->
                PATHS.flatMap { path ->
                    listOf("$name=; Max-Age=0; Path=$path", "$name=; Max-Age=0; Path=$path; Domain=$host")
                }
            }
}

/**
 * Removing an HTML page clears what it stored at its origin. `WebStorage.deleteOrigin` alone doesn't:
 * it covers IndexedDB, file systems and Web SQL, not `localStorage` (where Orrery keeps `T.store`). So,
 * after the page's own view is gone, a throwaway WebView loads [DOCUMENT] at [PATH] on that origin
 * (served by the interceptor, nothing else) and it clears `localStorage`, `sessionStorage`, every
 * IndexedDB database, Cache Storage and the cookies it can see, then navigates to [DONE_PATH], which
 * the launcher catches before calling `deleteOrigin` and destroying the view.
 */
object OriginWipe {
    const val PATH = "/.launcher-wipe"
    const val DONE_PATH = "/.launcher-wipe-done"

    val DOCUMENT: String = """
<!doctype html><meta charset=utf-8><script>
(async function(){
try{localStorage.clear();}catch(e){}
try{sessionStorage.clear();}catch(e){}
try{if(window.indexedDB&&indexedDB.databases){var dbs=await indexedDB.databases();await Promise.all(dbs.map(function(d){return new Promise(function(r){try{var q=indexedDB.deleteDatabase(d.name);q.onsuccess=q.onerror=q.onblocked=function(){r();};}catch(e){r();}});}));}}catch(e){}
try{if(window.caches){var ks=await caches.keys();await Promise.all(ks.map(function(k){return caches.delete(k);}));}}catch(e){}
try{document.cookie.split(';').forEach(function(c){var n=c.split('=')[0].trim();if(n){['/','/assets'].forEach(function(p){document.cookie=n+'=; Max-Age=0; Path='+p;document.cookie=n+'=; Max-Age=0; Path='+p+'; Domain='+location.hostname;});}});}catch(e){}
location.replace('$DONE_PATH');
})();
</script>
""".trim()

    /** True for the wipe document's own request on [pageId]'s origin. */
    fun isWipeRequest(url: String, pageId: String): Boolean =
        url == RuntimeOrigin.originFor(pageId) + PATH

    fun isDone(url: String, pageId: String): Boolean =
        url.startsWith(RuntimeOrigin.originFor(pageId) + DONE_PATH)
}
