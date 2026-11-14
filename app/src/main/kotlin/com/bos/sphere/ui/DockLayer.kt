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
import com.bos.sphere.core.data.AppEntry
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
        WorkspaceRepository.hooks = AppWorkspaceHooks(stores)
        manager.applyActive()
        onDispose { }
    }
    var panelOpen by remember { mutableStateOf(false) }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    DisposableEffect(Unit) { onDispose { } }

    if (visible) DockHost(
        apps = apps, launcher = launcher, controller = controller, refreshKey = homeSignal,
        onLaunchApp = onLaunch, onOpenPanel = { panelOpen = true }, inputIsKeyboard = keyboardMode,
        onRunGroup = { g -> scope.launch { launcher.runGroup(g) } },
        modifier = { edge ->
            when (edge) {
                DockEdge.Bottom -> Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(start = 76.dp, end = 76.dp, bottom = 8.dp)
                DockEdge.Left -> Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(start = 12.dp, top = 76.dp, bottom = 76.dp)
                DockEdge.Right -> Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(end = 12.dp, top = 76.dp, bottom = 76.dp)
            }
        },
    )
    if (panelOpen) {
        val tier = remember { CapabilityEvaluator.tier(launcher.inputs()) }
        val panelApps = remember(apps) { apps.map { PanelApp(it.key, it.label, it.packageName, launcher.targetOf(it)) } }
        DockPanel(
            manager = manager, tier = tier, apps = panelApps, displays = launcher.displays(),
            onRunGroup = { g -> scope.launch { launcher.runGroup(g) }; panelOpen = false },
            onRestore = { s -> scope.launch { launcher.restore(s) }; panelOpen = false },
            onSchedule = { target, label, at ->
                val sl = ScheduledLaunch(manager.idFor("sc"), label, at, target)
                manager.addSchedule(sl); Scheduler.schedule(context, sl)
                android.widget.Toast.makeText(context, "A notification with an Open action will appear in about an hour", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { panelOpen = false },
        )
    }
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

