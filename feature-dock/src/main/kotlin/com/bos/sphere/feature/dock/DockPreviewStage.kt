package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n

/**
 * Wave 57 (previewAll), CFG-51: the dock rail for the floating Settings Preview, drawn the way [DockHost] places it on the home (the same edge,
 * the same margins, the chosen style, the magnification strength and mode), over a transparent background (the Preview draws the wallpaper below).
 * Pinned apps are initials tiles; with nothing pinned it shows four of the user's apps as a sample. Inert: no touch, focus graph or menu.
 */
@Composable
fun DockPreviewStage(manager: WorkspaceManager, apps: List<PanelApp>, landscape: Boolean, modifier: Modifier = Modifier) {
    val state by manager.state.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val real = state.active.dockPins.mapNotNull { ref ->
        val label = when (ref.kind) { ItemKind.App -> byKey[ref.id]?.label; ItemKind.Group -> state.groups.firstOrNull { it.id == ref.id }?.name; else -> ref.id }
        label?.let { DockItem(ref, it) }
    }
    val pinned = real.ifEmpty { apps.take(4).map { DockItem(DockItemRef.app(it.key), it.label) } }
    val pinnedKeys = pinned.mapTo(HashSet()) { it.ref.id }
    val recent = if (state.dock.mode == DockMode.PinnedPlusRecent) apps.filter { it.key !in pinnedKeys }.take(2).map { DockItem(DockItemRef.app(it.key), it.label) } else emptyList()
    val content = DockContent(pinned, recent, showEnableRow = false, showRecentSection = recent.isNotEmpty(), collapsed = state.dock.mode == DockMode.Collapsed)
    val edge = state.dock.edgeFor(landscape)
    val m = if (state.dock.magnify) 20.dp else 76.dp
    Box(modifier.fillMaxSize()) {
        val place = when (edge) {
            DockEdge.Bottom -> Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(start = m, end = m, bottom = 8.dp)
            DockEdge.Left -> Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(start = 12.dp, top = m, bottom = m)
            DockEdge.Right -> Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(end = 12.dp, top = m, bottom = m)
        }
        DockRail(
            content, edge, state.active.name, expanded = false, focusRequest = 0, showNumbers = false, actions = DockActions(), graphBridge = null,
            modifier = place, style = state.dock.style, dark = !HyleTheme.isLight, magnify = state.dock.magnifyStrength.takeIf { state.dock.magnify },
            // Wave 58 (CFG-73): the magnification drawn as a sample bulge, a still pointer over the second icon
            demoPointer = if (state.dock.magnify) DEMO_POINTER else null,
        ) { item, size -> StageTile(item, size) }
    }
}

/** Where the Preview's still sample pointer rests along the dock (a fraction of its length): over the second of four or six icons. */
const val DEMO_POINTER = 0.3f

/** The dock's words for the screen reader: "Standard, Bottom" (and the magnification strength when it is on). */
@Composable
fun dockPreviewDetail(manager: WorkspaceManager, landscape: Boolean): String {
    val state by manager.state.collectAsState()
    val d = state.dock
    val base = L10n.tr("%s, %s", L10n.tr(d.style.label), L10n.tr(d.edgeFor(landscape).label))
    return if (d.magnify) base + ", " + L10n.tr(d.magnifyStrength.label) else base
}

@Composable
private fun StageTile(item: DockItem, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(HyleColors.Violet.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Text(DockInitials.of(item.label), color = HyleColors.OnAccent, fontSize = (size.value * 0.36f).sp)
    }
}
