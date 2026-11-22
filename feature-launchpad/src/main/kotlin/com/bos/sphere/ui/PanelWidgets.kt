package com.bos.sphere.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Wave 52 (panelWidgets, owner: "I would also like to be able to add widgets to it (as a fifth section) where I can add widgets instead of
 * on the right side, if I so choose"): the pure model of the Widgets section of the Comms panel. Nothing here touches Android, so the section
 * order, the list operations and the persisted text are tested on the JVM. The widgets themselves are hosted by the app (feature-grid's
 * [com.bos.sphere.feature.grid.LauncherWidgetHost], the one the home uses); this module only keeps which widget ids the section owns.
 */
enum class WidgetsSlot(val id: String, val label: String) {
    /** The owner's request: the fifth section, after Timeline. The default. */
    Last("last", "Last"),
    First("first", "First"),
    BeforeFiles("beforeFiles", "Before Files");

    companion object { fun fromId(id: String?): WidgetsSlot = entries.firstOrNull { it.id == id } ?: Last }
}

/** Which sections the panel's tab row has and in which order. */
object PanelSections {
    /** The four sections the panel always had, in their order (the Read history has no tab; it is a row of the Comms settings). */
    val BASE: List<HubView> = listOf(HubView.All, HubView.Unread, HubView.Files, HubView.Timeline)

    /** The tab order: [BASE] and, when [widgetsOn], Widgets at [slot]. With the setting off the row is exactly the old four, so nothing changes until the owner chooses. */
    fun order(widgetsOn: Boolean, slot: WidgetsSlot): List<HubView> {
        if (!widgetsOn) return BASE
        return when (slot) {
            WidgetsSlot.Last -> BASE + HubView.Widgets
            WidgetsSlot.First -> listOf(HubView.Widgets) + BASE
            WidgetsSlot.BeforeFiles -> BASE.flatMap { if (it == HubView.Files) listOf(HubView.Widgets, it) else listOf(it) }
        }
    }
}

/** One widget the section hosts: the host's id and the label shown when the provider cannot be loaded any more. */
data class PanelWidget(val appWidgetId: Int, val label: String)

/** The ordered list of the Widgets section and the operations on it; every operation returns a new list (the caller persists it). */
object PanelWidgetList {
    fun add(list: List<PanelWidget>, w: PanelWidget): List<PanelWidget> = if (list.any { it.appWidgetId == w.appWidgetId }) list else list + w
    fun remove(list: List<PanelWidget>, id: Int): List<PanelWidget> = list.filter { it.appWidgetId != id }

    /** Moves the widget [id] by [delta] places (negative = up); at an end it stays. */
    fun move(list: List<PanelWidget>, id: Int, delta: Int): List<PanelWidget> {
        val from = list.indexOfFirst { it.appWidgetId == id }
        if (from < 0) return list
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return list
        return list.toMutableList().also { val w = it.removeAt(from); it.add(to, w) }
    }
    fun canMove(list: List<PanelWidget>, id: Int, delta: Int): Boolean { val i = list.indexOfFirst { it.appWidgetId == id }; return i >= 0 && (i + delta) in list.indices }

    /** `id,label` lines, the label percent-escaped so a label with a newline or comma cannot break the list. A damaged line is skipped. */
    fun encode(list: List<PanelWidget>): String = list.joinToString("\n") { "${it.appWidgetId},${esc(it.label)}" }
    fun decode(text: String?): List<PanelWidget> =
        text.orEmpty().lines().mapNotNull { line ->
            val i = line.indexOf(',')
            val id = if (i > 0) line.substring(0, i).toIntOrNull() else null
            if (id == null || id < 0) null else PanelWidget(id, unesc(line.substring(i + 1)))
        }.distinctBy { it.appWidgetId }

    private fun esc(s: String) = buildString { s.forEach { c -> if (c == '%' || c == '\n' || c == '\r' || c == ',') append('%').append(c.code.toString(16).padStart(2, '0')) else append(c) } }
    private fun unesc(s: String): String {
        val b = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            val code = if (c == '%' && i + 3 <= s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (code != null) { b.append(code.toChar()); i += 3 } else { b.append(c); i++ }
        }
        return b.toString()
    }
}

/** Card height rule: the provider's minimum height, widened to what a resizable provider allows, between 72 and 360 dp. Width is always the full column. */
object PanelWidgetSizing {
    const val MIN_DP = 72f
    const val MAX_DP = 360f
    fun heightDp(minHeightDp: Float, minResizeHeightDp: Float, maxResizeHeightDp: Float): Float {
        var h = maxOf(minHeightDp, minResizeHeightDp, MIN_DP)
        if (maxResizeHeightDp > 0f) h = minOf(h, maxOf(maxResizeHeightDp, MIN_DP))
        return h.coerceIn(MIN_DP, MAX_DP)
    }
}

/** The section's live list, persisted through [HubPrefs] (pref file `launchpad_hub`, never exported: widget ids belong to this device's host). */
class PanelWidgetStore(private val prefs: HubPrefs) {
    var widgets by mutableStateOf(prefs.widgetList)
        private set
    private fun set(l: List<PanelWidget>) { if (l != widgets) { widgets = l; prefs.widgetList = l } }
    fun add(w: PanelWidget) = set(PanelWidgetList.add(widgets, w))
    fun remove(id: Int) = set(PanelWidgetList.remove(widgets, id))
    fun move(id: Int, delta: Int) = set(PanelWidgetList.move(widgets, id, delta))
    fun canMove(id: Int, delta: Int) = PanelWidgetList.canMove(widgets, id, delta)
}
