package com.bos.sphere

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.catalog.LayoutEditSession
import com.bos.sphere.core.catalog.SizeClass
import com.bos.sphere.core.catalog.SizeClassLayoutStore
import com.bos.sphere.core.data.HomeLayoutStore
import kotlinx.coroutines.flow.drop

/**
 * HOM-11: keeps one page arrangement per window size class. When the class changes (rotation,
 * folding, split-screen resize, moving to another display) the arrangement on screen is stored
 * under the old class and the new class's own arrangement (seeded from the nearest class the
 * first time) is loaded into the live store. Between changes every edit is stored under the
 * active class.
 */
@Composable
fun SizeClassLayoutBinding(layoutStore: HomeLayoutStore) {
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
        layoutStore.layout.collect { store.record(sc, it) }
    }
}

/** HOM-12: edit mode state for the live home; see [com.bos.sphere.core.catalog.LayoutEditSession]. */
class HomeEditState internal constructor(private val layoutStore: HomeLayoutStore) {
    private var session: LayoutEditSession? = null
    internal var rev by mutableIntStateOf(0)
    val active: Boolean get() { rev; return session?.active == true }
    val canUndo: Boolean get() { rev; return session?.canUndo == true }
    val canRedo: Boolean get() { rev; return session?.canRedo == true }
    val undoDepth: Int get() { rev; return session?.undoDepth ?: 0 }

    fun enter() { session = LayoutEditSession(layoutStore.layout.value); rev++ }
    fun exit() { session?.exit(); session = null; rev++ }
    fun undo() { session?.undo()?.let { layoutStore.replace(it) }; rev++ }
    fun redo() { session?.redo()?.let { layoutStore.replace(it) }; rev++ }
    internal fun observe(layout: com.bos.sphere.core.data.HomeLayout) { if (session?.observe(layout) == true) rev++ }
}

@Composable
fun rememberHomeEdit(layoutStore: HomeLayoutStore): HomeEditState {
    val state = remember(layoutStore) { HomeEditState(layoutStore) }
    LaunchedEffect(state) { layoutStore.layout.collect { state.observe(it) } }
    return state
}
