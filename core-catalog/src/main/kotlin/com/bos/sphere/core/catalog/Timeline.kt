package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.LaunchEvent

/**
 * HOM-18: a chronological view of app opens and scheduled launches (WSP-11). Opens come from the
 * launch history; scheduled launches are the pending timers. The list reads newest first for the
 * past and shows what is coming before it, so one scroll covers "what I did" and "what is next".
 */
data class TimelineEntry(val timeMs: Long, val key: String, val label: String, val scheduled: Boolean, val id: String = "")

object Timeline {
    /** The launch history keeps at most this many opens. */
    const val HISTORY_MAX = 400

    /** HOM-18: the timeline log keeps this many opens ([com.bos.sphere.core.data.TimelineLogCodec.MAX]). */
    const val LOG_MAX = com.bos.sphere.core.data.TimelineLogCodec.MAX

    /**
     * Upcoming scheduled launches (soonest first) followed by past opens (newest first), at most [limit] past ones.
     * [labelOf] resolves an app key to its label; keys that cannot be resolved (uninstalled) are dropped.
     */
    fun build(
        opens: List<LaunchEvent>,
        scheduled: List<TimelineEntry>,
        nowMs: Long,
        limit: Int = 60,
        labelOf: (String) -> String?,
    ): List<TimelineEntry> {
        val upcoming = scheduled.filter { it.timeMs >= nowMs }.sortedBy { it.timeMs }
        val past = opens.sortedByDescending { it.timeMillis }.mapNotNull { e ->
            labelOf(e.key)?.let { TimelineEntry(e.timeMillis, e.key, it, scheduled = false) }
        }.take(limit)
        return upcoming + past
    }

    /**
     * Day heading for grouping, as the English text. With a [zone] the calendar days are counted (a day of 23 or 25 hours at a
     * clock change still counts as one day); without one a day is 24 hours. [dayParts] gives the translatable form.
     */
    fun dayLabel(startOfTodayMs: Long, timeMs: Long, zone: java.util.TimeZone? = null): String =
        dayParts(startOfTodayMs, timeMs, zone).let { (key, n) -> if (n == null) key else key.format(n) }

    /** The heading as a translatable key plus the number it formats (null for Today, Yesterday and Tomorrow). */
    fun dayParts(startOfTodayMs: Long, timeMs: Long, zone: java.util.TimeZone? = null): Pair<String, Int?> {
        val days = if (zone == null) Math.floorDiv(timeMs - startOfTodayMs, 86_400_000L).toInt() else {
            val z = zone.toZoneId()
            val a = java.time.Instant.ofEpochMilli(startOfTodayMs).atZone(z).toLocalDate()
            val b = java.time.Instant.ofEpochMilli(timeMs).atZone(z).toLocalDate()
            java.time.temporal.ChronoUnit.DAYS.between(a, b).toInt()
        }
        return when {
            days == 0 -> "Today" to null
            days == -1 -> "Yesterday" to null
            days == 1 -> "Tomorrow" to null
            days > 1 -> "In %d days" to days
            else -> "%d days ago" to -days
        }
    }
}
