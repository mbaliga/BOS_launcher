package com.bos.sphere.feature.dock

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.capabilities.CapabilityEvaluator
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.input.KeyChord
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.ShortcutRegistry
import com.bos.sphere.core.signals.UsageRecents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PENDING_FOCUS_NS = 3_000_000_000L

/** The dock's command surface: the input layer resolves a shortcut to a [LauncherCommand] and calls [handle] (DCK-05). */
class DockController {
    var focusRequest by mutableIntStateOf(0)
        internal set
    private var pinLaunch: (Int) -> Boolean = { false }

    /** What number keys 1 to 9 launch: the rail's numbered items while a rail is composed, otherwise the pin list ([bindPinLaunch], INP-05). */
    internal var launchNth: (Int) -> Boolean = { pinLaunch(it) }
    internal var step: (Int) -> Boolean = { false }

    /**
     * INP-05: number keys launch the Nth pinned item even while no rail is on screen (Radar, the drawer, Ground Control or
     * the puck is open, or the dock setting is off). [f] gets the zero-based index. A composed rail takes over while it shows.
     */
    fun bindPinLaunch(f: (Int) -> Boolean) { pinLaunch = f }

    /** A rail left the composition: number keys go back to the pin list. */
    internal fun releaseRail() { launchNth = { pinLaunch(it) } }

    /** True when a rail is composed and numbering what it shows (so "focus dock" has something to focus). */
    var railShown: Boolean by mutableStateOf(false)
        internal set

    /** Lets a host that has no dock on screen still step workspaces from the shortcut keys (WSP-07). */
    fun bindWorkspaceStep(f: (Int) -> Boolean) { step = f }
    private var pendingFocusAt = 0L

    /**
     * "Focus dock" with a rail on screen focuses it; with none on screen (an overlay hides it) the request also waits for the
     * rail that appears next, for a couple of seconds, so closing the overlay and showing the rail takes focus to it.
     */
    private fun focusDock() { focusRequest++; if (!railShown) pendingFocusAt = System.nanoTime() }

    internal fun consumePendingFocus(): Boolean {
        val at = pendingFocusAt
        pendingFocusAt = 0L
        return at != 0L && System.nanoTime() - at < PENDING_FOCUS_NS
    }

    private val dock = DockCommandHandler(focusDock = ::focusDock, launchNth = { launchNth(it) })

    /** True when the command was the dock's (or a workspace switch) and has been handled. */
    fun handle(command: LauncherCommand): Boolean = when (command) {
        LauncherCommand.NextWorkspace -> step(1)
        LauncherCommand.PreviousWorkspace -> step(-1)
        else -> dock.handle(command)
    }

    /** Looks the chord up in [registry]; use from any key handler on a launcher surface. */
    fun onKey(registry: ShortcutRegistry, keyCode: Int, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean): Boolean =
        registry.commandFor(KeyChord(keyCode, ctrl, alt, shift, meta))?.let(::handle) ?: false
}

/** Delivers the dock and workspace shortcuts to [controller]; put it on any launcher surface that can hold focus. */
fun Modifier.dockShortcuts(registry: ShortcutRegistry, controller: DockController): Modifier = onPreviewKeyEvent { e ->
    e.type == KeyEventType.KeyDown && controller.onKey(registry, e.key.nativeKeyCode, e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.isMetaPressed)
}

/** Apps launched from this process, for the recency marker (DCK-03). */
object SessionUse {
    private val used = LinkedHashSet<String>()
    @Synchronized fun mark(appKey: String) { used.add(appKey) }
    @Synchronized fun all(): Set<String> = used.toSet()
}

/**
 * One dock instance for one display, sharing pins with the others (DCK-07). Place it where the surface wants the rail; it
 * draws nothing when the dock is off for the current tier and settings.
 */
@Composable
fun DockHost(
    apps: List<AppEntry>,
    launcher: AppLauncher,
    controller: DockController,
    refreshKey: Int,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenPanel: () -> Unit,
    /** W26: a tap on the Home glyph. */
    onGoHome: () -> Unit = {},
    modifier: (DockEdge) -> Modifier,
    displayId: Int = 0,
    inputIsKeyboard: Boolean = false,
    labelOfOther: (DockItemRef) -> String? = { null },
    /** HOM-10: app keys pinned here whose package is gone for now; the pin stays (dimmed) until the grace period ends. */
    unavailableApps: Set<String> = emptySet(),
    onLaunchOther: (DockItemRef) -> Unit = {},
    onRunGroup: (com.bos.sphere.core.launch.AppGroup) -> Unit = {},
    /** HOM-02: draws the icon of a pinned folder, shortcut or link; null falls back to the initials tile. */
    iconOther: (@Composable (DockItem, Dp) -> Unit)? = null,
    /** HOM-02: how many columns a pinned widget spans (its home span), and how a pinned widget is drawn into its rail slot. */
    spanOfOther: (DockItemRef) -> Int = { 1 },
    widgetOther: (@Composable (DockItem, Dp, Dp) -> Unit)? = null,
    /** A desktop surface always shows its dock; the tier default only decides for the phone's home (DCK-04, DCK-07). */
    forceEnabled: Boolean = false,
    /** W20 (HOM-23): the phone home shows the rail although the dock setting is off (Glance with Right side set to Dock rail); the rail stays in the focus graph. */
    alwaysShown: Boolean = false,
    /** The edge the host surface reserved for this rail; the desktop surface decides its own (EXT-09). */
    edgeOverride: DockEdge? = null,
    /** INP-11: extra modifiers for the rail itself, applied in composition (the app adds the drop target that pins a dropped app or link). */
    railModifier: @Composable Modifier.() -> Modifier = { this },
    /** W55 (dockSheet): the Desktop item's pin, offered as a switch in the dock's sheet; null where the item is not offered. */
    desktopPin: DockItemRef? = null,
) {
    val context = LocalContext.current
    val manager = remember { WorkspaceRepository.get(context) }
    val state by manager.state.collectAsState()
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tier = remember(configuration.screenWidthDp, refreshKey) { CapabilityEvaluator.tier(launcher.inputs()) }
    if (!forceEnabled && !alwaysShown && !state.dock.isEnabled(tier)) return

    val hub = remember { DockHubHolder.hub(manager) }
    val instance = remember(displayId) { hub.forDisplay(displayId) }
    DisposableEffect(displayId) { onDispose { hub.release(displayId) } }

    val usage = remember { UsageRecents(context) }
    var recents by remember { mutableStateOf<List<String>>(emptyList()) }
    var sinceStart by remember { mutableStateOf<List<String>>(emptyList()) }
    var granted by remember { mutableStateOf(usage.hasAccess()) }
    var resumes by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    // Updated when the launcher resumes (DCK-02), off the main thread.
    LaunchedEffect(refreshKey, resumes) {
        withContext(Dispatchers.IO) {
            granted = usage.hasAccess()
            recents = usage.recents(limit = 24)
            sinceStart = usage.recents(limit = 50, windowMs = SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime())
        }
    }

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val keyByPackage = remember(apps) { apps.groupBy { it.packageName }.mapValues { it.value.first().key } }
    val sessionUsed = remember(sinceStart, apps, resumes, refreshKey) { SessionUse.all() + sinceStart.mapNotNull { keyByPackage[it] } }
    val content = remember(state.active.dockPins, state.groups, apps, recents, granted, state.dock.mode, sessionUsed, unavailableApps) {
        DockContentBuilder.build(
            spanOf = spanOfOther,
            pins = state.active.dockPins,
            labelOf = { ref -> when (ref.kind) { ItemKind.App -> DockUnavailable.appLabel(ref.id, { byKey[it]?.label }, unavailableApps, com.bos.sphere.core.design.L10n.tr("App unavailable")); ItemKind.Group -> state.groups.firstOrNull { it.id == ref.id }?.name; else -> labelOfOther(ref) } },
            recentApps = recents.mapNotNull { keyByPackage[it] },
            recentLabelOf = { byKey[it]?.label },
            usageGranted = granted, mode = state.dock.mode, sessionUsed = sessionUsed,
        ).let { c -> DockUnavailable.mark(c) { id -> byKey[id] != null } }
    }
    // The band's expanded state is recorded on this display's own instance, so two displays never share it (DCK-07).
    var expanded by remember(instance) { mutableStateOf(instance.expanded) }

    fun launchItem(item: DockItem) {
        if (!DockUnavailable.launchable(item)) return // HOM-10: an unavailable pin has nothing to launch
        when (item.ref.kind) {
            ItemKind.App -> byKey[item.ref.id]?.let { SessionUse.mark(it.key); onLaunchApp(it) }
            ItemKind.Group -> state.groups.firstOrNull { it.id == item.ref.id }?.let(onRunGroup)
            else -> onLaunchOther(item.ref)
        }
    }
    // W55 (dockSheet): the sheet around the home (if any) opens from this dock; while it edits the dock, the rail shows even with nothing pinned (just the plus tile).
    val sheet = LocalDockSheet.current
    val editing = sheet != null && sheet.open && sheet.editing
    // W26: nothing pinned and nothing recent: no rail at all (not even the Home glyph), and the phone home's stack plan leaves the room out.
    if (!forceEnabled) androidx.compose.runtime.SideEffect { DockPresence.hasItems = content.hasItems || editing }
    if (!DockVisibility.shown(true, content.hasItems) && !editing) return
    controller.launchNth = { n -> content.numbered.getOrNull(n)?.let { launchItem(it); true } ?: false }
    DisposableEffect(controller) { controller.railShown = true; onDispose { controller.railShown = false; controller.releaseRail() } }
    // A "focus dock" that arrived while an overlay hid the rail is honoured once the rail is back (INP-05); the counter is relative to this entry.
    val focusBase = remember(controller) { controller.focusRequest }
    LaunchedEffect(controller) { if (controller.consumePendingFocus()) { androidx.compose.runtime.withFrameNanos { }; controller.focusRequest++ } }
    controller.step = { d -> if (d > 0) manager.next() else manager.previous() }

    // W25: the pickers behind "Replace with another app" and "Add apps" (the Edit dock dialog); the options themselves are long-press entries.
    var replacing by remember { mutableStateOf<DockItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    val edgeNow = edgeOverride ?: state.dock.edgeFor(landscape)
    val sheetSpec = remember(manager, tier, landscape, edgeOverride, forceEnabled, alwaysShown, desktopPin, onOpenPanel) {
        DockSheetSpec(manager, tier, landscape, edgeOverride, DockOptionsScope(edgeFixed = edgeOverride != null, canHide = !(forceEnabled || alwaysShown), desktopPin = desktopPin), onOpenPanel)
    }
    val actions = remember(content, state.active.dockPins, landscape, edgeOverride, forceEnabled, alwaysShown, onGoHome, sheet, sheetSpec) {
        DockActions(
            onLaunch = { item, _ -> launchItem(item) },
            onReorder = { from, to -> instance.reorder(from, to) },
            onUnpin = { instance.unpin(it.ref) },
            onPin = { instance.pin(it.ref) },
            onEnableUsage = { com.bos.sphere.core.design.GrantHooks.ask(com.bos.sphere.core.design.GrantKind.UsageAccess) { runCatching { context.startActivity(usage.settingsIntent()) } } },
            onOpenPanel = onOpenPanel,
            onOpenOptions = { sheet?.show(sheetSpec) },
            onEditDock = { sheet?.show(sheetSpec); sheet?.startEditing() },
            onGoHome = onGoHome,
            onSwitchWorkspace = { d -> if (d > 0) manager.next() else manager.previous() },
            onToggleExpanded = { expanded = !expanded; instance.expanded = expanded },
            onLeave = { expanded = false; instance.expanded = false },
            onAppInfo = { item -> if (item.ref.kind == ItemKind.App) byKey[item.ref.id]?.let { runCatching { context.startActivity(DockAppOptions.infoIntent(it.packageName)) } } },
            onReplace = { replacing = it },
            onAddApps = { adding = true },
        )
    }
    val edge = edgeNow
    val panelApps = remember(apps) { apps.map { PanelApp(it.key, it.label, it.packageName, launcher.targetOf(it)) } }
    replacing?.let { old ->
        AppPicker(L10n.tr("Replace with"), panelApps, emptySet(), max = 1, min = 1,
            onDone = { picked -> picked.firstOrNull()?.let { k -> DockItemRef.app(k).let { new -> manager.setPins(manager.current.active.dockPins.map { if (it == old.ref) new else it }.distinct()) } }; replacing = null },
            onDismiss = { replacing = null })
    }
    if (adding) AppPicker(L10n.tr("Add apps to the dock"), panelApps, state.active.dockPins.filter { it.kind == ItemKind.App }.map { it.id }.toSet(), max = Int.MAX_VALUE, min = 0,
        onDone = { picked -> manager.setPins(DockPinPicking.apply(state.active.dockPins, picked, panelApps)); adding = false },
        onDismiss = { adding = false })
    DockRail(
        itemExtras = { item -> DockAppOptions.entries(context, launcher, manager, byKey[item.ref.id].takeIf { item.ref.kind == ItemKind.App }) },
        style = state.dock.style, editing = editing, onCoordinates = sheet?.let { s -> { c -> s.dockCoords = c } },
        content = content, edge = edge, workspaceName = state.active.name, expanded = expanded || !content.collapsed,
        focusRequest = controller.focusRequest - focusBase, showNumbers = inputIsKeyboard, actions = actions, modifier = modifier(edge).railModifier(), widget = widgetOther,
        graphBridge = if (forceEnabled) null else DockInput.bridge, // the desktop surface's rail stays out of the phone home's graph
        icon = { item, size -> if (iconOther != null && item.ref.kind != ItemKind.App && item.ref.kind != ItemKind.Group && HomeItemPins.homeKeyOf(item.ref) != null) iconOther(item, size) else DockIcon(item, byKey, size) },
    )
}

/** One hub per process so every display's dock shares the pins. */
internal object DockHubHolder {
    private var hub: DockHub? = null
    @Synchronized fun hub(m: WorkspaceManager): DockHub = hub ?: DockHub({ m.current.active.dockPins }, { m.setPins(it) }).also { hub = it }
}

@Composable
private fun DockIcon(item: DockItem, byKey: Map<String, AppEntry>, size: Dp) {
    val entry = if (item.ref.kind == ItemKind.App) byKey[item.ref.id] else null
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.ref) {
        value = entry?.let { e -> withContext(Dispatchers.Default) { runCatching { e.loadIcon()?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() } }
    }
    Box(Modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (entry != null) bmp?.let { Image(it, item.label, Modifier.size(size)) }
        else {
            // Groups, shortcuts and the desktop item have no app icon: a tile with the label's initials, so they are never blank (WSP-05).
            Box(
                Modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(size / 4)).background(com.bos.sphere.core.design.HyleColors.Violet.copy(alpha = 0.85f)),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { androidx.compose.material3.Text(DockInitials.of(item.label), color = com.bos.sphere.core.design.HyleColors.OnAccent, fontSize = (size.value * 0.36f).sp) }
        }
    }
}

object DockInitials {
    /** One or two capitals from the label; "?" for an empty one. */
    fun of(label: String): String {
        val words = label.trim().split(' ', '-', '_').filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).replaceFirstChar { it.uppercase() }
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }
}

