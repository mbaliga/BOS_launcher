package com.bos.sphere.core.catalog

import android.content.Context
import com.bos.sphere.core.data.LaunchEvent

/**
 * HOM-15, HOM-17, HOM-18: the home panels as things that live on the home surface. A panel is
 * placed on home as a link item whose target is a [HomePanelLinks] URI; tapping it opens the panel
 * at its tab instead of firing an intent. The settings that shape the panels are in [HomePanelPrefs].
 */
enum class PanelTab(val title: String, val uriName: String) {
    Files("Files", "files"), Usage("Usage", "usage"), Timeline("Timeline", "timeline");
}

object HomePanelLinks {
    const val SCHEME = "liftoff-panel"

    fun uri(tab: PanelTab) = "$SCHEME://${tab.uriName}"

    fun tabOf(target: String): PanelTab? {
        if (!target.startsWith("$SCHEME://")) return null
        val name = target.removePrefix("$SCHEME://")
        return PanelTab.entries.firstOrNull { it.uriName == name }
    }

    /** Panel tiles on [state], in the order their keys appear on its pages. */
    fun placed(state: HomeState): List<PanelTab> =
        state.pages.flatMap { it.keys }.mapNotNull { k -> state.items[k]?.let { tabOf(it.target) } }
}

/** HOM-17: the period the usage panel covers. */
enum class UsageRange(val label: String, val days: Int) {
    Today("Today", 1), Week("Last 7 days", 7);

    /** First millisecond of the range: midnight [days] - 1 days before [startOfTodayMs]. */
    fun startMs(startOfTodayMs: Long): Long = startOfTodayMs - (days - 1) * 86_400_000L
}

/** Panel settings. Kept in the existing home state preference file under its own keys. */
class HomePanelPrefs(context: Context) {
    private val sp = com.bos.sphere.core.data.TolerantPrefs.of(context.applicationContext, "home_state")

    var usageRange: UsageRange
        get() = UsageRange.entries.firstOrNull { it.name == sp.getString("panel.usage", null) } ?: UsageRange.Today
        set(v) { sp.edit().putString("panel.usage", v.name).apply() }

    var filesPerFolder: Int
        get() = sp.getInt("panel.files.count", 20).coerceIn(5, 50)
        set(v) { sp.edit().putInt("panel.files.count", v.coerceIn(5, 50)).apply() }
}

object FilesView {
    /** Files of [selectedSource] only, or all of them when it is null. */
    fun filter(files: List<RecentFile>, selectedSource: String?): List<RecentFile> =
        if (selectedSource == null) files else files.filter { it.source == selectedSource }
}

/** One row of the timeline list: the entry and whether it starts a new day group. */
data class TimelineRow(
    val entry: TimelineEntry, val day: String, val header: Boolean,
    /** The heading as a translatable key and the number it formats, so a count of days is translated too. */
    val dayKey: String = day, val dayArg: Int? = null,
)

object TimelineView {
    fun rows(entries: List<TimelineEntry>, startOfTodayMs: Long, zone: java.util.TimeZone? = null): List<TimelineRow> =
        entries.mapIndexed { i, e ->
            val (key, n) = Timeline.dayParts(startOfTodayMs, e.timeMs, zone)
            val day = Timeline.dayLabel(startOfTodayMs, e.timeMs, zone)
            TimelineRow(e, day, i == 0 || day != Timeline.dayLabel(startOfTodayMs, entries[i - 1].timeMs, zone), key, n)
        }

    /** Wave 29 (usageSimplify): the one behaviour of the Timeline, with no filter: the last [OPENS] opens, scheduled launches included. */
    const val OPENS = 100

    fun build(history: List<LaunchEvent>, scheduled: List<TimelineEntry>, nowMs: Long, labelOf: (String) -> String?) =
        Timeline.build(history, scheduled, nowMs, OPENS, labelOf)
}
