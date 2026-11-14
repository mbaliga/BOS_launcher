package com.bos.sphere.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
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
private class AppWorkspaceHooks(private val stores: LauncherStores) : WorkspaceHooks {
    override fun captureHome(): String {
        val l = stores.layout.layout.value
        return JSONObject().put("pages", HomeLayoutCodec.encode(l.pages)).put("removed", JSONArray(l.removed.toList())).toString()
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
) {
    val context = LocalContext.current
    val stores = remember { LauncherStores.get(context) }
    val manager = remember { WorkspaceRepository.get(context) }
    DisposableEffect(Unit) {
        val hooks = AppWorkspaceHooks(stores)
        WorkspaceRepository.hooks = hooks
        manager.applyActive()
        onDispose { if (WorkspaceRepository.hooks === hooks) WorkspaceRepository.hooks = object : WorkspaceHooks {} }
    }
    var panelOpen by remember { mutableStateOf(false) }
    // Workspace shortcuts work with the dock off (DCK-05): the controller steps the workspace itself.
    DisposableEffect(controller) { controller.bindWorkspaceStep { d -> if (d > 0) manager.next() else manager.previous() }; onDispose { } }
    val wsState by manager.state.collectAsState()
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    DisposableEffect(Unit) { onDispose { scope.cancel() } }

    val desktop = remember { DesktopController.get(context) }
    val tierNow = remember { CapabilityEvaluator.tier(launcher.inputs()) }
    if (visible) GroupShelfSlot(wsState.groups, wsState.dock.groupShelf, wsState.dock.isEnabled(tierNow), onRun = { g -> scope.launch { launcher.runGroup(g) } })
    if (visible) DockHost(
        apps = apps, launcher = launcher, controller = controller, refreshKey = homeSignal,
        onLaunchApp = onLaunch, onOpenPanel = { panelOpen = true }, inputIsKeyboard = keyboardMode,
        onRunGroup = { g -> scope.launch { launcher.runGroup(g) } },
        labelOfOther = { ref -> if (ref.kind == ItemKind.Shortcut && ref.id == DESKTOP_PIN_ID) L10n.tr("Desktop") else null },
        onLaunchOther = { ref -> if (ref.id == DESKTOP_PIN_ID) openDesktop(context, desktop) },
        modifier = { edge ->
            when (edge) {
                DockEdge.Bottom -> Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(start = 76.dp, end = 76.dp, bottom = 8.dp)
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

/** Dock pin id of the "reopen the desktop surface" item (EXT-02). */
const val DESKTOP_PIN_ID = "liftoff.desktop"

private fun openDesktop(context: android.content.Context, desktop: DesktopController) {
    val a = desktop.reopenAction()
    if (a != null) a.run() else android.widget.Toast.makeText(context, L10n.tr("No external display is ready for the desktop"), android.widget.Toast.LENGTH_SHORT).show()
}

/** Saved groups as chips above the dock (WSP-05). The dock's own edge padding is reused so the two never overlap. */
@Composable
private fun BoxScope.GroupShelfSlot(groups: List<com.bos.sphere.core.launch.AppGroup>, on: Boolean, dockOn: Boolean, onRun: (com.bos.sphere.core.launch.AppGroup) -> Unit) {
    val shown = GroupShelfModel.visible(groups, on)
    if (shown.isEmpty()) return
    GroupShelf(
        shown, onRun,
        Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(start = 76.dp, end = 76.dp, bottom = if (dockOn) 84.dp else 8.dp),
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
