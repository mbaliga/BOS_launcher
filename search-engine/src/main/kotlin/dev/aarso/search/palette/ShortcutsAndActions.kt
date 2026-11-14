package dev.aarso.search.palette

/** A static or dynamic app shortcut as reported by LauncherApps (PAL-03). */
data class ShortcutRecord(
    val packageName: String, val appLabel: String, val shortcutId: String, val shortLabel: String,
    val longLabel: String = "", val dynamic: Boolean = false, val userId: Int = 0,
)

/** Shortcuts appear under their app: they match on their own label, or when the query names the app. */
class ShortcutProvider(private val shortcuts: () -> List<ShortcutRecord>) : PaletteProvider {
    override val id = "shortcuts"
    override val scope = PaletteScope.Apps

    override suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult> {
        val nq = TextMatch.norm(q.text)
        if (nq.length < 2) return emptyList()
        val out = ArrayList<PaletteResult>()
        for (s in shortcuts()) {
            val own = maxOf(TextMatch.score(nq, TextMatch.Target(s.shortLabel)), TextMatch.score(nq, TextMatch.Target(s.longLabel)) * 0.9)
            val viaApp = TextMatch.score(nq, TextMatch.Target(s.appLabel)) * 0.45
            val sc = maxOf(own * 0.9, viaApp)
            if (sc <= 0) continue
            out += PaletteResult(
                id = "shortcut:${s.packageName}:${s.userId}:${s.shortcutId}", provider = id, scope = scope,
                title = s.shortLabel.ifBlank { s.longLabel }, subtitle = s.appLabel + if (s.dynamic) "" else " · shortcut",
                score = sc, actions = listOf(ResultAction(ResultActionId.Run)),
                payload = mapOf("package" to s.packageName, "shortcutId" to s.shortcutId, "user" to s.userId.toString()),
            )
        }
        return out.sortedByDescending { it.score }.take(limit)
    }
}

/** A launcher action (PAL-03). [command] equals a core-input `LauncherCommand` name so the app can dispatch it. */
data class LauncherActionDef(val command: String, val title: String, val keywords: List<String>)

object LauncherActions {
    val defaults: List<LauncherActionDef> = listOf(
        LauncherActionDef("OpenSettings", "Open launcher settings", listOf("settings", "ground control", "preferences", "customize")),
        LauncherActionDef("EditMode", "Edit home", listOf("edit", "rearrange", "layout", "widgets", "wallpaper")),
        LauncherActionDef("NextWorkspace", "Switch to next workspace", listOf("switch workspace", "next workspace", "workspace")),
        LauncherActionDef("PreviousWorkspace", "Switch to previous workspace", listOf("switch workspace", "previous workspace", "workspace")),
        LauncherActionDef("OpenDrawer", "Open all apps", listOf("drawer", "app list", "all apps")),
        LauncherActionDef("OpenLaunchpad", "Open Launchpad", listOf("notifications", "launchpad", "status")),
        LauncherActionDef("ShortcutSheet", "Show keyboard shortcuts", listOf("keys", "shortcuts", "hotkeys", "help")),
    )
}

class LauncherActionProvider(
    private val defs: () -> List<LauncherActionDef> = { LauncherActions.defaults },
    /** Named workspaces so "switch workspace work" finds the one called Work. */
    private val workspaces: () -> List<String> = { emptyList() },
) : PaletteProvider {
    override val id = "launcher-actions"
    override val scope = PaletteScope.Actions

    override suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult> {
        val nq = TextMatch.norm(q.text)
        if (nq.isEmpty()) return emptyList()
        val out = ArrayList<PaletteResult>()
        for (d in defs()) {
            var s = TextMatch.score(nq, TextMatch.Target(d.title))
            for (k in d.keywords) s = maxOf(s, TextMatch.score(nq, TextMatch.Target(k)) * 0.9)
            if (s > 0) out += PaletteResult("action:${d.command}", id, scope, d.title, "Launcher", s * 0.95, listOf(ResultAction(ResultActionId.Run)), mapOf("command" to d.command))
        }
        workspaces().forEachIndexed { i, name ->
            val s = maxOf(TextMatch.score(nq, TextMatch.Target(name)), TextMatch.score(nq, TextMatch.Target("switch workspace $name")) * 0.9)
            if (s > 0) out += PaletteResult("action:workspace:$i", id, scope, "Switch to workspace $name", "Launcher", s * 0.95, listOf(ResultAction(ResultActionId.Run)), mapOf("command" to "GoToWorkspace", "index" to i.toString()))
        }
        return out.sortedByDescending { it.score }.take(limit)
    }

    override suspend fun suggestions(limit: Int): List<PaletteResult> =
        defs().take(limit).mapIndexed { i, d -> PaletteResult("action:${d.command}", id, scope, d.title, "Launcher", 0.5 - i * 0.01, listOf(ResultAction(ResultActionId.Run)), mapOf("command" to d.command)) }
}
