package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.OneUiCaption

/**
 * W55 (dockSheet): the top of Settings > Desktop & dock > Dock. The real [DockRail] drawn with the current settings (style, position, mode) over a
 * wallpaper-like frame, with the pinned apps as initials tiles (the settings page has no app icons loaded). It is inert: nothing in it takes a touch.
 * With nothing pinned (the real dock would not show at all) it draws four sample tiles and says so.
 */
@Composable
fun DockLivePreview(manager: WorkspaceManager, apps: List<PanelApp>, landscape: Boolean, modifier: Modifier = Modifier) {
    val state by manager.state.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val pins = state.active.dockPins
    val real = pins.mapNotNull { ref ->
        val label = when (ref.kind) { ItemKind.App -> byKey[ref.id]?.label; ItemKind.Group -> state.groups.firstOrNull { it.id == ref.id }?.name; else -> ref.id }
        label?.let { DockItem(ref, it) }
    }
    val sample = real.isEmpty()
    val pinned = if (sample) apps.take(4).map { DockItem(DockItemRef.app(it.key), it.label) } else real
    val pinnedKeys = pinned.mapTo(HashSet()) { it.ref.id }
    val recent = if (state.dock.mode == DockMode.PinnedPlusRecent) apps.filter { it.key !in pinnedKeys }.take(2).map { DockItem(DockItemRef.app(it.key), it.label) } else emptyList()
    val content = DockContent(pinned, recent, showEnableRow = false, showRecentSection = recent.isNotEmpty(), collapsed = state.dock.mode == DockMode.Collapsed)
    val edge = state.dock.edgeFor(landscape)
    val frame = RoundedCornerShape(26.dp)
    val wall = Brush.linearGradient(listOf(Color(0xFFE9D8A6), Color(0xFF6FA8DC), Color(0xFF3B4C7A)))
    Box(modifier.padding(horizontal = 16.dp).testTag(DockPageTags.PREVIEW)) {
        Box(
            Modifier.fillMaxWidth().height(if (edge == DockEdge.Bottom) 132.dp else 200.dp).clip(frame).background(wall)
                .semantics { contentDescription = L10n.tr("Dock preview. %s, %s.", L10n.tr(state.dock.style.label), L10n.tr(edge.label)) }
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } },
            contentAlignment = when (edge) { DockEdge.Bottom -> Alignment.BottomCenter; DockEdge.Left -> Alignment.CenterStart; DockEdge.Right -> Alignment.CenterEnd },
        ) {
            DockRail(
                content, edge, state.active.name, expanded = false, focusRequest = 0, showNumbers = false, actions = DockActions(), graphBridge = null,
                modifier = Modifier.padding(10.dp), style = state.dock.style, dark = !HyleTheme.isLight, magnify = state.dock.magnifyStrength.takeIf { state.dock.magnify },
            ) { item, size -> PreviewTile(item, size) }
        }
    }
    if (sample) OneUiCaption(L10n.tr("Nothing is pinned yet, so the dock is hidden. Sample icons are shown."))
}

@Composable
private fun PreviewTile(item: DockItem, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(HyleColors.Violet.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Text(DockInitials.of(item.label), color = HyleColors.OnAccent, fontSize = (size.value * 0.36f).sp)
    }
}
