package com.bos.sphere.feature.dock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.OneUiDialogSpec
import com.bos.sphere.feature.grid.OneUiMenuTags
import com.bos.sphere.feature.grid.OneUiPrimaryButton
import kotlinx.coroutines.launch

/** What the sheet needs to know about the dock that opened it. The settings it edits are read from [manager] live. */
class DockSheetSpec(
    val manager: WorkspaceManager,
    val tier: Tier,
    /** The window is wider than tall (a phone held sideways): a dock on a side then gets a side sheet. */
    val landscape: Boolean,
    /** The edge a surface fixed for itself (the desktop surface), or null when the stored position decides. */
    val edgeOverride: DockEdge? = null,
    val options: DockOptionsScope = DockOptionsScope(),
    /** Opens the Workspaces and groups page. */
    val onOpenWorkspaces: () -> Unit = {},
)

object DockSheetTags {
    const val ROOT = "dock.sheet.root"
    const val SHEET = "dock.sheet"
    const val TITLE = "dock.sheet.title"
    const val HOME = "dock.sheet.home"
    const val EDIT = "dock.sheet.edit"
    const val WORKSPACES = "dock.sheet.workspaces"
    const val ABOUT = "dock.sheet.about"
    const val DONE = "dock.sheet.done"
    const val EDIT_BAR = "dock.sheet.editBar"
}

/**
 * W55 (dockSheet): the dock's long-press sheet. While it is open the whole home (dock included) is pushed up (a bottom sheet) or sideways (a side
 * sheet beside a side dock) by the sheet's own size, so the dock stays fully visible next to the sheet and the owner edits it live. [open] is
 * the request; [progress] (0 hidden to 1 shown) is what both the sheet and the pushed home follow, a spring, or a snap with reduced motion.
 */
@Stable
class DockSheetState {
    var spec: DockSheetSpec? by mutableStateOf(null)
        private set
    var open: Boolean by mutableStateOf(false)
        private set
    /** Edit dock: the sheet is a compact bar and the dock above it is editable in place. */
    var editing: Boolean by mutableStateOf(false)
        private set
    /** The pushed home and the real dock's rail (published by it), so edit mode can leave exactly the dock touchable. */
    internal var homeCoords: LayoutCoordinates? = null
    internal var dockCoords: LayoutCoordinates? = null

    /** The dock's bounds in the home's own (unpushed) coordinates, or null while either is not on screen. Live: a push in progress does not change it. */
    fun dockRectInHome(): Rect? {
        val h = homeCoords?.takeIf { it.isAttached } ?: return null
        val d = dockCoords?.takeIf { it.isAttached } ?: return null
        return runCatching { h.localBoundingBoxOf(d, false) }.getOrNull()
    }
    internal var sheetSize: IntSize by mutableStateOf(IntSize.Zero)
    internal val progress = Animatable(0f)

    fun show(spec: DockSheetSpec) { this.spec = spec; open = true }
    fun dismiss() { open = false; editing = false }
    fun startEditing() { if (open) editing = true }
    fun stopEditing() { editing = false }

    /** True from the first frame of opening until the last frame of closing. */
    val shown: Boolean get() = open || progress.value > 0.001f
    val shownProgress: Float get() = progress.value
}

val LocalDockSheet = staticCompositionLocalOf<DockSheetState?> { null }

/** The home's translation while the sheet is open: where the sheet is and how far it has come. Pure, so a test can check the geometry. */
internal object DockSheetGeometry {
    /** A side sheet when the dock is on a side in landscape; a bottom sheet otherwise (portrait, tablets, a bottom dock). */
    fun isSide(spec: DockSheetSpec?, edge: DockEdge): Boolean = spec != null && spec.landscape && edge != DockEdge.Bottom

    /** True when the side sheet sits at the physical left edge (the start edge for [DockEdge.Left], the end edge for Right; the rail follows the layout direction). */
    fun physicalLeft(edge: DockEdge, rtl: Boolean): Boolean = (edge == DockEdge.Left) != rtl

    /** The home's translation in pixels at [progress]: up by the sheet's height (less the navigation bar the dock already clears), or sideways by its width. */
    fun homeOffset(side: Boolean, physicalLeft: Boolean, size: IntSize, navBottom: Int, progress: Float): Offset =
        if (side) Offset((if (physicalLeft) 1f else -1f) * size.width * progress, 0f)
        else Offset(0f, -(size.height - navBottom).coerceAtLeast(0) * progress)
}

/**
 * Hosts the sheet above [content] and pushes [content] out of its way. Put it around the launcher's home; every [DockHost] below it opens the
 * sheet through [LocalDockSheet]. With no host in the tree a dock offers no sheet (previews, tests of the rail alone).
 */
@Composable
fun DockSheetHost(state: DockSheetState = remember { DockSheetState() }, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = rememberMotionPrefs().reduced
    LaunchedEffect(state.open, reduced) {
        val target = if (state.open) 1f else 0f
        if (reduced) state.progress.snapTo(target) else state.progress.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 420f))
    }
    val spec = state.spec
    // The edge is read live: choosing another Position card moves the dock, and the sheet follows it to that side.
    val liveDock = spec?.manager?.state?.collectAsState()?.value?.dock
    val edge = if (spec == null) DockEdge.Bottom else spec.edgeOverride ?: liveDock?.edgeFor(spec.landscape) ?: DockEdge.Bottom
    val side = DockSheetGeometry.isSide(spec, edge)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val navBottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    val left = spec != null && DockSheetGeometry.physicalLeft(edge, rtl)
    fun offset() = DockSheetGeometry.homeOffset(side, left, state.sheetSize, navBottom, state.progress.value)

    Box(modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalDockSheet provides state) {
            Box(
                Modifier.fillMaxSize().testTag(DockSheetTags.HOME)
                    .then(if (state.shown) Modifier.graphicsLayer { val o = offset(); translationX = o.x; translationY = o.y } else Modifier)
                    .onGloballyPositioned { state.homeCoords = it }
                    // While the sheet is open the home underneath is not usable: a tap on it closes the sheet, and in edit mode only the dock itself still takes touches.
                    .then(if (state.open) Modifier.pointerInput(state) { blockHome(state) } else Modifier),
            ) { content() }
        }
        if (state.shown && spec != null) DockSheetOverlay(state, spec, side, left)
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.blockHome(state: DockSheetState) {
    awaitEachGesture {
        var downAt: Offset? = null
        var moved = false
        var anyAllowed = false
        do {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            ev.changes.forEach { c ->
                val allowed = state.editing && state.dockRectInHome()?.contains(c.position) == true
                if (c.changedToDown()) { downAt = c.position; anyAllowed = allowed }
                downAt?.let { if ((c.position - it).getDistance() > viewConfiguration.touchSlop) moved = true }
                if (!allowed) {
                    if (c.changedToUp() && !moved && !state.editing && !anyAllowed) state.dismiss()
                    c.consume()
                }
            }
        } while (ev.changes.any { it.pressed })
    }
}

@Composable
private fun DockSheetOverlay(state: DockSheetState, spec: DockSheetSpec, side: Boolean, physicalLeft: Boolean) {
    DockInput.dialogHook() // INP-20: the sheet counts as a dock dialog (a focus layer above the home, controller window hook where there is a window)
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val toward = if (side) (if (physicalLeft) -1f else 1f) else 1f // the sign of a drag that closes the sheet
    val back = { if (state.editing) state.stopEditing() else state.dismiss() }
    BackHandler(enabled = state.open, onBack = back)
    var about by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier.fillMaxSize().testTag(DockSheetTags.ROOT)
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && (it.key == Key.Escape || it.key == Key.ButtonB)) { back(); true } else false }
            .focusRequester(focus).focusable(),
    ) {
        val maxH = maxHeight * 0.62f
        val extent = (if (side) state.sheetSize.width else state.sheetSize.height).toFloat().coerceAtLeast(1f)
        // Physical corners and physical edges: the sheet sits on the side the dock is on, and the dock's side already followed the layout direction.
        val r = OneUiDialogSpec.SHEET_CORNER_DP.dp
        val shape = when {
            !side -> AbsoluteRoundedCornerShape(topLeft = r, topRight = r)
            physicalLeft -> AbsoluteRoundedCornerShape(topRight = r, bottomRight = r)
            else -> AbsoluteRoundedCornerShape(topLeft = r, bottomLeft = r)
        }
        val drag = rememberDraggableState { d -> scope.launch { state.progress.snapTo((state.progress.value - toward * d / extent).coerceIn(0f, 1f)) } }
        val orientation = if (side) Orientation.Horizontal else Orientation.Vertical
        val align = when { !side -> Alignment.BottomCenter; physicalLeft -> BiasAbsoluteAlignment(-1f, 0f); else -> BiasAbsoluteAlignment(1f, 0f) }
        Column(
            Modifier.align(align).then(if (side) Modifier.width(minOf(maxWidth * 0.46f, 380.dp)).fillMaxHeight() else Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(max = maxH))
                .testTag(DockSheetTags.SHEET)
                .onSizeChanged { state.sheetSize = it }
                .graphicsLayer {
                    // Hidden until measured, so the first frame never shows the sheet in its final place before it slides in.
                    alpha = if (state.sheetSize == IntSize.Zero) 0f else 1f
                    val away = (1f - state.progress.value) * (if (side) state.sheetSize.width else state.sheetSize.height)
                    if (side) translationX = toward * away else translationY = away
                }
                .clip(shape).background(HyleColors.Background, shape).border(1.dp, HyleColors.Hairline, shape)
                .semantics { paneTitle = L10n.tr("Dock") },
        ) {
            val insets = if (side) Modifier.safeDrawingPadding() else Modifier.navigationBarsPadding()
            Column(insets) {
                // The header is the drag handle: grabber and title. A drag toward the edge closes the sheet and the home follows it back.
                Column(
                    Modifier.fillMaxWidth().draggable(drag, orientation, onDragStopped = { v ->
                        val towardV = toward * v
                        if (state.progress.value < 0.7f || towardV > 1500f) state.dismiss() else scope.launch { state.progress.animateTo(1f, spring(stiffness = 500f)) }
                    }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(HyleColors.InkDim.copy(alpha = 0.6f)).testTag(OneUiMenuTags.GRABBER))
                    Text(
                        if (state.editing) L10n.tr("Edit dock") else L10n.tr("Dock"),
                        color = HyleColors.InkBright, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).testTag(DockSheetTags.TITLE).semantics { heading() },
                    )
                }
                if (state.editing) EditBar(state)
                else Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                    SheetBody(state, spec, onAbout = { about = true })
                }
            }
        }
    }
    if (about) AboutDockDialog { about = false }
}

@Composable
private fun SheetBody(state: DockSheetState, spec: DockSheetSpec, onAbout: () -> Unit) {
    val dockState by spec.manager.state.collectAsState()
    val dockOn = dockState.dock.isEnabled(spec.tier) || !spec.options.canHide
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DockEntryTile(Glyphs.Sliders, L10n.tr("Edit dock"), DockSheetTags.EDIT, Modifier.weight(1f), enabled = dockOn) { state.startEditing() }
        DockEntryTile(Glyphs.Home, L10n.tr("Workspaces and groups"), DockSheetTags.WORKSPACES, Modifier.weight(1f)) { state.dismiss(); spec.onOpenWorkspaces() }
        DockEntryTile(Glyphs.Info, L10n.tr("About the dock"), DockSheetTags.ABOUT, Modifier.weight(1f), onClick = onAbout)
    }
    androidx.compose.runtime.CompositionLocalProvider(com.bos.sphere.feature.grid.LocalOneUiDialogHook provides DockDialogHook) {
        DockOptionsBody(spec.manager, spec.tier, spec.options, landscape = spec.landscape)
        if (spec.options.canHide) DockUseDefaultRow(spec.manager)
    }
}

/** Edit dock: the sheet shrinks to this bar; the dock above it is the editor (drag to reorder, x to remove, plus to add). */
@Composable
private fun EditBar(state: DockSheetState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag(DockSheetTags.EDIT_BAR), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            L10n.tr("Drag an icon to reorder it. Tap the x to remove it. Tap + to add apps."),
            color = HyleColors.InkDim, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )
        OneUiPrimaryButton(L10n.tr("Done"), { state.stopEditing() }, Modifier.testTag(DockSheetTags.DONE), glyph = Glyphs.Check)
    }
}

/** The recency explanation and the reorder hint; the sheet's "About the dock". */
@Composable
internal fun AboutDockDialog(onDismiss: () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(com.bos.sphere.feature.grid.LocalOneUiDialogHook provides DockDialogHook) {
        com.bos.sphere.feature.grid.OneUiDialog(
            L10n.tr("About the dock"), onDismiss, cancelLabel = null, confirmLabel = L10n.tr("Done"), onConfirm = onDismiss,
            body = L10n.tr(DockText.RECENCY_HELP),
        ) {
            Text(
                L10n.tr("Alt with an arrow key, or a long press and drag, reorders pinned items."), color = HyleColors.InkDim, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
    }
}
