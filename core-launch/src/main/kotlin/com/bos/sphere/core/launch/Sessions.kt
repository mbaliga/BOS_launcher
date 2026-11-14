package com.bos.sphere.core.launch

/** A display as the launcher knows it right now. */
data class DisplayRef(val id: Int, val name: String)

/** One saved app placement. The display name is kept because display ids change when a monitor is reconnected. */
data class SessionEntry(
    val target: LaunchTarget,
    val displayId: Int? = null,
    val displayName: String? = null,
    val bounds: Bounds? = null,
    val placement: Placement = Placement.Default,
)

/** A named set of app placements (WSP-09). It never holds in-app state. */
data class Session(val id: String, val name: String, val entries: List<SessionEntry>, val layout: GroupLayout = GroupLayout.Tile, val savedAt: Long = 0L)

data class RestorePlan(val plan: GroupPlan, val report: List<String>, val notice: String = SessionRestorer.APPS_ONLY_NOTICE)

object SessionRestorer {
    const val APPS_ONLY_NOTICE = "Restoring reopens the apps and where they were placed. What was open inside each app is not restored."

    /** Saves a group with a display target for each member ([displayFor] returns null for the current display). */
    fun capture(id: String, name: String, group: AppGroup, displayFor: (Int) -> DisplayRef?, savedAt: Long): Session =
        Session(
            id, name,
            group.members.mapIndexed { i, m ->
                val d = displayFor(i)
                SessionEntry(m.target, d?.id, d?.name, m.bounds, m.placement)
            },
            group.layout, savedAt,
        )

    /**
     * Opens every entry again. An entry whose display is not connected falls back to the current display and the plan reports it (WSP-10).
     * Displays are matched by name first, then by id.
     */
    fun plan(session: Session, connected: List<DisplayRef>, defaultDisplayId: Int = 0): RestorePlan {
        val missing = LinkedHashMap<String, Int>()
        val steps = session.entries.map { e ->
            var display: Int? = null
            if (e.displayId != null || e.displayName != null) {
                val match = connected.firstOrNull { e.displayName != null && it.name == e.displayName }
                    ?: connected.firstOrNull { e.displayName == null && it.id == e.displayId }
                if (match != null) display = match.id.takeIf { it != defaultDisplayId }
                else {
                    val label = e.displayName ?: "display ${e.displayId}"
                    missing.merge(label, 1, Int::plus)
                }
            }
            LaunchRequest(e.target, displayId = display, bounds = e.bounds, placement = e.placement)
        }
        val report = missing.map { (label, n) -> "$label is not connected, so ${if (n == 1) "1 app" else "$n apps"} opened on this display" }
        return RestorePlan(GroupPlan(GroupMode.Bounds, steps, emptyList()), report)
    }
}
