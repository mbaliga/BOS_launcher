package com.bos.sphere.ui

import com.bos.sphere.core.design.L10n
import com.bos.sphere.ui.viz.VizTexture
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Wave 28 (usageTimeline): the data shaping of the usage block above the Timeline. Pure, so it is tested on the JVM; the Compose side is
 * [UsageTimelineBlock]. The numbers come from the existing usage statistics source through [UsageWeekSource] (the app adapts
 * `UsageStatsSource`; nothing here reads the system).
 */
data class AppTime(val key: String, val ms: Long)

/** One day's foreground time per app; [dayStartMs] is that day's local midnight. */
data class DayTime(val dayStartMs: Long, val apps: List<AppTime>)

/** What the block needs from the system: the grant, and per-app foreground time in a window. Both may block; callers run them off the main thread. */
interface UsageWeekSource {
    fun granted(): Boolean
    fun samples(fromMs: Long, toMs: Long): List<AppTime>
}

/** The textures an app can have: stipple is reserved for Other. */
val APP_TEXTURES: List<VizTexture> = listOf(VizTexture.Solid, VizTexture.Hatch, VizTexture.Dots, VizTexture.Fade)
val OTHER_TEXTURE: VizTexture = VizTexture.Stipple

/** One column of the week. */
data class WeekColumn(val dayStartMs: Long, val date: LocalDate, val totalMs: Long, val initial: String, val dayName: String, val isToday: Boolean)

/** One part of a day's stack. [key] is the package, "" for Other. */
data class UsagePart(val key: String, val ms: Long, val texture: VizTexture) { val isOther: Boolean get() = key.isEmpty() }

/** One ranked row of a day's list (wave 29): [share] is [ms] over the longest row of the day, in 0..1 (the longest is 1), the length of its bar. */
data class UsageRow(val key: String, val ms: Long, val share: Float, val texture: VizTexture) { val isOther: Boolean get() = key.isEmpty() }

/** The shaped week: seven columns oldest first (today last) and the apps ranked over the week (they decide the textures). */
class UsageWeek(val days: List<DayTime>, val columns: List<WeekColumn>, val weekRank: List<String>) {
    val hasData: Boolean get() = columns.any { it.totalMs > 0 }
    fun parts(index: Int): List<UsagePart> = UsageShaping.parts(days.getOrNull(index), weekRank)
    fun rows(index: Int): List<UsageRow> = UsageShaping.rows(days.getOrNull(index), weekRank)
    fun total(index: Int): Long = columns.getOrNull(index)?.totalMs ?: 0L
}

/** The language the app shows its own text in (the in-app choice, else the device's): day names must agree with every other string of the block. */
internal fun appLocale(): Locale = Locale.forLanguageTag(L10n.effectiveLanguage())

object UsageShaping {
    const val DAYS = 7
    const val TOP = 4

    private fun ms(v: Long): Long = if (v < 0) 0L else v

    /** A day's apps merged by key, non-positive time dropped, biggest first; ties by key so the order never flips between refreshes. */
    fun merged(day: DayTime?): List<AppTime> = day?.apps.orEmpty().filter { it.ms > 0 }.groupBy { it.key }
        .map { (k, v) -> AppTime(k, v.sumOf { ms(it.ms) }) }.sortedWith(compareByDescending<AppTime> { it.ms }.thenBy { it.key })

    fun total(day: DayTime?): Long = merged(day).sumOf { it.ms }

    /** The apps of the whole week, biggest first (ties by key): rank in this list is what pins an app's texture. */
    fun weekRank(days: List<DayTime>): List<String> =
        days.flatMap { merged(it) }.groupBy { it.key }.map { (k, v) -> AppTime(k, v.sumOf { it.ms }) }
            .sortedWith(compareByDescending<AppTime> { it.ms }.thenBy { it.key }).map { it.key }

    /**
     * The texture of each of a day's [dayTop] apps. An app keeps the texture of its rank over the week when that is one of the four app
     * textures (so it looks the same whichever day is selected); an app that ranks lower over the week takes the first texture the others left
     * free, in the order of its rank that day. Other is always stipple.
     */
    fun textures(weekRank: List<String>, dayTop: List<String>): Map<String, VizTexture> {
        val out = LinkedHashMap<String, VizTexture>()
        val used = HashSet<VizTexture>()
        dayTop.forEach { k -> val r = weekRank.indexOf(k); if (r in APP_TEXTURES.indices) { out[k] = APP_TEXTURES[r]; used += APP_TEXTURES[r] } }
        val free = APP_TEXTURES.filter { it !in used }.iterator()
        dayTop.forEach { k -> if (k !in out && free.hasNext()) out[k] = free.next() }
        return out
    }

    /** The stack of one day: its top [TOP] apps by time, then everything else folded into Other (only when something is left). */
    fun parts(day: DayTime?, weekRank: List<String>): List<UsagePart> {
        val all = merged(day)
        val total = all.sumOf { it.ms }
        if (total <= 0L) return emptyList()
        val head = all.take(TOP)
        val rest = all.drop(TOP).sumOf { it.ms }
        val tex = textures(weekRank, head.map { it.key })
        val parts = head.map { UsagePart(it.key, it.ms, tex.getValue(it.key)) }
        return if (rest > 0) parts + UsagePart("", rest, OTHER_TEXTURE) else parts
    }

    /**
     * Wave 29 (usageSimplify): the ranked list of one day: the [TOP] apps then Other, each with its share of the longest row (the toolkit's `bars`
     * scale: the longest row is a full bar, so Other, being a sum, can be the full one). Empty for a day with no time.
     */
    fun rows(day: DayTime?, weekRank: List<String>): List<UsageRow> {
        val parts = parts(day, weekRank)
        val longest = parts.maxOfOrNull { it.ms } ?: return emptyList()
        return parts.map { UsageRow(it.key, it.ms, (it.ms.toDouble() / longest).toFloat().coerceIn(0f, 1f), it.texture) }
    }

    /** The week for [days] (oldest first; the last is today). */
    fun week(days: List<DayTime>, zone: ZoneId, today: LocalDate, locale: Locale = appLocale()): UsageWeek {
        val columns = days.map { d ->
            val date = Instant.ofEpochMilli(d.dayStartMs).atZone(zone).toLocalDate()
            WeekColumn(d.dayStartMs, date, total(d), date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).take(2).uppercase(locale), date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), date == today)
        }
        return UsageWeek(days, columns, weekRank(days))
    }
}

/** Duration text. [full] is "2 h 5 min"; [compact] is the figure over a column ("2.1 h", "35 min"). */
object UsageFormat {
    fun full(ms: Long): String {
        val min = (if (ms < 0) 0L else ms) / 60_000
        return when {
            min < 1 -> L10n.tr("<1 min")
            min < 60 -> L10n.tr("%d min", min)
            else -> L10n.tr("%d h %d min", min / 60, min % 60)
        }
    }

    fun compact(ms: Long): String {
        val min = (if (ms < 0) 0L else ms) / 60_000
        return when {
            min < 1 -> "0"
            min < 60 -> L10n.tr("%d min", min)
            else -> L10n.tr("%s h", String.format(appLocale(), "%.1f", min / 60.0))
        }
    }
}

/**
 * Loads and caches the week: past days never change so they are kept; today is re-read after [ttlMs]. Blocking; call it from an I/O dispatcher.
 */
class UsageWeekRepo(private val source: UsageWeekSource, private val ttlMs: Long = 60_000L, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    private class Entry(val at: Long, val apps: List<AppTime>)
    private val cache = ConcurrentHashMap<Long, Entry>()

    fun granted(): Boolean = source.granted()

    /** The [UsageShaping.DAYS] days ending today, oldest first. */
    fun load(now: Long): List<DayTime> {
        val z = zone()
        val today = Instant.ofEpochMilli(now).atZone(z).toLocalDate()
        val dates = (UsageShaping.DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val first = dates.first().atStartOfDay(z).toInstant().toEpochMilli()
        cache.keys.removeAll { it < first }
        return dates.map { d ->
            val s = d.atStartOfDay(z).toInstant().toEpochMilli()
            val e = d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
            val hit = cache[s]
            // A day read after it ended is final; anything else is trusted for [ttlMs].
            val apps = if (hit != null && (hit.at >= e || now - hit.at < ttlMs)) hit.apps
            else source.samples(s, minOf(e, now)).also { cache[s] = Entry(now, it) }
            DayTime(s, apps)
        }
    }

    fun invalidate() = cache.clear()
}
