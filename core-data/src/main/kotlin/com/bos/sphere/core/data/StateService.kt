package com.bos.sphere.core.data

import android.appwidget.AppWidgetManager
import android.content.Context
import org.json.JSONObject
import java.io.File

/** What an import did, for the confirmation line in the UI. */
data class ImportReport(
    val fromVersion: Int,
    val fromNewer: Boolean,
    /** Layout keys for apps not installed here: kept as placeholders that resolve when the app is installed. */
    val placeholders: Set<String>,
    /** Widgets that were not carried over and re-bind later. */
    val pendingWidgets: List<PendingWidget>,
    val sections: Set<String>,
    val snapshotId: String?,
)

/**
 * One entry point for the state store: export, import, snapshots, restore, the build-31 bridge and the safe-mode hooks.
 * The live data stays in the existing SharedPreferences stores ([LauncherStores]); this class reads and writes them
 * through [StateApplier] and keeps snapshots and the extras file next to them.
 */
class StateService private constructor(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "state")
    val snapshots = SnapshotStore(File(root, "snapshots"))
    val extras = StateExtrasStore(File(root, "extras.json"))

    /** Palette aliases and the dock stream's workspaces, sessions and groups are read from and written to their own storage (CFG-01). */
    private val live = LiveExtras(
        extras,
        listOf(
            PaletteAliasSource({ app.getSharedPreferences("palette", Context.MODE_PRIVATE).getString("aliases", null) }) {
                app.getSharedPreferences("palette", Context.MODE_PRIVATE).edit().putString("aliases", it).apply()
            },
            WorkspaceFileSource(File(app.filesDir, "liftoff_workspaces.json")),
            PrefFilesSource({ app.getSharedPreferences(it, Context.MODE_PRIVATE) }),
        ),
    )

    private val applier by lazy {
        LaunchpadSettings.init(app)
        val s = LauncherStores.get(app)
        StateApplier(s.prefs, s.layout, s.shortcuts, LaunchpadSink.Live, live::read, live::write,
            hiddenApps = object : HiddenSink {
                private val sp get() = app.getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
                override fun get(): Set<String> = sp.getStringSet("keys", emptySet())!!.toSet()
                override fun set(keys: Set<String>) { sp.edit().putStringSet("keys", keys).apply() }
            })
    }

    fun currentParts(): StateParts = applier.current()

    fun exportDocument(): StateDocument = StateCodec.export(currentParts(), System.currentTimeMillis(), appTag(), ::widgetProvider)

    fun exportText(): String = exportDocument().encode()

    /** CFG-11: just the settings, as a document of its own. */
    fun exportSettingsText(): String = SettingsCodec.encode(currentParts().prefs, currentParts().extras.unknownPrefs).toString(2)

    /** Takes a snapshot of the live state (CFG-04). Null when nothing changed since the last one. */
    fun snapshot(reason: String, force: Boolean = false): SnapshotStore.Info? =
        runCatching { snapshots.take(reason, exportText(), force) }.getOrNull()

    /**
     * Parses [text], snapshots the live state, then applies it (CFG-02, CFG-04). Throws [StateFormatException] for a
     * file that is not a state file, before anything is touched.
     */
    fun import(text: String, installedKeys: Set<String>): ImportReport {
        val dec = StateCodec.decode(StateDocument.parse(text))
        val snap = snapshot("before import", force = true)
        applier.apply(dec.parts, dec.present)
        return ImportReport(
            fromVersion = dec.migration.fromVersion,
            fromNewer = dec.newer,
            placeholders = StateCodec.unresolvedApps(dec.parts.layout, installedKeys).takeIf { StateSchema.K_LAYOUT in dec.present } ?: emptySet(),
            pendingWidgets = dec.parts.extras.widgets,
            sections = dec.present - META_KEYS,
            snapshotId = snap?.id,
        )
    }

    /**
     * CFG-12: replaces the home layout with one read from another launcher ([LauncherImport]). Snapshots first; hidden apps,
     * corners, settings and everything else stay as they are.
     */
    fun importLauncherLayout(layout: HomeLayout): String? {
        val snap = snapshot("before launcher import", force = true)
        val cur = currentParts()
        applier.apply(cur.copy(layout = layout, extras = cur.extras.copy(nested = org.json.JSONObject())), setOf(StateSchema.K_LAYOUT))
        return snap?.id
    }

    /** One-tap restore (CFG-04): snapshots the current state first so a restore can itself be undone. */
    fun restore(id: String, installedKeys: Set<String> = emptySet()): ImportReport {
        val text = snapshots.read(id) ?: throw StateFormatException("That snapshot no longer exists")
        val dec = StateCodec.decode(StateDocument.parse(text))
        val snap = snapshot("before restore", force = true)
        applier.apply(dec.parts, dec.present)
        return ImportReport(dec.migration.fromVersion, dec.newer, emptySet(), dec.parts.extras.widgets, dec.present, snap?.id)
    }

    /** Called before a destructive edit (delete a page, remove a group of items): snapshot first (CFG-04). Rate limited. */
    fun beforeDestructive(reason: String) = takeRateLimited(reason, exportText())

    internal fun takeRateLimited(reason: String, text: String) {
        val last = snapshots.latest()
        if (last != null && System.currentTimeMillis() - last.takenAt < DESTRUCTIVE_MIN_GAP_MS) return
        snapshots.take(reason, text)
    }

    /** The component of the widget provider bound to [appWidgetId], so an export can say what to re-add (CFG-02). Public API only. */
    private fun widgetProvider(appWidgetId: Int): String? =
        runCatching { AppWidgetManager.getInstance(app).getAppWidgetInfo(appWidgetId)?.provider?.flattenToString() }.getOrNull()

    /** Widgets an import could not carry and that are still waiting to be re-added (CFG-02). */
    fun pendingWidgets(): List<PendingWidget> = extras.read().widgets

    /** Called once a pending widget has been re-bound (or dismissed). */
    fun clearPendingWidget(slot: String) {
        val cur = extras.read()
        extras.write(cur.copy(widgets = cur.widgets.filterNot { it.slot == slot }))
    }

    /** The keyboard shortcut overrides (`ShortcutRegistry.export()` text) that are part of the setup (CFG-01). */
    fun keyboardOverrides(): String = extras.read().keyboardShortcuts.orEmpty()

    fun saveKeyboardOverrides(raw: String) {
        val cur = extras.read()
        if ((cur.keyboardShortcuts.orEmpty()) != raw) extras.write(cur.copy(keyboardShortcuts = raw.takeIf { it.isNotEmpty() }))
    }

    private fun appTag() = "liftoff"

    companion object {
        private const val DESTRUCTIVE_MIN_GAP_MS = 5_000L
        private const val META = "state_meta"
        private val META_KEYS = setOf(StateSchema.K_FORMAT, StateSchema.K_VERSION, StateSchema.K_EXPORTED_AT, StateSchema.K_APP)

        @Volatile private var instance: StateService? = null
        fun get(context: Context): StateService =
            instance ?: synchronized(this) { instance ?: StateService(context).also { instance = it } }

        /** After the launcher has been up for the stability window: the state is known good, so snapshot it (safe mode restores this). */
        fun onStable(context: Context) { io.execute { runCatching { get(context).snapshot("stable start") } } }

        /**
         * Called on the UI thread before a destructive edit. The state is read here, before the edit runs, and the file
         * work (write, hash, prune) happens on a background thread so the main thread does no disk I/O.
         */
        fun beforeDestructive(context: Context, reason: String) {
            runCatching {
                val svc = get(context)
                val text = svc.exportText()
                io.execute { runCatching { svc.takeRateLimited(reason, text) } }
            }
        }

        private val io = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "state-io").apply { isDaemon = true } }

        /**
         * Build-31 bridge. Runs once, before any store is touched: if the old preference files hold a setup, it is
         * written out as a snapshot (a state document), then the schema marker is set so it never runs again. The
         * live stores are not modified, and nothing is ever written back to the old files (one-way).
         */
        fun bridgeBuild31(context: Context): Boolean {
            val meta = context.getSharedPreferences(META, Context.MODE_PRIVATE)
            if (meta.getInt("schema", 0) >= StateSchema.CURRENT) return false
            val files = Build31Bridge.FILES.associateWith { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap() }
            var bridged = false
            if (files.values.any { it.isNotEmpty() }) {
                val doc = StateCodec.export(Build31Bridge.fromPrefFiles(files), System.currentTimeMillis(), "liftoff build-31")
                val dir = File(File(context.applicationContext.filesDir, "state"), "snapshots")
                bridged = SnapshotStore(dir).take("build 31 prefs", doc.encode(), force = true) != null
            }
            meta.edit().putInt("schema", StateSchema.CURRENT).apply()
            return bridged
        }
    }
}
