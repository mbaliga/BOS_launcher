package com.bos.sphere.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.capabilities.CapabilityEvaluator
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.launcherDropTarget
import androidx.compose.runtime.collectAsState
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.LeftPage
import com.bos.sphere.feature.dock.WorkspaceWidgets
import com.bos.sphere.feature.dock.GroupShelf
import com.bos.sphere.feature.dock.GroupShelfModel
import com.bos.sphere.feature.dock.PinnableActions
import com.bos.sphere.feature.dock.DockItemRef
import com.bos.sphere.feature.dock.ItemKind
import com.bos.sphere.feature.desktop.DesktopController
import com.bos.sphere.core.data.HomeLayoutCodec
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.feature.dock.AppLauncher
import com.bos.sphere.feature.dock.DockController
import com.bos.sphere.feature.dock.DockEdge
import com.bos.sphere.feature.dock.DockHost
import com.bos.sphere.feature.dock.DockPanel
import com.bos.sphere.feature.dock.GroupPaletteProvider
import com.bos.sphere.feature.dock.PanelApp
import com.bos.sphere.feature.dock.Scheduler
import com.bos.sphere.feature.dock.ScheduledLaunch
import com.bos.sphere.feature.dock.WorkspaceHooks
import com.bos.sphere.feature.dock.WorkspacePaletteProvider
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.core.signals.NotificationSignals
import com.bos.sphere.core.signals.NotificationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Connects workspace switching to the home arrangement and to the Launchpad's display filter (WSP-07, WSP-08). */
private class AppWorkspaceHooks(private val stores: LauncherStores, private val extras: com.bos.sphere.core.catalog.HomeExtrasStore) : WorkspaceHooks {
    override fun captureHome(): String {
        val l = stores.layout.layout.value
        // HOM-02: the workspace's dock is part of its arrangement now (HomeState.dock is the one dock list).
        return JSONObject().put("pages", HomeLayoutCodec.encode(l.pages)).put("removed", JSONArray(l.removed.toList()))
            .put("dock", JSONArray(extras.state.value.dock)).toString()
    }

    override fun onSwitched(workspace: com.bos.sphere.feature.dock.Workspace) {
        // A workspace saved before the dock moved into the arrangement keeps its own pins.
        val dock = com.bos.sphere.feature.dock.HomeDockKeys.forWorkspace(com.bos.sphere.feature.dock.HomeDockKeys.dockOf(workspace.homeArrangement), workspace.dockPins)
        if (dock != extras.state.value.dock) extras.set(extras.state.value.copy(dock = dock))
    }

    override fun applyHome(arrangement: String?) {
        if (arrangement == null) return
        runCatching {
            val o = JSONObject(arrangement)
            val removed = o.optJSONArray("removed")?.let { a -> (0 until a.length()).mapTo(HashSet()) { a.getString(it) } } ?: emptySet()
            stores.layout.replace(HomeLayoutCodec.decode(o.getString("pages")), removed)
        }
    }

    override fun applyNotificationScope(allowed: Set<String>?) = NotificationSignals.setScope(NotificationScope(allowed))

    /** The left-page widget and the corner widgets are what a workspace keeps as its widgets (WSP-07). */
    override fun captureWidgets(): List<String> {
        val p = stores.prefs.prefs.value
        val slots = ArrayList<WorkspaceWidgets.Slot>()
        if (p.leftPage == LeftPage.Widget && p.leftWidgetId >= 0) slots += WorkspaceWidgets.Slot.Left(p.leftWidgetId)
        stores.shortcuts.corners.value.forEach { (c, slot) -> if (slot is CornerSlot.Widget) slots += WorkspaceWidgets.Slot.Corner(c.name, slot.appWidgetId, slot.label) }
        return WorkspaceWidgets.encode(slots)
    }

    override fun applyWidgets(widgets: List<String>) {
        if (!WorkspaceWidgets.isSnapshot(widgets)) return
        val slots = WorkspaceWidgets.decode(widgets)
        val left = slots.filterIsInstance<WorkspaceWidgets.Slot.Left>().firstOrNull()
        val cur = stores.prefs.prefs.value
        if (left != null) stores.prefs.update { it.copy(leftPage = LeftPage.Widget, leftWidgetId = left.appWidgetId) }
        else if (cur.leftPage == LeftPage.Widget) stores.prefs.update { it.copy(leftPage = LeftPage.Off, leftWidgetId = -1) }
        val wanted = slots.filterIsInstance<WorkspaceWidgets.Slot.Corner>().associateBy { it.corner }
        Corner.entries.forEach { c ->
            val now = stores.shortcuts.corners.value[c]
            val w = wanted[c.name]
            if (w != null) stores.shortcuts.setCorner(c, CornerSlot.Widget(w.appWidgetId, w.label))
            else if (now is CornerSlot.Widget) stores.shortcuts.setCorner(c, CornerSlot.Empty)
        }
    }
}

/**
 * The dock rail and its panel, drawn at the edge the user chose, inside the home frame. [visible] is false while a full-screen
 * overlay is open. Shortcuts reach it through [controller].
 */
@Composable
fun BoxScope.DockLayer(
    apps: List<AppEntry>,
    launcher: AppLauncher,
    controller: DockController,
    homeSignal: Int,
    visible: Boolean,
    keyboardMode: Boolean,
    onLaunch: (AppEntry) -> Unit,
    /** HOM-10: pinned app keys that are gone for now (kept dimmed until the grace period ends). */
    unavailableApps: Set<String> = emptySet(),
    /** HOM-02: the home's folders, shortcuts and links, so they can be pinned to and drawn in the rail. */
    homeExtras: com.bos.sphere.feature.grid.HomeExtras? = null,
    /** HOM-19: where the bottom rail and what rides on it sit above the bottom clock and page dots; null keeps the fixed margins. */
    bottomPlan: com.bos.sphere.feature.dock.HomeBottomStack.Plan? = null,
) {
    val context = LocalContext.current
    val stores = remember { LauncherStores.get(context) }
    val manager = remember { WorkspaceRepository.get(context) }
    DisposableEffect(Unit) {
        DockPinBinding.ensure(context)
        val hooks = AppWorkspaceHooks(stores, com.bos.sphere.core.catalog.HomeExtrasStore.get(context))
        WorkspaceRepository.hooks = hooks
        manager.applyActive()
        onDispose { if (WorkspaceRepository.hooks === hooks) WorkspaceRepository.hooks = object : WorkspaceHooks {} }
    }
    DockInputWiring.Install()
    var panelOpen by remember { mutableStateOf(false) }
    // Workspace shortcuts work with the dock off (DCK-05): the controller steps the workspace itself.
    DisposableEffect(controller) { controller.bindWorkspaceStep { d -> if (d > 0) manager.next() else manager.previous() }; onDispose { } }
    val wsState by manager.state.collectAsState()
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    DisposableEffect(Unit) { onDispose { scope.cancel() } }

    val desktop = remember { DesktopController.get(context) }
    // INP-05: Dock 1 to 9 launch the Nth pin even when no rail is composed (an overlay hides it, or the dock setting is off).
    val latestApps = androidx.compose.runtime.rememberUpdatedState(apps)
    val latestHome = androidx.compose.runtime.rememberUpdatedState(homeExtras)
    val latestLaunch = androidx.compose.runtime.rememberUpdatedState(onLaunch)
    fun pinDropped(r: com.bos.sphere.feature.grid.DropResult): Int {
        val known = latestApps.value.mapTo(HashSet()) { it.key }
        val appRefs = r.appKeys.filter { it in known }.map { DockItemRef.app(it) }
        if (r.items.isNotEmpty()) com.bos.sphere.feature.grid.LauncherDrops.onItems?.invoke(r.items) // a link or file lives on the home first; the pin refers to it
        val itemRefs = if (com.bos.sphere.feature.grid.LauncherDrops.onItems != null) r.items.mapNotNull { com.bos.sphere.feature.dock.HomeItemPins.refOf(it.key) } else emptyList()
        val refs = appRefs + itemRefs
        manager.setPins(refs.fold(manager.current.active.dockPins) { acc, ref -> com.bos.sphere.feature.dock.DockPins.add(acc, ref) })
        return refs.size
    }
    DisposableEffect(controller) {
        fun otherKey(ref: DockItemRef) = com.bos.sphere.feature.dock.HomeItemPins.homeKeyOf(ref)?.takeIf { latestHome.value?.owns(it) == true }
        controller.bindPinLaunch { n ->
            val st = manager.current
            val byKey = latestApps.value.associateBy { it.key }
            val available = { ref: DockItemRef ->
                when (ref.kind) {
                    ItemKind.App -> byKey.containsKey(ref.id)
                    ItemKind.Group -> st.groups.any { it.id == ref.id }
                    else -> ref.id == DESKTOP_PIN_ID || otherKey(ref) != null
                }
            }
            com.bos.sphere.feature.dock.DockPinLaunch.launchNth(n, st.active.dockPins, available) { ref ->
                when (ref.kind) {
                    ItemKind.App -> byKey[ref.id]?.let { com.bos.sphere.feature.dock.SessionUse.mark(it.key); latestLaunch.value(it) }
                    ItemKind.Group -> st.groups.firstOrNull { it.id == ref.id }?.let { g -> scope.launch { launcher.runGroup(g) } }
                    else -> if (ref.id == DESKTOP_PIN_ID) openDesktop(context, desktop) else otherKey(ref)?.let { latestHome.value?.onTap?.invoke(it) }
                }
            }
        }
        onDispose { controller.bindPinLaunch { false } }
    }
    val tierNow = remember { CapabilityEvaluator.tier(launcher.inputs()) }
    if (visible) GroupShelfSlot(wsState.groups, wsState.dock.groupShelf, wsState.dock.isEnabled(tierNow), bottomPlan?.shelfBottomDp, onRun = { g -> scope.launch { launcher.runGroup(g) } })
    if (visible) DockHost(
        unavailableApps = unavailableApps,
        apps = apps, launcher = launcher, controller = controller, refreshKey = homeSignal,
        onLaunchApp = onLaunch, onOpenPanel = { panelOpen = true }, inputIsKeyboard = keyboardMode,
        onRunGroup = { g -> scope.launch { launcher.runGroup(g) } },
        labelOfOther = { ref ->
            if (ref.kind == ItemKind.Shortcut && ref.id == DESKTOP_PIN_ID) L10n.tr("Desktop")
            else com.bos.sphere.feature.dock.HomeItemPins.homeKeyOf(ref)?.takeIf { homeExtras?.owns(it) == true }?.let { com.bos.sphere.feature.grid.extraLabel(it, homeExtras!!) }
        },
        onLaunchOther = { ref ->
            if (ref.id == DESKTOP_PIN_ID) openDesktop(context, desktop)
            else com.bos.sphere.feature.dock.HomeItemPins.homeKeyOf(ref)?.let { homeExtras?.onTap?.invoke(it) }
        },
        spanOfOther = { ref -> com.bos.sphere.feature.dock.HomeItemPins.homeKeyOf(ref)?.let { homeExtras?.spanOf(it)?.cols } ?: 1 },
        widgetOther = if (homeExtras == null) null else { item, w, h ->
            // HOM-02: a pinned widget is the same hosted widget the home shows, in its rail slot.
            com.bos.sphere.feature.dock.HomeItemPins.homeKeyOf(item.ref)?.let { k -> androidx.compose.foundation.layout.Box(Modifier.size(w, h)) { homeExtras.widget(k, Modifier.fillMaxSize()) } }
        },
        iconOther = if (homeExtras == null) null else { item, size ->
            com.bos.sphere.feature.dock.HomeItemPins.homeKeyOf(item.ref)?.let { com.bos.sphere.feature.grid.ExtraItemFace(it, homeExtras, size) }
        },
        // INP-11: an app dragged out of the drawer or Radar, or a link or file dragged in, is pinned by dropping it on the rail.
        railModifier = {
            val ctx = LocalContext.current
            launcherDropTarget { r -> pinDropped(r).also { n -> if (n > 0) android.widget.Toast.makeText(ctx, L10n.tr("Pinned %d to dock", n), android.widget.Toast.LENGTH_SHORT).show() } > 0 }
        },
        modifier = { edge ->
            when (edge) {
                DockEdge.Bottom -> Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(start = 76.dp, end = 76.dp, bottom = (bottomPlan?.railBottomDp ?: 8f).dp)
                DockEdge.Left -> Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(start = 12.dp, top = 76.dp, bottom = 76.dp)
                DockEdge.Right -> Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(end = 12.dp, top = 76.dp, bottom = 76.dp)
            }
        },
    )
    if (panelOpen) DockPanelHost(apps, launcher, onDismiss = { panelOpen = false }, onRunGroup = { g -> scope.launch { launcher.runGroup(g) } }, onRestore = { sess -> scope.launch { launcher.restore(sess) } })
}

/**
 * The dock panel with everything the app supplies to it. Shared by the phone's home and the desktop surface so both reach the
 * same workspaces, groups, schedules and Radar actions (DCK-07).
 */
@Composable
fun DockPanelHost(
    apps: List<AppEntry>,
    launcher: AppLauncher,
    onDismiss: () -> Unit,
    onRunGroup: (com.bos.sphere.core.launch.AppGroup) -> Unit,
    onRestore: (com.bos.sphere.core.launch.Session) -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { WorkspaceRepository.get(context) }
    val wsState by manager.state.collectAsState()
    val tier = remember { CapabilityEvaluator.tier(launcher.inputs()) }
    val panelApps = remember(apps) { apps.map { PanelApp(it.key, it.label, it.packageName, launcher.targetOf(it)) } }
    DockPanel(
        manager = manager, tier = tier, apps = panelApps, displays = launcher.displays(),
        onRunGroup = { g -> onRunGroup(g); onDismiss() },
        onRestore = { s -> onRestore(s); onDismiss() },
        onSchedule = { target, label, at ->
            val sl = ScheduledLaunch(manager.idFor("sc"), label, at, target)
            manager.addSchedule(sl); Scheduler.schedule(context, sl)
            android.widget.Toast.makeText(context, L10n.tr("A notification with an Open action will appear at the chosen time"), android.widget.Toast.LENGTH_SHORT).show()
        },
        onDismiss = onDismiss,
        paletteActionChoices = PinnableActions.choices(),
        groupShelfOn = wsState.dock.groupShelf,
        onGroupShelf = { v -> manager.setDock { it.copy(groupShelf = v) } },
        extra = {
            // EXT-02: a quick way to reopen the desktop surface, as a dock item.
            val pin = DockItemRef(ItemKind.Shortcut, DESKTOP_PIN_ID)
            val pinned = pin in wsState.active.dockPins
            com.bos.sphere.feature.dock.Heading("Desktop on an external display")
            com.bos.sphere.feature.dock.Btn(if (pinned) "Remove the Desktop item from the dock" else "Add a Desktop item to the dock") {
                manager.setPins(if (pinned) wsState.active.dockPins - pin else wsState.active.dockPins + pin)
            }
            com.bos.sphere.feature.dock.Hint(L10n.tr("The Desktop item opens the Liftoff desktop on a connected display."))
        },
    )
}

/**
 * HOM-02: makes `HomeState.dock` (the home extras store) the one place the dock's pins live. Pins written anywhere
 * (`WorkspaceManager.setPins`) go to that list; the active workspace's own list only mirrors it. Runs once per process, from
 * whichever surface composes a dock first, and migrates the old per-workspace pins into the list the first time.
 */
object DockPinBinding {
    private var bound = false

    @Synchronized fun ensure(context: android.content.Context) {
        if (bound) return
        bound = true
        val store = com.bos.sphere.core.catalog.HomeExtrasStore.get(context)
        val manager = WorkspaceRepository.get(context)
        com.bos.sphere.feature.dock.HomeDockKeys.migrate(store.state.value.dock, manager.current.active.dockPins)?.let { store.set(store.state.value.copy(dock = it)) }
        manager.pinSink = { pins ->
            val enc = com.bos.sphere.feature.dock.HomeDockKeys.encodeAll(pins)
            if (store.state.value.dock != enc) store.set(store.state.value.copy(dock = enc))
            manager.mirrorPins(pins)
        }
        manager.mirrorPins(com.bos.sphere.feature.dock.HomeDockKeys.decodeAll(store.state.value.dock))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            var last = store.state.value.dock
            store.state.collect { st -> if (st.dock != last) { last = st.dock; manager.mirrorPins(com.bos.sphere.feature.dock.HomeDockKeys.decodeAll(st.dock)) } }
        }
    }
}

/** Dock pin id of the "reopen the desktop surface" item (EXT-02). */
const val DESKTOP_PIN_ID = "liftoff.desktop"

private fun openDesktop(context: android.content.Context, desktop: DesktopController) {
    val a = desktop.reopenAction()
    if (a != null) a.run() else android.widget.Toast.makeText(context, L10n.tr("No external display is ready for the desktop"), android.widget.Toast.LENGTH_SHORT).show()
}

/** Saved groups as chips above the dock (WSP-05). The dock's own edge padding is reused so the two never overlap. */
@Composable
private fun BoxScope.GroupShelfSlot(groups: List<com.bos.sphere.core.launch.AppGroup>, on: Boolean, dockOn: Boolean, shelfBottomDp: Float?, onRun: (com.bos.sphere.core.launch.AppGroup) -> Unit) {
    val shown = GroupShelfModel.visible(groups, on)
    if (shown.isEmpty()) return
    GroupShelf(
        shown, onRun,
        Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(start = 76.dp, end = 76.dp, bottom = (shelfBottomDp ?: if (dockOn) 84f else 8f).dp),
    )
}

/** The palette result sources from this stream (groups, workspaces, sessions), for the search stream to register. */
fun dockPaletteProviders(context: android.content.Context, launcher: AppLauncher): List<com.bos.sphere.feature.dock.PaletteProvider> {
    val manager = WorkspaceRepository.get(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    return listOf(
        GroupPaletteProvider({ manager.current.groups }) { g -> scope.launch { launcher.runGroup(g) } },
        WorkspacePaletteProvider({ manager.current }, { manager.switchTo(it) }) { s -> scope.launch { launcher.restore(s) } },
    )
}


/** Radar results for groups, workspaces, sessions, workspace-pinned actions and the desktop (WSP-05, WSP-07, EXT-02, EXT-10). */
fun dockSearchProvider(context: android.content.Context): com.bos.sphere.core.data.SearchProvider {
    val manager = WorkspaceRepository.get(context)
    val desktop = DesktopController.get(context)
    return com.bos.sphere.feature.dock.DockSearchProvider(
        state = { manager.current },
        intentFor = { t -> com.bos.sphere.feature.dock.ScheduleNotificationsApi.runIntent(context, t).toUri(android.content.Intent.URI_INTENT_SCHEME) },
        extra = { q -> DesktopSearchHits.hits(context, desktop, q) },
    )
}

/** The pieces of Radar's results that belong to the external display stream. */
object DesktopSearchHits {
    fun hits(context: android.content.Context, desktop: DesktopController, q: String): List<com.bos.sphere.core.data.SearchHit> {
        val out = ArrayList<com.bos.sphere.core.data.SearchHit>()
        val reopen = desktop.reopenAction()
        if (reopen != null && com.bos.sphere.feature.dock.PaletteMatch.matches(q, "desktop external display")) out += com.bos.sphere.core.data.SearchHit(
            id = "desktop:open", category = com.bos.sphere.core.data.SearchCategory.Actions, title = reopen.label, subtitle = L10n.tr("Desktop surface"),
            kind = com.bos.sphere.core.data.HitKind.Action, intentUri = desktop.openIntent().toUri(android.content.Intent.URI_INTENT_SCHEME), score = 0.8f,
        )
        if (com.bos.sphere.feature.desktop.DesktopSurfaces.list().isNotEmpty() && com.bos.sphere.feature.dock.PaletteMatch.matches(q, "trackpad keyboard phone input")) out += com.bos.sphere.core.data.SearchHit(
            id = "desktop:pad", category = com.bos.sphere.core.data.SearchCategory.Actions, title = L10n.tr("Use this phone as trackpad"), subtitle = L10n.tr("Controls the Liftoff desktop only"),
            kind = com.bos.sphere.core.data.HitKind.Action,
            intentUri = android.content.Intent().setComponent(android.content.ComponentName(context.packageName, DesktopController.PAD_CLASS)).toUri(android.content.Intent.URI_INTENT_SCHEME), score = 0.8f,
        )
        return out
    }
}

/**
 * The launch-animation source tracker for the home: the press position plus the rectangle of the registered home item under it, so
 * the app opens out of the icon's own bounds (WSP-06).
 */
fun homeLastTouch(rootView: () -> android.view.View): com.bos.sphere.feature.dock.LastTouch =
    com.bos.sphere.feature.dock.LastTouch(
        origin = { IntArray(2).also { rootView().getLocationOnScreen(it) }.let { it[0] to it[1] } },
        itemRects = { com.bos.sphere.feature.grid.LauncherInput.focus.let { f -> f.nodes().filter { it.layer >= f.topLayer }.map { it.rect } } },
    )

/**
 * INP-01, INP-20: connects the dock module to the launcher's shared input hub. The rail's items and controls register as
 * nodes of the focus graph (layer 0, the home's), and every dock dialog installs the controller window hook and counts as
 * a focus layer above the home while it is open. Installed while the home is composed; the desktop surface's rail passes
 * no bridge (see `DockHost.forceEnabled`).
 */
internal object DockInputWiring {
    val bridge = object : com.bos.sphere.feature.dock.DockInput.FocusBridge {
        override fun register(id: String, left: Float, top: Float, right: Float, bottom: Float, requester: androidx.compose.ui.focus.FocusRequester) {
            com.bos.sphere.feature.grid.LauncherInput.focus.register(
                com.bos.sphere.core.input.FocusNode(id, com.bos.sphere.core.input.FRect(left, top, right, bottom), 0, com.bos.sphere.core.input.FocusKind.Item), requester,
            )
        }
        override fun unregister(id: String) = com.bos.sphere.feature.grid.LauncherInput.focus.unregister(id)
        override fun onFocus(id: String, focused: Boolean) = com.bos.sphere.feature.grid.LauncherInput.focus.onFocus(id, focused)
    }

    val dialogHook: @Composable () -> Unit = {
        com.bos.sphere.feature.grid.DialogControllerKeys()
        DisposableEffect(Unit) {
            com.bos.sphere.feature.grid.LauncherInput.focus.layerActive(com.bos.sphere.feature.dock.DockInput.DIALOG_LAYER, true)
            onDispose { com.bos.sphere.feature.grid.LauncherInput.focus.layerActive(com.bos.sphere.feature.dock.DockInput.DIALOG_LAYER, false) }
        }
    }

    private var users = 0
    private var bridgeUsers = 0

    /** [withBridge] false for the desktop surface, which needs the dialog hook but keeps its rail off the phone home's graph. */
    @Composable
    fun Install(withBridge: Boolean = true) {
        DisposableEffect(withBridge) {
            synchronized(this@DockInputWiring) {
                users++
                com.bos.sphere.feature.dock.DockInput.dialogHook = dialogHook
                if (withBridge) { bridgeUsers++; com.bos.sphere.feature.dock.DockInput.bridge = bridge }
            }
            onDispose {
                synchronized(this@DockInputWiring) {
                    users--
                    if (withBridge && --bridgeUsers <= 0) com.bos.sphere.feature.dock.DockInput.bridge = null
                    if (users <= 0) com.bos.sphere.feature.dock.DockInput.dialogHook = {}
                }
            }
        }
    }
}
