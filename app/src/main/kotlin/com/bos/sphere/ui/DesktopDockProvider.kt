package com.bos.sphere.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.feature.desktop.DesktopController
import com.bos.sphere.feature.desktop.DesktopDock
import com.bos.sphere.feature.desktop.DesktopDockState
import com.bos.sphere.feature.dock.AppLauncher
import com.bos.sphere.feature.dock.DockController
import com.bos.sphere.feature.dock.DockEdge
import com.bos.sphere.feature.dock.DockHost
import com.bos.sphere.feature.dock.ItemKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The dock stream's rail on the desktop surface (DCK-07, EXT-03): one [DockHost] per display, sharing the active workspace's
 * pins with the phone's dock and keeping its own focus and expansion. Launches go to the display the surface is on (EXT-07).
 * [controllers] lets the activity route keyboard shortcuts to the right display's dock (DCK-05).
 */
object DesktopDockProvider : DesktopDock {
    private val controllers = java.util.concurrent.ConcurrentHashMap<Int, DockController>()

    /** True when the dock of [displayId] handled the command. */
    fun handle(displayId: Int, command: LauncherCommand): Boolean = controllers[displayId]?.handle(command) == true

    fun controllerFor(displayId: Int): DockController? = controllers[displayId]

    @Composable
    override fun Content(state: DesktopDockState, modifier: Modifier) {
        val context = LocalContext.current
        val view = LocalView.current
        val repo = remember { LauncherAppsAppRepository(context) }
        val apps by repo.apps.collectAsState(initial = emptyList())
        val launcher = remember { AppLauncher(context, rootView = { view }) }
        val controller = remember(state.displayId) { DockController() }
        val desktop = remember { DesktopController.get(context) }
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
        var panelOpen by remember { mutableStateOf(false) }
        DisposableEffect(state.displayId, controller) {
            controllers[state.displayId] = controller
            val manager = com.bos.sphere.feature.dock.WorkspaceRepository.get(context)
            controller.bindWorkspaceStep { d -> if (d > 0) manager.next() else manager.previous() }
            onDispose { if (controllers[state.displayId] === controller) controllers.remove(state.displayId) }
        }
        // The scope outlives a display change of the same surface; it ends only when the dock leaves the screen.
        DisposableEffect(scope) { onDispose { scope.cancel() } }
        Box(modifier.fillMaxSize()) {
            DockHost(
                apps = apps, launcher = launcher, controller = controller, refreshKey = 0,
                onLaunchApp = { a ->
                    LauncherStores.get(context).history.record(a.key)
                    desktop.launchApp(a.packageName, a.componentName.className, 0, state.displayId)
                },
                onOpenPanel = { panelOpen = true },
                onRunGroup = { g -> scope.launch { launcher.runGroup(g, state.displayId) } },
                labelOfOther = { ref -> if (ref.kind == ItemKind.Shortcut && ref.id == DESKTOP_PIN_ID) L10n.tr("Desktop") else null },
                // The surface is already open here; the item opens it on another display when one is ready.
                onLaunchOther = { ref -> if (ref.id == DESKTOP_PIN_ID) desktop.reopenAction()?.run() },
                modifier = { _ -> Modifier.fillMaxSize() },
                displayId = state.displayId,
                inputIsKeyboard = true,
                forceEnabled = true,
                edgeOverride = when (state.edge) {
                    com.bos.sphere.feature.desktop.DockEdge.Left -> DockEdge.Left
                    com.bos.sphere.feature.desktop.DockEdge.Right -> DockEdge.Right
                    else -> DockEdge.Bottom
                },
            )
            if (panelOpen) DockPanelHost(
                apps, launcher, onDismiss = { panelOpen = false },
                onRunGroup = { g -> scope.launch { launcher.runGroup(g, state.displayId) } },
                onRestore = { s -> scope.launch { launcher.restore(s) } },
            )
        }
    }
}
