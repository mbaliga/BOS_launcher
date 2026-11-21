package com.bos.sphere.feature.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** One entry of the desktop dock. */
data class DockEntry(val key: String, val label: String, val activate: () -> Unit, val icon: (() -> android.graphics.drawable.Drawable?)? = null)

/** Everything a dock rail needs to draw itself on the desktop surface. Own instance per display (EXT-03). */
data class DesktopDockState(
    val displayId: Int,
    val edge: DockEdge,
    val entries: List<DockEntry>,
    /** Icon size in dp from [DesktopLayout.iconDp]. */
    val iconDp: Float,
    val onShowAllApps: () -> Unit,
)

/**
 * Placeholder interface for the dock stream: its rail implements [DesktopDock] and sets
 * [DesktopDockSlot.provider]. Until then the surface renders [BasicRecentDock].
 */
interface DesktopDock {
    @Composable fun Content(state: DesktopDockState, modifier: Modifier)
}

object DesktopDockSlot {
    @Volatile var provider: DesktopDock? = null
}
