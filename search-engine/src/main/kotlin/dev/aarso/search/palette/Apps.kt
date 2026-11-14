package dev.aarso.search.palette

/** One launchable app in any profile. [key] is unique across profiles (package plus user). */
data class AppRecord(
    val key: String,
    val label: String,
    val packageName: String,
    val userId: Int = 0,
    val work: Boolean = false,
    val canUninstall: Boolean = true,
    /** User-defined alias (PAL-02), may be blank. */
    val alias: String = "",
)

/** Match quality of apps for a typed query: label, alias, package, initials and one typo. 0..1, 0 = no match. */
class AppMatcher(apps: List<AppRecord>) {
    private class Entry(val app: AppRecord, val label: TextMatch.Target, val alias: TextMatch.Target?, val pkg: String, val pkgWords: List<String>)

    private val entries = apps.map { a ->
        val pkg = a.packageName.lowercase()
        Entry(a, TextMatch.Target(a.label), a.alias.takeIf { it.isNotBlank() }?.let { TextMatch.Target(it) }, pkg, pkg.split('.', '_'))
    }

    class Hit(val app: AppRecord, val score: Double)

    fun search(query: String, limit: Int = 20): List<Hit> {
        val q = TextMatch.norm(query)
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Hit>()
        for (e in entries) {
            var s = TextMatch.score(q, e.label)
            e.alias?.let { a ->
                // An alias is an explicit user choice: it outranks the same quality of label match.
                val sa = TextMatch.score(q, a)
                if (sa > 0) s = maxOf(s, minOf(1.0, sa + 0.05))
            }
            if (s < 0.6 && q.length >= 3) {
                val sp = when {
                    e.pkgWords.any { it.startsWith(q) } -> 0.45
                    e.pkg.contains(q) -> 0.35
                    else -> 0.0
                }
                s = maxOf(s, sp)
            }
            if (s > 0) out += Hit(e.app, s - e.app.label.length * 0.0005)
        }
        return out.sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.app.label.lowercase() }).take(limit)
    }
}

/** Apps provider (PAL-02, PAL-08). [actions] decides the per-result action list; the default offers every app action. */
class AppProvider(
    private val apps: () -> List<AppRecord>,
    private val actions: (AppRecord) -> List<ResultAction> = { DEFAULT_ACTIONS },
    private val recents: () -> List<AppRecord> = { emptyList() },
) : PaletteProvider {
    override val id = "apps"
    override val scope = PaletteScope.Apps

    // The index is rebuilt only when the inventory changes, not per keystroke.
    private var indexed: List<AppRecord>? = null
    private var matcher: AppMatcher? = null

    private fun matcher(): AppMatcher {
        val now = apps()
        if (indexed !== now && indexed != now) { matcher = AppMatcher(now); indexed = now }
        return matcher!!
    }

    private fun result(a: AppRecord, score: Double) = PaletteResult(
        id = "app:${a.key}", provider = id, scope = scope, title = a.label,
        subtitle = if (a.work) a.packageName + " · work" else a.packageName,
        score = score, actions = actions(a),
        payload = mapOf("package" to a.packageName, "user" to a.userId.toString(), "usage" to "app:${a.packageName}"),
    )

    override suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult> =
        matcher().search(q.text, limit).map { result(it.app, it.score) }

    override suspend fun suggestions(limit: Int): List<PaletteResult> =
        recents().take(limit).mapIndexed { i, a -> result(a, 1.0 - i * 0.01) }

    companion object {
        val DEFAULT_ACTIONS = listOf(
            ResultActionId.Open, ResultActionId.OpenOnDisplay, ResultActionId.OpenBeside, ResultActionId.AppInfo,
            ResultActionId.AddToDock, ResultActionId.AddToWorkspace, ResultActionId.Uninstall,
        ).map { ResultAction(it) }
    }
}
