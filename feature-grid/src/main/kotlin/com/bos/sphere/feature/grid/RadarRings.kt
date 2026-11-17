package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LaunchEvent
import dev.aarso.search.palette.UsageRecord
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow

/**
 * Wave 18 (PAL-09, PAL-18, PAL-20; owner request on build 49): what you open most sits on the innermost Orbit ring,
 * the next most on the next ring, and so on outward. Pure logic, no Android, so the tiers are testable without a screen.
 *
 * - [RadarHabits] turns the launch history and what Radar learned (PaletteHost.learn) into one decayed frequency score
 *   per usage key, so recent habits count more than old ones.
 * - [RadarRings] gives the ring of the n-th result: results are laid out best first, ring by ring, each ring holding
 *   at most [RadarRings.capacity] of them (5, 9, 13, 17) and never more than its geometry has room for.
 */
internal object RadarHabits {
    /** A launch counts half as much every two weeks. */
    const val HALF_LIFE_DAYS = 14.0
    private const val DAY_MS = 86_400_000.0

    fun weight(eventMillis: Long, nowMillis: Long): Double {
        val ageDays = (nowMillis - eventMillis).coerceAtLeast(0L) / DAY_MS
        return 0.5.pow(ageDays / HALF_LIFE_DAYS)
    }

    /** `app:<AppEntry.key>`: the key both the launch history and Radar's own learning use for an app. */
    fun appKey(app: AppEntry): String = "app:${app.key}"

    /**
     * Decayed use per usage key. The home's launch history counts every launch with its own age; what Radar learned only
     * keeps a count and the last time, so its score is the count decayed from that time. The two overlap (a launch made
     * from Radar is in both), so the larger one wins instead of adding them up.
     */
    fun scores(launches: List<LaunchEvent>, learned: Map<String, UsageRecord>, nowMillis: Long): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (e in launches) out.merge("app:${e.key}", weight(e.timeMillis, nowMillis), Double::plus)
        for ((k, u) in learned) {
            val w = u.count * weight(u.lastMillis, nowMillis)
            out[k] = max(out[k] ?: 0.0, w)
        }
        return out
    }

    /**
     * [items] with a score above zero, most used first. A tie keeps the order of [items] (the caller passes them newest
     * first, so of two equally used things the more recent one wins), which also makes the order stable between frames.
     */
    fun <T> byUse(items: List<T>, key: (T) -> String, scores: Map<String, Double>): List<T> =
        items.filter { (scores[key(it)] ?: 0.0) > 0.0 }.sortedByDescending { scores[key(it)] ?: 0.0 }

    /** Apps the user has opened, most used first; apps never opened are left out (the rings fill up as Radar is used). */
    fun rankApps(apps: List<AppEntry>, recent: List<AppEntry>, scores: Map<String, Double>): List<AppEntry> {
        val newestFirst = recent.map { it.key }.withIndex().associate { it.value to it.index }
        val candidates = apps.sortedWith(compareBy<AppEntry>({ newestFirst[it.key] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
        return byUse(candidates, ::appKey, scores)
    }
}

internal object RadarRings {
    const val MAX_RINGS = 4

    /** Ring [ring] (0 is the innermost) holds at most this many results: 5, 9, 13, 17. */
    fun capacity(ring: Int): Int = 5 + 4 * ring

    /** The TalkBack names of the tiers, innermost first. These are L10n keys. */
    val tierNames: List<String> = listOf("Most used", "Often used", "Sometimes", "Rarely")

    /** Wave 20: while a query is typed the rings are match tiers, so the words say so (L10n keys, innermost first). */
    val queryTierNames: List<String> = listOf("Best match", "Close", "Related", "Other")

    fun tierName(ring: Int, query: Boolean = false): String =
        (if (query) queryTierNames else tierNames).let { it[ring.coerceIn(0, it.lastIndex)] }

    /** The full capacity of each ring (5, 9, 13, 17): the ring boundaries the typed order keeps stable against. */
    val capacities: List<Int> = List(MAX_RINGS) { capacity(it) }

    /**
     * Wave 20 (PAL-12, PAL-18): [ranked] is the typed result order, best match first. The ring of each result is its rank's
     * ring ([assign]); this only decides the order inside a ring so tiles keep their place from one keystroke to the next:
     * a result that was on screen before stays ahead of newcomers in its ring, in its old order. The best match is always
     * first (Enter opens it), and a result changes ring only when its rank crosses a ring boundary. Results no ring has room
     * for follow in rank order.
     */
    fun <T> stableOrder(previous: List<String>, ranked: List<T>, key: (T) -> String, limits: List<Int> = capacities): List<T> {
        if (ranked.size < 2) return ranked
        val rings = assign(ranked.size, limits)
        val before = previous.withIndex().associate { it.value to it.index }
        val out = ArrayList<T>(ranked.size)
        for (r in limits.indices) {
            val members = ranked.withIndex().filter { rings.getOrNull(it.index) == r }
            if (members.isEmpty()) continue
            val pinned = if (r == 0) members.first() else null
            val rest = members.filter { it !== pinned }.sortedWith(compareBy({ before[key(it.value)] ?: Int.MAX_VALUE }, { it.index }))
            if (pinned != null) out += pinned.value
            rest.forEach { out += it.value }
        }
        for (i in rings.size until ranked.size) out += ranked[i]
        return out
    }

    /** How many results each ring takes: its capacity, limited by [room] (how many of its slots fit on the screen). */
    fun limits(room: List<Int>): List<Int> = List(MAX_RINGS) { r -> minOf(capacity(r), room.getOrElse(r) { 0 }).coerceAtLeast(0) }

    /** The ring of each of [count] results in rank order; results that no ring has room for are not in the list (they are not drawn). */
    fun assign(count: Int, limits: List<Int>): List<Int> {
        val out = ArrayList<Int>()
        for (r in limits.indices) {
            var n = limits[r]
            while (n > 0 && out.size < count) { out += r; n-- }
        }
        return out
    }

    /**
     * Evenly spaced angles for [n] results on [ring], the first one straight above the centre. Alternate rings are turned
     * half a step so tiles of neighbouring rings do not line up. Angles are radians from the positive x axis (screen y points down).
     */
    fun evenAngles(n: Int, ring: Int): List<Double> {
        if (n <= 0) return emptyList()
        val turn = if (ring % 2 == 1) PI / max(n, 1) else 0.0
        return List(n) { k -> -PI / 2 + turn + k * 2 * PI / n }
    }
}
