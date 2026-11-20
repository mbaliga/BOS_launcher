package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.HomeLayoutCodec
import com.bos.sphere.core.design.L10n

/**
 * Wave 41 (settingsPopups): what the Workspaces page says about each workspace, kept pure so a test reads it without a screen. A
 * workspace is a named home setup: its home pages (and what was removed from them), its dock, its widgets (left page and corners),
 * the Radar actions pinned for it and the apps the calm centre shows while it is active. App groups, sessions and schedules are shared by all.
 */
object WorkspaceSummary {
    /** One card of the page. [pages] is null when the workspace was never left, so no arrangement is saved for it yet and its pages are unknown. */
    data class Row(val id: String, val name: String, val active: Boolean, val pages: Int?, val dockItems: Int, val calm: String) {
        val summary: String get() = line(pages, dockItems)
    }

    /** The number of home pages in a saved arrangement, or null when there is none (or it cannot be read). */
    fun pageCount(arrangement: String?): Int? {
        if (arrangement == null) return null
        return runCatching {
            val raw = org.json.JSONObject(arrangement).getString("pages")
            HomeLayoutCodec.decode(raw).size.takeIf { it > 0 }
        }.getOrNull()
    }

    /** The dock items of a workspace: the arrangement's dock list when it has one, else the workspace's own pins. [livePins] stand in for the active one. */
    fun dockCount(w: Workspace, arrangement: String?, livePins: List<DockItemRef>?): Int =
        livePins?.size ?: HomeDockKeys.dockOf(arrangement)?.let { HomeDockKeys.decodeAll(it).size } ?: w.dockPins.size

    /** "3 pages · 5 dock items"; the pages part is left out when it is not known, the dock part says "no dock items" for zero. */
    fun line(pages: Int?, dockItems: Int): String {
        val p = pages?.let { if (it == 1) L10n.tr("1 page") else L10n.tr("%d pages", it) }
        val d = when (dockItems) { 0 -> L10n.tr("No dock items"); 1 -> L10n.tr("1 dock item"); else -> L10n.tr("%d dock items", dockItems) }
        return listOfNotNull(p, d).joinToString(" · ")
    }

    /** What the calm centre shows while [w] is active. */
    fun calmValue(w: Workspace): String = w.notificationApps?.let { if (it.size == 1) L10n.tr("1 chosen app") else L10n.tr("%d chosen apps", it.size) } ?: L10n.tr("All apps")

    /**
     * The cards, in the stored order. The active workspace is read from the live home ([liveArrangement], when the host can capture one),
     * because its stored arrangement is only refreshed when it is left.
     */
    fun rows(state: DockWorkspaceState, liveArrangement: String?): List<Row> = state.workspaces.map { w ->
        val active = w.id == state.activeId
        val arr = if (active && liveArrangement != null) liveArrangement else w.homeArrangement
        Row(w.id, w.name, active, pageCount(arr), dockCount(w, arr, if (active) w.dockPins else null), calmValue(w))
    }

    /** "Active: Home" for the large title of the page. */
    fun activeLine(state: DockWorkspaceState): String = L10n.tr("Active: %s", state.active.name)

    /** A name for a duplicate that is not already taken: "Home copy", "Home copy 2". */
    fun copyName(base: String, taken: Collection<String>): String {
        val first = L10n.tr("%s copy", base)
        if (first !in taken) return first
        var n = 2
        while (L10n.tr("%s copy %d", base, n) in taken) n++
        return L10n.tr("%s copy %d", base, n)
    }

    /** Whether a typed workspace name is already used (case-insensitive, ignoring surrounding spaces). */
    fun nameTaken(name: String, taken: Collection<String>, except: String? = null): Boolean =
        taken.any { it.trim().equals(name.trim(), ignoreCase = true) && !it.equals(except, ignoreCase = false) }
}
