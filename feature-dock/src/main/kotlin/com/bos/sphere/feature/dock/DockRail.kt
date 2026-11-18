package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
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
    /** W26: a tap on the Home glyph goes to the home's first page (nothing when already there). It never opens the panel: that is the long-press menu's job. */
    val onGoHome: () -> Unit = {},
    val onSwitchWorkspace: (delta: Int) -> Unit = {},
    val onToggleExpanded: () -> Unit = {},
    val onLeave: () -> Unit = {},
    /** W25: the options that used to sit behind "i" and "...", now behind a long press. */
    val onAppInfo: (DockItem) -> Unit = {},
    val onReplace: (DockItem) -> Unit = {},
    val onAddApps: () -> Unit = {},
    /** Null when the dock cannot be hidden from here (a desktop surface, or a landing that forces the rail). */
    val onHide: (() -> Unit)? = null,
    /** Null when the dock has no side to choose (portrait, or the surface decides its own edge). */
    val onSetEdge: ((DockEdge) -> Unit)? = null,
)

/**
 * The dock rail (DCK-01 to DCK-08): pinned items, a Recent section or an enable row, and a workspace chip.
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
    /** INP-01: where this rail's items register on the shared focus graph; null for a rail that must stay out of it (the desktop surface's). */
    graphBridge: DockInput.FocusBridge? = DockInput.bridge,
    /** HOM-02: draws a pinned widget into a slot of the given width and height; null draws it as an icon like any other item. */
    widget: (@Composable (DockItem, Dp, Dp) -> Unit)? = null,
    /** W25: the host's own entries for an icon's long-press menu (an app's shortcuts and open choices); loaded when the menu opens. */
    itemExtras: suspend (DockItem) -> List<DockMenuEntry> = { emptyList() },
    icon: @Composable (DockItem, Dp) -> Unit,
) {
    val horizontal = edge == DockEdge.Bottom
    val collapsedBand = content.collapsed && !expanded
    var helpOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    // W25: the long-press menu: null, the dock's own (index -1) or one icon's; [menuPress] is where it opens, in window pixels.
    var menuFor by remember { mutableIntStateOf(MENU_NONE) }
    var menuPress by remember { mutableStateOf(androidx.compose.ui.unit.IntOffset.Zero) }
    var railOrigin by remember { mutableStateOf(Offset.Zero) }
    var railSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val slotBounds = remember { HashMap<Int, androidx.compose.ui.geometry.Rect>() }
    val items = remember(content) { content.pinned + content.recent }
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    var pendingFocus by remember { mutableIntStateOf(-1) }

    LaunchedEffect(focusRequest) { if (focusRequest > 0) requesters.firstOrNull()?.runCatching { requestFocus() } }
    LaunchedEffect(content.pinned.map { it.ref }) {
        if (pendingFocus in requesters.indices) { requesters[pendingFocus].runCatching { requestFocus() }; pendingFocus = -1 }
    }

    val dockEntries = DockMenuModel.dock(actions, edge, onEdit = { editOpen = true }, onAbout = { helpOpen = true })
    val openDockMenu = { at: Offset -> menuPress = androidx.compose.ui.unit.IntOffset(at.x.toInt(), at.y.toInt()); menuFor = MENU_DOCK }
    val openItemMenu = { i: Int ->
        val b = slotBounds[i]
        val at = if (b != null) Offset(b.center.x, b.bottom) else railOrigin
        menuPress = androidx.compose.ui.unit.IntOffset(at.x.toInt(), at.y.toInt()); menuFor = i
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

    Box(
        modifier.then(surface)
            .onGloballyPositioned { c -> railOrigin = c.positionInRoot(); railSize = c.size }
            // W25: a long press on the dock (an icon or the empty part) opens its options; the menu key and Shift+F10 do the same from the keyboard.
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { p ->
                    val root = Offset(p.x + railOrigin.x, p.y + railOrigin.y)
                    val hit = slotBounds.entries.firstOrNull { it.value.contains(root) }?.key
                    if (hit != null) openItemMenu(hit) else openDockMenu(root)
                })
            }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && isMenuKey(ev)) { openDockMenu(Offset(railOrigin.x + railSize.width / 2f, railOrigin.y + railSize.height.toFloat())); true } else false
            }
            .semantics {
                contentDescription = L10n.tr("Dock")
                // TalkBack: every long-press action is also an action here.
                customActions = dockEntries.map { e -> CustomAccessibilityAction(e.label) { e.run(); true } }
            },
    ) {
        Strip {
            WorkspaceChip(workspaceName, actions, graphBridge)
            content.pinned.forEachIndexed { i, item ->
                DockSlot(item, i, items.size, horizontal, showNumbers, icon, actions, requesters[i], items, onFocusMove = { requesters.getOrNull(it)?.runCatching { requestFocus() } }, onMoved = { pendingFocus = it }, bridge = graphBridge, widget = widget,
                    onBounds = { slotBounds[i] = it }, onMenu = { openItemMenu(i) }, onDragBegin = { menuFor = MENU_NONE })
            }
            if (content.showRecentSection && content.recent.isNotEmpty()) {
                SectionDivider(horizontal)
                content.recent.forEachIndexed { j, item ->
                    val i = content.pinned.size + j
                    DockSlot(item, i, items.size, horizontal, showNumbers, icon, actions, requesters[i], items, onFocusMove = { requesters.getOrNull(it)?.runCatching { requestFocus() } }, onMoved = {}, reorderable = false, bridge = graphBridge,
                        onBounds = { slotBounds[i] = it }, onMenu = { openItemMenu(i) }, onDragBegin = { menuFor = MENU_NONE })
                }
            }
            if (content.showEnableRow) EnableButton(actions.onEnableUsage, graphBridge)
        }
    }
    if (menuFor == MENU_DOCK) DockMenuPopup(menuPress, DockMenuModel.TAG_DOCK_MENU, dockEntries) { menuFor = MENU_NONE }
    else if (menuFor >= 0) items.getOrNull(menuFor)?.let { item ->
        val extra by androidx.compose.runtime.produceState(emptyList<DockMenuEntry>(), item) { value = runCatching { itemExtras(item) }.getOrDefault(emptyList()) }
        DockMenuPopup(menuPress, DockMenuModel.TAG_ITEM_MENU, DockMenuModel.item(item, menuFor, items.size, menuFor < content.pinned.size, actions, extra)) { menuFor = MENU_NONE }
    }
    if (editOpen) EditDockDialog(content, actions) { editOpen = false }
    if (helpOpen) HelpDialog { helpOpen = false }
}

private const val MENU_NONE = Int.MIN_VALUE
private const val MENU_DOCK = -1

/** The menu key, or Shift+F10 (the standard context-menu chord). */
private fun isMenuKey(ev: androidx.compose.ui.input.key.KeyEvent): Boolean = ev.key == Key.Menu || (ev.key == Key.F10 && ev.isShiftPressed)

/** INP-01: a rail control (not a pinned item) as a node of the shared focus graph; place it before the control's own clickable. */
@Composable
private fun Modifier.graphNode(id: String, bridge: DockInput.FocusBridge?): Modifier {
    if (bridge == null) return this
    val requester = remember { FocusRequester() }
    androidx.compose.runtime.DisposableEffect(id) { onDispose { bridge.unregister(id) } }
    return this
        .onGloballyPositioned { c -> val b = c.boundsInRoot(); bridge.register(id, b.left, b.top, b.right, b.bottom, requester) }
        .focusRequester(requester).onFocusChanged { bridge.onFocus(id, it.isFocused) }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        DockDialogInput() // INP-20: controller window hook and the shared focus layer
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

/** A thin rule between the pinned items and the recent ones; icons only, so no "Recent" text. */
@Composable
private fun SectionDivider(horizontal: Boolean) {
    Box(Modifier.then(if (horizontal) Modifier.width(1.dp).height(28.dp) else Modifier.width(28.dp).height(1.dp)).background(HyleColors.Hairline).semantics { contentDescription = L10n.tr("Recent apps") })
}

/**
 * The home button: a house glyph instead of the workspace name's pill. W26: a tap goes home (it never opens the panel; the panel is behind the
 * long-press menu and Ground Control). A horizontal swipe past the touch slop and 40 px switches workspace (WSP-07 gesture route); a tap cannot
 * reach that. TalkBack gets the same two switches as actions. The name is only its description.
 */
@Composable
private fun WorkspaceChip(name: String, actions: DockActions, bridge: DockInput.FocusBridge?) {
    val tint = HyleColors.InkBright
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(HyleColors.Violet.copy(alpha = 0.22f))
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total > 40f) actions.onSwitchWorkspace(-1) else if (total < -40f) actions.onSwitchWorkspace(1) }) { _, d -> total += d }
            }
            .graphNode("dock.workspaces", bridge)
            .clickable(onClickLabel = L10n.tr("Home"), onClick = actions.onGoHome)
            .semantics {
                role = Role.Button
                contentDescription = L10n.tr("Workspace %s", name)
                customActions = listOf(
                    CustomAccessibilityAction(L10n.tr("Switch to the next workspace")) { actions.onSwitchWorkspace(1); true },
                    CustomAccessibilityAction(L10n.tr("Switch to the previous workspace")) { actions.onSwitchWorkspace(-1); true },
                )
            }
            .drawBehind {
                val w = size.width; val h = size.height
                val roof = Path().apply { moveTo(w * 0.5f, h * 0.24f); lineTo(w * 0.78f, h * 0.48f); lineTo(w * 0.22f, h * 0.48f); close() }
                drawPath(roof, tint)
                drawRect(tint, Offset(w * 0.3f, h * 0.48f), androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.28f))
            },
    )
}

/** Asks for usage access (so Recent can fill): an icon button, a ring with a plus, instead of a sentence. */
@Composable
private fun EnableButton(onClick: () -> Unit, bridge: DockInput.FocusBridge?) {
    val tint = HyleColors.Ink
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(1.dp, HyleColors.Hairline, CircleShape).graphNode("dock.enable", bridge).clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = L10n.tr(DockText.ENABLE_ROW) }
            .drawBehind {
                val c = Offset(size.width / 2, size.height / 2); val r = size.width * 0.2f
                drawLine(tint, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 3f)
                drawLine(tint, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 3f)
            },
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
    bridge: DockInput.FocusBridge? = null,
    widget: (@Composable (DockItem, Dp, Dp) -> Unit)? = null,
    onBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onMenu: () -> Unit = {},
    /** A drag after the long press is a reorder: the menu the press opened closes. */
    onDragBegin: () -> Unit = {},
) {
    val slot = 52.dp
    // HOM-02: a pinned widget takes its span of slots along the rail and one slot across it.
    val wide = item.ref.kind == ItemKind.Widget && widget != null
    val along = if (wide) DockWidgetSlot.lengthDp(item.span).dp else slot
    val boxW = if (horizontal) along else slot
    val boxH = if (horizontal) slot else along
    val slotPx = with(LocalDensity.current) { (slot + 8.dp).toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var focused by remember { mutableStateOf(false) }
    val nodeId = "dock.${item.ref.encode()}"
    androidx.compose.runtime.DisposableEffect(nodeId) { onDispose { bridge?.unregister(nodeId) } }
    val desc = item.label + if (item.recentlyUsed) ", " + L10n.tr("used this session") else ""
    val shape = RoundedCornerShape(16.dp)

    val reorderDrag = Modifier.pointerInput(index, count) {
        detectDragGesturesAfterLongPress(
            onDragStart = { drag = 0f; onMenu() }, // W25: the long press opens the icon's options; moving on from it is a reorder and closes them
            onDragEnd = {
                val to = DockReorder.targetIndex(index, drag, slotPx, count)
                drag = 0f
                if (to != index) actions.onReorder(index, to)
            },
            onDragCancel = { drag = 0f },
        ) { change, amount -> change.consume(); drag += if (horizontal) amount.x else amount.y; if (drag != 0f) onDragBegin() }
    }

    Box(
        Modifier.size(boxW, boxH)
            .graphicsLayer { if (horizontal) translationX = drag else translationY = drag; alpha = if (item.unavailable) 0.4f else if (drag != 0f) 0.8f else 1f }
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, HyleColors.Violet, shape) else Modifier)
            // INP-01: the slot is a node of the shared focus graph (arrows leave the rail into the home and back, the graph knows what has focus).
            .onGloballyPositioned { c -> val b = c.boundsInRoot(); onBounds(b); bridge?.register(nodeId, b.left, b.top, b.right, b.bottom, requester) }
            .focusRequester(requester).onFocusChanged { focused = it.isFocused; bridge?.onFocus(nodeId, it.isFocused) }.focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (isMenuKey(ev)) { onMenu(); return@onPreviewKeyEvent true }
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
            .then(if (reorderable) Modifier.clickable { actions.onLaunch(item, index) } else Modifier.combinedClickable(onLongClick = onMenu) { actions.onLaunch(item, index) }) // W25: a recent icon has no reorder drag, so its long press is the click's own
            // HOM-02: an icon slot starts a reorder with a long press anywhere on it. A widget slot does not: the hosted view takes the touches that
            // start on it (its own taps, scrolls and long-presses), so its reorder lives on the grip strip drawn above it.
            .then(if (reorderable && !wide) reorderDrag else Modifier)
            .semantics {
                contentDescription = desc
                role = Role.Button
                if (reorderable) customActions = listOf(
                    CustomAccessibilityAction(L10n.tr("Move earlier")) { if (index > 0) { actions.onReorder(index, index - 1); true } else false },
                    CustomAccessibilityAction(L10n.tr("Move later")) { actions.onReorder(index, index + 1); true },
                    CustomAccessibilityAction(L10n.tr("Unpin")) { actions.onUnpin(item); true },
                    CustomAccessibilityAction(L10n.tr("Replace with another app")) { actions.onReplace(item); true },
                ) + (if (item.ref.kind == ItemKind.App) listOf(CustomAccessibilityAction(L10n.tr("App info")) { actions.onAppInfo(item); true }) else emptyList()) + CustomAccessibilityAction(L10n.tr("Dock item options")) { onMenu(); true } else customActions = listOf(CustomAccessibilityAction(L10n.tr("Pin to dock")) { actions.onPin(item); true }, CustomAccessibilityAction(L10n.tr("Dock item options")) { onMenu(); true })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (wide) {
            widget!!(item, boxW, boxH)
            // HOM-02: the grip strip along the slot's top edge: long-press and drag reorders the widget, a plain drag on it scrolls the rail.
            if (reorderable) Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(WIDGET_GRIP).testTag(widgetGripTag(item.ref.encode()))
                    .semantics { contentDescription = L10n.tr("Drag to reorder") }.then(reorderDrag),
                contentAlignment = Alignment.TopCenter,
            ) { Box(Modifier.padding(top = 4.dp).size(24.dp, 3.dp).clip(CircleShape).background(HyleColors.InkDim.copy(alpha = 0.7f))) }
        } else icon(item, 40.dp)
        if (item.recentlyUsed) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(8.dp).clip(CircleShape).background(HyleColors.Violet))
        if (showNumbers && index < 9) Text("${index + 1}", color = HyleColors.OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(2.dp).clip(CircleShape).background(HyleColors.Violet).padding(horizontal = 5.dp))
    }
}

/** HOM-02: height of the reorder grip over a widget slot, and its test tag. */
internal val WIDGET_GRIP = 16.dp
fun widgetGripTag(encodedRef: String) = "dock.widgetGrip.$encodedRef"
