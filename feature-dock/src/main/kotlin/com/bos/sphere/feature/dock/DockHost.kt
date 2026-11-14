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
import com.bos.sphere.core.capabilities.CapabilityEvaluator
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.input.KeyChord
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.ShortcutRegistry
import com.bos.sphere.core.signals.MediaSignals
import com.bos.sphere.core.signals.UsageRecents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The dock's command surface: the input layer resolves a shortcut to a [LauncherCommand] and calls [handle] (DCK-05). */
class DockController {
    var focusRequest by mutableIntStateOf(0)
        internal set
    internal var launchNth: (Int) -> Boolean = { false }
    internal var step: (Int) -> Boolean = { false }

    /** Lets a host that has no dock on screen still step workspaces from the shortcut keys (WSP-07). */
    fun bindWorkspaceStep(f: (Int) -> Boolean) { step = f }
    private val dock = DockCommandHandler(focusDock = { focusRequest++ }, launchNth = { launchNth(it) })

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
    modifier: (DockEdge) -> Modifier,
    displayId: Int = 0,
    inputIsKeyboard: Boolean = false,
    labelOfOther: (DockItemRef) -> String? = { null },
    onLaunchOther: (DockItemRef) -> Unit = {},
    onRunGroup: (com.bos.sphere.core.launch.AppGroup) -> Unit = {},
    /** A desktop surface always shows its dock; the tier default only decides for the phone's home (DCK-04, DCK-07). */
    forceEnabled: Boolean = false,
    /** The edge the host surface reserved for this rail; the desktop surface decides its own (EXT-09). */
    edgeOverride: DockEdge? = null,
) {
    val context = LocalContext.current
    val manager = remember { WorkspaceRepository.get(context) }
    val state by manager.state.collectAsState()
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tier = remember(configuration.screenWidthDp, refreshKey) { CapabilityEvaluator.tier(launcher.inputs()) }
    if (!forceEnabled && !state.dock.isEnabled(tier)) return

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
    DisposableEffect(Unit) { MediaSignals.start(context); onDispose { MediaSignals.stop() } }
    val media by MediaSignals.info.collectAsState()

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val keyByPackage = remember(apps) { apps.groupBy { it.packageName }.mapValues { it.value.first().key } }
    val sessionUsed = remember(sinceStart, apps, resumes, refreshKey) { SessionUse.all() + sinceStart.mapNotNull { keyByPackage[it] } }
    val content = remember(state.active.dockPins, state.groups, apps, recents, granted, state.dock.mode, sessionUsed, media) {
        DockContentBuilder.build(
            pins = state.active.dockPins,
            labelOf = { ref -> when (ref.kind) { ItemKind.App -> byKey[ref.id]?.label; ItemKind.Group -> state.groups.firstOrNull { it.id == ref.id }?.name; else -> labelOfOther(ref) } },
            recentApps = recents.mapNotNull { keyByPackage[it] },
            recentLabelOf = { byKey[it]?.label },
            usageGranted = granted, mode = state.dock.mode, sessionUsed = sessionUsed, media = media,
        )
    }
    var expanded by remember { mutableStateOf(false) }

    fun launchItem(item: DockItem) {
        when (item.ref.kind) {
            ItemKind.App -> byKey[item.ref.id]?.let { SessionUse.mark(it.key); onLaunchApp(it) }
            ItemKind.Group -> state.groups.firstOrNull { it.id == item.ref.id }?.let(onRunGroup)
            else -> onLaunchOther(item.ref)
        }
    }
    controller.launchNth = { n -> content.numbered.getOrNull(n)?.let { launchItem(it); true } ?: false }
    controller.step = { d -> if (d > 0) manager.next() else manager.previous() }

    val actions = remember(content, state.active.dockPins) {
        DockActions(
            onLaunch = { item, _ -> launchItem(item) },
            onReorder = { from, to -> instance.reorder(from, to) },
            onUnpin = { instance.unpin(it.ref) },
            onPin = { instance.pin(it.ref) },
            onEnableUsage = { runCatching { context.startActivity(usage.settingsIntent()) } },
            onOpenPanel = onOpenPanel,
            onSwitchWorkspace = { d -> if (d > 0) manager.next() else manager.previous() },
            onToggleExpanded = { expanded = !expanded },
            onLeave = { expanded = false },
            onMediaPrev = MediaSignals::previous, onMediaPlayPause = MediaSignals::playPause, onMediaNext = MediaSignals::next,
            onMediaOpen = { MediaSignals.open(context) },
        )
    }
    val edge = edgeOverride ?: state.dock.edgeFor(landscape)
    DockRail(
        content = content, edge = edge, workspaceName = state.active.name, expanded = expanded || !content.collapsed,
        focusRequest = controller.focusRequest, showNumbers = inputIsKeyboard, actions = actions, modifier = modifier(edge),
        icon = { item, size -> DockIcon(item, byKey, size) },
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

