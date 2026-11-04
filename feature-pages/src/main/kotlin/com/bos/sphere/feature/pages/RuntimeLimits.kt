package com.bos.sphere.feature.pages

import java.util.Locale

/**
 * What a link a page hands to the system may carry. Pure, so the rules are unit-tested; the launcher
 * applies them before building any intent (`ExternalLinks`).
 *
 * - Only a VIEW intent leaves (an `intent:` URL without an action is VIEW; one that names another
 *   action, such as CALL or SEND, is refused).
 * - Never a `file:` or `content:` address (or another scheme pages may never open): a `file://` intent
 *   would make the platform throw FileUriExposedException inside the launcher.
 * - Never a URL longer than [MAX_URL_CHARS]: a megabyte-long `mailto:` would fail the binder call and
 *   throw inside the launcher.
 * The launcher also clears the intent's flags (no `FLAG_GRANT_*` from a page), clip data, component and
 * selector.
 */
object IntentRules {
    const val VIEW = "android.intent.action.VIEW"
    const val MAX_URL_CHARS = 8 * 1024

    private val REFUSED_SCHEMES = setOf(
        "file", "content", "android.resource", "javascript", "data", "blob", "filesystem", "about", "chrome",
    )

    fun urlFits(url: String): Boolean = url.length <= MAX_URL_CHARS

    /** [action] is the intent's action (null = none given); [dataScheme] its data's scheme (null = no data). */
    fun allowed(action: String?, dataScheme: String?): Boolean =
        (action == null || action == VIEW) && dataScheme?.lowercase(Locale.ROOT) !in REFUSED_SCHEMES
}

/**
 * JavaScript dialogs (`alert`, `confirm`, `prompt`, `beforeunload`) from launcher pages: at most [max]
 * within [windowMillis], so a page calling `alert()` in a loop can't bury the home screen; the rest are
 * answered "cancel" without showing anything. (Pages that aren't on screen get no dialogs at all;
 * the caller checks that.) Pure; one per WebView.
 */
class DialogBudget(private val max: Int = 3, private val windowMillis: Long = 30_000L) {
    private var windowStart = Long.MIN_VALUE
    private var used = 0

    fun take(nowMillis: Long): Boolean {
        if (windowStart == Long.MIN_VALUE || nowMillis - windowStart >= windowMillis) {
            windowStart = nowMillis
            used = 0
        }
        if (used >= max) return false
        used++
        return true
    }
}

/**
 * Rate limit for one widget's host-API port: a token bucket of [ratePerSecond] with a burst of
 * [burst]. Messages over the limit are dropped before they are parsed; a widget that keeps flooding
 * ([closeAfterDrops] drops within [dropWindowMillis]) has its port closed until its page reloads.
 * Bounds the launcher's main-thread work whatever a widget sends. Pure.
 */
class RateGate(
    private val ratePerSecond: Double = 30.0,
    private val burst: Double = 60.0,
    private val closeAfterDrops: Int = 600,
    private val dropWindowMillis: Long = 10_000L,
) {
    enum class Admit { Ok, Drop, Close }

    private var tokens = burst
    private var last = Long.MIN_VALUE
    private var dropWindowStart = Long.MIN_VALUE
    private var drops = 0

    fun admit(nowMillis: Long): Admit {
        if (last != Long.MIN_VALUE) {
            tokens = minOf(burst, tokens + (nowMillis - last).coerceAtLeast(0L) * ratePerSecond / 1000.0)
        }
        last = nowMillis
        if (tokens >= 1.0) {
            tokens -= 1.0
            return Admit.Ok
        }
        if (dropWindowStart == Long.MIN_VALUE || nowMillis - dropWindowStart >= dropWindowMillis) {
            dropWindowStart = nowMillis
            drops = 0
        }
        drops++
        return if (drops >= closeAfterDrops) Admit.Close else Admit.Drop
    }
}

/**
 * `orrery.open` is honoured only as the answer to a tap: within [windowMillis] of the user lifting a
 * finger on that widget's page (and the caller also requires the page to be on screen and at rest,
 * with the launcher in front). Each tap opens at most one thing. A widget on a neighbour page, or one
 * calling `open` on a timer, is refused. Pure.
 */
class OpenGate(private val windowMillis: Long = 5_000L) {
    private var tapAt = Long.MIN_VALUE

    fun tapped(nowMillis: Long) {
        tapAt = nowMillis
    }

    /** True (and the tap is used up) if a tap happened within the window. */
    fun take(nowMillis: Long): Boolean {
        if (tapAt == Long.MIN_VALUE || nowMillis - tapAt !in 0..windowMillis) return false
        tapAt = Long.MIN_VALUE
        return true
    }
}
