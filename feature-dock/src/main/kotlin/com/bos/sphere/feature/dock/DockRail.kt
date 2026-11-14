package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** Everything the rail does that touches state outside it. */
class DockActions(
    val onLaunch: (DockItem, Int) -> Unit = { _, _ -> },
    val onReorder: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onUnpin: (DockItem) -> Unit = {},
    val onPin: (DockItem) -> Unit = {},
    val onEnableUsage: () -> Unit = {},
    val onOpenPanel: () -> Unit = {},
    val onSwitchWorkspace: (delta: Int) -> Unit = {},
    val onToggleExpanded: () -> Unit = {},
    val onLeave: () -> Unit = {},
    val onMediaPrev: () -> Unit = {},
    val onMediaPlayPause: () -> Unit = {},
    val onMediaNext: () -> Unit = {},
    val onMediaOpen: () -> Unit = {},
)

/**
 * The dock rail (DCK-01 to DCK-08): pinned items, a Recent section or an enable row, a compact media control, and a workspace chip.
 * Horizontal at the bottom edge, vertical on a side edge. [icon] draws an item's icon so this module needs no app list.
 */
@Composable
fun DockRail(
    content: DockContent,
    edge: DockEdge,
    workspaceName: String,
    expanded: Boolean,
    focusRequest: Int,
    showNumbers: Boolean,
    actions: DockActions,
    modifier: Modifier = Modifier,
    icon: @Composable (DockItem, Dp) -> Unit,
) {
    val horizontal = edge == DockEdge.Bottom
    val collapsedBand = content.collapsed && !expanded
    var helpOpen by remember { mutableStateOf(false) }
    val items = remember(content) { content.pinned + content.recent }
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    var pendingFocus by remember { mutableIntStateOf(-1) }

    LaunchedEffect(focusRequest) { if (focusRequest > 0) requesters.firstOrNull()?.runCatching { requestFocus() } }
    LaunchedEffect(content.pinned.map { it.ref }) {
        if (pendingFocus in requesters.indices) { requesters[pendingFocus].runCatching { requestFocus() }; pendingFocus = -1 }
    }

    val shape = RoundedCornerShape(22.dp)
    val surface = Modifier.clip(shape).background(HyleColors.SurfaceTranslucent).border(1.dp, HyleColors.Hairline, shape)

    if (collapsedBand) {
        // The collapsed band: a slim grab strip that expands the dock on tap, Enter or the dock-focus shortcut.
        Box(
            modifier.then(if (horizontal) Modifier.width(120.dp).height(18.dp) else Modifier.width(18.dp).height(120.dp))
                .then(surface).clickable(onClickLabel = L10n.tr("Show dock"), onClick = actions.onToggleExpanded)
                .semantics { role = Role.Button; contentDescription = L10n.tr("Dock, collapsed") },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.then(if (horizontal) Modifier.width(36.dp).height(4.dp) else Modifier.width(4.dp).height(36.dp)).clip(CircleShape).background(HyleColors.InkDim))
        }
        LaunchedEffect(focusRequest) { if (focusRequest > 0 && !expanded) actions.onToggleExpanded() }
        return
    }

    val pad = Modifier.padding(10.dp)
    @Composable fun Strip(body: @Composable () -> Unit) {
        if (horizontal) Row(Modifier.horizontalScroll(rememberScrollState()).then(pad), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { body() }
        else Column(Modifier.verticalScroll(rememberScrollState()).then(pad), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { body() }
    }

    Box(modifier.then(surface).semantics { contentDescription = L10n.tr("Dock") }) {
        Strip {
            WorkspaceChip(workspaceName, horizontal, actions)
            content.pinned.forEachIndexed { i, item ->
                DockSlot(item, i, items.size, horizontal, showNumbers, icon, actions, requesters[i], items, onFocusMove = { requesters.getOrNull(it)?.runCatching { requestFocus() } }, onMoved = { pendingFocus = it })
            }
            if (content.showRecentSection && content.recent.isNotEmpty()) {
                SectionLabel(L10n.tr(DockText.RECENT_LABEL), horizontal)
                content.recent.forEachIndexed { j, item ->
                    val i = content.pinned.size + j
                    DockSlot(item, i, items.size, horizontal, showNumbers, icon, actions, requesters[i], items, onFocusMove = { requesters.getOrNull(it)?.runCatching { requestFocus() } }, onMoved = {}, reorderable = false)
                }
            }
            if (content.showEnableRow) EnableRow(horizontal, actions.onEnableUsage)
            content.media?.let { MediaItem(it, horizontal, actions) }
            RoundButton("i", L10n.tr("About the dock")) { helpOpen = true }
            RoundButton("...", L10n.tr("Workspaces, groups and dock settings"), actions.onOpenPanel)
        }
    }
    if (helpOpen) HelpDialog { helpOpen = false }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(20.dp)
        Column(Modifier.clip(shape).background(HyleColors.Surface).border(1.dp, HyleColors.Hairline, shape).padding(20.dp)) {
            Text(L10n.tr("About the dock"), color = HyleColors.InkBright, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Text(L10n.tr(DockText.RECENCY_HELP), color = HyleColors.Ink, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(L10n.tr("Alt with an arrow key, or a long press and drag, reorders pinned items."), color = HyleColors.InkDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String, horizontal: Boolean) {
    if (horizontal) Text(text, color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp, end = 2.dp))
    else Text(text, color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun WorkspaceChip(name: String, horizontal: Boolean, actions: DockActions) {
    // Tap opens the workspace panel; a horizontal swipe switches workspace (WSP-07 gesture route).
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(HyleColors.Violet.copy(alpha = 0.22f))
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total > 40f) actions.onSwitchWorkspace(-1) else if (total < -40f) actions.onSwitchWorkspace(1) }) { _, d -> total += d }
            }
            .clickable(onClickLabel = L10n.tr("Workspaces"), onClick = actions.onOpenPanel)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { contentDescription = L10n.tr("Workspace %s", name) },
    ) {
        Text(name, color = HyleColors.InkBright, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.then(if (horizontal) Modifier.width(72.dp) else Modifier.width(44.dp)))
    }
}

@Composable
private fun RoundButton(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(HyleColors.Hairline).clickable(onClickLabel = description, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = HyleColors.Ink, fontSize = 14.sp) }
}

@Composable
private fun EnableRow(horizontal: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, HyleColors.Hairline, RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp).semantics { role = Role.Button },
    ) {
        Text(L10n.tr(DockText.ENABLE_ROW), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.then(if (horizontal) Modifier.width(150.dp) else Modifier.width(60.dp)))
    }
}

@Composable
private fun DockSlot(
    item: DockItem,
    index: Int,
    count: Int,
    horizontal: Boolean,
    showNumbers: Boolean,
    icon: @Composable (DockItem, Dp) -> Unit,
    actions: DockActions,
    requester: FocusRequester,
    all: List<DockItem>,
    onFocusMove: (Int) -> Unit,
    onMoved: (Int) -> Unit,
    reorderable: Boolean = true,
) {
    val slot = 52.dp
    val slotPx = with(LocalDensity.current) { (slot + 8.dp).toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var focused by remember { mutableStateOf(false) }
    val desc = item.label + if (item.recentlyUsed) ", " + L10n.tr("used this session") else ""
    val shape = RoundedCornerShape(16.dp)

    Box(
        Modifier.size(slot)
            .graphicsLayer { if (horizontal) translationX = drag else translationY = drag; alpha = if (drag != 0f) 0.8f else 1f }
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, HyleColors.Violet, shape) else Modifier)
            .focusRequester(requester).onFocusChanged { focused = it.isFocused }.focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (DockKeys.interpret(ev.key.nativeKeyCode, ev.isAltPressed, horizontal)) {
                    DockKeyAction.FocusPrevious -> { onFocusMove(DockKeys.step(index, -1, count)); true }
                    DockKeyAction.FocusNext -> { onFocusMove(DockKeys.step(index, 1, count)); true }
                    DockKeyAction.MoveEarlier -> if (reorderable && index > 0) { actions.onReorder(index, index - 1); onMoved(index - 1); true } else false
                    DockKeyAction.MoveLater -> if (reorderable) { actions.onReorder(index, index + 1); onMoved(index + 1); true } else false
                    DockKeyAction.Activate -> { actions.onLaunch(item, index); true }
                    DockKeyAction.Leave -> { actions.onLeave(); true }
                    DockKeyAction.None -> false
                }
            }
            .clickable { actions.onLaunch(item, index) }
            .then(
                if (reorderable) Modifier.pointerInput(index, count) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            val to = DockReorder.targetIndex(index, drag, slotPx, count)
                            drag = 0f
                            if (to != index) actions.onReorder(index, to)
                        },
                        onDragCancel = { drag = 0f },
                    ) { change, amount -> change.consume(); drag += if (horizontal) amount.x else amount.y }
                } else Modifier,
            )
            .semantics {
                contentDescription = desc
                role = Role.Button
                if (reorderable) customActions = listOf(
                    CustomAccessibilityAction(L10n.tr("Move earlier")) { if (index > 0) { actions.onReorder(index, index - 1); true } else false },
                    CustomAccessibilityAction(L10n.tr("Move later")) { actions.onReorder(index, index + 1); true },
                    CustomAccessibilityAction(L10n.tr("Unpin")) { actions.onUnpin(item); true },
                ) else customActions = listOf(CustomAccessibilityAction(L10n.tr("Pin to dock")) { actions.onPin(item); true })
            },
        contentAlignment = Alignment.Center,
    ) {
        icon(item, 40.dp)
        if (item.recentlyUsed) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(8.dp).clip(CircleShape).background(HyleColors.Violet))
        if (showNumbers && index < 9) Text("${index + 1}", color = HyleColors.OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(2.dp).clip(CircleShape).background(HyleColors.Violet).padding(horizontal = 5.dp))
    }
}

/** Compact transport: previous, play or pause, next, drawn as shapes so it does not depend on a font (DCK-08). */
@Composable
private fun MediaItem(info: MediaInfo, horizontal: Boolean, actions: DockActions) {
    val shape = RoundedCornerShape(16.dp)
    val body: @Composable () -> Unit = {
        TransportButton(Transport.Prev, info.canPrev, L10n.tr("Previous track"), actions.onMediaPrev)
        TransportButton(if (info.playing) Transport.Pause else Transport.Play, true, if (info.playing) L10n.tr("Pause") else L10n.tr("Play"), actions.onMediaPlayPause)
        TransportButton(Transport.Next, info.canNext, L10n.tr("Next track"), actions.onMediaNext)
    }
    val m = Modifier.clip(shape).background(HyleColors.Violet.copy(alpha = 0.16f)).clickable(onClickLabel = info.title, onClick = actions.onMediaOpen)
        .padding(4.dp).semantics { contentDescription = L10n.tr("Now playing: %s", info.title) }
    if (horizontal) Row(m, verticalAlignment = Alignment.CenterVertically) { body() } else Column(m, horizontalAlignment = Alignment.CenterHorizontally) { body() }
}

private enum class Transport { Prev, Play, Pause, Next }

@Composable
private fun TransportButton(kind: Transport, enabled: Boolean, description: String, onClick: () -> Unit) {
    val tint = if (enabled) HyleColors.InkBright else HyleColors.InkDim.copy(alpha = 0.4f)
    Box(
        Modifier.size(36.dp).clickable(enabled = enabled, onClickLabel = description, onClick = onClick).semantics { role = Role.Button; contentDescription = description }
            .drawBehind {
                val w = size.width; val h = size.height; val s = w * 0.28f
                val cx = w / 2; val cy = h / 2
                fun tri(dir: Float, x: Float) = Path().apply { moveTo(x + dir * s, cy); lineTo(x - dir * s * 0.8f, cy - s); lineTo(x - dir * s * 0.8f, cy + s); close() }
                when (kind) {
                    Transport.Play -> drawPath(tri(1f, cx), tint)
                    Transport.Pause -> { drawRect(tint, Offset(cx - s * 0.9f, cy - s), androidx.compose.ui.geometry.Size(s * 0.6f, s * 2)); drawRect(tint, Offset(cx + s * 0.3f, cy - s), androidx.compose.ui.geometry.Size(s * 0.6f, s * 2)) }
                    Transport.Next -> { drawPath(tri(1f, cx - s * 0.2f), tint); drawRect(tint, Offset(cx + s * 0.7f, cy - s), androidx.compose.ui.geometry.Size(s * 0.35f, s * 2)) }
                    Transport.Prev -> { drawPath(tri(-1f, cx + s * 0.2f), tint); drawRect(tint, Offset(cx - s * 1.05f, cy - s), androidx.compose.ui.geometry.Size(s * 0.35f, s * 2)) }
                }
            },
    )
}

