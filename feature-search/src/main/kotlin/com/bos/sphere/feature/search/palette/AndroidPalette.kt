package com.bos.sphere.feature.search.palette

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.HitKind
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.data.SearchProvider
import com.bos.sphere.core.design.L10n
import dev.aarso.search.palette.AppRecord
import dev.aarso.search.palette.LauncherActionProvider
import dev.aarso.search.palette.CalculatorProvider
import dev.aarso.search.palette.FileProvider
import dev.aarso.search.palette.PaletteEngine
import dev.aarso.search.palette.PaletteProvider
import dev.aarso.search.palette.PaletteQuery
import dev.aarso.search.palette.PaletteResult
import dev.aarso.search.palette.PaletteScope
import dev.aarso.search.palette.PaletteStrings
import dev.aarso.search.palette.PrefixTable
import dev.aarso.search.palette.ProviderRegistry
import dev.aarso.search.palette.Ranker
import dev.aarso.search.palette.SettingsProvider
import dev.aarso.search.palette.ShortcutProvider
import dev.aarso.search.palette.ShortcutRecord
import dev.aarso.search.palette.UsageStore
import dev.aarso.search.palette.WebFallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Strings for rows the engine builds itself, through the app's localiser. */
fun paletteStrings() = PaletteStrings(
    searchWeb = { L10n.tr("Search the web for “%s”", it) },
    searchStore = { L10n.tr("Search the app store for “%s”", it) },
    enableContacts = L10n.tr("Allow contacts to search people"),
    enableFiles = L10n.tr("Allow files to search them"),
    calculatorHint = L10n.tr("Press Enter to copy"),
)

/** App-private persistence for what the palette learns (PAL-09) and the editable prefixes (PAL-13). Nothing leaves the device. */
class PalettePrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("palette", Context.MODE_PRIVATE)

    fun loadUsage(): UsageStore = UsageStore.decode(sp.getString("usage", null))
    fun saveUsage(s: UsageStore) = sp.edit().putString("usage", s.encode()).apply()
    fun resetUsage() = sp.edit().remove("usage").apply()

    /** The Radar scope-prefix mapping as stored by feature-grid's `ScopePrefixes.Mapping.encode` (PAL-13); null = defaults. */
    fun scopeMapping(): String? = sp.getString("scopeMapping", null)
    fun setScopeMapping(raw: String?) = sp.edit().apply { if (raw == null) remove("scopeMapping") else putString("scopeMapping", raw) }.apply()

    /** Packages of third-party search providers the user approved (PAL-17). */
    fun approvedSources(): Set<String> = sp.getStringSet("approvedSources", emptySet()).orEmpty().toSet()
    fun setApproved(pkg: String, on: Boolean) {
        val n = approvedSources().toMutableSet().also { if (on) it += pkg else it -= pkg }
        sp.edit().putStringSet("approvedSources", n).apply()
    }

    fun loadPrefixes(): PrefixTable = PrefixTable.deserialize(sp.getString("prefixes", null))
    fun savePrefixes(t: PrefixTable) = sp.edit().putString("prefixes", t.serialize()).apply()
    fun aliases(): Map<String, String> = sp.getString("aliases", "").orEmpty().lineSequence().mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 } }.associate { it[0] to it[1] }
    fun setAlias(packageName: String, alias: String) {
        val m = aliases().toMutableMap(); if (alias.isBlank()) m.remove(packageName) else m[packageName] = alias.trim()
        sp.edit().putString("aliases", m.entries.joinToString("\n") { "${it.key}\t${it.value.replace('\n', ' ')}" }).apply()
    }
}

/** Installed apps across every profile, from LauncherApps (the same source the home uses). */
object AndroidApps {
    fun records(context: Context, aliases: Map<String, String> = emptyMap()): List<AppRecord> {
        val la = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val um = context.getSystemService(UserManager::class.java)
        val self = android.os.Process.myUserHandle()
        return la.profiles.flatMap { user ->
            val serial = um.getSerialNumberForUser(user).toInt()
            la.getActivityList(null, user).map { a ->
                AppRecord("${a.applicationInfo.packageName}/$serial", a.label.toString(), a.applicationInfo.packageName, serial, work = user != self, alias = aliases[a.applicationInfo.packageName].orEmpty())
            }
        }.distinctBy { it.key }
    }
}

/** Static and dynamic shortcuts via LauncherApps.getShortcuts, cached so a keystroke never waits for the system (PAL-03). */
class ShortcutCache(private val context: Context, private val scope: CoroutineScope) {
    @Volatile private var cached: List<ShortcutRecord> = emptyList()
    @Volatile private var loadedAt = 0L
    @Volatile private var loading = false

    fun get(): List<ShortcutRecord> {
        if (!loading && System.currentTimeMillis() - loadedAt > TTL_MS) {
            loading = true
            scope.launch(Dispatchers.IO) {
                try { cached = load() } catch (_: SecurityException) { cached = emptyList() } catch (_: RuntimeException) { /* keep the old list */ } finally { loadedAt = System.currentTimeMillis(); loading = false }
            }
        }
        return cached
    }

    private fun load(): List<ShortcutRecord> {
        val la = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        // Only the default home app may read shortcuts; anything else gets nothing instead of a crash.
        if (!la.hasShortcutHostPermission()) return emptyList()
        val um = context.getSystemService(UserManager::class.java)
        val out = ArrayList<ShortcutRecord>()
        for (user in la.profiles) {
            val serial = um.getSerialNumberForUser(user).toInt()
            for (act in la.getActivityList(null, user)) {
                val pkg = act.applicationInfo.packageName
                val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
                la.getShortcuts(q, user)?.filter { it.isEnabled }?.forEach {
                    out += ShortcutRecord(pkg, act.label.toString(), it.id, it.shortLabel?.toString().orEmpty(), it.longLabel?.toString().orEmpty(), it.isDynamic, serial)
                }
            }
        }
        return out.distinctBy { Triple(it.packageName, it.userId, it.shortcutId) }
    }

    fun userFor(serial: Int): UserHandle? {
        val um = context.getSystemService(UserManager::class.java)
        return um.userProfiles.firstOrNull { um.getSerialNumberForUser(it).toInt() == serial }
    }

    private companion object { const val TTL_MS = 2 * 60_000L }
}

/** Wires the engine's providers to Android. One instance per process, created by the home host. */
class AndroidPalette(context: Context, private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) {
    private val app = context.applicationContext
    val prefs = PalettePrefs(app)
    val shortcuts = ShortcutCache(app, scope)
    val fileIndex: dev.aarso.search.palette.FileIndex get() = FileIndexHolder.get(app)
    private val strings = paletteStrings()

    /** Does the system have a handler for this settings screen? Entries without one are hidden (PAL-04). */
    private fun resolves(action: String): Boolean =
        app.packageManager.resolveActivity(Intent(action), 0) != null

    val settingsProvider: PaletteProvider = SettingsProvider { resolves(it.action) }
    val fileProvider: PaletteProvider = FileProvider({ FileIndexHolder.get(app) }, { FileIndexer.hasAccess(app) }, strings)
    val shortcutProvider: PaletteProvider = ShortcutProvider { shortcuts.get() }
    val calculatorProvider: PaletteProvider = CalculatorProvider(strings)
    val launcherActions: PaletteProvider = LauncherActionProvider()

    /** The non-app providers, as SearchProvider for the existing hub (apps and contacts keep their existing UI path). */
    fun hubProviders(): List<SearchProvider> =
        listOf(settingsProvider, calculatorProvider, shortcutProvider, launcherActions, fileProvider).map { adapt(it) }

    /** The full engine for callers that want results, scopes, ranking and streaming (the palette UI). */
    fun engine(webTemplate: () -> String = { "https://duckduckgo.com/?q=%s" }, apps: () -> List<AppRecord> = { AndroidApps.records(app, prefs.aliases()) }): PaletteEngine {
        val usage = prefs.loadUsage()
        val registry = ProviderRegistry(
            listOf(
                dev.aarso.search.palette.AppProvider(apps),
                calculatorProvider, settingsProvider, shortcutProvider, launcherActions, fileProvider,
            ),
        )
        return PaletteEngine(registry, prefs.loadPrefixes(), Ranker(usage), WebFallback(webTemplate, strings))
    }

    fun adapt(p: PaletteProvider): SearchProvider = SearchProvider { q, limit ->
        p.query(PaletteQuery(q, PaletteScope.Everything), limit).map { PaletteHits.toHit(app, it) }
    }
}

/** PaletteResult to the SearchHit the existing UI draws. Pure apart from building the intent URI. */
object PaletteHits {
    fun toHit(context: Context, r: PaletteResult): SearchHit {
        val category = when (r.scope) {
            PaletteScope.Files -> SearchCategory.Files
            PaletteScope.Contacts -> SearchCategory.Contacts
            PaletteScope.Web -> SearchCategory.Web
            PaletteScope.Apps -> SearchCategory.Actions // the hub only has Actions and Files; shortcuts surface as actions
            else -> SearchCategory.Actions
        }
        val intentUri = r.payload["intentAction"]?.let { Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).toUri(Intent.URI_INTENT_SCHEME) }
        val action = when (r.payload["command"]) {
            "OpenSettings" -> CornerActionId.LauncherSettings
            "OpenDrawer" -> CornerActionId.AllApps
            "OpenLaunchpad" -> CornerActionId.Notifications
            else -> null
        }
        return SearchHit(
            id = r.id, category = category, title = r.title, subtitle = r.subtitle, kind = if (category == SearchCategory.Files) HitKind.Document else HitKind.Action,
            uri = r.payload["uri"]?.takeIf { it.isNotEmpty() }, mime = r.payload["mime"]?.takeIf { it.isNotEmpty() },
            intentUri = intentUri, actionId = action, score = r.score.coerceIn(0.0, 1.0).toFloat(), copyText = r.copyText, extras = r.payload,
        )
    }
}

/** Runs the parts of a hit the existing UI does not know: copy, app shortcut, launcher command. */
object PaletteHitRunner {
    sealed interface Outcome {
        data object NotHandled : Outcome
        data object Done : Outcome
        /** Dispatch this core-input LauncherCommand name (and [index] for workspace jumps) in the host. */
        data class Command(val name: String, val index: Int? = null) : Outcome
    }

    fun run(context: Context, hit: SearchHit, palette: AndroidPalette? = null): Outcome {
        hit.copyText?.let {
            val cm = context.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("result", it))
            return Outcome.Done
        }
        val sid = hit.extras["shortcutId"]
        val pkg = hit.extras["package"]
        if (sid != null && pkg != null) {
            val la = context.getSystemService(LauncherApps::class.java)
            val user = palette?.shortcuts?.userFor(hit.extras["user"]?.toIntOrNull() ?: 0) ?: android.os.Process.myUserHandle()
            return try { la.startShortcut(pkg, sid, null, null, user); Outcome.Done } catch (_: RuntimeException) { Outcome.NotHandled }
        }
        hit.extras["command"]?.takeIf { hit.actionId == null }?.let { return Outcome.Command(it, hit.extras["index"]?.toIntOrNull()) }
        return Outcome.NotHandled
    }
}
