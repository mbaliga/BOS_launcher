package com.bos.sphere

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.catalog.HomeEdit
import com.bos.sphere.core.catalog.HomeExtrasStore
import com.bos.sphere.core.catalog.HomeRekey
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.HiddenAppsView
import com.bos.sphere.core.catalog.IconOverrideStore
import com.bos.sphere.core.catalog.IconPackLoader
import com.bos.sphere.core.catalog.IconPackMap
import com.bos.sphere.core.catalog.IconSpec
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.RestoreAction
import com.bos.sphere.core.catalog.WidgetSizing
import com.bos.sphere.core.catalog.WidgetStartup
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HiddenAppsStore
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.HomeExtras
import com.bos.sphere.feature.grid.HomeItemMenu
import com.bos.sphere.feature.grid.HomeItemsSheet
import com.bos.sphere.feature.grid.FolderSheet
import com.bos.sphere.feature.grid.HiddenAppsDialog
import com.bos.sphere.feature.grid.HostedWidget
import com.bos.sphere.feature.grid.IconOverrideDialog
import com.bos.sphere.feature.grid.IconPackDialog
import com.bos.sphere.feature.grid.IconSources
import com.bos.sphere.feature.grid.LauncherWidgetHost
import com.bos.sphere.feature.grid.LinkDialog
import com.bos.sphere.feature.grid.PinnableShortcut
import com.bos.sphere.feature.grid.ScrollExtrasBand
import com.bos.sphere.feature.grid.ScrollExtrasLayout
import com.bos.sphere.feature.grid.ShortcutPickerDialog
import com.bos.sphere.feature.grid.WidgetPlaceholder
import com.bos.sphere.feature.grid.bandKeys
import com.bos.sphere.feature.grid.rememberWidgetPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HOM-01 to HOM-08 in the live app: one host that owns the shared home arrangement beyond app
 * keys (folders, shortcuts, links, widget tiles), turns it into the [HomeExtras] every home
 * surface renders, runs the edits (each one writes the extras store and the page list), restores
 * widgets at startup, and holds the icon, folder, link, shortcut and hidden-app dialogs.
 */
class HomeItems internal constructor(
    private val context: Context,
    private val store: HomeExtrasStore,
    private val gridDims: () -> Pair<Int, Int>,
    private val pagesNow: () -> List<HomePage>,
    private val savePages: (List<HomePage>) -> Unit,
    private val capacityOf: (PageLayout) -> Int,
    private val currentPage: () -> Int,
    private val host: LauncherWidgetHost,
    private val allApps: () -> List<AppEntry>,
    private val appsByKey: () -> Map<String, AppEntry>,
    private val launchApp: (AppEntry) -> Unit,
) {
    /** The latest extras (set on every composition by [rememberHomeItems]). */
    var extrasState: HomeState = HomeState()
    internal var folderOpen by mutableStateOf(false)

    // ---- state read by the composables
    internal var sheetOpen by mutableStateOf(false)
    internal var openFolder by mutableStateOf<String?>(null)
    internal var itemMenu by mutableStateOf<String?>(null)
    internal var resizing by mutableStateOf<String?>(null)
    internal var linkOpen by mutableStateOf(false)
    internal var shortcutOpen by mutableStateOf(false)
    internal var packOpen by mutableStateOf(false)
    internal var hiddenOpen by mutableStateOf(false)
    internal var iconFor by mutableStateOf<AppEntry?>(null)
    internal var restoreRev by mutableIntStateOf(0)

    fun openSheet() { sheetOpen = true }

    /** Runs [f] on the shared arrangement (pages from the live list, extras from the store) and writes both back. */
    fun edit(f: (HomeState) -> HomeState) {
        val cur = store.state.value.copy(pages = pagesNow())
        val next = f(cur)
        store.set(next)
        if (next.pages != cur.pages) savePages(next.pages)
    }

    private fun newId() = "f" + System.currentTimeMillis().toString(36)
    private fun page() = currentPage().coerceIn(0, (pagesNow().size - 1).coerceAtLeast(0))

    // ---- adding things
    fun addWidget(id: Int, label: String) {
        val info = host.providerInfo(id) ?: return
        val d = context.resources.displayMetrics.density
        val span = WidgetSizing.spanFor((info.minWidth / d).toInt(), (info.minHeight / d).toInt(), 80f)
        edit { HomeEdit.add(it, ItemDef(ItemKeys.widget(id), ItemKind.Widget, label, info.provider.flattenToString()), page(), capacityOf, span) }
    }

    fun addFolderWith(keys: List<String>, name: String = L10n.tr("Folder")) {
        if (keys.isEmpty()) return
        val id = newId()
        edit { HomeEdit.createFolder(it, keys, id, name, capacityOf) }
    }

    fun addLink(uri: String, label: String, isFile: Boolean) {
        val def = ItemDef(ItemKeys.link(uri), ItemKind.Link, label, uri, isFile)
        edit { HomeEdit.add(it, def, page(), capacityOf) }
    }

    fun addShortcut(app: AppEntry, s: PinnableShortcut) {
        val def = ItemDef(ItemKeys.shortcut(app.packageName, s.id, app.user.hashCode()), ItemKind.Shortcut, s.label, s.id)
        edit { HomeEdit.add(it, def, page(), capacityOf) }
    }

    // ---- shortcuts
    private val launcherApps get() = context.getSystemService(LauncherApps::class.java)

    private fun userOf(hash: Int) = allApps().firstOrNull { it.user.hashCode() == hash }?.user ?: Process.myUserHandle()

    private fun shortcutInfo(def: ItemDef): android.content.pm.ShortcutInfo? = runCatching {
        val body = def.key.removePrefix(ItemKeys.SHORTCUT)
        val pkg = body.substringBefore('/')
        val uid = body.substringAfterLast('#').toIntOrNull() ?: 0
        val q = LauncherApps.ShortcutQuery().setPackage(pkg).setShortcutIds(listOf(def.target))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        launcherApps.getShortcuts(q, userOf(uid))?.firstOrNull()
    }.getOrNull()

    fun shortcutsOf(app: AppEntry): List<PinnableShortcut> = runCatching {
        val q = LauncherApps.ShortcutQuery().setPackage(app.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        launcherApps.getShortcuts(q, app.user).orEmpty().map { PinnableShortcut(it.id, (it.shortLabel ?: it.id).toString()) }
    }.getOrDefault(emptyList())

    private fun shortcutIcon(def: ItemDef): Drawable? =
        if (def.kind != ItemKind.Shortcut) null
        else runCatching { shortcutInfo(def)?.let { launcherApps.getShortcutIconDrawable(it, 0) } }.getOrNull()

    // ---- taps and menus
    private fun tap(key: String) {
        when (ItemKeys.kind(key)) {
            ItemKind.Folder -> openFolder = ItemKeys.folderId(key)
            ItemKind.Shortcut -> extrasState.items[key]?.let { def ->
                runCatching {
                    val body = key.removePrefix(ItemKeys.SHORTCUT)
                    launcherApps.startShortcut(body.substringBefore('/'), def.target, null, null, userOf(body.substringAfterLast('#').toIntOrNull() ?: 0))
                }
            }
            ItemKind.Link -> extrasState.items[key]?.let { def ->
                // The content resolver lookup is a provider call: off the main thread.
                kotlin.concurrent.thread(name = "home-link-open") {
                    runCatching {
                        val uri = Uri.parse(def.target)
                        val i = Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (def.isFile) i.setDataAndType(uri, context.contentResolver.getType(uri)) else i.data = uri
                        context.startActivity(i)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun removeKey(key: String) {
        ItemKeys.widgetId(key)?.let { host.deleteWidget(it) }
        edit { HomeEdit.remove(it, key, capacityOf) }
    }

    private fun entry(label: String, run: () -> Unit): Pair<String, () -> Unit> = label to run

    private fun menuEntries(key: String): List<Pair<String, () -> Unit>> {
        val own: List<Pair<String, () -> Unit>> = when (ItemKeys.kind(key)) {
            ItemKind.Folder -> listOf(
                entry(L10n.tr("Open")) { tap(key) },
                entry(L10n.tr("Dissolve folder")) { ItemKeys.folderId(key)?.let { id -> edit { HomeEdit.dissolve(it, id) } } },
            )
            ItemKind.Widget -> buildList {
                add(entry(L10n.tr("Resize")) { resizing = key })
                add(entry(L10n.tr("Move earlier")) { edit { s -> HomeEdit.reorderOnPage(s, key, (HomeLayoutIndex.indexOf(s, key) - 1).coerceAtLeast(0), capacityOf) } })
                add(entry(L10n.tr("Move later")) { edit { s -> HomeEdit.reorderOnPage(s, key, HomeLayoutIndex.indexOf(s, key) + 1, capacityOf) } })
                val info = ItemKeys.widgetId(key)?.let { host.providerInfo(it) }
                if (info?.configure != null) add(entry(L10n.tr("Reconfigure")) { reconfigure(key) })
            }
            else -> listOf(entry(L10n.tr("Open")) { tap(key) })
        }
        return own + entry(L10n.tr("Remove from home")) { removeKey(key) }
    }

    private fun reconfigure(key: String) {
        val id = ItemKeys.widgetId(key) ?: return
        val info = host.providerInfo(id) ?: return
        val cfg = info.configure ?: return
        runCatching {
            context.startActivity(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(cfg).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Entries for an app's long-press menu: folders and the per-item icon (HOM-03, HOM-04). */
    fun appMenuEntries(app: AppEntry, dismiss: () -> Unit): List<Pair<String, () -> Unit>> = buildList {
        val folders = extrasState.folders.values
        val inFolder = folders.firstOrNull { app.key in it.keys }
        if (inFolder != null) {
            add(entry(L10n.tr("Take out of folder")) {
                edit { HomeEdit.takeOut(it, inFolder.id, app.key, page(), pagesNow().getOrNull(page())?.keys?.size ?: 0, capacityOf) }; dismiss()
            })
        } else {
            add(entry(L10n.tr("New folder with this app")) { addFolderWith(listOf(app.key)); dismiss() })
            folders.forEach { f -> add(entry(L10n.tr("Add to folder %s", f.name)) { edit { HomeEdit.fileInto(it, f.id, app.key, capacityOf) }; dismiss() }) }
        }
        add(entry(L10n.tr("Change icon")) { iconFor = app; dismiss() })
    }

    /** The icon sources every [AppIcon] resolves against. */
    internal var sources: IconSources = IconSources()

    /** Extra padding the scrolling homes take so the band never overlaps their tiles. */
    fun padFor(base: PaddingValues, widthDp: Float, heightDp: Float, topLevel: List<String>, style: com.bos.sphere.core.data.HomeStyle): PaddingValues {
        val (items, widgets) = bandKeys(homeExtras, topLevel, style, widthDp.toInt(), heightDp.toInt())
        return ScrollExtrasLayout.padFor(base, widthDp, heightDp, widgets.size, items.size)
    }

    val iconSources: IconSources get() = sources

    /** What the surfaces render. */
    val homeExtras: HomeExtras get() = HomeExtras(
        state = extrasState,
        resolveApp = { appsByKey()[it] },
        shortcutIcon = ::shortcutIcon,
        widget = { key, modifier ->
            val id = ItemKeys.widgetId(key)
            val rev = restoreRev
            val available = remember(id, rev) { id != null && host.providerInfo(id) != null }
            if (available && id != null) HostedWidget(host, id, modifier)
            else WidgetPlaceholder(extrasState.items[key]?.label.orEmpty(), onRestore = { restoreRev++ }, modifier = modifier)
        },
        resizing = resizing,
        onResize = { key, dc, dr ->
            val (cols, rows) = gridDims()
            edit { HomeEdit.resizeWidget(it, key, dc, dr, max = com.bos.sphere.core.catalog.WidgetSpan(cols, rows), gridCols = cols, gridRows = rows) }
        },
        onResizeDone = { resizing = null },
        onTap = ::tap,
        onLongPress = { itemMenu = it },
        onDropOnto = { drag, target ->
            edit { s -> HomeEdit.dropOnto(s, drag, target, newId(), L10n.tr("Folder"), capacityOf) }
        },
    )

    /** Rebinds saved widgets whose id was lost (reboot, provider update) and keeps their slots (HOM-07). */
    internal suspend fun restoreWidgets(allowPrompt: ((SavedRebind) -> Unit)?) {
        val st = store.state.value
        val saved = WidgetStartup.saved(st)
        if (saved.isEmpty()) return
        val awm = AppWidgetManager.getInstance(context)
        val installed = withContext(Dispatchers.IO) { runCatching { awm.installedProviders.map { it.provider.flattenToString() }.toSet() }.getOrDefault(emptySet()) }
        val hosted = saved.filter { host.providerInfo(it.appWidgetId) != null }.map { it.appWidgetId }.toSet()
        for (a in WidgetStartup.plan(st, hosted, installed)) {
            if (a !is RestoreAction.Rebind) continue
            val provider = ComponentName.unflattenFromString(a.widget.provider) ?: continue
            val newId = host.allocateAppWidgetId()
            if (runCatching { awm.bindAppWidgetIdIfAllowed(newId, provider) }.getOrDefault(false)) {
                edit { HomeRekey.widget(it, a.widget.key, ItemKeys.widget(newId)) }
            } else {
                host.deleteWidget(newId)
                allowPrompt?.invoke(SavedRebind(a.widget.key, provider))
            }
        }
    }

    data class SavedRebind(val key: String, val provider: ComponentName)

    internal fun rekeyBound(oldKey: String, newId: Int) = edit { HomeRekey.widget(it, oldKey, ItemKeys.widget(newId)) }

    internal fun topLevelKeys(stored: List<HomePage>): List<String> = stored.flatMap { it.keys } + extrasState.dock

    // ---- dialogs
    @Composable
    fun Overlays(hiddenStore: HiddenAppsStore, hidden: Set<String>) {
        val ctx = LocalContext.current
        val picker = rememberWidgetPicker(host) { id, label -> addWidget(id, label) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                // Provider queries run off the main thread.
                val name = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    runCatching {
                        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    }.getOrNull()
                } ?: uri.lastPathSegment.orEmpty()
                addLink(uri.toString(), name, isFile = true)
            }
        }
        var pendingRebind by remember { mutableStateOf<SavedRebind?>(null) }
        val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val p = pendingRebind
            pendingRebind = null
            val id = r.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (p != null && id >= 0) { if (r.resultCode == Activity.RESULT_OK) rekeyBound(p.key, id) else host.deleteWidget(id) }
        }
        // Startup restore, again on "tap to restore".
        LaunchedEffect(restoreRev) {
            restoreWidgets { p ->
                if (restoreRev > 0 && pendingRebind == null) {
                    val id = host.allocateAppWidgetId()
                    pendingRebind = p
                    runCatching {
                        bindLauncher.launch(
                            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider),
                        )
                    }.onFailure { host.deleteWidget(id); pendingRebind = null }
                }
            }
        }
        if (sheetOpen) HomeItemsSheet(
            onWidget = { sheetOpen = false; picker.launch() },
            onFolder = { sheetOpen = false; folderOpen = true },
            onLink = { sheetOpen = false; linkOpen = true },
            onFile = { sheetOpen = false; openDoc.launch(arrayOf("*/*")) },
            onShortcut = { sheetOpen = false; shortcutOpen = true },
            onIconPack = { sheetOpen = false; packOpen = true },
            onHiddenApps = { sheetOpen = false; hiddenOpen = true },
            onDismiss = { sheetOpen = false },
        )
        if (folderOpen) com.bos.sphere.feature.grid.NewFolderDialog(allApps().sortedBy { it.label.lowercase() }, onCreate = { keys, n -> addFolderWith(keys, n) }, onDismiss = { folderOpen = false })
        if (linkOpen) LinkDialog(onAdd = { u -> addLink(u, Uri.parse(u).host ?: u, false) }, onDismiss = { linkOpen = false })
        if (shortcutOpen) ShortcutPickerDialog(allApps().sortedBy { it.label.lowercase() }, ::shortcutsOf, onPick = ::addShortcut, onDismiss = { shortcutOpen = false })
        itemMenu?.let { k -> HomeItemMenu(extrasState.items[k]?.label ?: extrasState.folders[ItemKeys.folderId(k)]?.name.orEmpty(), menuEntries(k), onDismiss = { itemMenu = null }) }
        openFolder?.let { id ->
            val f = extrasState.folders[id]
            if (f == null) openFolder = null
            else FolderSheet(
                folder = f, resolve = { appsByKey()[it] }, editing = true,
                onLaunch = { openFolder = null; launchApp(it) },
                onRename = { n -> edit { HomeEdit.renameFolder(it, id, n) } },
                onReorder = { k, to -> edit { HomeEdit.reorderInFolder(it, id, k, to) } },
                onTakeOut = { k -> edit { HomeEdit.takeOut(it, id, k, page(), pagesNow().getOrNull(page())?.keys?.size ?: 0, capacityOf) } },
                onDismiss = { openFolder = null },
            )
        }
        if (packOpen) {
            val ov = remember { OverrideStoreHolder.get(ctx) }
            val pack by ov.pack.collectAsStateWithLifecycle()
            val themed by ov.themed.collectAsStateWithLifecycle()
            val packs by produceState(emptyList<Pair<String, String>>()) { value = withContext(Dispatchers.IO) { runCatching { IconPackLoader(ctx).installed() }.getOrDefault(emptyList()) } }
            IconPackDialog(packs, pack, themed, onSelect = { ov.setPack(it) }, onThemed = { ov.setThemed(it) }, onDismiss = { packOpen = false })
        }
        iconFor?.let { app ->
            val ov = remember { OverrideStoreHolder.get(ctx) }
            val overrides by ov.overrides.collectAsStateWithLifecycle()
            val loader = remember { IconPackLoader(ctx) }
            val packs by produceState(emptyList<Pair<String, String>>()) { value = withContext(Dispatchers.IO) { runCatching { loader.installed() }.getOrDefault(emptyList()) } }
            IconOverrideDialog(
                title = app.label, packs = packs,
                drawablesOf = { p -> loader.load(p)?.drawables().orEmpty() },
                drawable = { p, n -> loader.drawable(p, n) },
                hasOverride = app.key in overrides,
                onPick = { p, n -> ov.setOverride(app.key, IconSpec.encode(p, n)) },
                onReset = { ov.setOverride(app.key, null) },
                onDismiss = { iconFor = null },
            )
        }
        if (hiddenOpen) HiddenAppsDialog(
            apps = HiddenAppsView.hiddenOnly(allApps(), { it.key }, { it.label }, hidden),
            onUnhide = { hiddenStore.setHidden(it.key, false) },
            onLaunch = { hiddenOpen = false; launchApp(it) },
            onDismiss = { hiddenOpen = false },
        )
    }

    /** The band over the scrolling homes (List, XMB, DSi, continuous): widgets and the item strip. */
    @Composable
    fun Band(stored: List<HomePage>, widthDp: Float, heightDp: Float, style: com.bos.sphere.core.data.HomeStyle) {
        val (items, widgets) = bandKeys(homeExtras, topLevelKeys(stored), style, widthDp.toInt(), heightDp.toInt())
        ScrollExtrasBand(homeExtras, items, widgets, widthDp, heightDp)
    }
}

/** Index of [key] on its page, for the widget "move" menu entries. */
internal object HomeLayoutIndex {
    fun indexOf(s: HomeState, key: String): Int = s.pages.firstOrNull { key in it.keys }?.keys?.indexOf(key) ?: 0
}

internal object OverrideStoreHolder {
    @Volatile private var store: IconOverrideStore? = null
    fun get(context: Context): IconOverrideStore = store ?: synchronized(this) { store ?: IconOverrideStore(context).also { store = it } }
}

@Composable
fun rememberHomeItems(
    store: HomeExtrasStore,
    pages: List<HomePage>,
    savePages: (List<HomePage>) -> Unit,
    capacityOf: (PageLayout) -> Int,
    currentPage: () -> Int,
    host: LauncherWidgetHost,
    allApps: List<AppEntry>,
    appsByKey: Map<String, AppEntry>,
    launchApp: (AppEntry) -> Unit,
    accentArgb: Int,
    gridDims: () -> Pair<Int, Int>,
): HomeItems {
    val context = LocalContext.current
    val state by store.state.collectAsStateWithLifecycle()
    val pagesNow by rememberUpdatedState(pages)
    val save by rememberUpdatedState(savePages)
    val cap by rememberUpdatedState(capacityOf)
    val cur by rememberUpdatedState(currentPage)
    val all by rememberUpdatedState(allApps)
    val byKey by rememberUpdatedState(appsByKey)
    val launch by rememberUpdatedState(launchApp)
    val dims by rememberUpdatedState(gridDims)
    val items = remember { HomeItems(context, store, { dims() }, { pagesNow }, { save(it) }, { cap(it) }, { cur() }, host, { all }, { byKey }, { launch(it) }) }
    items.extrasState = state
    // Icon sources: overrides, the chosen pack (parsed off the main thread), themed icons.
    val ov = remember { OverrideStoreHolder.get(context) }
    val overrides by ov.overrides.collectAsStateWithLifecycle()
    val packName by ov.pack.collectAsStateWithLifecycle()
    val themed by ov.themed.collectAsStateWithLifecycle()
    val loader = remember { IconPackLoader(context) }
    val pack by produceState<IconPackMap?>(null, packName) { value = packName?.let { n -> withContext(Dispatchers.IO) { loader.load(n) } } }
    val dark = isSystemInDarkTheme()
    items.sources = remember(overrides, pack, themed, dark, accentArgb) {
        IconSources(overrides, pack, themed, dark, accentArgb, drawable = { p, n -> loader.drawable(p, n) })
    }
    return items
}
