package com.bos.sphere.feature.pages

import java.net.IDN
import java.net.URI
import java.util.Locale

/** Why an address typed for a website page was refused. */
enum class UrlProblem {
    Empty,
    TooLong,

    /** Anything but http or https (`javascript:`, `file:`, `intent:`, `data:`, …). */
    Scheme,
    NoHost,

    /** `user:password@host`: refused, a classic way to disguise where a link goes. */
    Credentials,
    BadPort,
    Malformed,
}

sealed interface UrlCheck {
    /** [url] is normalised (lower-case scheme and ASCII host); [schemeAdded] = we put `https://` in front. */
    data class Ok(val url: String, val host: String, val secure: Boolean, val schemeAdded: Boolean) : UrlCheck

    data class Rejected(val problem: UrlProblem) : UrlCheck
}

/** Where a navigation inside a page goes. */
enum class LinkRoute {
    /** Stays in this page's view. */
    InPage,

    /** Handed to the user's browser (an http(s) link leaving an HTML page's own origin). */
    Browser,

    /** Handed to the system with an explicit chooser (`tel:`, `mailto:`, `intent:`, …). */
    System,

    /** Refused: no user gesture, or a scheme pages may never open (`javascript:`, `file:`, `content:`, `data:`). */
    Blocked,
}

/**
 * Website pages accept only http and https addresses. Pure (java.net only), so every rule is unit-tested.
 */
object UrlRules {
    const val MAX_LENGTH = 2048

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val HOST_PORT_ONLY = Regex("^[^/:?#@]+:\\d+([/?#].*)?$")
    private val ASCII_HOST = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*\\.?$")
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val IPV6 = Regex("^\\[[0-9a-fA-F:.]+]$")

    /** Schemes a page may never navigate to, gesture or not. */
    private val NEVER = setOf("javascript", "file", "content", "data", "blob", "filesystem", "chrome", "view-source")

    /**
     * Validates what the user typed. No scheme → `https://` is added (https suggested). Only http and
     * https pass; the host must be a real host name (IDN converted to ASCII), an IPv4 or a bracketed
     * IPv6 address; credentials in the address are refused.
     */
    fun check(input: String): UrlCheck {
        val text = input.trim()
        if (text.isEmpty()) return UrlCheck.Rejected(UrlProblem.Empty)
        if (text.length > MAX_LENGTH) return UrlCheck.Rejected(UrlProblem.TooLong)
        if (text.any { it.isWhitespace() || it.code < 0x20 }) return UrlCheck.Rejected(UrlProblem.Malformed)

        val explicit = SCHEME.find(text)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
            ?.takeUnless { HOST_PORT_ONLY.matches(text) }
        val withScheme = when {
            explicit != null -> {
                if (explicit != "http" && explicit != "https") return UrlCheck.Rejected(UrlProblem.Scheme)
                text
            }
            text.startsWith("//") -> "https:$text"
            else -> "https://$text"
        }
        val scheme = withScheme.substringBefore(':').lowercase(Locale.ROOT)
        val afterScheme = withScheme.substring(scheme.length + 1)
        if (!afterScheme.startsWith("//")) return UrlCheck.Rejected(UrlProblem.NoHost)
        val rest = afterScheme.substring(2)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        val tail = rest.substring(authorityEnd)
        if (authority.isEmpty()) return UrlCheck.Rejected(UrlProblem.NoHost)
        if ('@' in authority) return UrlCheck.Rejected(UrlProblem.Credentials)

        val (rawHost, port) = splitHostPort(authority) ?: return UrlCheck.Rejected(UrlProblem.BadPort)
        if (rawHost.isEmpty()) return UrlCheck.Rejected(UrlProblem.NoHost)
        val host = asciiHost(rawHost) ?: return UrlCheck.Rejected(UrlProblem.NoHost)

        val normalised = buildString {
            append(scheme).append("://").append(host)
            if (port != null) append(':').append(port)
            append(if (tail.isEmpty()) "/" else tail)
        }
        val parsed = runCatching { URI(normalised) }.getOrNull() ?: return UrlCheck.Rejected(UrlProblem.Malformed)
        if (parsed.scheme == null) return UrlCheck.Rejected(UrlProblem.Malformed)
        return UrlCheck.Ok(
            url = normalised,
            host = host.trimEnd('.'),
            secure = scheme == "https",
            schemeAdded = explicit == null,
        )
    }

    /** The host of an http(s) address, for the page's header, or null. */
    fun hostOf(url: String): String? = (check(url) as? UrlCheck.Ok)?.host

    fun isSecure(url: String): Boolean = url.trim().lowercase(Locale.ROOT).startsWith("https://")

    fun schemeOf(url: String): String? = SCHEME.find(url.trim())?.groupValues?.get(1)?.lowercase(Locale.ROOT)

    /**
     * A navigation inside a website page. http(s) stays in the page; other schemes (`tel:`,
     * `mailto:`, `intent:`, `market:`, …) go to the system's chooser, but only after a user gesture
     * (a redirect can't launch an app); `javascript:`, `file:`, `content:` and `data:` never.
     */
    fun websiteRoute(url: String, hasGesture: Boolean): LinkRoute {
        val scheme = schemeOf(url) ?: return LinkRoute.Blocked
        return when {
            scheme == "http" || scheme == "https" -> LinkRoute.InPage
            url.trim().equals("about:blank", ignoreCase = true) -> LinkRoute.InPage
            scheme in NEVER || scheme == "about" -> LinkRoute.Blocked
            hasGesture -> LinkRoute.System
            else -> LinkRoute.Blocked
        }
    }

    /**
     * A navigation inside an HTML widget or page served from [ownOrigin]. Its own origin stays.
     * For a single-file page: an http(s) link elsewhere goes to the user's browser (gesture only);
     * other schemes go to the system's chooser (gesture only); the never-schemes and other runtime
     * origins are refused. A D-AC widget package ([isPackage]) is sandboxed like D-AC §3.3's iframe
     * (`sandbox="allow-scripts"` only): it never navigates or opens anything outside its own origin,
     * gesture or not, so it can't carry data out through the browser or start another app; its only
     * way out is `orrery.open`, checked against its declared `opens`.
     */
    fun runtimeRoute(url: String, ownOrigin: String, hasGesture: Boolean, isPackage: Boolean = false): LinkRoute {
        val scheme = schemeOf(url) ?: return LinkRoute.Blocked
        if (originOf(url) == ownOrigin.lowercase(Locale.ROOT).trimEnd('/')) return LinkRoute.InPage
        if (isPackage) return LinkRoute.Blocked
        // Another page's or widget's origin exists only inside the launcher: never handed out.
        if (RuntimeOrigin.isRuntimeHost(runCatching { URI(url.trim()).host }.getOrNull())) return LinkRoute.Blocked
        return when {
            scheme in NEVER || scheme == "about" -> LinkRoute.Blocked
            !hasGesture -> LinkRoute.Blocked
            scheme == "http" || scheme == "https" -> LinkRoute.Browser
            else -> LinkRoute.System
        }
    }

    /** `scheme://host[:port]` in lower case, or null. */
    fun originOf(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    private fun splitHostPort(authority: String): Pair<String, Int?>? {
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            val host = authority.substring(0, close + 1)
            val after = authority.substring(close + 1)
            if (after.isEmpty()) return host to null
            if (!after.startsWith(":")) return null
            return host to (parsePort(after.substring(1)) ?: return null)
        }
        val colon = authority.lastIndexOf(':')
        if (colon < 0) return authority to null
        val port = parsePort(authority.substring(colon + 1)) ?: return null
        return authority.substring(0, colon) to port
    }

    private fun parsePort(s: String): Int? = s.toIntOrNull()?.takeIf { s.all(Char::isDigit) && it in 1..65535 }

    private fun asciiHost(raw: String): String? {
        if (IPV6.matches(raw)) return raw.lowercase(Locale.ROOT)
        IPV4.matchEntire(raw)?.let { m ->
            return if (m.groupValues.drop(1).all { it.toInt() in 0..255 }) raw else null
        }
        val ascii = runCatching { IDN.toASCII(raw, IDN.ALLOW_UNASSIGNED) }.getOrNull()?.lowercase(Locale.ROOT)
            ?: return null
        if (ascii.length > 253 || !ASCII_HOST.matches(ascii)) return null
        // A host made only of digits and dots that isn't a valid IPv4 address is not a host.
        if (ascii.trimEnd('.').all { it.isDigit() || it == '.' }) return null
        return ascii
    }
}
