package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.HitKind
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.data.SearchProvider
import com.bos.sphere.core.design.L10n

/** Launcher actions a workspace can pin to Radar's empty state (WSP-07). [actionId] runs in the launcher; [run] goes through [RunActivity]. */
object PinnableActions {
    data class Def(val id: String, val title: String, val actionId: CornerActionId? = null, val run: RunTarget? = null)

    val all: List<Def> = listOf(
        Def("OpenSettings", "Open launcher settings", actionId = CornerActionId.LauncherSettings),
        Def("OpenDrawer", "Open all apps", actionId = CornerActionId.AllApps),
        Def("OpenLaunchpad", "Open Launchpad", actionId = CornerActionId.Notifications),
        Def("NextWorkspace", "Switch to next workspace", run = RunTarget(RunKind.Workspace, WorkspaceRefs.NEXT)),
        Def("PreviousWorkspace", "Switch to previous workspace", run = RunTarget(RunKind.Workspace, WorkspaceRefs.PREVIOUS)),
    )

    fun choices(): List<Pair<String, String>> = all.map { it.id to it.title }
}

/** Special workspace ids the automation and palette routes understand (WSP-12). */
object WorkspaceRefs {
    const val NEXT = "@next"
    const val PREVIOUS = "@previous"
}

/**
 * Radar results from the dock and workspace stream (WSP-05, WSP-07, WSP-09): groups, "switch to workspace", sessions, and the
 * actions pinned to the active workspace. With an empty query only the pinned actions show, which is Radar's empty state.
 * Every hit runs through [intentFor], the explicit [RunActivity] intent for its target, so nothing here needs the home to run it.
 */
class DockSearchProvider(
    private val state: () -> DockWorkspaceState,
    private val intentFor: (RunTarget) -> String,
    private val extra: (String) -> List<SearchHit> = { emptyList() },
) : SearchProvider {
    override suspend fun search(query: String, limit: Int): List<SearchHit> = hits(query).take(limit)

    fun hits(query: String): List<SearchHit> {
        val s = state()
        val q = query.trim()
        val out = ArrayList<SearchHit>()
        val pinned = PinnableActions.all.filter { it.id in s.active.paletteActions }
        for (d in pinned) {
            if (q.isNotEmpty() && !PaletteMatch.matches(q, d.title)) continue
            out += SearchHit(
                id = "pin:${d.id}", category = SearchCategory.Actions, title = L10n.tr(d.title), subtitle = L10n.tr("Pinned in %s", s.active.name),
                kind = HitKind.Action, actionId = d.actionId, intentUri = d.run?.let(intentFor), score = 0.9f,
            )
        }
        if (q.isEmpty()) return out
        for (g in s.groups) if (PaletteMatch.matches(q, g.name)) out += SearchHit(
            id = "group:${g.id}", category = SearchCategory.Actions, title = g.name, subtitle = L10n.tr("App group, %d apps", g.members.size),
            kind = HitKind.Action, intentUri = intentFor(RunTarget(RunKind.Group, g.id)), score = 0.8f,
        )
        for (w in s.workspaces) if (w.id != s.activeId && (PaletteMatch.matches(q, w.name) || PaletteMatch.matches(q, "workspace"))) out += SearchHit(
            id = "workspace:${w.id}", category = SearchCategory.Actions, title = L10n.tr("Switch to %s", w.name), subtitle = L10n.tr("Workspace"),
            kind = HitKind.Action, intentUri = intentFor(RunTarget(RunKind.Workspace, w.id)), score = 0.75f,
        )
        for (x in s.sessions) if (PaletteMatch.matches(q, x.name)) out += SearchHit(
            id = "session:${x.id}", category = SearchCategory.Actions, title = L10n.tr("Restore %s", x.name), subtitle = L10n.tr("Session, reopens apps only"),
            kind = HitKind.Action, intentUri = intentFor(RunTarget(RunKind.Session, x.id)), score = 0.7f,
        )
        out += extra(q)
        return out
    }
}
