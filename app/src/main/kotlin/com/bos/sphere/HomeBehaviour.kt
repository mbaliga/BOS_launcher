package com.bos.sphere

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.catalog.DockPinsView
import com.bos.sphere.core.catalog.EditRestore
import com.bos.sphere.core.catalog.LayoutDockEditSession
import com.bos.sphere.core.catalog.SizeClass
import com.bos.sphere.core.catalog.SizeClassLayoutStore
import com.bos.sphere.core.data.HomeLayoutStore
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/**
 * HOM-11: keeps one page arrangement per window size class. When the class changes (rotation,
 * folding, split-screen resize, moving to another display) the arrangement on screen is stored
 * under the old class and the new class's own arrangement (seeded from the nearest class the
 * first time) is loaded into the live store. Between changes every edit is stored under the
 * active class.
 */
@Composable
fun SizeClassLayoutBinding(layoutStore: HomeLayoutStore, installedKeys: Set<String> = emptySet()) {
    val installed by rememberUpdatedState(installedKeys)
    val context = LocalContext.current
    val cfg: Configuration = LocalConfiguration.current
    val sc = SizeClass.of(cfg.screenWidthDp, cfg.screenHeightDp)
    val store = remember { SizeClassLayoutStore(context) }
    var active by remember { mutableStateOf<SizeClass?>(null) }
    LaunchedEffect(sc.key) {
        val now = layoutStore.layout.value
        val next = store.activate(sc, now, active)
        active = sc
        if (next != now) layoutStore.replace(next)
        // A layout stored under another class may remember removals for apps uninstalled since.
        if (installed.isNotEmpty()) layoutStore.pruneRemoved(installed)
        layoutStore.layout.collect { store.record(sc, it) }
    }
}

/** HOM-12: what edit mode asks of the item host: keep removals undoable while it lasts, settle them when it ends. */
interface HomeEditHooks {
    fun begin(pages: List<com.bos.sphere.core.data.HomePage>)
    /** The arrangement [pages] is now current (a change, or an echo of our own write). */
    fun observed(pages: List<com.bos.sphere.core.data.HomePage>)
    /** An undo or redo took the arrangement from [before] to [after]. */
    fun revived(before: List<com.bos.sphere.core.data.HomePage>, after: List<com.bos.sphere.core.data.HomePage>)
    fun end(pages: List<com.bos.sphere.core.data.HomePage>)
}

/** HOM-12: edit mode state for the live home; see [com.bos.sphere.core.catalog.LayoutEditSession]. */
class HomeEditState internal constructor(private val layoutStore: HomeLayoutStore, private val dock: HomeDockPins? = null) {
    private var session: LayoutDockEditSession? = null
    internal var rev by mutableIntStateOf(0)
    val active: Boolean get() { rev; return session?.active == true }
    val canUndo: Boolean get() { rev; return session?.canUndo == true }
    val canRedo: Boolean get() { rev; return session?.canRedo == true }
    val undoDepth: Int get() { rev; return session?.undoDepth ?: 0 }

    /** Set by the screen once the item host exists. */
    var hooks: HomeEditHooks? = null

    fun enter() { session = LayoutDockEditSession(layoutStore.layout.value, dock?.view() ?: DockPinsView("", emptyList())); hooks?.begin(layoutStore.layout.value.pages); rev++ }
    fun exit() {
        if (session != null) hooks?.end(layoutStore.layout.value.pages)
        session?.exit(); session = null; rev++
    }
    private fun write(was: List<com.bos.sphere.core.data.HomePage>, r: EditRestore?) {
        r ?: return
        // HOM-12: the dock pins go back first so the rail and the page are never seen out of step.
        val ws = r.workspaceId; val pins = r.pins
        if (pins != null && ws != null) dock?.write(ws, pins)
        layoutStore.replace(r.layout); hooks?.revived(was, r.layout.pages)
    }
    fun undo() { write(layoutStore.layout.value.pages, session?.undo()); rev++ }
    fun redo() { write(layoutStore.layout.value.pages, session?.redo()); rev++ }
    /** HOM-12: the rail's pin list changed (a move to the dock, a pin, an unpin, a reorder). */
    internal fun observePins(view: DockPinsView) { if (session?.observePins(view) == true) rev++ }
    internal fun observe(layout: com.bos.sphere.core.data.HomeLayout) {
        if (session != null) hooks?.observed(layout.pages)
        if (session?.observe(layout) == true) rev++
    }
}

/** HOM-12: the dock pin list as the layout undo sees it: read the active list, write one back. Pins are encoded refs. */
interface HomeDockPins {
    fun view(): DockPinsView
    fun changes(): kotlinx.coroutines.flow.Flow<DockPinsView>
    fun write(workspaceId: String, pins: List<String>)
}

@Composable
fun rememberHomeEdit(layoutStore: HomeLayoutStore, dock: HomeDockPins? = null): HomeEditState {
    val state = remember(layoutStore, dock) { HomeEditState(layoutStore, dock) }
    LaunchedEffect(state) { layoutStore.layout.collect { state.observe(it) } }
    if (dock != null) LaunchedEffect(state) { dock.changes().collect { state.observePins(it) } }
    return state
}

/** HOM-12: the active workspace's dock pins, for the layout undo. */
class WorkspaceHomeDockPins(private val manager: com.bos.sphere.feature.dock.WorkspaceManager) : HomeDockPins {
    private fun view(s: com.bos.sphere.feature.dock.DockWorkspaceState) = DockPinsView(s.active.id, s.active.dockPins.map { it.encode() })
    override fun view() = view(manager.current)
    override fun changes() = manager.state.map { view(it) }
    override fun write(workspaceId: String, pins: List<String>) {
        if (manager.current.active.id != workspaceId) return
        manager.setPins(pins.mapNotNull(com.bos.sphere.feature.dock.DockItemRef::decode))
    }
}
