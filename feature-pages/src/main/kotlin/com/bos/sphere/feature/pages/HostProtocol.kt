package com.bos.sphere.feature.pages

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Where `orrery.open(route)` may lead. Everything else is refused (and logged nowhere). */
sealed interface OpenTarget {
    /** `app:<package>`: launch that app through `LauncherApps`. */
    data class App(val packageName: String) : OpenTarget

    /** `page:<id>`: turn to that page (`page:home` is page 0). */
    data class Page(val pageId: String) : OpenTarget
}

object OpenRoutes {
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val PLACEHOLDER = Regex("\\{[A-Za-z0-9_]+}")

    fun parse(route: String): OpenTarget? {
        val r = route.trim()
        return when {
            r.startsWith("app:") -> r.removePrefix("app:").takeIf { PACKAGE.matches(it) && it.length <= 255 }
                ?.let { OpenTarget.App(it) }
            r.startsWith("page:") -> r.removePrefix("page:")
                .takeIf { it == com.bos.sphere.feature.pages.Page.HOME_ID || PageIds.isValid(it) }
                ?.let { OpenTarget.Page(it) }
            else -> null
        }
    }

    /** True for a template this launcher can route at all (`app:…` or `page:…`). */
    fun isRouteTemplate(template: String): Boolean =
        template.startsWith("app:") || template.startsWith("page:")

    /**
     * Whether [route] fits [template]: literal text must match exactly; each `{name}` matches one run
     * of characters other than `/`, `:`, `{`, `}`.
     */
    fun matches(template: String, route: String): Boolean {
        val pattern = StringBuilder("^")
        var last = 0
        for (m in PLACEHOLDER.findAll(template)) {
            pattern.append(Regex.escape(template.substring(last, m.range.first)))
            pattern.append("[^/:{}]+")
            last = m.range.last + 1
        }
        pattern.append(Regex.escape(template.substring(last))).append('$')
        return Regex(pattern.toString()).matches(route)
    }

    /** A route is allowed only if the widget declared it in `opens` and it is an app: or page: route. */
    fun resolve(manifest: WidgetManifest, route: String): OpenTarget? =
        if (manifest.opens.any { matches(it, route) }) parse(route) else null
}

/** The D-AC channel message envelope `{channel, at, from, ttl, data}` (spec §3.5). */
object Envelope {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /** `at` is ISO-8601 with the zone's offset, to the second ("2026-10-14T05:21:00+05:30"). */
    fun at(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).truncatedTo(ChronoUnit.SECONDS).atZone(zone).toOffsetDateTime().format(ISO)

    fun build(channel: String, epochMillis: Long, zone: ZoneId, from: String, ttlSeconds: Int, data: Any?): Map<String, Any?> =
        linkedMapOf(
            "channel" to channel,
            "at" to at(epochMillis, zone),
            "from" to from,
            "ttl" to ttlSeconds.coerceAtLeast(0),
            "data" to data,
        )
}

/** A message from a widget's `orrery.*` call (over its MessagePort). */
sealed interface HostRequest {
    data class On(val channel: String) : HostRequest
    data class Publish(val channel: String, val data: Any?, val ttl: Int) : HostRequest
    data class Open(val route: String) : HostRequest
}

/** What the host does about a [HostRequest]. */
sealed interface HostDecision {
    data class Subscribe(val channel: String) : HostDecision
    data class Deliver(val envelope: Map<String, Any?>) : HostDecision
    data class Open(val target: OpenTarget) : HostDecision

    /** Sent back to the widget only (a DOM event there); never logged. */
    data class Refuse(val op: String, val reason: String) : HostDecision
}

/**
 * The host side of the D-AC host API (spec §3.4): `on` only for declared `reads`, `publish` only for
 * declared `writes` (never `launcher.*`), `open` only for declared `opens` that are `app:` or
 * `page:` routes. Messages are JSON strings: `{"op":"on","channel":…}`,
 * `{"op":"publish","channel":…,"data":…,"ttl":…}`, `{"op":"open","route":…}`. Pure.
 */
object HostProtocol {
    const val MAX_MESSAGE_CHARS = 64 * 1024
    const val DEFAULT_TTL = 60
    const val MAX_TTL = 24 * 60 * 60

    /** The handshake a widget's page receives (with the MessagePort) once it has loaded. */
    const val HANDSHAKE = "orrery-host-port"

    fun parse(text: String?): HostRequest? {
        if (text == null || text.length > MAX_MESSAGE_CHARS) return null
        val m = MiniJson.parseOrNull(text).asObject() ?: return null
        return when (m.str("op")) {
            "on" -> m.str("channel")?.let { HostRequest.On(it) }
            "publish" -> m.str("channel")?.let {
                val ttl = (m.long("ttl") ?: DEFAULT_TTL.toLong()).coerceIn(0, MAX_TTL.toLong()).toInt()
                HostRequest.Publish(it, m["data"], ttl)
            }
            "open" -> m.str("route")?.let { HostRequest.Open(it) }
            else -> null
        }
    }

    fun decide(manifest: WidgetManifest, request: HostRequest, nowMillis: Long, zone: ZoneId): HostDecision =
        when (request) {
            is HostRequest.On ->
                if (request.channel in manifest.reads) HostDecision.Subscribe(request.channel)
                else HostDecision.Refuse("on", "'${request.channel}' is not in this widget's reads")

            is HostRequest.Publish -> when {
                request.channel.startsWith(ManifestRules.LAUNCHER_PREFIX) ->
                    HostDecision.Refuse("publish", "only the launcher publishes '${request.channel}'")
                request.channel !in manifest.writes ->
                    HostDecision.Refuse("publish", "'${request.channel}' is not in this widget's writes")
                else -> {
                    val envelope = Envelope.build(request.channel, nowMillis, zone, manifest.id, request.ttl, request.data)
                    if (MiniJson.write(envelope).length > MAX_MESSAGE_CHARS) {
                        HostDecision.Refuse("publish", "message too large")
                    } else {
                        HostDecision.Deliver(envelope)
                    }
                }
            }

            is HostRequest.Open -> OpenRoutes.resolve(manifest, request.route)?.let { HostDecision.Open(it) }
                ?: HostDecision.Refuse("open", "route not allowed")
        }

    fun readyMessage(context: ReadyContext): String = MiniJson.write(
        linkedMapOf(
            "op" to "ready",
            "ctx" to linkedMapOf(
                "theme" to context.theme,
                "size" to linkedMapOf("name" to "fill", "width" to context.widthDp, "height" to context.heightDp),
                "locale" to context.locale,
                "reduceMotion" to context.reduceMotion,
            ),
        ),
    )

    fun messageFor(envelope: Map<String, Any?>): String =
        MiniJson.write(linkedMapOf("op" to "message", "envelope" to envelope))

    fun refusedMessage(refuse: HostDecision.Refuse): String =
        MiniJson.write(linkedMapOf("op" to "refused", "what" to refuse.op, "detail" to refuse.reason))
}

/** What `orrery.ready(cb)` delivers: `{ theme, size, locale, reduceMotion }`. */
data class ReadyContext(
    val theme: Map<String, String>,
    val widthDp: Int,
    val heightDp: Int,
    val locale: String,
    val reduceMotion: Boolean,
)

/** Theme tokens for widgets (D-AC §3.3: `--field`, `--ink`, `--violet`, `--native`, `--cloud`, font stacks). */
object ThemeTokens {
    /** `#rrggbb` for an opaque colour, else `rgba(r,g,b,a)`. */
    fun css(argb: Long): String {
        val a = ((argb shr 24) and 0xFF).toInt()
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return if (a == 0xFF) String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b)
        else String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)", r, g, b, a / 255f)
    }

    /** Token name → css value, from the launcher's palette ([colors]: token name → ARGB). */
    fun build(colors: Map<String, Long>): Map<String, String> =
        colors.mapValues { css(it.value) } + mapOf(
            "--font-sans" to "system-ui, -apple-system, \"Segoe UI\", Roboto, sans-serif",
            "--font-mono" to "ui-monospace, \"Roboto Mono\", monospace",
        )
}

/** One active notification, reduced to what the counts channel may carry. */
data class NotificationFact(val packageName: String, val appLabel: String, val isGroupSummary: Boolean, val isOngoing: Boolean)

/** The data the launcher publishes on its own channels. Counts and app names only, never content. */
object LauncherChannelData {
    const val CLOCK = "launcher.clock"
    const val BATTERY = "launcher.battery"
    const val NOTIFICATIONS = "launcher.notifications"
    val ALL = setOf(CLOCK, BATTERY, NOTIFICATIONS)

    const val CLOCK_TTL = 60
    const val BATTERY_TTL = 300
    const val NOTIFICATIONS_TTL = 60

    fun clock(epochMillis: Long, zone: ZoneId, is24Hour: Boolean): Map<String, Any?> {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return linkedMapOf(
            "iso" to Envelope.at(epochMillis, zone),
            "hour" to t.hour,
            "minute" to t.minute,
            "zone" to zone.id,
            "is24Hour" to is24Hour,
        )
    }

    fun battery(level: Int, charging: Boolean): Map<String, Any?> =
        linkedMapOf("level" to level.coerceIn(0, 100), "charging" to charging)

    /**
     * Counts per app. Group summaries are skipped (their children are counted) and so are ongoing
     * notifications (media, navigation, foreground services: status, not something waiting).
     * [access] false = notification access isn't granted, so there is nothing to count.
     */
    fun notifications(facts: List<NotificationFact>, access: Boolean): Map<String, Any?> {
        val counted = facts.filter { !it.isGroupSummary && !it.isOngoing }
        val apps = counted.groupBy { it.packageName }
            .map { (pkg, list) -> Triple(pkg, list.first().appLabel.ifBlank { pkg }, list.size) }
            .sortedWith(compareByDescending<Triple<String, String, Int>> { it.third }.thenBy { it.second.lowercase(Locale.ROOT) })
            .map { (pkg, label, n) -> linkedMapOf("package" to pkg, "app" to label, "count" to n) }
        return linkedMapOf("access" to access, "total" to counted.size, "apps" to apps)
    }

    /** Milliseconds until the next whole minute (when the clock channel publishes again). */
    fun millisToNextMinute(epochMillis: Long): Long = 60_000L - Math.floorMod(epochMillis, 60_000L)
}
