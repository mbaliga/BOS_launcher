package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.LaunchEvent

/**
 * HOM-18: a chronological view of app opens and scheduled launches (WSP-11). Opens come from the
 * launch history; scheduled launches are the pending timers. The list reads newest first for the
 * past and shows what is coming before it, so one scroll covers "what I did" and "what is next".
 */
data class TimelineEntry(val timeMs: Long, val key: String, val label: String, val scheduled: Boolean, val id: String = "")

object Timeline {
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

    /** Day heading for grouping: whole days between [nowMs] and [thenMs] by calendar day offset in [dayMs] steps from [startOfToday]. */
    fun dayLabel(startOfTodayMs: Long, timeMs: Long): String {
        val days = Math.floorDiv(timeMs - startOfTodayMs, 86_400_000L).toInt()
        return when {
            days == 0 -> "Today"
            days == -1 -> "Yesterday"
            days == 1 -> "Tomorrow"
            days > 1 -> "In ${days} days"
            else -> "${-days} days ago"
        }
    }
}
