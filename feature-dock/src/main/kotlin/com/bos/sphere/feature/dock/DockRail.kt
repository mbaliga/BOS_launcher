package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.semantics.stateDescription
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
    /** W55 (dockSheet): a long press on the dock, the menu key, or the TalkBack action opens the dock's sheet; [onEditDock] opens it straight into edit. */
    val onOpenOptions: () -> Unit = {},
    val onEditDock: () -> Unit = {},
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
    /** W39 (dockThemes): how the dock is drawn; [dark] picks the 3D slab's variant (default: the UI theme). */
    style: com.bos.sphere.core.data.DockStyle = com.bos.sphere.core.data.DockStyle.Standard,
    dark: Boolean = !com.bos.sphere.core.design.HyleTheme.isLight,
    /** W56 (dockMagnify): icons grow around the finger, mouse or focus by this strength; null = off. Never while [editing]. */
    magnify: MagnifyStrength? = null,
    /** W55 (dockSheet): the dock is being edited in place (the sheet's Edit dock): icons wiggle, a drag reorders, an x removes, a plus adds. */
    editing: Boolean = false,
    /** W55: gets the rail's layout coordinates, so the sheet's host can leave exactly the dock touchable while editing. */
    onCoordinates: ((androidx.compose.ui.layout.LayoutCoordinates) -> Unit)? = null,
    /**
     * Wave 58 (CFG-73): a still sample pointer for the Settings Preview, as a fraction (0..1) along the row: with [magnify] on, the row is drawn
     * magnified there as if a finger rested on it (no tooltip, nothing pressed). Null (the default) is the real dock: only a finger, a pointer or the focus magnify.
     */
    demoPointer: Float? = null,
    icon: @Composable (DockItem, Dp) -> Unit,
) {
    val horizontal = edge == DockEdge.Bottom
    val collapsedBand = content.collapsed && !expanded && !editing
    // W25: an icon's long-press menu: null or the icon's index; [menuPress] is where it opens, in window pixels.
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

    val openDock = { if (!editing) actions.onOpenOptions() }
    val openItemMenu = { i: Int ->
        val b = slotBounds[i]
        val at = if (b != null) Offset(b.center.x, b.bottom) else railOrigin
        menuPress = androidx.compose.ui.unit.IntOffset(at.x.toInt(), at.y.toInt()); menuFor = i
    }
    val shape = RoundedCornerShape(22.dp)
    val surface = Modifier.dockSurface(style, dark, edge, shape)
    val glyph = glyphColor(style, HyleColors.InkDim)
    val threeD = style == com.bos.sphere.core.data.DockStyle.ThreeD

    if (collapsedBand) {
        // The collapsed band: a slim grab strip that expands the dock on tap, Enter or the dock-focus shortcut.
        Box(
            modifier.then(if (horizontal) Modifier.width(120.dp).height(18.dp) else Modifier.width(18.dp).height(120.dp))
                .then(surface).clickable(onClickLabel = L10n.tr("Show dock"), onClick = actions.onToggleExpanded)
                .semantics { role = Role.Button; contentDescription = L10n.tr("Dock, collapsed") },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.then(if (horizontal) Modifier.width(36.dp).height(4.dp) else Modifier.width(4.dp).height(36.dp)).glyphHalo(style, RoundedCornerShape(50)).clip(CircleShape).background(glyph))
        }
        LaunchedEffect(focusRequest) { if (focusRequest > 0 && !expanded) actions.onToggleExpanded() }
        return
    }

    // W56 (dockMagnify): one pointer state for the whole row (a finger, a mouse or the focus), read only by layers; the tooltip is on for the 3D look and with magnification.
    val magnifyOn = magnify != null && !editing
    val field = if (magnifyOn || (threeD && !editing)) remember { MagnifyField() } else null
    val dens = LocalDensity.current.density
    var availMain by remember { mutableIntStateOf(0) }
    var restMain by remember { mutableIntStateOf(0) }
    var engaged by remember { mutableStateOf(false) }
    val reduced = com.bos.sphere.core.design.rememberMotionPrefs().reduced
    // W60 (dockMagnifyFix): how far outside the rail's own measured bounds a finger may drift before the press-drag-release gesture cancels — a
    // small forgiving margin (roughly a third of a slot), so a finger that is still clearly "on" the strip (just a few px past its hit-tested edge,
    // the usual slop of a real touch) is not punished, while a finger dragged meaningfully away (up off a bottom dock, say) still cancels promptly.
    val gestureMarginPx = 18f * dens
    if (field != null) {
        SideEffect {
            field.density = dens; field.horizontal = horizontal; field.edge = edge; field.factor = if (magnifyOn) magnify!!.factor else 1f
            field.pitch = DockMagnify.PITCH_DP * dens; field.gapHalf = (DockMagnify.PITCH_DP - DockMagnify.SLOT_DP) / 2f * dens
            field.lo = 0f; field.hi = (if (horizontal) railSize.width else railSize.height).toFloat()
            field.demo = if (magnifyOn) demoPointer else null
            // W60 (dockMagnifyFix): the press-drag-release gesture (see trackMagnify's doc). Off while editing or with magnify off: a per-item
            // clickable keeps launching exactly as it always did. Refreshed every recomposition so the long-lived tracking coroutine always sees
            // the current rail bounds, items and whether a menu is open, without ever needing to restart (which would drop the finger mid-gesture).
            field.gestureActive = magnifyOn
            field.insideBounds = { p ->
                p.x >= -gestureMarginPx && p.x <= railSize.width + gestureMarginPx && p.y >= -gestureMarginPx && p.y <= railSize.height + gestureMarginPx
            }
            field.onGestureRelease = { cancelled, at ->
                if (!cancelled && menuFor < 0) {
                    val main = if (horizontal) at.x else at.y
                    val r = field.result.value
                    val i = r.indexAt(main)
                    val idx = if (i >= 0) r.ids[i] - 1 else -1
                    if (idx in items.indices) actions.onLaunch(items[idx], idx)
                }
            }
        }
        LaunchedEffect(field) { snapshotFlow { field.engaged }.collect { engaged = it } }
        LaunchedEffect(field, reduced) {
            snapshotFlow { if (field.wantsMagnify) 1f else 0f }.collectLatest { target ->
                if (reduced) field.amount = target
                else androidx.compose.animation.core.animate(field.amount, target, animationSpec = androidx.compose.animation.core.spring(if (target > 0f) 0.72f else 0.8f, 420f)) { v, _ -> field.amount = v }
            }
        }
        LaunchedEffect(field, reduced) {
            // Keyboard or D-pad focus moves the effect along the row: the pointer follows the focused icon.
            snapshotFlow { field.focusId to field.pointerDown }.collectLatest { (fid, down) ->
                val target = if (down || fid == MagnifyField.NONE) null else field.focusTarget()
                if (target != null) {
                    if (reduced || field.amount < 0.01f) field.pointer = target
                    else androidx.compose.animation.core.animate(field.pointer, target, animationSpec = androidx.compose.animation.core.spring(0.85f, 500f)) { v, _ -> field.pointer = v }
                }
            }
        }
    }
    // While it is touched the rail asks for room: the spread has somewhere to go at the ends (never more than the parent allows) and the grown icons have headroom.
    val padded = magnifyOn && engaged
    val slackDp = if (padded) (minOf(DockMagnify.slackDp(magnify!!) * dens, maxOf(0f, (availMain - restMain) / 2f))) / dens else 0f
    val headDp = if (padded) DockMagnify.headDp(magnify!!) else 0f
    val base = 10f
    val shelfSide = if (threeD) DockStyleTokens.shelfSidePad(base) else base
    val padL = when (edge) { DockEdge.Bottom -> base + slackDp; DockEdge.Left -> shelfSide; DockEdge.Right -> base + headDp }
    val padR = when (edge) { DockEdge.Bottom -> base + slackDp; DockEdge.Left -> base + headDp; DockEdge.Right -> shelfSide }
    val padT = when (edge) { DockEdge.Bottom -> base + headDp; else -> base + slackDp }
    val padB = when (edge) { DockEdge.Bottom -> shelfSide; else -> base + slackDp }
    val pad = Modifier.absolutePadding(padL.dp, padT.dp, padR.dp, padB.dp)
    val seatAlign = when (edge) { DockEdge.Left -> Alignment.End; DockEdge.Right -> Alignment.Start; else -> Alignment.CenterHorizontally }
    @Composable fun Strip(body: @Composable () -> Unit) {
        if (horizontal) Row(Modifier.horizontalScroll(rememberScrollState()).then(pad), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = if (threeD) Alignment.Top else Alignment.CenterVertically) { body() }
        else Column(Modifier.verticalScroll(rememberScrollState()).then(pad), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = seatAlign) { body() }
    }

    Box(
        modifier.then(if (field != null) Modifier.layout { m, c -> availMain = if (horizontal) c.maxWidth else c.maxHeight; val p = m.measure(c); layout(p.width, p.height) { p.place(0, 0) } } else Modifier)
            .onGloballyPositioned { c ->
                railOrigin = c.positionInRoot(); railSize = c.size; onCoordinates?.invoke(c)
                field?.viewport = c; field?.refreshAll()
                // W60 (dockMagnifyFix): set [lo]/[hi] straight from this callback's own measured size, not only through the `SideEffect` below that
                // reads the `railSize` state var — a recomposition that changes `padded` (engaging the slack) can relayout the rail, growing its
                // real measured size, several frames before anything schedules DockRail to recompose again and re-run that `SideEffect`, which left
                // `compute()` clamping the spread to the OLD, smaller room long after the rail had actually grown to hold it (part of the root cause
                // behind the owner's screenshot: a plainly non-overflowing dock still getting cropped once touched). This keeps the room compute()
                // sees always exactly what was just measured, with no extra round trip through recomposition.
                if (field != null) { field.lo = 0f; field.hi = (if (horizontal) c.size.width else c.size.height).toFloat() }
                if (!padded) restMain = if (horizontal) c.size.width else c.size.height
            }
            .then(if (field != null) Modifier.pointerInput(field) { trackMagnify(field) } else Modifier)
            // W25: a long press on the dock (an icon or the empty part) opens its options; the menu key and Shift+F10 do the same from the keyboard.
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { p ->
                    val root = Offset(p.x + railOrigin.x, p.y + railOrigin.y)
                    val magnified = if (field != null && field.amount > 0.05f) field.result.value.let { r -> r.indexAt(if (horizontal) p.x else p.y).let { k -> if (k >= 0) r.ids[k] - 1 else -1 } }.takeIf { it in items.indices } else null
                    val hit = magnified ?: slotBounds.entries.firstOrNull { it.value.contains(root) }?.key
                    if (hit != null) { if (!editing) openItemMenu(hit) } else openDock()
                })
            }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && isMenuKey(ev)) { openDock(); true } else false
            }
            .semantics {
                contentDescription = L10n.tr("Dock")
                stateDescription = DockStyleUi.stateText(style)
                // TalkBack: every long-press action is also an action here.
                customActions = listOf(
                    CustomAccessibilityAction(L10n.tr("Dock options")) { actions.onOpenOptions(); true },
                    CustomAccessibilityAction(L10n.tr("Edit dock")) { actions.onEditDock(); true },
                    CustomAccessibilityAction(L10n.tr("Profiles and groups")) { actions.onOpenPanel(); true },
                )
            },
    ) {
        // The dock's own surface (the pill, nothing, or the glass shelf) behind the row. It grows with the spread so the icons never leave it.
        Box(
            Modifier.matchParentSize().absolutePadding(if (edge == DockEdge.Bottom) slackDp.dp else if (edge == DockEdge.Right) headDp.dp else 0.dp, if (edge == DockEdge.Bottom) headDp.dp else slackDp.dp, if (edge == DockEdge.Bottom) slackDp.dp else if (edge == DockEdge.Left) headDp.dp else 0.dp, if (edge == DockEdge.Bottom) 0.dp else slackDp.dp)
                .then(if (field != null && magnifyOn) Modifier.graphicsLayer {
                    val r = field.result.value
                    if (r.size > 0 && field.amount != 0f) {
                        // W60 (dockMagnifyFix): the same pure function the stress test checks, fed the exact fields the icon layers draw from — so the
                        // shelf can never disagree with (or grow past) the icons' own, now always-contained, geometry.
                        val (gs, ge) = DockMagnify.surfaceGrowth(r.baseStart, r.baseEnd, r.start, r.end)
                        val len = if (horizontal) size.width else size.height
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        if (horizontal) { scaleX = (len + gs + ge) / len; translationX = -gs } else { scaleY = (len + gs + ge) / len; translationY = -gs }
                    }
                } else Modifier)
                .then(surface),
        )
        Strip {
            WorkspaceChip(workspaceName, actions, graphBridge, style, edge, field)
            if (editing) DockAddTile(actions.onAddApps, graphBridge, style) // W55: the plus tile (first, so a long row never scrolls it out of reach) opens the app picker
            content.pinned.forEachIndexed { i, item ->
                DockSlot(item, i, items.size, horizontal, showNumbers, icon, actions, requesters[i], items, onFocusMove = { requesters.getOrNull(it)?.runCatching { requestFocus() } }, onMoved = { pendingFocus = it }, bridge = graphBridge, widget = widget, style = style, dark = dark, editing = editing,
                    onBounds = { slotBounds[i] = it }, onMenu = { openItemMenu(i) }, onDragBegin = { menuFor = MENU_NONE }, edge = edge, field = field, magnifyOn = magnifyOn)
            }
            if (!editing && content.showRecentSection && content.recent.isNotEmpty()) {
                SectionDivider(horizontal, style, edge, field)
                content.recent.forEachIndexed { j, item ->
                    val i = content.pinned.size + j
                    DockSlot(item, i, items.size, horizontal, showNumbers, icon, actions, requesters[i], items, onFocusMove = { requesters.getOrNull(it)?.runCatching { requestFocus() } }, onMoved = {}, reorderable = false, bridge = graphBridge, style = style, dark = dark,
                        onBounds = { slotBounds[i] = it }, onMenu = { openItemMenu(i) }, onDragBegin = { menuFor = MENU_NONE }, edge = edge, field = field, magnifyOn = magnifyOn)
                }
            }
            if (!editing && content.showEnableRow) EnableButton(actions.onEnableUsage, graphBridge, style, edge, field)
        }
        if (field != null && menuFor < 0) DockTooltipLayer(field, { railOrigin.x }, { railOrigin.y })
    }
    if (menuFor >= 0 && !editing) items.getOrNull(menuFor)?.let { item ->
        val extra by androidx.compose.runtime.produceState(emptyList<DockMenuEntry>(), item) { value = runCatching { itemExtras(item) }.getOrDefault(emptyList()) }
        DockMenuPopup(menuPress, DockMenuModel.TAG_ITEM_MENU, DockMenuModel.item(item, menuFor, items.size, menuFor < content.pinned.size, actions, extra)) { menuFor = MENU_NONE }
    }
}

private const val MENU_NONE = Int.MIN_VALUE
private const val CHIP_ID = 0
private const val DIVIDER_ID = 500
private const val ENABLE_ID = 1000

/** W56: in the 3D look every member of the row stands on the shelf: its seat is the slot's icon margin, measured from the side away from the shelf ([extra] lowers it further). */
private fun Modifier.seat(style: com.bos.sphere.core.data.DockStyle, edge: DockEdge, extra: Dp = 0.dp): Modifier {
    if (style != com.bos.sphere.core.data.DockStyle.ThreeD) return this
    val m = (DockStyleTokens.SLOT_MARGIN_DP).dp + extra
    return when (edge) { DockEdge.Bottom -> padding(top = m); DockEdge.Left -> absolutePadding(right = m); DockEdge.Right -> absolutePadding(left = m) }
}

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

/** A thin rule between the pinned items and the recent ones; icons only, so no "Recent" text. */
@Composable
private fun SectionDivider(horizontal: Boolean, style: com.bos.sphere.core.data.DockStyle, edge: DockEdge, field: MagnifyField?) {
    val desc = Modifier.seat(style, edge, 6.dp).magnifyMember(field, DIVIDER_ID, "", magnifiable = false, outerFrac = 1f).semantics { contentDescription = L10n.tr("Recent apps") }
    if (style == com.bos.sphere.core.data.DockStyle.Transparent) {
        // Over the wallpaper a thin white rule would vanish on a light one: it sits on a dark halo strip.
        Box(
            Modifier.then(desc).then(if (horizontal) Modifier.width(5.dp).height(30.dp) else Modifier.width(30.dp).height(5.dp)).clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = DockStyleTokens.HALO_ALPHA)),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.then(if (horizontal) Modifier.width(1.dp).height(26.dp) else Modifier.width(26.dp).height(1.dp)).background(androidx.compose.ui.graphics.Color(DockStyleTokens.GLYPH))) }
        return
    }
    Box(Modifier.then(desc).then(if (horizontal) Modifier.width(1.dp).height(28.dp) else Modifier.width(28.dp).height(1.dp)).background(HyleColors.Hairline))
}

/**
 * The home button: a house glyph instead of the workspace name's pill. W26: a tap goes home (it never opens the panel; the panel is behind the
 * long-press menu and Ground Control). A horizontal swipe past the touch slop and 40 px switches workspace (WSP-07 gesture route); a tap cannot
 * reach that. TalkBack gets the same two switches as actions. The name is only its description.
 */
@Composable
private fun WorkspaceChip(name: String, actions: DockActions, bridge: DockInput.FocusBridge?, style: com.bos.sphere.core.data.DockStyle, edge: DockEdge, field: MagnifyField?) {
    val tint = glyphColor(style, HyleColors.InkBright)
    Box(
        Modifier.seat(style, edge).size(40.dp).magnifyMember(field, CHIP_ID, L10n.tr("Home"), outerFrac = 1f).clip(CircleShape).glyphHalo(style).background(HyleColors.Violet.copy(alpha = 0.22f))
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total > 40f) actions.onSwitchWorkspace(-1) else if (total < -40f) actions.onSwitchWorkspace(1) }) { _, d -> total += d }
            }
            .graphNode("dock.workspaces", bridge)
            .clickable(onClickLabel = L10n.tr("Home"), onClick = actions.onGoHome)
            .semantics {
                role = Role.Button
                contentDescription = L10n.tr("Profile %s", name)
                customActions = listOf(
                    CustomAccessibilityAction(L10n.tr("Switch to the next profile")) { actions.onSwitchWorkspace(1); true },
                    CustomAccessibilityAction(L10n.tr("Switch to the previous profile")) { actions.onSwitchWorkspace(-1); true },
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
private fun EnableButton(onClick: () -> Unit, bridge: DockInput.FocusBridge?, style: com.bos.sphere.core.data.DockStyle, edge: DockEdge, field: MagnifyField?) {
    val tint = glyphColor(style, HyleColors.Ink)
    Box(
        Modifier.seat(style, edge).size(40.dp).magnifyMember(field, ENABLE_ID, L10n.tr(DockText.ENABLE_ROW), outerFrac = 1f).clip(CircleShape).glyphHalo(style).border(1.dp, if (style == com.bos.sphere.core.data.DockStyle.Transparent) tint else HyleColors.Hairline, CircleShape).graphNode("dock.enable", bridge).clickable(onClick = onClick)
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
    style: com.bos.sphere.core.data.DockStyle = com.bos.sphere.core.data.DockStyle.Standard,
    dark: Boolean = true,
    editing: Boolean = false,
    edge: DockEdge = DockEdge.Bottom,
    field: MagnifyField? = null,
    /** W60 (dockMagnifyFix): while this is on, the slot's own clickable no longer launches on its own — the rail's press-drag-release gesture
     * (trackMagnify's `onGestureRelease`) does all the launching, for both a plain tap and a drag release, so the two can never double-fire. */
    magnifyOn: Boolean = false,
) {
    val slot = 52.dp
    // HOM-02: a pinned widget takes its span of slots along the rail and one slot across it.
    val wide = item.ref.kind == ItemKind.Widget && widget != null
    val along = if (wide) DockWidgetSlot.lengthDp(item.span).dp else slot
    val threeD = style == com.bos.sphere.core.data.DockStyle.ThreeD && !wide
    val extra = if (threeD) DockStyleTokens.SLOT_EXTRA_DP.dp else 0.dp // 3D: floor room for the reflection, on the shelf's side
    val boxW = if (horizontal) along else slot + extra
    val boxH = if (horizontal) slot + extra else along
    val memberId = index + 1
    val outerFrac = if (threeD) (DockStyleTokens.SLOT_MARGIN_DP + DockStyleTokens.ICON_DP) / (DockStyleTokens.SLOT_DP + DockStyleTokens.SLOT_EXTRA_DP) else DockMagnify.OUTER_FRAC
    val slotPx = with(LocalDensity.current) { (slot + 8.dp).toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var focused by remember { mutableStateOf(false) }
    val nodeId = "dock.${item.ref.encode()}"
    androidx.compose.runtime.DisposableEffect(nodeId) { onDispose { bridge?.unregister(nodeId) } }
    val desc = item.label + if (item.recentlyUsed) ", " + L10n.tr("used this session") else ""
    val shape = RoundedCornerShape(16.dp)

    // W55 (dockSheet): while the dock is edited in place, a plain drag (no long press first) reorders, and every icon wiggles (not with reduced motion).
    val wiggle = if (editing && reorderable) rememberDockWiggle(index) else 0f
    val editDrag = Modifier.pointerInput(index, count, editing) {
        detectDragGestures(
            onDragStart = { drag = 0f },
            onDragEnd = {
                val to = DockReorder.targetIndex(index, drag, slotPx, count)
                drag = 0f
                if (to != index) actions.onReorder(index, to)
            },
            onDragCancel = { drag = 0f },
        ) { change, amount -> change.consume(); drag += if (horizontal) amount.x else amount.y }
    }
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

    val noRippleSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val motion = remember(field, memberId) { field?.let { f -> IconMotion({ f.scaleOf(memberId) }, { f.liftPx(memberId) }) } }
    Box(
        Modifier.size(boxW, boxH)
            .magnifyMember(field, memberId, desc, magnifiable = !wide && !editing, outerFrac = outerFrac)
            .graphicsLayer { if (horizontal) translationX = drag else translationY = drag; rotationZ = wiggle; alpha = if (item.unavailable) 0.4f else if (drag != 0f) 0.8f else 1f }
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, HyleColors.Violet, shape) else Modifier)
            // INP-01: the slot is a node of the shared focus graph (arrows leave the rail into the home and back, the graph knows what has focus).
            .onGloballyPositioned { c -> val b = c.boundsInRoot(); onBounds(b); bridge?.register(nodeId, b.left, b.top, b.right, b.bottom, requester) }
            .focusRequester(requester).onFocusChanged { focused = it.isFocused; bridge?.onFocus(nodeId, it.isFocused); field?.let { f -> if (it.isFocused) f.focusId = memberId else if (f.focusId == memberId) f.focusId = MagnifyField.NONE } }.focusable()
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
            // W60 (dockMagnifyFix): while magnify is on, the rail's own press-drag-release gesture does the launching (see trackMagnify's doc and
            // the rail's `onGestureRelease`); this modifier still supplies the ripple/indication, the role and (for a recent item) the long click,
            // but its own `onClick` is a no-op so a plain tap is never launched twice.
            .then(if (editing) Modifier else if (reorderable) {
                // W56: in the 3D look the press feedback is the lifted icon and its tooltip (a ripple over the whole slot reads as a dark box on glass)
                if (threeD) Modifier.clickable(interactionSource = noRippleSource, indication = null) { if (!magnifyOn) actions.onLaunch(item, index) }
                else Modifier.clickable { if (!magnifyOn) actions.onLaunch(item, index) }
            } else if (threeD) Modifier.combinedClickable(interactionSource = noRippleSource, indication = null, onLongClick = onMenu) { if (!magnifyOn) actions.onLaunch(item, index) }
            else Modifier.combinedClickable(onLongClick = onMenu) { if (!magnifyOn) actions.onLaunch(item, index) }) // W25: a recent icon has no reorder drag, so its long press is the click's own
            // HOM-02: an icon slot starts a reorder with a long press anywhere on it. A widget slot does not: the hosted view takes the touches that
            // start on it (its own taps, scrolls and long-presses), so its reorder lives on the grip strip drawn above it.
            .then(if (editing && reorderable && !wide) editDrag else if (reorderable && !wide) reorderDrag else Modifier)
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
        contentAlignment = if (!threeD) Alignment.Center else when (edge) { DockEdge.Bottom -> Alignment.TopCenter; DockEdge.Left -> Alignment.CenterEnd; DockEdge.Right -> Alignment.CenterStart },
    ) {
        if (wide) {
            widget!!(item, boxW, boxH)
            // HOM-02: the grip strip along the slot's top edge: long-press and drag reorders the widget, a plain drag on it scrolls the rail.
            if (reorderable) Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(WIDGET_GRIP).testTag(widgetGripTag(item.ref.encode()))
                    .semantics { contentDescription = L10n.tr("Drag to reorder") }.then(reorderDrag),
                contentAlignment = Alignment.TopCenter,
            ) { Box(Modifier.padding(top = 4.dp).size(24.dp, 3.dp).clip(CircleShape).background(HyleColors.InkDim.copy(alpha = 0.7f))) }
        } else Box(if (threeD) Modifier.seat(style, edge) else Modifier) { StyledIcon(style, dark, item, 40.dp, edge, DockStyleTokens.REFLECT_DP.dp, motion, icon) }
        if (editing && reorderable) DockRemoveBadge(item, actions.onUnpin)
        // The active dot keeps its place in every style; over the wallpaper (Transparent) it gets a dark ring so it reads on a light one.
        if (item.recentlyUsed && threeD) ShelfDot(edge, dark, motion, Modifier.align(when (edge) { DockEdge.Bottom -> Alignment.BottomCenter; DockEdge.Left -> Alignment.CenterStart; DockEdge.Right -> Alignment.CenterEnd }))
        else if (item.recentlyUsed) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(8.dp).clip(CircleShape).background(HyleColors.Violet).then(if (style == com.bos.sphere.core.data.DockStyle.Transparent) Modifier.border(1.5.dp, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f), CircleShape) else Modifier).testTag("dock.activeDot"))
        if (showNumbers && index < 9) Text("${index + 1}", color = HyleColors.OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(2.dp).clip(CircleShape).background(HyleColors.Violet).padding(horizontal = 5.dp))
    }
}

/** HOM-02: height of the reorder grip over a widget slot, and its test tag. */
internal val WIDGET_GRIP = 16.dp
fun widgetGripTag(encodedRef: String) = "dock.widgetGrip.$encodedRef"

/** W56: the running dot of the 3D look: a small light dot on the shelf's lip under the icon, kept at its place on the lip while the icon grows and lifts. */
@Composable
private fun ShelfDot(edge: DockEdge, dark: Boolean, motion: IconMotion?, modifier: Modifier) {
    val d = 13f // from the icon's foot to the dot, dp
    Box(
        modifier.then(when (edge) { DockEdge.Bottom -> Modifier.padding(bottom = 1.dp); DockEdge.Left -> Modifier.padding(start = 1.dp); DockEdge.Right -> Modifier.padding(end = 1.dp) })
            .size(4.dp).testTag("dock.activeDot")
            .graphicsLayer {
                val s = motion?.scale?.invoke() ?: 1f
                val lift = motion?.lift?.invoke() ?: 0f
                val shift = ((d * density + lift) / s - d * density)
                scaleX = 1f / s; scaleY = 1f / s
                when (edge) { DockEdge.Bottom -> translationY = shift; DockEdge.Left -> translationX = -shift; DockEdge.Right -> translationX = shift }
            }
            .clip(CircleShape).background(if (dark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f) else androidx.compose.ui.graphics.Color(0xFF283044)),
    )
}
